#!/usr/bin/env python3
import asyncio
import serial_asyncio

import jwt
import hashlib
from datetime import datetime, timedelta, timezone
from time import sleep
import key
import sys # reading keys from files
from functools import partial

class SerProto(asyncio.Protocol):
    """ serial-asyncio protocol evaluating lines """
    def __init__(self):
        super().__init__()
        self.valid_token_cb = None

    def connection_made(self, transport):
        self.transport = transport
        self.data = b""

    def data_received(self, data: bytes):
        self.data += data
        while b'\n' in self.data:
            # jwt content until first newline found
            newline = self.data.find(b'\n')
            # evaluate and run handler
            print("received:", self.data[:newline].decode())
            val_result = key.check_jwt(self.data[:newline].decode())
            if val_result:
                if self.valid_token_cb:
                    self.valid_token_cb(val_result)
                else:
                    print(val_result)
            self.data = self.data[(newline + 1):]

    def connection_lost(self, exc):
        print("port missing")


def renew_cb(token, transport):
    transport.write(token.encode() + b"\n")
    print("generated:", token)


async def main(loop):
    """ serial event loop. Also renews token each roll_minutes/2 minutes. """
    transport, proto = await serial_asyncio.create_serial_connection(loop,
        SerProto, '/dev/ttyUSB0', baudrate=9600)
    transport.write(b"\n") # initialize connection
    proto.valid_token_cb = valid_token_cb

    await key.renew_token(partial(renew_cb, transport=transport))


def read_auth_keys(name):
    """ reads all valid keys from ssh authorized_keys like file.
        File must use single spaces as delimiter inside keys """
    key_prefixes = {"ssh-rsa": "RS256", "ecdsa-sha2-nistp256": "EC256",
        "ecdsa-sha2-nistp384": "EC384", "ecdsa-sha2-nistp521": "EC512",
        "ssh-ed25519": "EdDSA"}
    keys = { alg:[] for alg in key_prefixes.values() }
    with open(name, 'rb') as f:
        for l in f.readlines():
            l = l.decode()
            if l.split(' ', 1)[0] in key_prefixes.keys():
                keys[key_prefixes[l.split(' ', 1)[0]]].append(l)
            elif l.split(' ', 2)[1] in key_prefixes.keys():
                keys[key_prefixes[l.split(' ', 2)[1]]].\
                    append(l.split(b' ', 1)[1])
            else:
                print("invalid key: "+ l.split(b' ', 1)[0].decode())
    return keys


def set_actions():
    key.set_actions(["open", "lock"])


def add_authkeys():
    """ read all valid public keys from authorized_keys like file """
    authkey_file = "./authorized_keys" if len(sys.argv) <= 2 else sys.argv[2]
    keys = read_auth_keys(authkey_file)
    for alg, akeys in keys.items():
        for k in akeys:
            key.add_key(alg, k)
    return len(keys)


def valid_token_cb(action: str):
    print("ACTION:", action)


if __name__ == "__main__":
    key_file = "./key" if len(sys.argv) <= 1 else sys.argv[1]

    # set signing key to use to sign our own JWTs
    with open(key_file, 'r') as f:
        key.set_signkey(f.read())

    set_actions()
    add_authkeys()

    # run serial interface loop
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main(loop))
    loop.close()
