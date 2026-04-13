SUMMARY = "Rockchip binary blobs for RV1106 boot chain"
DESCRIPTION = "Proprietary DDR init, SPL, and OP-TEE binaries from Rockchip's rkbin \
repository. Required by the RV1106 boot chain."
HOMEPAGE = "https://github.com/rockchip-linux/rkbin"

LICENSE = "CLOSED"
LICENSE_FLAGS = "commercial"

SRCREV = "74213af1e952c4683d2e35952507133b61394862"
SRC_URI = "git://github.com/rockchip-linux/rkbin.git;protocol=https;branch=master"

S = "${WORKDIR}/git"

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"

inherit deploy

# RV1106 blobs (shared across G2/G3 variants — no G3-specific files)
RKBIN_DDR = "bin/rv11/rv1106_ddr_924MHz_v1.15.bin"
RKBIN_SPL = "bin/rv11/rv1106_spl_v1.02.bin"
RKBIN_TEE = "bin/rv11/rv1106_tee_ta_v1.13.bin"
RKBIN_USBPLUG = "bin/rv11/rv1106_usbplug_v1.09.bin"
RKBIN_MINIALL_INI = "RKBOOT/RV1106MINIALL.ini"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    # Verify MINIALL.ini exists at pinned commit
    if [ ! -f "${S}/${RKBIN_MINIALL_INI}" ]; then
        bbfatal "MINIALL.ini not found at ${RKBIN_MINIALL_INI} — rkbin commit may not support RV1106"
    fi

    # Verify all required blobs exist
    for blob in "${RKBIN_DDR}" "${RKBIN_SPL}" "${RKBIN_USBPLUG}" "${RKBIN_TEE}"; do
        if [ ! -f "${S}/${blob}" ]; then
            bbfatal "Required blob not found: ${blob} at rkbin commit ${SRCREV}"
        fi
    done

    install -d ${D}${datadir}/rkbin
    install -m 0644 ${S}/${RKBIN_DDR} ${D}${datadir}/rkbin/
    install -m 0644 ${S}/${RKBIN_SPL} ${D}${datadir}/rkbin/
    install -m 0644 ${S}/${RKBIN_USBPLUG} ${D}${datadir}/rkbin/
    install -m 0644 ${S}/${RKBIN_TEE} ${D}${datadir}/rkbin/
    install -m 0644 ${S}/${RKBIN_MINIALL_INI} ${D}${datadir}/rkbin/
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${S}/${RKBIN_DDR} ${DEPLOYDIR}/
    install -m 0644 ${S}/${RKBIN_SPL} ${DEPLOYDIR}/
    install -m 0644 ${S}/${RKBIN_USBPLUG} ${DEPLOYDIR}/
    install -m 0644 ${S}/${RKBIN_TEE} ${DEPLOYDIR}/
    install -m 0644 ${S}/${RKBIN_MINIALL_INI} ${DEPLOYDIR}/
}

addtask deploy after do_install

FILES:${PN} = "${datadir}/rkbin"

INSANE_SKIP:${PN} = "already-stripped"
