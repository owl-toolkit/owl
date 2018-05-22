#!/usr/bin/env bash

set -euo pipefail
IFS=$'\t\n'

any_err="0"

for binary in $(find build/bin/* -executable -type f \! -name "*.bat" -print); do
  if ! [[ "$binary" =~ ^build/bin/owl.* ]]; then
    if ! ${binary} < /dev/null; then
      any_err="1"
    fi
  fi
done

exit ${any_err}