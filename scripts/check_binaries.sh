#!/usr/bin/env bash

set -euo pipefail
IFS=$'\t\n'

if [ $# -eq 0 ]; then
  script_dir="build/bin"
elif [ $# -eq 1 ]; then
  script_dir="$1"
else
  exit 1
fi

[ -d "${script_dir}" ] || exit 1

any_err="0"

for binary in $(find "$script_dir" -executable -type f \! -name "*.bat" -print); do
  if ! ${binary} --version >/dev/null 2>&1; then
    echo "Failed to run $binary"
    any_err="1"
  fi
done

exit ${any_err}
