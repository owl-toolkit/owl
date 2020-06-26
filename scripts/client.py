#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import socket

if __name__ == "__main__":
    if len(sys.argv) == 1 and sys.argv[0] == "--version":
        print("owl python client version 1.0")
        sys.exit(0)

    if not 3 <= len(sys.argv) <= 4:
        print("Args: <hostname> <port> <input>?")

    hostname, port = sys.argv[1], int(sys.argv[2])
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((hostname, port))

        if len(sys.argv) == 4:
            s.sendall(sys.argv[3].encode())
        else:
            s.sendall(sys.stdin.buffer.read())
        s.shutdown(socket.SHUT_WR)
        data = b''
        while True:
            recv = s.recv(1024)
            if not recv:
                break
            data += recv
        print(data.decode(), end="")
