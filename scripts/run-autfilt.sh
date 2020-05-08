#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

exec "$@" | autfilt --high --parity --deterministic -