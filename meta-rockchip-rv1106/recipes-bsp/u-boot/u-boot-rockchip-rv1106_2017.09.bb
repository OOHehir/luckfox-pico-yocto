SUMMARY = "Rockchip U-Boot for RV1106"
DESCRIPTION = "Rockchip's vendor U-Boot 2017.09 fork for the RV1106 SoC family. \
Produces idbloader.img (DDR init + SPL) and uboot.img (U-Boot + OP-TEE FIT) \
for the Rockchip boot chain."
HOMEPAGE = "https://github.com/rockchip-linux/u-boot"

LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://Licenses/gpl-2.0.txt;md5=b234ee4d69f5fce4486a80fdaf4a4263"

DEPENDS += "bc-native dtc-native python3-native"

# U-Boot source (Rockchip vendor fork)
SRCREV = "b14196eade471bbc000c368f8555f2a2a1ecc17d"
SRCREV_rkbin = "74213af1e952c4683d2e35952507133b61394862"

SRC_URI = " \
    git://github.com/rockchip-linux/u-boot.git;protocol=https;branch=next-dev;name=default \
    git://github.com/rockchip-linux/rkbin.git;protocol=https;branch=master;name=rkbin;destsuffix=rkbin \
    file://rv1106-distroboot.config \
    file://rv1106-display.config \
    file://rv1106-luckfox-rgb-reset.config \
    file://rv1106-luckfox-display.dtsi \
    file://0001-rv1106-add-distro-boot-fallback.patch \
"

SRCREV_FORMAT = "default_rkbin"

S = "${WORKDIR}/git"
B = "${S}"

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"

UBOOT_MACHINE ?= "rv1106_defconfig"

# rkbin blob paths for RV1106
RKBIN_DDR = "bin/rv11/rv1106_ddr_924MHz_v1.15.bin"
RKBIN_SPL = "bin/rv11/rv1106_spl_v1.02.bin"
RKBIN_TEE = "bin/rv11/rv1106_tee_ta_v1.13.bin"

inherit deploy

# Vendor U-Boot 2017.09 needs HOSTCC=gcc and -Wno-error for modern GCC
EXTRA_OEMAKE = " \
    HOSTCC='gcc' \
    HOSTCXX='g++' \
    CROSS_COMPILE=${TARGET_PREFIX} \
    PYTHON=python3 \
    KCFLAGS='-Wno-error' \
"

do_configure() {
    # --- Luckfox RGB panel adapter MCU reset (CH32V003F4 on adapter board) ---
    # Add Kconfig symbol + luckfox_execute_cmd() function + call site so
    # board_late_init releases GPIO1 pin 1 and the adapter MCU boots.
    # Backport of LuckfoxTECH/luckfox-pico sysdrv uboot change.
    KCONFIG=${S}/arch/arm/mach-rockchip/Kconfig
    BOARDC=${S}/arch/arm/mach-rockchip/board.c
    if ! grep -q "LUCKFOX_EXECUTE_CMD" $KCONFIG; then
        awk '
        /^config EMBED_KERNEL_DTB_ALWAYS$/,/^$/ {print; next}
        /^config ROCKCHIP_CRC$/ && !x {
            print "config LUCKFOX_EXECUTE_CMD"
            print "\tbool \"Luckfox Pico RGB adapter MCU reset\""
            print "\tdefault n"
            print ""
            x = 1
        }
        {print}
        ' $KCONFIG > $KCONFIG.new && mv $KCONFIG.new $KCONFIG
    fi
    if ! grep -q "luckfox_execute_cmd" $BOARDC; then
        awk '
        /^__weak int rk_board_late_init\(void\)$/ && !x {
            print "#ifdef CONFIG_LUCKFOX_EXECUTE_CMD"
            print "static int luckfox_execute_cmd(void)"
            print "{"
            print "\t/* Luckfox Pico RGB adapter MCU reset/enable */"
            print "\trun_command(\"gpio set 1 1\", 0);"
            print "\treturn 0;"
            print "}"
            print "#endif"
            print ""
            x = 1
        }
        /^int board_late_init\(void\)$/ {
            print
            getline
            print
            print "#ifdef CONFIG_LUCKFOX_EXECUTE_CMD"
            print "\tluckfox_execute_cmd();"
            print "#endif"
            next
        }
        {print}
        ' $BOARDC > $BOARDC.new && mv $BOARDC.new $BOARDC
    fi

    # --- Display DTS: add panel/backlight/VOP/RGB nodes to U-Boot device tree ---
    # Used by U-Boot's drm-logo handoff region; actual display init is done
    # by the Linux kernel driver after boot.
    cp ${WORKDIR}/rv1106-luckfox-display.dtsi ${S}/arch/arm/dts/
    if ! grep -q "rv1106-luckfox-display.dtsi" ${S}/arch/arm/dts/rv1106-evb.dts; then
        sed -i '/#include "rv1106-u-boot.dtsi"/a #include "rv1106-luckfox-display.dtsi"' \
            ${S}/arch/arm/dts/rv1106-evb.dts
    fi

    oe_runmake ${UBOOT_MACHINE}

    # Apply config fragments
    for cfg in ${WORKDIR}/rv1106-distroboot.config ${WORKDIR}/rv1106-display.config ${WORKDIR}/rv1106-luckfox-rgb-reset.config; do
        if [ -f "$cfg" ]; then
            ${S}/scripts/kconfig/merge_config.sh -m -O ${B} ${B}/.config "$cfg"
        fi
    done

    # Resolve any new options introduced by fragments (avoid interactive prompts)
    yes '' | oe_runmake oldconfig
}

do_compile() {
    # Copy OP-TEE blob — U-Boot's FIT generator expects tee.bin in source tree
    cp ${WORKDIR}/rkbin/${RKBIN_TEE} ${S}/tee.bin

    oe_runmake

    # If the Makefile didn't create idbloader.img, build it manually
    if [ ! -f "${B}/idbloader.img" ]; then
        ${B}/tools/mkimage -n rv1106 -T rksd \
            -d ${WORKDIR}/rkbin/${RKBIN_DDR}:${WORKDIR}/rkbin/${RKBIN_SPL} \
            ${B}/idbloader.img
    fi
}

do_deploy() {
    install -d ${DEPLOYDIR}

    if [ -f "${B}/idbloader.img" ]; then
        install -m 0644 ${B}/idbloader.img ${DEPLOYDIR}/idbloader.img
    fi

    if [ -f "${B}/u-boot.img" ]; then
        install -m 0644 ${B}/u-boot.img ${DEPLOYDIR}/u-boot.img
    fi

    if [ -f "${B}/u-boot-dtb.bin" ]; then
        install -m 0644 ${B}/u-boot-dtb.bin ${DEPLOYDIR}/u-boot-dtb.bin
    fi
    if [ -f "${B}/spl/u-boot-spl.bin" ]; then
        install -m 0644 ${B}/spl/u-boot-spl.bin ${DEPLOYDIR}/u-boot-spl.bin
    fi
}

addtask deploy after do_compile

PROVIDES += "virtual/bootloader"

INSANE_SKIP:${PN} = "ldflags"
