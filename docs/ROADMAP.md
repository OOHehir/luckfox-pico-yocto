# Luckfox Pico Ultra W — Roadmap

Forward-looking to-do list for the Yocto BSP in this repo. Audience:
anyone extending the build or considering using it as a starting point.

For the current feature status (what works on a clean flash, what's
deferred) see `README.md` — this file only tracks what's *next*.

## Unexplored hardware — where to start next

### High leverage

1. **MIPI CSI-2 camera pipeline.** dtsi already wires `sc3336` and
   `mis5001` behind `csi2_dphy0`. `v4l2-ctl --list-formats-ext` and
   a GStreamer `v4l2src` pipeline would bring the board's intended
   IP-camera use case online.

2. **Hardware H.264 / H.265 (`mpp_vcodec`).** Module is loaded
   (422 KB), plus `mpp_service`, `rkvenc`, `rga3`. Needs
   `librockchip_mpp.so` userspace from the Luckfox SDK binaries and
   a `camera → mpp → .h264` demo. With item 1 this turns the board
   into a functional low-end IP camera.

3. **NPU demos beyond BlazeFace.** Runtime is solid at 332 FPS.
   Worth adding YOLOv5n or a segmentation head so the webserver has
   a live "latest inference" tile.

### Medium

4. **Watchdog.** `systemd` has a built-in watchdog feeder —
   `RuntimeWatchdogSec=30s` in `system.conf` plus the kernel's
   `CONFIG_WATCHDOG_NOWAYOUT=y` makes the board self-recover from
   a runaway kernel thread.

5. **OP-TEE secure world.** `rv1106_tee_ta_v1.13.bin` is already in
   the FIT. Could host a secure key store, verified-boot anchor, or
   a crypto-offload TA.

6. **Thermal / cpufreq.** Chip is fixed at 1104 MHz with no scaling.
   A simple 85 °C trip that throttles the NPU would be prudent for
   enclosed deployments.

### Nice-to-have

7. **RTC wakealarm / suspend-to-RAM.**
8. **Hardware RNG** — quick `rngd -t /dev/hwrng` check.
9. **SARADC** — once the relay-BOOT pin is freed, a battery/voltage
   monitor app could live here.

## Tooling wishlist

- `ROOTFS_POSTPROCESS_COMMAND` that drops a short `/etc/issue` with
  hostname + build date + git sha. Five-second "which image did I
  flash?" check.
- A `luckfox-image-ipcam` variant that swaps the CGI status page
  for MediaMTX or `rkipc`.
- A `luckfox-info` CLI that prints the same thing the webserver
  shows, for when the network's down.
- CI: `bitbake -k` smoke build on every PR via GitHub Actions with
  a cached sstate, to catch recipe syntax breakage early.
- Rockchip `update.img` packaging (parameter.txt + afptool +
  rkImageMaker) so Windows users can flash via the LuckFox GUI
  tool without needing `rkdeveloptool` or `dd`.

## Cross-repo coordination

If the BSP gets traction:

- Upstream `meta-rockchip-rv1106` to the OpenEmbedded Layer Index.
- Open a PR on the LuckfoxTECH SDK README pointing here as a Yocto
  alternative.
- Ping linux-rockchip with the GCC 13 `-Wdangling-pointer` fix in
  `rwnx_rx.c` — they will hit it when they upgrade their build host.
