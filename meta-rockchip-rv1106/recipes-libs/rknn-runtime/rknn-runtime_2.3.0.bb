SUMMARY = "Rockchip RKNN NPU runtime library"
DESCRIPTION = "Prebuilt userspace runtime library for the Rockchip RKNPU2 \
neural processing unit. Uses the Luckfox SDK's librknnmrt.so v1.6.0 which \
matches the kernel RKNPU driver v0.9.2 in the SDK kernel."
HOMEPAGE = "https://github.com/airockchip/rknn-toolkit2"
LICENSE = "CLOSED"

# Luckfox SDK — contains matching librknnmrt.so v1.6.0 for driver v0.9.2
SRCREV = "824b817f889c2cbff1d48fcdb18ab494a68f69d1"
SRC_URI = "git://github.com/LuckfoxTECH/luckfox-pico.git;protocol=https;branch=main;destsuffix=git"

# rknn-toolkit2 repo — for headers and RV1106-specific demo models
SRCREV_toolkit = "59a913d172e7f5ff03c9076e2ec7b1b1288ffd08"
SRC_URI += "git://github.com/airockchip/rknn-toolkit2.git;protocol=https;branch=master;name=toolkit;destsuffix=toolkit"

SRCREV_FORMAT = "default_toolkit"

S = "${WORKDIR}/git"

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"

# Prebuilt binary — skip compilation
do_compile[noexec] = "1"
do_configure[noexec] = "1"

# SDK runtime path (v1.6.0, matches driver v0.9.2)
SDK_LIBDIR = "${S}/project/cfg/BoardConfig_IPC/overlay/overlay-luckfox-glibc-rockchip/usr/lib"

# Toolkit headers
TOOLKIT_INCDIR = "${WORKDIR}/toolkit/rknpu2/runtime/Linux/librknn_api/include"

do_install() {
    # Runtime library — install as both librknnmrt.so and librknnrt.so (compat symlink)
    install -d ${D}${libdir}
    install -m 0755 ${SDK_LIBDIR}/librknnmrt.so ${D}${libdir}/librknnmrt.so
    ln -sf librknnmrt.so ${D}${libdir}/librknnrt.so

    # Headers (from toolkit2 — API is compatible)
    install -d ${D}${includedir}/rknn
    install -m 0644 ${TOOLKIT_INCDIR}/rknn_api.h ${D}${includedir}/rknn/
    install -m 0644 ${TOOLKIT_INCDIR}/rknn_custom_op.h ${D}${includedir}/rknn/
    install -m 0644 ${TOOLKIT_INCDIR}/rknn_matmul_api.h ${D}${includedir}/rknn/
}

# Prebuilt binary — skip QA checks
INSANE_SKIP:${PN} = "already-stripped ldflags dev-so"
INSANE_SKIP:${PN}-dev = "dev-elf"

FILES:${PN} = "${libdir}/librknnmrt.so ${libdir}/librknnrt.so"
FILES:${PN}-dev = "${includedir}/rknn"
