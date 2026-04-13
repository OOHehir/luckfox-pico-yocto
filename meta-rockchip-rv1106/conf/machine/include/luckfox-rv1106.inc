# Shared machine settings for all LuckFox boards using Rockchip RV1106 SoC family.
# Individual board .conf files include this and set board-specific overrides.

# ARM Cortex-A7 tuning (single-core, 32-bit ARMv7-A with NEON)
require conf/machine/include/arm/armv7a/tune-cortexa7.inc

DEFAULTTUNE = "armv7athf-neon"

# Vendor BSP kernel is 5.10.x — glibc must be told the oldest kernel version
OLDEST_KERNEL = "5.10"

# SoC vendor layer provides kernel and bootloader
PREFERRED_PROVIDER_virtual/kernel = "linux-rockchip-rv1106"
PREFERRED_PROVIDER_virtual/bootloader = "u-boot-rockchip-rv1106"

# Vendor BSP kernel 5.10.x — no mainline alternative exists
PREFERRED_VERSION_linux-rockchip-rv1106 = "5.10%"

# In-tree defconfig for the RV1106 SoC
KBUILD_DEFCONFIG = "rv1106_defconfig"

# Serial console: Rockchip FIQ debugger on UART2
# RV1106 uses 115200 baud (not 1.5 Mbaud like RK3506)
SERIAL_CONSOLES = "115200;ttyFIQ0"

# Kernel console arguments for bootloader
KERNEL_CONSOLE = "ttyFIQ0,115200"

# Image types — Rockchip boot chain requires custom wic layout
IMAGE_FSTYPES = "wic ext4"

# Boot partition contents (kernel + DTB for the bootimg-partition wic plugin)
IMAGE_BOOT_FILES = "zImage ${KERNEL_DEVICETREE}"

# Inherit the Rockchip boot image assembly class
IMAGE_CLASSES += "rockchip-rv1106-image"

# Base machine features common to all RV1106 boards
MACHINE_FEATURES = "usbhost"

# Suppress wic's default /etc/fstab update. wic would otherwise inject a
# `/dev/sda1 /boot vfat defaults 0 0` line because it can't infer the
# eMMC device path from the wks. systemd then waits 90s on boot for the
# phantom /dev/sda1 device, blocks local-fs.target, and drops to
# emergency mode (lighttpd never starts, network units stall, etc.).
# Our base-files fstab is already complete so we can skip the update.
WIC_CREATE_EXTRA_ARGS = "--no-fstab-update"
