#!/bin/sh
# Luckfox Pico status page — lighttpd CGI

# Handle LED trigger change via query string. Toggles between heartbeat
# and none so the user can verify that GPIO3_C6 is actually wired and
# software-controllable.
LED_PATH=/sys/class/leds/work
case "$QUERY_STRING" in
    led=heartbeat|led=none|led=default-on|led=timer)
        TRIG=${QUERY_STRING#led=}
        echo "$TRIG" > $LED_PATH/trigger 2>/dev/null
        echo "Status: 303 See Other"
        echo "Location: /status.cgi"
        echo ""
        exit 0
        ;;
esac

echo "Content-Type: text/html"
echo ""

HOSTNAME=$(hostname)
UPTIME=$(uptime -p 2>/dev/null || uptime | sed 's/.*up /up /' | sed 's/,.*load.*//')
LOAD=$(cat /proc/loadavg | cut -d' ' -f1-3)
NPROC=$(nproc)

# Memory
MEM_TOTAL=$(awk '/^MemTotal/ {printf "%.0f", $2/1024}' /proc/meminfo)
MEM_AVAIL=$(awk '/^MemAvailable/ {printf "%.0f", $2/1024}' /proc/meminfo)
MEM_USED=$((MEM_TOTAL - MEM_AVAIL))
MEM_PCT=$((MEM_USED * 100 / MEM_TOTAL))
CMA_TOTAL=$(awk '/^CmaTotal/ {printf "%.0f", $2/1024}' /proc/meminfo)
CMA_FREE=$(awk '/^CmaFree/  {printf "%.0f", $2/1024}' /proc/meminfo)

# Network — end0 is the integrated RMII ethernet
IP_ADDR=$(ip -4 addr show end0 2>/dev/null | awk '/inet / {print $2}')
MAC_ADDR=$(cat /sys/class/net/end0/address 2>/dev/null)
LINK_SPEED=$(cat /sys/class/net/end0/speed 2>/dev/null)
LINK_STATE=$(cat /sys/class/net/end0/operstate 2>/dev/null)

# WiFi (AIC8800DC)
WIFI_IF=$(ls /sys/class/net/ 2>/dev/null | grep -E '^wl' | head -n1)
if [ -n "$WIFI_IF" ]; then
    WIFI_IP=$(ip -4 addr show "$WIFI_IF" 2>/dev/null | awk '/inet / {print $2}')
    WIFI_SSID=$(iw dev "$WIFI_IF" link 2>/dev/null | awk '/SSID/ {print $2}')
    WIFI_STATE=$(cat /sys/class/net/"$WIFI_IF"/operstate 2>/dev/null)
fi

# System
KERNEL=$(uname -r)
MODEL=$(tr -d '\0' < /proc/device-tree/model 2>/dev/null)
TEMP=$(awk '{printf "%.1f", $1/1000}' /sys/class/thermal/thermal_zone0/temp 2>/dev/null)
CPU_FREQ=$(awk '{printf "%.0f", $1/1000}' /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null)
# Fallback for SoCs without cpufreq scaling (e.g. RV1106) — read armclk
# from the clock-tree debugfs and convert Hz → MHz.
if [ -z "$CPU_FREQ" ] && [ -r /sys/kernel/debug/clk/clk_summary ]; then
    CPU_FREQ=$(awk '$1=="armclk" {printf "%.0f", $5/1000000; exit}' /sys/kernel/debug/clk/clk_summary)
fi

# Storage
ROOT_DEV=$(mount | awk '/ on \/ / {print $1}')
if echo "$ROOT_DEV" | grep -q 'mmcblk0'; then
    if [ -e /sys/class/block/mmcblk0boot0 ]; then
        BOOT_MEDIA="eMMC"
    else
        BOOT_MEDIA="SD card"
    fi
    EMMC_SIZE=$(awk '{printf "%.1f GB", $1 * 512 / 1073741824}' /sys/class/block/mmcblk0/size 2>/dev/null)
else
    BOOT_MEDIA="unknown"
    EMMC_SIZE="n/a"
fi
# eMMC wear level: life_time reports two hex digits, one per flash vendor slot.
# 0x01 = 0-10% used, 0x02 = 10-20%, ..., 0x0a = 90-100%, 0x0b = exceeded.
EMMC_LIFE_RAW=$(cat /sys/class/mmc_host/mmc0/mmc0:0001/life_time 2>/dev/null)
EMMC_LIFE_A=$(echo "$EMMC_LIFE_RAW" | awk '{gsub(/0x0?/,"",$1); v=$1*10; if(v>0) printf "%d%%", v-10; else print "n/a"}')
DISK_USED=$(df / | awk 'NR==2 {printf "%.0f", $3/1024}')
DISK_TOTAL=$(df / | awk 'NR==2 {printf "%.0f", $2/1024}')
DISK_PCT=$(df / | awk 'NR==2 {gsub(/%/,""); print $5}')

# NPU
NPU_PRESENT="no"
[ -e /dev/rknpu ] && NPU_PRESENT="yes"
NPU_DRV=$(cat /sys/kernel/debug/rknpu/version 2>/dev/null | sed -n 's/^RKNPU driver: v//p')
[ -z "$NPU_DRV" ] && NPU_DRV=$(cat /sys/kernel/debug/rknpu/version 2>/dev/null | head -1)
[ -z "$NPU_DRV" ] && NPU_DRV=$(sed -n 's/^RKNPU driver: v//p' /proc/driver/rknpu/version 2>/dev/null)

# LED — RV1106 Pico Ultra W has a single gpio-led on GPIO3_C6
# labeled "work" with the heartbeat trigger by default.
if [ -d "$LED_PATH" ]; then
    LED_TRIG=$(sed -n 's/.*\[\(.*\)\].*/\1/p' "$LED_PATH/trigger" 2>/dev/null)
    LED_BRIGHT=$(cat "$LED_PATH/brightness" 2>/dev/null)
fi

# Touch — GT911 on I2C3, driver goodix. Locates the matching input
# device by walking /sys/class/input/*/device/name.
TOUCH_DEV=""
TOUCH_NAME=""
for n in /sys/class/input/event*/device/name; do
    [ -r "$n" ] || continue
    nm=$(cat "$n")
    case "$nm" in
        *Goodix*|*goodix*|*GT911*|*gt911*)
            TOUCH_DEV=$(basename $(dirname $(dirname "$n")))
            TOUCH_NAME="$nm"
            break
            ;;
    esac
done

# Display
DISPLAY_MODE=$(cat /sys/class/drm/card0-DPI-1/modes 2>/dev/null | head -n1)
DISPLAY_STATUS=$(cat /sys/class/drm/card0-DPI-1/status 2>/dev/null)
if [ -e /dev/fb0 ]; then
    FB_SIZE=$(cat /sys/class/graphics/fb0/virtual_size 2>/dev/null)
fi

cat <<HTML
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="refresh" content="5">
<title>${HOSTNAME}</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: -apple-system, sans-serif; background: #0d1117; color: #c9d1d9; padding: 2rem; }
  h1 { color: #58a6ff; margin-bottom: 0.5rem; }
  .subtitle { color: #8b949e; margin-bottom: 2rem; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1rem; }
  .card { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 1.2rem; }
  .card h2 { color: #58a6ff; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.8rem; }
  .stat { display: flex; justify-content: space-between; padding: 0.4rem 0; border-bottom: 1px solid #21262d; }
  .stat:last-child { border-bottom: none; }
  .label { color: #8b949e; }
  .value { color: #f0f6fc; font-family: monospace; }
  .bar { background: #21262d; border-radius: 4px; height: 8px; margin-top: 0.5rem; }
  .bar-fill { background: #58a6ff; border-radius: 4px; height: 100%; }
  .btn { display: inline-block; padding: 0.3rem 0.8rem; border: 1px solid #30363d; border-radius: 6px; background: #21262d; color: #c9d1d9; text-decoration: none; font-size: 0.8rem; margin-right: 0.3rem; }
  .btn:hover { background: #30363d; }
  .btn.active { background: #1f6feb; border-color: #1f6feb; color: #fff; }
</style>
</head>
<body>
<h1>${HOSTNAME}</h1>
<p class="subtitle">${MODEL}</p>
<div class="grid">
  <div class="card">
    <h2>System</h2>
    <div class="stat"><span class="label">Uptime</span><span class="value">${UPTIME}</span></div>
    <div class="stat"><span class="label">Kernel</span><span class="value">${KERNEL}</span></div>
    <div class="stat"><span class="label">CPUs</span><span class="value">${NPROC}</span></div>
    <div class="stat"><span class="label">CPU Freq</span><span class="value">${CPU_FREQ:-?} MHz</span></div>
    <div class="stat"><span class="label">Load (1/5/15 min)</span><span class="value">${LOAD}</span></div>
    <div class="stat"><span class="label">Temperature</span><span class="value">${TEMP:-n/a} &deg;C</span></div>
  </div>
  <div class="card">
    <h2>Memory</h2>
    <div class="stat"><span class="label">Used / Total</span><span class="value">${MEM_USED} / ${MEM_TOTAL} MB</span></div>
    <div class="bar"><div class="bar-fill" style="width:${MEM_PCT}%"></div></div>
    <div class="stat"><span class="label">CMA Total</span><span class="value">${CMA_TOTAL:-n/a} MB</span></div>
    <div class="stat"><span class="label">CMA Free</span><span class="value">${CMA_FREE:-n/a} MB</span></div>
  </div>
  <div class="card">
    <h2>Network (end0)</h2>
    <div class="stat"><span class="label">State</span><span class="value">${LINK_STATE:-n/a}</span></div>
    <div class="stat"><span class="label">IP Address</span><span class="value">${IP_ADDR:-down}</span></div>
    <div class="stat"><span class="label">MAC</span><span class="value">${MAC_ADDR:-n/a}</span></div>
    <div class="stat"><span class="label">Link Speed</span><span class="value">${LINK_SPEED:-?} Mbps</span></div>
  </div>
HTML

if [ -n "$WIFI_IF" ]; then
cat <<HTML
  <div class="card">
    <h2>WiFi (${WIFI_IF})</h2>
    <div class="stat"><span class="label">State</span><span class="value">${WIFI_STATE:-n/a}</span></div>
    <div class="stat"><span class="label">IP Address</span><span class="value">${WIFI_IP:-down}</span></div>
    <div class="stat"><span class="label">SSID</span><span class="value">${WIFI_SSID:-n/a}</span></div>
  </div>
HTML
fi

cat <<HTML
  <div class="card">
    <h2>Storage</h2>
    <div class="stat"><span class="label">Boot Media</span><span class="value">${BOOT_MEDIA}</span></div>
    <div class="stat"><span class="label">Device</span><span class="value">${ROOT_DEV} (${EMMC_SIZE})</span></div>
    <div class="stat"><span class="label">Rootfs Used</span><span class="value">${DISK_USED} / ${DISK_TOTAL} MB</span></div>
    <div class="bar"><div class="bar-fill" style="width:${DISK_PCT}%"></div></div>
    <div class="stat"><span class="label">eMMC Wear</span><span class="value">${EMMC_LIFE_A:-n/a}</span></div>
  </div>
  <div class="card">
    <h2>Display</h2>
    <div class="stat"><span class="label">Connector</span><span class="value">DPI-1 ${DISPLAY_STATUS:-n/a}</span></div>
    <div class="stat"><span class="label">Mode</span><span class="value">${DISPLAY_MODE:-n/a}</span></div>
    <div class="stat"><span class="label">Framebuffer</span><span class="value">${FB_SIZE:-n/a}</span></div>
  </div>
  <div class="card">
    <h2>NPU</h2>
    <div class="stat"><span class="label">/dev/rknpu</span><span class="value">${NPU_PRESENT}</span></div>
    <div class="stat"><span class="label">Driver</span><span class="value">${NPU_DRV:-n/a}</span></div>
  </div>
HTML

if [ -d "$LED_PATH" ]; then
    btn_class() { [ "$LED_TRIG" = "$1" ] && echo "btn active" || echo "btn"; }
cat <<HTML
  <div class="card">
    <h2>LED (work, GPIO3_C6)</h2>
    <div class="stat"><span class="label">Trigger</span><span class="value">${LED_TRIG:-?}</span></div>
    <div class="stat"><span class="label">Brightness</span><span class="value">${LED_BRIGHT:-?}</span></div>
    <div class="stat" style="border:none">
      <a class="$(btn_class heartbeat)" href="/status.cgi?led=heartbeat">Heartbeat</a>
      <a class="$(btn_class default-on)" href="/status.cgi?led=default-on">On</a>
      <a class="$(btn_class none)" href="/status.cgi?led=none">Off</a>
    </div>
  </div>
HTML
fi

if [ -n "$TOUCH_DEV" ]; then
cat <<HTML
  <div class="card">
    <h2>Touch (GT911)</h2>
    <div class="stat"><span class="label">Device</span><span class="value">/dev/input/${TOUCH_DEV#event/}</span></div>
    <div class="stat"><span class="label">Name</span><span class="value">${TOUCH_NAME}</span></div>
  </div>
HTML
fi

cat <<HTML
</div>
</body>
</html>
HTML
