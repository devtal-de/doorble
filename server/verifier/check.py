#!/usr/bin/env python3
import key
import service
import sys

if __name__ == "__main__":
    service.add_authkeys()
    if len(sys.argv) <= 1:
        print("need token")
        exit(1)
    key.check_jwt(sys.argv[1])
