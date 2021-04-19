#!/bin/bash
# Usage: tlsf-to-json.sh [directory containing tlsf files]
# Outputs json to standard output
set -e
result=""
for file in $1/*.tlsf; do
        formula="$(syfco -f ltl -q double -m fully $file)"
        controllable="$(syfco -outs $file)"
        uncontrollable="$(syfco -ins $file)"
        result+=$(jq -n --arg formula "$formula" --arg controllable "$controllable" --arg uncontrollable "$uncontrollable" '{formula: ($formula | sub("\"(?<ap>[^\"]*)\"";(.ap | ascii_downcase);"g")), controllable: ($controllable | ascii_downcase  | split(", ")), uncontrollable: ($uncontrollable | ascii_downcase | split(", "))}')
done

echo "$result" | jq -sr '.'