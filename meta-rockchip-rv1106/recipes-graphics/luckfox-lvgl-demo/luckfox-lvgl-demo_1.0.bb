SUMMARY = "LVGL framebuffer demo for Luckfox Pico Ultra W"
DESCRIPTION = "Minimal LVGL v9 application that renders system stats and \
a touch counter on the 720x720 RGB panel via /dev/fb0 and picks up \
touch events from /dev/input/eventN (GT911)."
HOMEPAGE = "https://github.com/lvgl/lvgl"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    git://github.com/lvgl/lvgl.git;protocol=https;branch=release/v9.2;name=lvgl;destsuffix=git/lvgl \
    file://CMakeLists.txt \
    file://main.c \
    file://lv_conf.h \
    file://luckfox-lvgl-demo.service \
"

# LVGL v9.2 release branch tip — pinned for reproducibility
SRCREV_lvgl = "f718ef47d9c4f729e130b4cec00aff9f3d629335"
SRCREV_FORMAT = "lvgl"

S = "${WORKDIR}/git"

inherit cmake systemd

DEPENDS = ""
RDEPENDS:${PN} = ""

EXTRA_OECMAKE = " \
    -DCMAKE_BUILD_TYPE=Release \
    -DLV_BUILD_EXAMPLES=OFF \
    -DLV_BUILD_DEMOS=OFF \
"

do_configure:prepend() {
    # Sources shipped in the recipe files/ dir land directly in
    # ${WORKDIR}; move them into ${S} so the CMakeLists.txt there
    # can reference them as plain top-level files.
    for f in CMakeLists.txt main.c lv_conf.h; do
        cp ${WORKDIR}/$f ${S}/
    done

    # LVGL v9.2 ships vector-blend assembly (lv_blend_helium.S,
    # lv_blend_neon.S) whose preprocessor include chain pulls glibc
    # typedefs into the assembler input, which breaks `as` for any
    # non-M-profile target. Also, Helium is M55/M85-only (our CPU
    # is a Cortex-A7), and the NEON asm path would only help if we
    # also wired the runtime dispatch through it, which we don't.
    # Delete the files so CMake can't add them to the build list —
    # LVGL falls back to the plain C SW renderer.
    rm -f ${S}/lvgl/src/draw/sw/blend/helium/lv_blend_helium.S
    rm -f ${S}/lvgl/src/draw/sw/blend/neon/lv_blend_neon.S
}

do_install:append() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/luckfox-lvgl-demo.service \
        ${D}${systemd_system_unitdir}/luckfox-lvgl-demo.service
}

# Leave disabled by default so the status webserver remains the
# primary UI. Users can `systemctl enable --now luckfox-lvgl-demo`
# to take over the framebuffer.
SYSTEMD_AUTO_ENABLE = "disable"
SYSTEMD_SERVICE:${PN} = "luckfox-lvgl-demo.service"

FILES:${PN} += "${systemd_system_unitdir}/luckfox-lvgl-demo.service"

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"
