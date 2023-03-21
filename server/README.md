# DoorBLE server

Run `./verifier/service.py <key> <authorized_keys>`.

This will require
 - a device /dev/ttyUSB0 connected to the bluetooth interface
 - a file `<key>` containing a PEM encoded EC private key
 - a file `<authorized_keys>` containing a list of public keys in OpenSSH
    authorized_keys file format

All issued and received tokens will be printed on stdout. Actions validated
against a public key in `authorized_keys` file will be printed as well.

## tips and tricks

`cat | xargs ./verifier/sign.py key  | qr`

`ssh-keygen -i -f key.pem -m pkcs8` for ECDSA public keys
