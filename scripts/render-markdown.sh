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
IFS=$'\n\t'

[ ${#} -eq 1 ] || echo "No destination path given" && exit 1

# shellcheck source=scripts/vars.sh
source "$(dirname "$0")/vars.sh"

files=("README.md" "CHANGELOG.md" "doc/"*)
destination="$1"

for file_path in "${files[@]}"; do
  source_path="${project_folder}/$file_path"
  if [ ! -f "$source_path" ]; then
    echo "File $source_path does not exist"
    exit 1
  fi
  destination_path="$destination/${file_path%.md}.html"
  mkdir -p "$(dirname "${destination_path}")"

  pandoc --standalone -f gfm -t html5 -o "$destination_path" "$source_path"
  sed -i -- 's/[.]md/.html/g' "$destination_path"
done
