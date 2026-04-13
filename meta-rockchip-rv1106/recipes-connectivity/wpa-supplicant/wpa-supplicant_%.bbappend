FILESEXTRAPATHS:prepend := "${THISDIR}/${BPN}:"

SRC_URI += " \
    file://wpa_supplicant-nl80211-wlan0.conf.in \
    file://wlan0.network \
    file://end0.network \
"

# Credentials — read from local.conf (or environment) at build
# time. Leaving them unset is fine: a placeholder config is baked
# in so wpa_supplicant starts, scans, and sits idle waiting for
# someone to drop a real config on the target, and the networkd
# wlan0.network file is installed regardless.
#
# Do NOT set these in any committed file — set them in
# build/conf/local.conf or via `bitbake -e` env overrides.
LUCKFOX_WIFI_SSID ??= ""
LUCKFOX_WIFI_PSK ??= ""
LUCKFOX_WIFI_COUNTRY ??= "00"

do_install:append() {
    # wpa_supplicant config — substitute credentials from local.conf
    install -d ${D}${sysconfdir}/wpa_supplicant
    if [ -n "${LUCKFOX_WIFI_SSID}" ] && [ -n "${LUCKFOX_WIFI_PSK}" ]; then
        sed \
            -e "s|@@LUCKFOX_WIFI_COUNTRY@@|${LUCKFOX_WIFI_COUNTRY}|g" \
            -e "s|@@LUCKFOX_WIFI_SSID@@|${LUCKFOX_WIFI_SSID}|g" \
            -e "s|@@LUCKFOX_WIFI_PSK@@|${LUCKFOX_WIFI_PSK}|g" \
            ${WORKDIR}/wpa_supplicant-nl80211-wlan0.conf.in \
            > ${D}${sysconfdir}/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf
        chmod 600 ${D}${sysconfdir}/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf
        bbnote "wpa_supplicant-nl80211-wlan0.conf baked with SSID '${LUCKFOX_WIFI_SSID}'"
    else
        # Install a placeholder so wpa_supplicant still has a valid
        # config to read — scans but never associates. User can edit
        # it on the target after flashing, or rebuild with the
        # variables set in local.conf.
        cat > ${D}${sysconfdir}/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf <<EOF
# Placeholder wpa_supplicant config. Build with
#   LUCKFOX_WIFI_SSID = "your-ssid"
#   LUCKFOX_WIFI_PSK = "your-psk"
#   LUCKFOX_WIFI_COUNTRY = "IE"
# in conf/local.conf to have this replaced at image-build time.
ctrl_interface=/var/run/wpa_supplicant
update_config=1
country=00
EOF
        chmod 600 ${D}${sysconfdir}/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf
        bbwarn "LUCKFOX_WIFI_SSID / LUCKFOX_WIFI_PSK not set — shipped a placeholder wpa_supplicant config"
    fi

    # systemd-networkd network files — auto-DHCP both interfaces.
    install -d ${D}${sysconfdir}/systemd/network
    install -m 0644 ${WORKDIR}/wlan0.network ${D}${sysconfdir}/systemd/network/20-wlan0.network
    install -m 0644 ${WORKDIR}/end0.network ${D}${sysconfdir}/systemd/network/10-end0.network
}

# Enable the wpa_supplicant + systemd-networkd units at first boot.
SYSTEMD_AUTO_ENABLE:${PN} = "enable"
SYSTEMD_SERVICE:${PN} += "wpa_supplicant-nl80211@wlan0.service"

FILES:${PN} += " \
    ${sysconfdir}/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf \
    ${sysconfdir}/systemd/network/20-wlan0.network \
    ${sysconfdir}/systemd/network/10-end0.network \
"

# Treat the baked-in config as a config file so package upgrades
# don't clobber any on-device edits.
CONFFILES:${PN} += "${sysconfdir}/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf"
