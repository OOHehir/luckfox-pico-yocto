# Boot image assembly class for RV1106
#
# Rockchip boot chain:
#   BootROM -> idbloader.img (sector 64) -> uboot.img -> kernel -> Linux
#
# Ensures the correct Rockchip boot images are deployed before wic assembly.

do_image_wic[depends] += " \
    u-boot-rockchip-rv1106:do_deploy \
    virtual/kernel:do_deploy \
"

IMAGE_FSTYPES:append = " wic"

IMAGE_BOOT_FILES ?= ""
