#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import socket
import subprocess
import sys
import time
import signal
from contextlib import closing


def is_open(port):
    with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
        sock.settimeout(0.1)
        try:
            if sock.connect_ex(('localhost', port)) == 0:
                return True
        except socket.timeout:
            pass
    return False


def run(servers, env=None):
    open_ports = set()
    for port in servers.keys():
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
            sock.settimeout(0.1)
            try:
                if sock.connect_ex(('localhost', port)) == 0:
                    open_ports.add(port)
            except socket.timeout:
                pass
    if open_ports:
        print("Open ports: {0}".format(", ".join(map(str, open_ports))))

    server_processes = dict()
    for port, server in servers.items():
        server_process = subprocess.Popen(server, stdin=subprocess.DEVNULL,
                                          stdout=subprocess.DEVNULL,
                                          stderr=None, env=env)
        server_processes[port] = server_process

    for port, process in server_processes.items():
        while not is_open(port):
            if process.poll() is not None:
                sys.exit(1)
            time.sleep(0.25)

    return server_processes


def stop(server_processes):
    for server_process in server_processes.values():
        if os.name == 'nt':
            os.kill(server_process.pid, signal.CTRL_C_EVENT)
        else:
            server_process.terminate()

    for server_process in server_processes.values():
        try:
            server_process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            server_process.kill()

        time.sleep(0.5)


if __name__ == "__main__":
    servers = dict()
    args = sys.argv[1:]

    while args:
        port = int(args[0])
        try:
            idx = args.index("#")
            server_args = args[1:idx]
            args = args[idx + 1:]
        except ValueError:
            server_args = args[1:]
            args = []
        servers[port] = server_args

    servers = run(servers)

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass

    stop(servers)
