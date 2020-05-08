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

case "$(uname -s)" in
  CYGWIN*)
    os="windows"
    ;;
  *)
    os="linux"
    ;;
esac

if [[ $os == "windows" ]]; then
    function win_path() {
      for path in "$@"; do
        cygpath -w "$path"
      done
    }
    function unix_path() {
      for path in "$@"; do
        cygpath -u "$path"
      done
    }
else
    function win_path() {
      echo "$@"
    }
    function unix_path() {
      echo "$@"
    }
fi
