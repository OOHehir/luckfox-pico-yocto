SUMMARY = "AIC8800DC WiFi/BT kernel modules"
DESCRIPTION = "Out-of-tree kernel modules for the AIC8800DC WiFi 6 + BLE 5.2 \
chip used on the Luckfox Pico Ultra W. Builds aic8800_bsp, aic8800_fdrv, \
and aic8800_btlpm modules."
HOMEPAGE = "https://github.com/LuckfoxTECH/luckfox-pico"
# Driver source extracted from Luckfox SDK (luckfox-pico @ 824b817)
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://aic8800_fdrv/rwnx_main.c;beginline=1;endline=10;md5=ca251a784254f17794c6ee8790479700"

SRC_URI = " \
    file://aic8800_bsp \
    file://aic8800_fdrv \
    file://aic8800_btlpm \
    file://Makefile \
    file://Kconfig \
"

S = "${WORKDIR}"

inherit module

DEPENDS += "virtual/kernel python3-native"

EXTRA_OEMAKE = " \
    ARCH=${ARCH} \
    CROSS_COMPILE=${TARGET_PREFIX} \
    KDIR=${STAGING_KERNEL_DIR} \
    M=${S} \
    CONFIG_PLATFORM_ROCKCHIP=y \
    CONFIG_PLATFORM_ROCKCHIP2=n \
    CONFIG_PLATFORM_ALLWINNER=n \
    CONFIG_PLATFORM_AMLOGIC=n \
    CONFIG_PLATFORM_UBUNTU=n \
    CONFIG_AIC8800_BTLPM_SUPPORT=m \
    CONFIG_AIC8800_WLAN_SUPPORT=m \
    CONFIG_AIC_WLAN_SUPPORT=m \
"

# GCC 13+ is overzealous about -Wdangling-pointer in rwnx_rx.c — the
# driver passes the address of a local sk_buff_head to __skb_queue_head()
# and then drains the queue before the local goes out of scope. GCC
# flags the pattern as a dangling-pointer store; the driver is correct.
# Suppress the warning (and unbreak -Werror) via KCFLAGS on do_compile.

# The upstream Makefile's 'modules' target has post-build cp commands
# that fail. Use the kernel build system directly instead.
do_compile() {
    unset LDFLAGS

    # The SDK kernel's scripts/gcc-wrapper.py (invoked for every .o) uses
    # '#!/usr/bin/env python', which doesn't exist on modern build hosts.
    # Create a local 'python' -> python3 shim and prepend it to PATH.
    mkdir -p ${WORKDIR}/python-shim
    ln -sf $(which python3) ${WORKDIR}/python-shim/python
    export PATH="${WORKDIR}/python-shim:${PATH}"

    # auto.conf lives in the STAGING_KERNEL_BUILDDIR (not the source tree),
    # so we must pass O=... or the kernel build system will bail with
    # "Kernel configuration is invalid".
    oe_runmake -C ${STAGING_KERNEL_DIR} M=${S} \
        O=${STAGING_KERNEL_BUILDDIR} \
        ARCH=${ARCH} CROSS_COMPILE=${TARGET_PREFIX} \
        KCFLAGS='-Wno-error=dangling-pointer -Wno-dangling-pointer' \
        modules
}

do_install() {
    install -d ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra
    install -m 0644 ${S}/aic8800_bsp/aic8800_bsp.ko ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/
    install -m 0644 ${S}/aic8800_fdrv/aic8800_fdrv.ko ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/
    install -m 0644 ${S}/aic8800_btlpm/aic8800_btlpm.ko ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/
}

FILES:${PN} = "${nonarch_base_libdir}/modules"

# Provide all versioned kernel-module names that auto-deps might look for
RPROVIDES:${PN} += " \
    kernel-module-aic8800-bsp \
    kernel-module-aic8800-bsp-${KERNEL_VERSION} \
    kernel-module-aic8800-fdrv \
    kernel-module-aic8800-fdrv-${KERNEL_VERSION} \
    kernel-module-aic8800-btlpm \
    kernel-module-aic8800-btlpm-${KERNEL_VERSION} \
"
RDEPENDS:${PN} += "aic8800dc-firmware"

COMPATIBLE_MACHINE = "(luckfox-pico-ultra-w)"
