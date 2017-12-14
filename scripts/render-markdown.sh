#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

source "$(dirname $0)/vars.sh"

FILES="LTL_GRAMMAR
README
CHANGELOG"

if [[ ${#} -eq 0 ]]; then
  DESTINATION="$PROJECT_FOLDER/build/html"
else
  DESTINATION="$1"
fi

mkdir -p ${DESTINATION}
for FILE in ${FILES}; do
  SOURCE_PATH="$PROJECT_FOLDER/$FILE.md"
  if [ ! -f "$SOURCE_PATH" ]; then
    echo "$FILE not found in $PROJECT_FOLDER"
    exit 1
  fi

  pandoc --standalone -f markdown_github -t html5 -o "$DESTINATION/$FILE.html" "$SOURCE_PATH" || true
done
