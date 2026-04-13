# Minimal Luckfox RV1106 image for eMMC boot.
# Self-contained — owns its IMAGE_INSTALL list and does not depend on
# the meta-luckfox-distro variant (which is RK3506-targeted).
#
# Flash via gatecmd / rkdeveloptool wl 0 from maskrom mode.

SUMMARY = "Minimal Luckfox RV1106 Linux image"
DESCRIPTION = "Minimal bootable image for the Luckfox Pico Ultra W and \
other RV1106 boards. Provides serial console (ttyFIQ0), networking, \
SSH, ALSA, the AIC8800DC WiFi driver, the rknpu auto-load, and the \
luckfox-pico-status webserver."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit core-image

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-luckfox-rv1106-base \
    kernel-modules \
    alsa-utils \
    avahi-daemon \
    aic8800dc-wifi-bt \
    aic8800dc-firmware \
    bluez5 \
    luckfox-bt-init \
    luckfox-pico-status \
    luckfox-lvgl-demo \
"

WKS_FILE = "luckfox-rv1106-emmc.wks"

# Keep the image small — Pico Ultra W has 8 GB eMMC but the rootfs
# partition we create is sized for fast flashing, not for a heavy
# userspace.
IMAGE_ROOTFS_SIZE ?= "131072"
IMAGE_OVERHEAD_FACTOR ?= "1.2"

# No root password for initial bringup. For production builds, drop
# debug-tweaks and ship a locked root with SSH key auth.
EXTRA_IMAGE_FEATURES += "debug-tweaks"

# Auto-load kernel modules at boot.
#   rknpu   — /dev/rknpu for the NPU demos.
#   aic8800 — WiFi driver (wlan0) and BT driver (UART1 HCI). The
#             luckfox-bt-attach.service relies on aic8800_fdrv being
#             loaded before it runs hciattach, because the firmware
#             is pushed by the WiFi side.
rknpu_modules_load() {
    install -d ${IMAGE_ROOTFS}${sysconfdir}/modules-load.d
    echo rknpu > ${IMAGE_ROOTFS}${sysconfdir}/modules-load.d/rknpu.conf
    cat > ${IMAGE_ROOTFS}${sysconfdir}/modules-load.d/aic8800.conf <<'EOF'
aic8800_bsp
aic8800_fdrv
aic8800_btlpm
EOF
}

# Enable systemd-networkd so the 10-end0.network / 20-wlan0.network
# files dropped by the wpa-supplicant bbappend actually get applied
# on boot. Default systemd image has networkd installed but masked.
enable_networkd() {
    install -d ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/multi-user.target.wants
    ln -sf /usr/lib/systemd/system/systemd-networkd.service \
        ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/multi-user.target.wants/systemd-networkd.service
    install -d ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/sockets.target.wants
    ln -sf /usr/lib/systemd/system/systemd-networkd.socket \
        ${IMAGE_ROOTFS}${sysconfdir}/systemd/system/sockets.target.wants/systemd-networkd.socket
}

ROOTFS_POSTPROCESS_COMMAND:append = " rknpu_modules_load; enable_networkd;"
