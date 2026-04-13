SUMMARY = "Luckfox Pico Ultra W Bluetooth init glue"
DESCRIPTION = "systemd unit that runs hciattach on /dev/ttyS1 at \
1.5 Mbaud so the AIC8800DC's Bluetooth side enumerates as hci0 \
after the WiFi driver has loaded its firmware."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://luckfox-bt-attach.service"

S = "${WORKDIR}"

inherit systemd allarch

RDEPENDS:${PN} = "bluez5 aic8800dc-wifi-bt"

do_install() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/luckfox-bt-attach.service \
        ${D}${systemd_system_unitdir}/luckfox-bt-attach.service
}

FILES:${PN} = "${systemd_system_unitdir}/luckfox-bt-attach.service"

SYSTEMD_SERVICE:${PN} = "luckfox-bt-attach.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"
