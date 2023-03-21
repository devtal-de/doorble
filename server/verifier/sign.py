#!/usr/bin/env python3
from datetime import timedelta
import sys
import key

if __name__ == "__main__":
    if len(sys.argv) <= 2:
        print("usage: " + sys.argv[0] + " key token")
        exit(1)
    with open(sys.argv[1], 'rb') as f:
        k = f.read()
    print(key.gen_jwt(sys.argv[2], k, timedelta(seconds=30)))
