SUMMARY = "RKNN NPU demo for Luckfox Pico Ultra W"
DESCRIPTION = "Simple demo that loads a MobileNet V1 model on the RV1106 NPU, \
runs inference with random input, and prints top-5 results with timing."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://rknn_demo.c \
    file://npu_hello.c \
    file://detection.rknn \
"

S = "${WORKDIR}"

DEPENDS += "rknn-runtime"
RDEPENDS:${PN} += "rknn-runtime kernel-module-rknpu"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} -o rknn_demo rknn_demo.c -lrknnrt
    ${CC} ${CFLAGS} ${LDFLAGS} -o npu_hello npu_hello.c
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/rknn_demo ${D}${bindir}/
    install -m 0755 ${S}/npu_hello ${D}${bindir}/

    install -d ${D}${datadir}/rknn-demo
    install -m 0644 ${S}/detection.rknn ${D}${datadir}/rknn-demo/

    # Auto-load rknpu module at boot
    install -d ${D}${sysconfdir}/modules-load.d
    echo "rknpu" > ${D}${sysconfdir}/modules-load.d/rknpu.conf
}

FILES:${PN} += "${datadir}/rknn-demo ${sysconfdir}/modules-load.d"

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"
