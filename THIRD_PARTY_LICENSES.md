# Third-Party Licenses

This repository is MIT licensed (see `LICENSE`), but it vendors, fetches, or
redistributes third-party components under their own terms. This file is a
non-exhaustive pointer to the ones you are most likely to care about when
shipping derived work.

For a complete, machine-generated manifest of everything that ends up in a
built image, see the SPDX SBOM produced at build time:
`build/tmp-glibc/deploy/images/luckfox-pico-ultra-w/*.spdx.tar.zst`.

## Fetched at build time (not in this git tree)

| Component | Upstream | License |
|---|---|---|
| Linux kernel 5.10.160 | `LuckfoxTECH/luckfox-pico` (Rockchip SDK fork) | GPL-2.0 |
| U-Boot | `rockchip-linux/u-boot` (next-dev) | GPL-2.0+ |
| rkbin (DDR init, SPL loader, BL31, OP-TEE, bootloader blobs) | `rockchip-linux/rkbin` | Proprietary — redistributable per rkbin `LICENSE` |
| Poky / OpenEmbedded-Core (scarthgap) | `yoctoproject/poky` | MIT (build system); individual recipes carry their own licenses |
| LVGL v9.2 | `lvgl/lvgl` | MIT |
| BusyBox, lighttpd, BlueZ, wpa_supplicant, systemd, etc. | Yocto scarthgap | GPL-2.0 / LGPL / BSD / MIT (per-package, see SBOM) |

## Vendored in this tree

| Path | Upstream | License |
|---|---|---|
| `meta-rockchip-rv1106/recipes-connectivity/aic8800dc/files/aic8800_fdrv/` `aic8800_bsp/` | AICSemi AIC8800DC Linux driver | GPL-2.0 (see in-file headers) |
| `meta-rockchip-rv1106/recipes-connectivity/aic8800dc/files/aic8800dc_fw/*.bin` | AICSemi AIC8800DC firmware | Proprietary — redistributable binary blobs |
| `meta-rockchip-rv1106/recipes-multimedia/rknn-runtime/` (librknnmrt.so v1.6.0) | `LuckfoxTECH/luckfox-pico` RKNN runtime | Proprietary — Rockchip redistributable runtime |
| `meta-rockchip-rv1106/recipes-demo/rknn-demo/files/mobilenet_v1.rknn` | Rockchip RKNN model zoo | Redistributable demo asset (see upstream model zoo terms) |
| `meta-rockchip-rv1106/recipes-demo/rknn-demo/files/detection.rknn` | BlazeFace, converted via rknn-toolkit | Redistributable demo asset |
| `images/Luckfox-Pico-Ultra-pinout.jpg` | LuckFox documentation | Reproduced under fair-use for reference |

## GPL compliance note

Derived kernel, U-Boot, and AIC8800DC driver binaries distributed in the
release image are subject to GPL-2.0. Source for each is fetched by the
recipes in this repository from public upstream git URLs at the pinned SHAs
listed in `README.md` → *Source Versions*. That satisfies the GPL "written
offer" requirement via public source availability. If an upstream ever goes
dark, open an issue and a source mirror will be attached to the release.

## Proprietary binary blobs

The following components are redistributed as binary-only blobs under the
redistribution terms of their respective upstreams. They are **not** MIT
licensed and are not covered by this repository's `LICENSE`:

- `rkbin/` blobs (DDR init, TF-A, OP-TEE pager)
- AIC8800DC firmware (`*.bin` under `aic8800dc_fw/`)
- `librknnmrt.so` NPU runtime
- `.rknn` model files

If you redistribute a derived image, preserve the upstream notices and
consult the original vendor's terms.
