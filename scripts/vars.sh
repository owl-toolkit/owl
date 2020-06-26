#!/usr/bin/env bash

# Source this for common variables
script_folder="$(cd "$(dirname "$0")" && pwd -P)"
project_folder="$(dirname "$script_folder")"
version=$(grep "project.version" "${project_folder}/build.gradle" | \
    sed "s#project\.version \?= \?'\(.*\)'#\1#")

# Prefer python3 if installed (ubuntu / debian)
if command -v python3 >/dev/null 2>&1; then
  python="python3"
else
  python="python"
fi