#!/bin/bash
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