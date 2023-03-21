#!/usr/bin/env python3

import bluetooth.ble
import sys
import time
import key
from datetime import timedelta

BT_COMM_UUID = "12345678-1234-5678-1234-56789abcdef1"

if __name__ == "__main__":
    if len(sys.argv) <= 2:
        print("usage: " + sys.argv[0] + " key mac")

    with open(sys.argv[1], 'rb') as f:
        pkey = f.read()

    bt_mac = '58:BF:25:17:1E:96' if len(sys.argv) <= 2 else sys.argv[2]

    bt_rq = bluetooth.ble.GATTRequester(bt_mac, False)
    bt_rq.connect()
    while not bt_rq.is_connected():
        time.sleep(1)
    bt_hnd = [ x for x in bt_rq.discover_characteristics() 
                 if x["uuid"] == BT_COMM_UUID ][0]["value_handle"]
    sec_token = bt_rq.read_by_handle(bt_hnd)
    print(sec_token)
    req_jwt = key.gen_jwt(sec_token[0].decode(), pkey, timedelta(seconds=30), keytype="ES256")
    print(req_jwt)
    print(len(req_jwt))
    tok_rt = bt_rq.write_by_handle(bt_hnd, req_jwt)
    print(tok_rt)
    bt_rq.disconnect()

