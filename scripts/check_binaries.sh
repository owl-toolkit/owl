#!/usr/bin/env bash

#
# Copyright (C) 2016 - 2021  (See AUTHORS)
#
# This file is part of Owl.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

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
