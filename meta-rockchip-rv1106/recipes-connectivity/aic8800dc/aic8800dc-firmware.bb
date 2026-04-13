SUMMARY = "AIC8800DC WiFi/BT firmware"
DESCRIPTION = "Proprietary firmware blobs for the AIC8800DC WiFi/BT chip."
HOMEPAGE = "https://github.com/LuckfoxTECH/luckfox-pico"
# Firmware extracted from Luckfox SDK (luckfox-pico @ 824b817)
LICENSE = "CLOSED"

SRC_URI = "file://aic8800dc_fw"

S = "${WORKDIR}/aic8800dc_fw"

do_compile[noexec] = "1"
do_configure[noexec] = "1"

do_install() {
    install -d ${D}${nonarch_base_libdir}/firmware/aic8800dc
    install -m 0644 ${S}/*.bin ${D}${nonarch_base_libdir}/firmware/aic8800dc/
    install -m 0644 ${S}/*.txt ${D}${nonarch_base_libdir}/firmware/aic8800dc/

    # AIC8800DC BSP driver hardcodes firmware path to /oem/usr/ko/aic8800dc_fw/
    install -d ${D}/oem/usr/ko
    ln -sf ${nonarch_base_libdir}/firmware/aic8800dc ${D}/oem/usr/ko/aic8800dc_fw
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/aic8800dc /oem"

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"
