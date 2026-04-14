# Yocto BSP for LuckFox Pico Ultra W (RV1106G3)

Yocto (scarthgap) Board Support Package for the LuckFox Pico Ultra W,
built around the Rockchip RV1106G3 SoC.

## Hardware

- **Board:** LuckFox Pico Ultra W
- **SoC:** Rockchip RV1106G3 (single Cortex-A7 @ 1.2GHz, 32-bit)
- **RAM:** 128MB DDR3L (in-package, detected by DDR init blob)
- **Storage:** 8GB eMMC (Samsung AT2S38, HS200)
- **Ethernet:** 100Mbps RMII (integrated RK630 PHY)
- **WiFi/BT:** AIC8800DC (WiFi 6 2.4GHz SDIO + BLE 5.2 UART)
- **NPU:** 0.5 TOPS RKNPU (INT8, rv1106 variant)
- **Display:** 4.0" round LCD, 720x720, RGB parallel interface
- **Audio:** Built-in ADC codec (rv1106-acodec), microphone input
- **Serial console:** UART2, ttyFIQ0, 115200 baud (pins 42/43 on P1 header)

## Current Status

### Working first-boot (clean flash, zero intervention)

- [x] Boot chain → multi-user.target (no emergency mode)
- [x] Upstream Linux Tux logo at kernel handoff
- [x] Kernel 5.10.160 (Luckfox SDK)
- [x] eMMC (HS200), ext4 root, systemd + journald
- [x] Ethernet 100 Mbps via systemd-networkd DHCP
- [x] Serial console (ttyFIQ0, 115200)
- [x] USB-A host mode
- [x] **NPU inference: 3 ms / 332 FPS** (BlazeFace, INT8), auto-loads via modules-load.d
- [x] Microphone: stereo 16 kHz S16_LE via rv1106-acodec
- [x] **4" 720x720 RGB panel** + fbcon + tty1 visible
- [x] Backlight PWM1
- [x] **GT911 touchscreen** on `/dev/input/event0`
- [x] `work` LED (GPIO3_C6) heartbeat, toggleable from the webserver
- [x] RTC, I2C0/3/4, PWM0/1, GPIO, SPI, SDIO
- [x] **AIC8800DC WiFi** — driver auto-loads, `wpa_supplicant-nl80211@wlan0` running, DHCP via systemd-networkd. Credentials come from `LUCKFOX_WIFI_SSID` / `LUCKFOX_WIFI_PSK` / `LUCKFOX_WIFI_COUNTRY` in your `conf/local.conf` at build time (see below). A placeholder config ships if they're unset so the service still starts.
- [x] **AIC8800DC Bluetooth** — `hci0`, BT 5.2, UART HCI @ 1.5 Mbaud, BlueZ `bluetoothd` active, advertises the hostname as its friendly name. `hciattach` is driven by a `luckfox-bt-attach.service` systemd unit that waits for the wifi driver to load first.
- [x] **`luckfox-pico-status` webserver** (lighttpd + CGI) at `http://<ip>/` or `http://luckfox-pico.local/` via avahi mDNS
- [x] **LVGL v9.2 framebuffer demo** (`luckfox-lvgl-demo`) — built but not auto-started. `systemctl enable --now luckfox-lvgl-demo` to take the framebuffer over from fbcon. Shows title, breathing arc, live clock, stats row, touch-counter.

### Deferred (see `docs/ROADMAP.md`)

- [ ] Camera CSI (sc3336 / mis5001 already in DT, no hardware to test)
- [ ] Hardware H.264 via `mpp_vcodec` (depends on camera)
- [ ] OP-TEE secure-world TA (rkbin + U-Boot FIT ship TEE but userspace doesn't talk to it yet)
- [ ] Watchdog daemon feeding `/dev/watchdog`
- [ ] Thermal trip points / thermal-governed NPU throttling
- [ ] USB device-mode gadget profiles (CDC-NCM + CDC-ACM composite) — scoped, skipped because existing serial + network + flashing paths already work

### WiFi credentials — set these in `conf/local.conf`, not in git

```
# ~/build/conf/local.conf
LUCKFOX_WIFI_SSID = "your-home-ssid"
LUCKFOX_WIFI_PSK  = "your-home-psk"
LUCKFOX_WIFI_COUNTRY = "IE"
```

The `wpa-supplicant.bbappend` in this layer substitutes those at `do_install` time into `/etc/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf` (mode 600). They never enter the git tree. If you leave them unset, a placeholder config is installed instead and you can drop a real one on the target after flashing.

### Display root cause (resolved 2026-04-13)

Three things had to land together to light the 4" 720x720 panel:

1. **U-Boot must release the adapter's bridge MCU from reset.** The
   Luckfox 720x720 adapter board has an onboard WCH CH32V003F4 RISC-V
   MCU (alongside the GT911 touch controller) which runs the panel's
   own init sequence before RGB data is accepted. Its reset line is
   GPIO0_A1. Stock's U-Boot handles this via a `luckfox_execute_cmd()`
   helper in `arch/arm/mach-rockchip/board.c` that runs `gpio set 1 1`
   from `board_late_init()`, gated by `CONFIG_LUCKFOX_EXECUTE_CMD`.
   That symbol and function only exist in the LuckfoxTECH SDK fork of
   U-Boot, not in the upstream `rockchip-linux/u-boot` that our recipe
   fetches — so our `do_configure:prepend` awks them into place.
   `CONFIG_CMD_GPIO=y` must also be enabled, otherwise the `gpio`
   console command doesn't exist and the `run_command` silently fails.
2. **Kernel must draw into the framebuffer.** `CONFIG_LOGO=y`,
   `CONFIG_FRAMEBUFFER_CONSOLE=y`, `CONFIG_VT=y` and
   `CONFIG_VT_CONSOLE=y` in `rv1106-emmc.cfg`. Without these the DRM
   driver allocates an empty framebuffer and VOP scans all-zeros — the
   panel shows uniform black even with the MCU awake, which is
   indistinguishable from "blank" to the naked eye.
3. **`rk_dma_heap_cma=66M` in the kernel bootargs.** This activates
   the Rockchip-specific `rk_dma_heap` CMA pool that DRM actually
   allocates framebuffers from (separate from the `linux,cma` DT
   region). Added to `chosen/bootargs` in the Ultra IPC dtsi.

Every earlier theory (GRF_SOC_CON1 bits, DCLK_VOP, U-Boot VOP DTS,
route_rgb, data-sync-bypass, CMA inactive, panel reset-gpios in DT)
turned out to be a dead end — the DTB, VOP registers, clocks and
pinctrl were already correct on our side. The break was purely that
the adapter's bridge MCU was never powered on.

### Known Issues

1. **NPU model format** — runtime v1.6.0 requires models compiled with
   toolkit v1.x. The v2.x format loads but fails at rknn_inputs_set.
   Use `rknn_create_mem` + `rknn_set_io_mem` API instead of `rknn_inputs_set`.
2. **U-Boot VIDCONSOLE** — steals serial from ttyFIQ0, must be disabled.

## NPU Quick Start

```bash
# On the board:
npu_hello          # Verify /dev/rknpu is accessible
rknn_demo          # Run BlazeFace detection at 332 FPS

# NPU programming notes:
# - Runtime: librknnmrt.so v1.6.0 (matches kernel driver v0.9.2)
# - API: use rknn_create_mem + rknn_set_io_mem (not rknn_inputs_set)
# - Query RKNN_QUERY_NATIVE_INPUT_ATTR for correct tensor format
# - Output tensors use NC1HWC2 layout
# - Models must be compiled with rknn-toolkit v1.x (format 4099)
```

## Layer Structure

This repo ships a single self-contained Yocto layer:

```
meta-rockchip-rv1106/     SoC vendor layer — kernel, U-Boot, rkbin, machine
                          config, WIC layout, image recipe, RKNN runtime,
                          AIC8800DC WiFi/BT, webserver, LVGL demo.
```

The only dependency is `core` (poky). Clone poky (scarthgap branch)
alongside this repo to build.

## Flash the prebuilt image (no build required)

A ready-to-flash WIC is attached to every tagged release. Grab the latest:

```bash
# https://github.com/OOHehir/luckfox-pico-yocto/releases/latest
WIC=luckfox-image-minimal-luckfox-pico-ultra-w.wic
curl -L -o ${WIC}.gz \
  https://github.com/OOHehir/luckfox-pico-yocto/releases/latest/download/${WIC}.gz
gunzip ${WIC}.gz
```

The Pico Ultra W has no SD slot, so flashing is over USB to the onboard
eMMC using `rkdeveloptool` with the board in maskrom mode:

```bash
# Put board in maskrom: short SARADC_IN0 to GND at power-on.
rkdeveloptool ld                           # confirm "DevNo=1 Maskrom"
rkdeveloptool db rk1106_ddr_924MHz_v1.09.bin   # from rkbin, first stage
rkdeveloptool wl 0 ${WIC}                  # write whole image to sector 0
rkdeveloptool rd                           # reset
```

The WIC contains idbloader + u-boot + boot partition + ext4 rootfs in the
layout described below. WiFi credentials are unset in the release image —
edit `/etc/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf` on the board
after first boot, or rebuild from source with `LUCKFOX_WIFI_*` set in
`local.conf`.

## Build from source

```bash
# One-time setup: clone this repo and poky side by side
git clone https://github.com/OOHehir/luckfox-pico-yocto.git
git clone -b scarthgap https://git.yoctoproject.org/poky

# Each shell: source the Yocto env and enter the build dir
cd luckfox-pico-yocto
source ../poky/oe-init-build-env build

# Add this layer to bblayers.conf (first build only)
bitbake-layers add-layer ../meta-rockchip-rv1106

# Set the machine and (optionally) WiFi credentials in conf/local.conf:
#   MACHINE = "luckfox-pico-ultra-w"
#   LUCKFOX_WIFI_SSID = "your-ssid"
#   LUCKFOX_WIFI_PSK  = "your-psk"
#   LUCKFOX_WIFI_COUNTRY = "IE"

bitbake luckfox-image-minimal
```

Output: `build/tmp-glibc/deploy/images/luckfox-pico-ultra-w/`

## Flashing a source build

Same recipe as [Flash the prebuilt image](#flash-the-prebuilt-image-no-build-required) above, just point `rkdeveloptool` at the WIC your build produced:

```bash
# Put the board in maskrom mode: short SARADC_IN0 to GND at power-on,
# or hold the BOOT button (if your carrier exposes one) while plugging
# in USB. `rkdeveloptool ld` should then report "DevNo=1 Maskrom".

WIC=build/tmp-glibc/deploy/images/luckfox-pico-ultra-w/luckfox-image-minimal-luckfox-pico-ultra-w.rootfs.wic

rkdeveloptool ld                              # confirm maskrom
rkdeveloptool db rk1106_ddr_924MHz_v1.09.bin  # DDR init, from rkbin
rkdeveloptool wl 0 ${WIC}                     # write whole WIC to sector 0
rkdeveloptool rd                              # reset the board
```

`rk1106_ddr_924MHz_v1.09.bin` is the DDR init blob shipped by [rockchip-linux/rkbin](https://github.com/rockchip-linux/rkbin) under `bin/rv11/`. rkdeveloptool itself is available at [rockchip-linux/rkdeveloptool](https://github.com/rockchip-linux/rkdeveloptool) or as `rkdeveloptool` in many Linux distros. The whole flash takes ~20 seconds for the 287 MB WIC on a reasonable USB 2.0 link.

## Boot Chain

```
BootROM → idbloader.img (sector 64) → u-boot.img (8MiB) → sysboot extlinux.conf → Linux
```

## Partition Layout (WIC)

| Offset | Content |
|--------|---------|
| 32K (sector 64) | idbloader.img (DDR init + SPL) |
| 8 MiB (sector 0x4000) | u-boot.img (U-Boot + OP-TEE FIT) |
| 16 MiB (sector 0x8000) | /boot (FAT32 — zImage + DTB + extlinux.conf) |
| 24 MiB (sector 0xC000) | / (ext4 rootfs) |

## Source Versions

| Component | Repository | Branch | Commit |
|-----------|-----------|--------|--------|
| rkbin | rockchip-linux/rkbin | master | `74213af` |
| Kernel | LuckfoxTECH/luckfox-pico | main | `824b817` (SDK 5.10.160) |
| U-Boot | rockchip-linux/u-boot | next-dev | `b14196e` |
| RKNN Runtime | LuckfoxTECH/luckfox-pico | main | librknnmrt.so v1.6.0 |
| Poky | yoctoproject/poky | scarthgap | `7d50718` |

## Key Fixes Applied

- **Bridge MCU reset release in U-Boot**: The 720x720 adapter's WCH
  CH32V003F4 MCU reset line is GPIO0_A1. Release it via `gpio set 1 1`
  in `board_late_init()` — requires `CONFIG_LUCKFOX_EXECUTE_CMD` and
  `CONFIG_CMD_GPIO` injected into our upstream-sourced U-Boot.
- **Kernel fbcon + LOGO**: Without `CONFIG_FRAMEBUFFER_CONSOLE=y` and
  `CONFIG_LOGO=y` the kernel draws nothing into the DRM framebuffer
  and VOP scans all zeros (looks blank even with the MCU awake).
- **`rk_dma_heap_cma=66M`** bootarg: activates the Rockchip DMA heap
  CMA pool that DRM allocates framebuffers from.
- **VOP rgb_en for DPI**: Kernel VOP driver missing `DRM_MODE_CONNECTOR_DPI`
  case — RGB output gate never opened. Fixed via sed in do_configure.
  (Already present in newer SDK commits but the sed is defensive.)
- **NPU runtime mismatch**: v2.3.2 runtime incompatible with driver v0.9.2.
  Switched to SDK's librknnmrt.so v1.6.0.
- **NPU API**: v1.6.0 runtime requires `rknn_create_mem` + `rknn_set_io_mem`
  instead of `rknn_inputs_set`.
