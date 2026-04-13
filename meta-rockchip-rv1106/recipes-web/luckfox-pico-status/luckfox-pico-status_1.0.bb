SUMMARY = "Luckfox Pico system status webserver"
DESCRIPTION = "Lightweight system status page served via lighttpd CGI \
on port 80. Shows uptime, memory, network, storage, eMMC health, \
thermal, NPU and display status. Reachable at http://luckfox-pico.local \
when avahi-daemon is running."
HOMEPAGE = "https://github.com/OOHehir/luckfox-pico-yocto"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

RDEPENDS:${PN} = "lighttpd lighttpd-module-cgi"

SRC_URI = " \
    file://status.cgi \
    file://lighttpd-status.conf \
"

S = "${WORKDIR}"

do_install() {
    install -d ${D}/www/pages
    install -m 0755 ${WORKDIR}/status.cgi ${D}/www/pages/status.cgi

    install -d ${D}${sysconfdir}/lighttpd.d
    install -m 0644 ${WORKDIR}/lighttpd-status.conf ${D}${sysconfdir}/lighttpd.d/status.conf
}

# Replace lighttpd's default index.html with a redirect to the status CGI
pkg_postinst:${PN}() {
    echo '<html><head><meta http-equiv="refresh" content="0;url=/status.cgi"></head></html>' \
        > $D/www/pages/index.html
}

FILES:${PN} = "/www/pages/status.cgi ${sysconfdir}/lighttpd.d"
