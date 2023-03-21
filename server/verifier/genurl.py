#!/usr/bin/env python3
from cryptography.hazmat.primitives.serialization import load_pem_private_key, PublicFormat, Encoding
import sys

key_file = "./key" if len(sys.argv) <= 1 else sys.argv[1]


with open(key_file, 'r') as f:
    priv_key_pem = f.read()
    priv_key_o = load_pem_private_key(priv_key_pem.encode(), password=None)
    pub_key_o = priv_key_o.public_key()
    pub_key_pem = pub_key_o.public_bytes(
        format=PublicFormat.SubjectPublicKeyInfo,
        encoding=Encoding.PEM)

import json

j = json.dumps(dict(name = "xyz",
    mac = "58:BF:25:17:1E:96", # "58:BF:25:82:2E:26"
    pubkey = pub_key_pem.decode('utf8'),
    sub = "open"))

import urllib

url = urllib.parse.urlunparse(
    urllib.parse.ParseResult(
        scheme="app", netloc="doorble.devtal.de", path="/", params="",
        query="", fragment=urllib.parse.quote(j)))

print(url)
