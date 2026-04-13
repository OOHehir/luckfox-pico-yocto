/*
 * Luckfox Pico Ultra W LVGL demo
 *
 * Minimal LVGL v9 framebuffer + evdev application. Shows system
 * stats (uptime, CPU MHz, load, mem), a live clock, a breathing
 * LVGL arc, and a tap-counter to prove that the GT911 touch
 * controller is wired through.
 *
 * Target hardware: 4" 720x720 RGB parallel panel over /dev/fb0,
 * Goodix GT911 touch on /dev/input/eventN (auto-detected).
 */

#include "lvgl/lvgl.h"
#include "lvgl/src/drivers/display/fb/lv_linux_fbdev.h"
#include "lvgl/src/drivers/evdev/lv_evdev.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <dirent.h>

#define W 720
#define H 720

static lv_obj_t *lbl_time;
static lv_obj_t *lbl_stats;
static lv_obj_t *lbl_taps;
static lv_obj_t *arc;
static int tap_count = 0;

static void on_tap(lv_event_t *e)
{
    tap_count++;
    lv_label_set_text_fmt(lbl_taps, "Taps: %d", tap_count);
}

static void build_ui(void)
{
    lv_obj_t *scr = lv_screen_active();
    lv_obj_set_style_bg_color(scr, lv_color_hex(0x0d1117), 0);

    /* Title */
    lv_obj_t *title = lv_label_create(scr);
    lv_label_set_text(title, "Luckfox Pico Ultra W");
    lv_obj_set_style_text_color(title, lv_color_hex(0x58a6ff), 0);
    lv_obj_set_style_text_font(title, &lv_font_montserrat_28, 0);
    lv_obj_align(title, LV_ALIGN_TOP_MID, 0, 30);

    lv_obj_t *subtitle = lv_label_create(scr);
    lv_label_set_text(subtitle, "LVGL " LVGL_VERSION_INFO);
    lv_obj_set_style_text_color(subtitle, lv_color_hex(0x8b949e), 0);
    lv_obj_align(subtitle, LV_ALIGN_TOP_MID, 0, 70);

    /* Breathing arc as "alive" indicator */
    arc = lv_arc_create(scr);
    lv_obj_set_size(arc, 300, 300);
    lv_arc_set_rotation(arc, 270);
    lv_arc_set_bg_angles(arc, 0, 360);
    lv_arc_set_value(arc, 50);
    lv_obj_remove_style(arc, NULL, LV_PART_KNOB);
    lv_obj_set_style_arc_color(arc, lv_color_hex(0x161b22), LV_PART_MAIN);
    lv_obj_set_style_arc_color(arc, lv_color_hex(0x1f6feb), LV_PART_INDICATOR);
    lv_obj_set_style_arc_width(arc, 12, LV_PART_MAIN);
    lv_obj_set_style_arc_width(arc, 12, LV_PART_INDICATOR);
    lv_obj_center(arc);
    lv_obj_remove_flag(arc, LV_OBJ_FLAG_CLICKABLE);

    /* Live clock in the middle */
    lbl_time = lv_label_create(scr);
    lv_label_set_text(lbl_time, "--:--:--");
    lv_obj_set_style_text_color(lbl_time, lv_color_hex(0xf0f6fc), 0);
    lv_obj_set_style_text_font(lbl_time, &lv_font_montserrat_48, 0);
    lv_obj_align(lbl_time, LV_ALIGN_CENTER, 0, -20);

    /* System stats line */
    lbl_stats = lv_label_create(scr);
    lv_label_set_text(lbl_stats, "");
    lv_obj_set_style_text_color(lbl_stats, lv_color_hex(0xc9d1d9), 0);
    lv_obj_align(lbl_stats, LV_ALIGN_CENTER, 0, 40);

    /* Tap counter */
    lbl_taps = lv_label_create(scr);
    lv_label_set_text(lbl_taps, "Taps: 0");
    lv_obj_set_style_text_color(lbl_taps, lv_color_hex(0x3fb950), 0);
    lv_obj_set_style_text_font(lbl_taps, &lv_font_montserrat_18, 0);
    lv_obj_align(lbl_taps, LV_ALIGN_BOTTOM_MID, 0, -40);

    /* Whole-screen tap target */
    lv_obj_add_event_cb(scr, on_tap, LV_EVENT_CLICKED, NULL);
}

static int read_int_from_file(const char *path, long *out)
{
    FILE *f = fopen(path, "r");
    if (!f) return -1;
    int r = fscanf(f, "%ld", out);
    fclose(f);
    return r == 1 ? 0 : -1;
}

static void tick_stats(lv_timer_t *t)
{
    (void)t;

    /* Clock */
    time_t now = time(NULL);
    struct tm *tm = localtime(&now);
    char buf[16];
    strftime(buf, sizeof buf, "%H:%M:%S", tm);
    lv_label_set_text(lbl_time, buf);

    /* Breathing arc */
    static int angle = 0;
    angle = (angle + 6) % 360;
    int v = (int)(50.0 + 50.0 * (0.5 - 0.5 *
        ((double)(angle < 180 ? angle : 360 - angle) / 180.0) *
        ((double)(angle < 180 ? angle : 360 - angle) / 180.0) * 4.0));
    if (v < 0) v = 0;
    if (v > 100) v = 100;
    lv_arc_set_value(arc, v);

    /* CPU freq via clk_summary (we maintain the status CGI fallback) */
    long mhz = 0;
    FILE *fp = fopen("/sys/kernel/debug/clk/clk_summary", "r");
    if (fp) {
        char line[256];
        while (fgets(line, sizeof line, fp)) {
            if (strstr(line, "armclk ") == line ||
                (strncmp(line + strspn(line, " "), "armclk", 6) == 0 &&
                 !isalnum(line[strspn(line, " ") + 6]))) {
                long rate = 0;
                char *p = line;
                /* skip name + 4 integer fields, 5th is rate */
                for (int i = 0; i < 4; i++) { while (*p && !isspace(*p)) p++; while (*p && isspace(*p)) p++; }
                /* now at 4th token (prepare_count), advance once more */
                while (*p && !isspace(*p)) p++; while (*p && isspace(*p)) p++;
                rate = strtol(p, NULL, 10);
                mhz = rate / 1000000;
                break;
            }
        }
        fclose(fp);
    }

    /* Load avg + memory */
    double load = 0;
    FILE *lf = fopen("/proc/loadavg", "r");
    if (lf) { (void)fscanf(lf, "%lf", &load); fclose(lf); }

    long mem_total_kb = 0, mem_avail_kb = 0;
    fp = fopen("/proc/meminfo", "r");
    if (fp) {
        char line[128];
        while (fgets(line, sizeof line, fp)) {
            if (sscanf(line, "MemTotal: %ld kB", &mem_total_kb) == 1) continue;
            if (sscanf(line, "MemAvailable: %ld kB", &mem_avail_kb) == 1) continue;
        }
        fclose(fp);
    }
    long mem_used_mb = (mem_total_kb - mem_avail_kb) / 1024;

    lv_label_set_text_fmt(lbl_stats, "%ld MHz  load %.2f  mem %ld/%ld MB",
        mhz, load, mem_used_mb, mem_total_kb / 1024);
}

/* Auto-discover the Goodix input device path */
static const char *find_touch_device(void)
{
    static char path[64];
    DIR *d = opendir("/sys/class/input");
    if (!d) return "/dev/input/event0";
    struct dirent *de;
    while ((de = readdir(d))) {
        if (strncmp(de->d_name, "event", 5) != 0) continue;
        char namepath[128];
        snprintf(namepath, sizeof namepath, "/sys/class/input/%s/device/name", de->d_name);
        FILE *f = fopen(namepath, "r");
        if (!f) continue;
        char name[64] = {0};
        fgets(name, sizeof name, f);
        fclose(f);
        if (strstr(name, "Goodix") || strstr(name, "gt911") || strstr(name, "GT911")) {
            snprintf(path, sizeof path, "/dev/input/%s", de->d_name);
            closedir(d);
            return path;
        }
    }
    closedir(d);
    return "/dev/input/event0";
}

int main(void)
{
    lv_init();

    lv_display_t *disp = lv_linux_fbdev_create();
    lv_linux_fbdev_set_file(disp, "/dev/fb0");

    const char *touch = find_touch_device();
    printf("luckfox-lvgl-demo: touch device = %s\n", touch);
    lv_indev_t *ind = lv_evdev_create(LV_INDEV_TYPE_POINTER, touch);
    (void)ind;

    build_ui();
    lv_timer_create(tick_stats, 100, NULL);

    while (1) {
        uint32_t t = lv_timer_handler();
        if (t > 500) t = 500;
        usleep(t * 1000);
    }
    return 0;
}
