#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

[ ${#} -eq 1 ] || (echo "No destination path given"; exit 1)

# shellcheck source=./vars.sh
source "$(dirname "$0")/vars.sh"

files=("README.md" "CONTRIBUTING.md" "CHANGELOG.md" "doc/"*)
destination="$1"

for file_path in "${files[@]}"; do
  source_path="${project_folder}/$file_path"
  if [ ! -f "$source_path" ]; then
    echo "File $source_path does not exist"
    exit 1
  fi
  destination_path="$destination/${file_path%.md}.html"
  mkdir -p $(dirname "${destination_path}")

  pandoc --standalone -f markdown_github -t html5 -o "$destination_path" "$source_path"
  sed -i -- 's/[.]md/.html/g' "$destination_path"
done
