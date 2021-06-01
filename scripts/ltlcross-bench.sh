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

# shellcheck disable=SC2145
echo "Running bench with args $@"

# shellcheck source=./vars.sh
source "$(dirname "$0")/vars.sh"

results_folder="${project_folder}/build/results"
evaluation_script="${script_folder}/spotcross-eval.py"
timeout_sec="300"

declare -a tool_names
declare -a tool_invocations
declare -a tools_invocation

# Read tools to test from args
echo "Tools:"
while [ "$1" == "-t" ]; do
  tool_names+=("$2")
  tool_invocations+=("$3")
  tools_invocation+=("{$2} $3 >%O")
  echo "  $2 - $3"
  shift 3
done

declare -a csv_files
declare -a dataset_names

mkdir -p "$results_folder"

num_datasets=${#}
if [ ${num_datasets} -eq 0 ]; then
  echo "No datasets given"
  exit 1
fi

while [ ${#} -gt 0 ]; do
  # Strip -d to be compatible to ltlcross-run.sh
  if [ "$1" = "-d" ]; then
    if [ ${#} -eq 1 ]; then
      echo "Missing dataset after -d"
      exit 1
    fi
    shift 1
  fi

  # Iterate over all remaining arguments - these are formula specifications
  echo "Evaluating dataset $1"
  dataset="$1"
  shift

  if [ "$dataset" = "random" ]; then
    dataset_name="random"
  else
    dataset_basename="$(basename "${dataset}")"
    dataset_name="${dataset_basename%.*}"
  fi

  ltlcross_output_file="$results_folder/ltlcross-$dataset_name.txt"
  csv_file="$results_folder/data-$dataset_name.csv"

  ! ${python} scripts/util.py formula "${dataset}" |
    ltlcross --stop-on-error --strength --no-checks \
      --csv="$csv_file" --timeout="$timeout_sec" \
      "${tools_invocation[@]}" 2> >(tee "$ltlcross_output_file")

  dataset_names+=("$dataset_name")
  csv_files+=( "$csv_file" )
done

if [ ${num_datasets} -gt 1 ]; then
  for ((i = 0; i < ${#csv_files[@]}; ++i)); do
    csv_file=${csv_files[i]}
    dataset_name=${dataset_names[i]}
    echo "Stats for $dataset_name"
    ! ${python} "$evaluation_script" "ltl" "$csv_file" | tee "$results_folder/stats-$dataset_name.txt"
    echo ""
  done
fi

[ ${num_datasets} -eq 1 ] || echo "Overall stats"
! ${python} "$evaluation_script" "ltl" "${csv_files[@]}" | tee "$results_folder/stats.txt"
