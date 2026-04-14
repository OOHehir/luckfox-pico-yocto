
# Dev setup

- Luckfox Pico Ultra connected via USB
- An ESP-PROG USB to serial adapter connected to get console output
  - Adapter TX → Board pin 43 (RX)
  - Adapter RX → Board pin 42 (TX)
  - Adapter GND → Board GND
  - Do NOT connect VCC/3.3V — board is powered via pin header
  - 115200 baud, 8N1 (do not use 1500000 — adapter can't handle it)
- A board with 4 relays connected via USB (see /home/claude/projects/usb_relay_controller)
 - relay 1 (NO) shorts SARADC_IN0 to GND (maskrom entry) via 22 ohm resistor
 - relay 2 (NC) controls the USB power line
 - relay 3 (NC) Data +
 - relay 4 (NC) Data -

**USB relay sequencing**
— Cutting VBUS (relay 2) while D+/D- (relays 3/4)
remain connected can back-power the target through the host's data-line
drivers and the SoC's ESD clamp diodes, risking latch-up on the RV1106.
Safe order: **power off** = disconnect D+/D- first, then VBUS;
**power on** = connect VBUS first, then D+/D-. The relay controller's
`usb_power_off()` / `usb_power_on()` helpers enforce this sequence.