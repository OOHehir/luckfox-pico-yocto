# Base packagegroup for Luckfox RV1106 boards.
# Pulled into luckfox-image-minimal. Self-contained — replaces
# packagegroup-luckfox-base from meta-luckfox-distro.

SUMMARY = "Luckfox RV1106 base system packages"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

RDEPENDS:${PN} = " \
    openssh-sshd \
    openssh-ssh \
    openssh-sftp-server \
    openssh-keygen \
    iproute2 \
    ethtool \
    util-linux \
    e2fsprogs \
"

# WiFi userspace pulled in only when the machine declares wifi support.
RRECOMMENDS:${PN} = " \
    ${@bb.utils.contains('MACHINE_FEATURES', 'wifi', 'wpa-supplicant iw wireless-regdb', '', d)} \
"
