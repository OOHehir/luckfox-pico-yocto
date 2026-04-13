FILESEXTRAPATHS:prepend := "${THISDIR}/${BPN}:"

SRC_URI += "file://luckfox-runtime-dir.conf"

# Drop the systemd unit override in place so /run/avahi-daemon gets
# created by systemd before avahi-daemon tries to write its PID file.
do_install:append() {
    install -d ${D}${systemd_system_unitdir}/avahi-daemon.service.d
    install -m 0644 ${WORKDIR}/luckfox-runtime-dir.conf \
        ${D}${systemd_system_unitdir}/avahi-daemon.service.d/10-luckfox-runtime-dir.conf
}

FILES:${PN}-daemon:append = " ${systemd_system_unitdir}/avahi-daemon.service.d"
