SUMMARY = "OP-TEE Trusted OS for the Rockchip RV1106 (RV1106G3)"
DESCRIPTION = "Source-built OP-TEE secure world for the RV1106 plat-rockchip flavour \
(platform_rv1106.c, PLATFORM_FLAVOR=rv1106). Replaces the proprietary rv1106_tee_ta \
blob previously pulled from rkbin."
HOMEPAGE = "https://optee.org"

LICENSE = "BSD-2-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=c1f21c4f72f372ef38a5a4aee55ec173"

# RV1106 support is on optee_os master (post-4.10.0), not yet in a tagged release.
PV = "4.10.0+git"
SRCREV = "ed18ba2d11ce4b447e45e26b9065e77228f8f0de"
SRC_URI = "git://git.trustedfirmware.org/OP-TEE/optee_os.git;protocol=https;branch=master"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"

# Native helpers for OP-TEE's ELF-relocation and image-signing steps.
DEPENDS = "python3-pyelftools-native python3-cryptography-native"

inherit deploy python3native

# Stop python3-cryptography aborting on OpenSSL 3's legacy provider in the native sysroot.
export CRYPTOGRAPHY_OPENSSL_NO_LEGACY = "1"

# RV1106G3: single-core ARMv7-A Cortex-A7. CFG_DT_ADDR pins boot_arg_fdt to where the
# SPL FIT loads the control DTB (0x08000000); without it OP-TEE parses the bogus FDT
# pointer the Rockchip SPL passes in r2 (inside TZDRAM) and external-aborts at boot.
EXTRA_OEMAKE = " \
    PLATFORM=rockchip-rv1106 \
    CFG_ARM32_core=y \
    CROSS_COMPILE=${HOST_PREFIX} \
    CROSS_COMPILE_core=${HOST_PREFIX} \
    NOWERROR=1 \
    CFG_TEE_CORE_LOG_LEVEL=1 \
    CFG_DT_ADDR=0x08000000 \
    O=${B} \
"

do_configure[noexec] = "1"

do_compile() {
    # Freestanding build: keep the distro's hosted-libc flags out.
    unset LDFLAGS CFLAGS CPPFLAGS
    # Build only the core targets. tee-raw.bin is the headerless image the SPL FIT
    # jumps to directly (the OPTE-headered tee.bin would hang). Building core targets
    # also skips the in-tree sample TAs, whose link step needs a libgcc path Yocto's
    # cross-gcc reports as a bare "libgcc.a".
    oe_runmake -C ${S} ${B}/core/tee.bin ${B}/core/tee-raw.bin ${B}/core/tee.elf
}
do_compile[cleandirs] = "${B}"

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/core/tee.bin ${DEPLOYDIR}/tee.bin
    install -m 0644 ${B}/core/tee-raw.bin ${DEPLOYDIR}/tee-raw.bin
    install -m 0644 ${B}/core/tee.elf ${DEPLOYDIR}/tee.elf
}
addtask deploy after do_compile before do_build

# Stage into the sysroot for u-boot's DEPENDS edge. u-boot uses tee-raw.bin.
do_install() {
    install -d ${D}${datadir}/optee
    install -m 0644 ${B}/core/tee.bin ${D}${datadir}/optee/tee.bin
    install -m 0644 ${B}/core/tee-raw.bin ${D}${datadir}/optee/tee-raw.bin
    install -m 0644 ${B}/core/tee.elf ${D}${datadir}/optee/tee.elf
}
FILES:${PN} = "${datadir}/optee"

# Secure-world firmware, not a host-loadable object.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INSANE_SKIP:${PN} = "arch staticdev"
