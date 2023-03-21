# DoorBLE

## About

DoorBLE is a tool to open doors using Bluetooth Low Energy. The client provided
runs on Android devices and issues signed messages to an ESP32 bluetooth module
relaying these messages to a single board computer running a Python 3
application through a UART interface. The application running on the SBC
authenticates these messages to a small list of public keys (currently only
ECDSA is supported) and performs a certain action like toggling a GPIO pin.
Messages are utilizing a protocol quite similar to JSON Web Tokens.

Many single board computers already provide a bluetooth interface, but using a
separate bluetooth module prevent bugs in the bluetooth stack or application to
compromise the authentication mechanism. Currently, the bluetooth interface is
very unstable.

## Components and Building

All components are included in different directories:
 - Client: `client-android` (requires Java, Android-SDK and Make for building).
    Not building yet, since icons are missing for license reasons.
 - BLE UART interface: `interface` (requires Zephyr RTOS on a ESP32 board)
    Not included yet, lots of work needs to be done.
 - Message validator: `server` (requires Python3 and pyjwt library)

## License

GPLv3 (see LICENSE.md)
