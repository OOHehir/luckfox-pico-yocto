# Dev setup

## General

- LuckFox board powered via USB-C.

## Serial setup

- Console via a serial-to-USB adapter (e.g. RP2040 Debugprobe on Pico with CMSIS-DAP firmware, or ESP-PROG):
  - Probe UART2 TX → Board pin 43 (RX)
  - Probe UART2 RX → Board pin 42 (TX)
  - Probe GND → Board GND
  - Do **not** connect VCC/3.3V — the board is powered via its USB-C (relay rig).

- An alternate (for example when SWD is required):
  - Probe UART4 TX → Board pin 40 (RX)
  - Probe UART4 RX → Board pin 41 (TX)
  - Probe GND → Board GND
  - Do **not** connect VCC/3.3V — the board is powered via its USB-C (relay rig).

## SWD setup

- If you need SWD the serial console (above) needs to be moved as there is overlap.
  - Probe SWDIO → Board pin 43
  - Probe SWCLK → Board pin 42

## Baudrate

- Rockchip default is 1500000.
- This image uses 115200 baud, 8N1 for the console.

## USB relay sequencing for automated power cycling

A USB relay board with 4 relays, driven by a small host-side controller script:

- Relay 1 (NO) shorts SARADC_IN0 to GND (maskrom entry). Better yet, the CLK pin of eMMC.
- Relay 2 (NC) controls the USB power line.
- Relay 3 (NC) controls Data +.
- Relay 4 (NC) controls Data -.

### Note on power cycling

Cutting VBUS (relay 2) while D+/D- (relays 3/4) remain connected can back-power
the target through the host's data-line drivers and the SoC's ESD clamp diodes,
risking latch-up on the RV1106.

Safe order:

- **Power off** — disconnect D+/D- first, then VBUS.
- **Power on** — connect VBUS first, then D+/D-.

The relay controller's `usb_power_off()` / `usb_power_on()` helpers enforce this sequence.

## Notes

- The RP2040 Debugprobe enumerates as a CDC ACM serial port at `/dev/ttyACM0` on the host.
- The board can occasionally get wedged, where grounding SARADC_IN0 is insufficient. In this case, shorting the CLK pin of eMMC to GND is required. ([Ref](https://forums.luckfox.com/viewtopic.php?t=2018))
