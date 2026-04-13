/*
 * Minimal LVGL v9 config for the Luckfox Pico Ultra W demo.
 * Targets /dev/fb0 (720x720 XRGB8888) + /dev/input/eventN (GT911).
 * Only enables what the demo actually uses to keep the binary
 * small (the RV1106's 256 MB DDR isn't tiny, but the rootfs is).
 */

#ifndef LV_CONF_H
#define LV_CONF_H

#include <stdint.h>

/* Color depth: panel is XRGB8888 via DRM → pick 32 */
#define LV_COLOR_DEPTH 32

/* Software draw backend — use the plain C path. LVGL otherwise
 * tries to build lv_blend_helium.S (ARM M-profile Helium MVE
 * assembly) which is a Cortex-M55/M85 extension and has no
 * meaning on the RV1106's Cortex-A7. The assembler chokes on
 * the preprocessor pulling in glibc headers that declare typedefs.
 */
#define LV_USE_DRAW_SW 1
#define LV_USE_DRAW_SW_ASM LV_DRAW_SW_ASM_NONE

#define LV_USE_STDLIB_MALLOC  LV_STDLIB_CLIB
#define LV_USE_STDLIB_STRING  LV_STDLIB_CLIB
#define LV_USE_STDLIB_SPRINTF LV_STDLIB_CLIB

#define LV_MEM_SIZE (2U * 1024U * 1024U)

#define LV_TICK_CUSTOM 1
#define LV_TICK_CUSTOM_INCLUDE <time.h>
#define LV_TICK_CUSTOM_SYS_TIME_EXPR \
    ((lv_tick_get_cb_t)(uintptr_t)0)  /* Overridden by fbdev driver */

/* Display driver: Linux /dev/fbN framebuffer */
#define LV_USE_LINUX_FBDEV 1
#define LV_LINUX_FBDEV_BSD 0
#define LV_LINUX_FBDEV_RENDER_MODE LV_DISPLAY_RENDER_MODE_PARTIAL
#define LV_LINUX_FBDEV_BUFFER_COUNT 2
#define LV_LINUX_FBDEV_BUFFER_SIZE 60

/* Input driver: Linux /dev/input/eventN */
#define LV_USE_EVDEV 1

/* Widgets the demo uses */
#define LV_USE_LABEL 1
#define LV_LABEL_TEXT_SELECTION 0
#define LV_USE_ARC 1
#define LV_USE_LINE 1

/* Fonts — a couple of Montserrat sizes for the labels */
#define LV_FONT_MONTSERRAT_14 1
#define LV_FONT_MONTSERRAT_18 1
#define LV_FONT_MONTSERRAT_28 1
#define LV_FONT_MONTSERRAT_48 1
#define LV_FONT_DEFAULT &lv_font_montserrat_14

/* Themes */
#define LV_USE_THEME_DEFAULT 1
#define LV_THEME_DEFAULT_DARK 1

#endif /* LV_CONF_H */
