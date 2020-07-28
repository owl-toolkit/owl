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
echo "Running test with args $@"

# shellcheck source=./vars.sh
source "$(dirname "$0")/vars.sh"

results_folder="${project_folder}/build/results"
evaluation_script="${script_folder}/spotcross-eval.py"
timeout_sec="300"
any_error=0

if command -v dot >/dev/null 2>&1; then
  has_dot=1
else
  has_dot=0
fi

# This tool will be the "trusted" one - we assume that this is always correct
reference_tool_name="$1"
reference_tool_invocation="$2"
shift 2

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

mkdir -p "$results_folder"

num_datasets=${#}
if [ ${num_datasets} -eq 0 ]; then
  echo "No datasets given"
  exit 1
fi

while [ ${#} -gt 0 ]; do
  additional_args=""

  if [ "$1" = "-d" ]; then
    additional_args="--determinize"
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
  grind_file="$results_folder/debug-$dataset_name.ltl"
  csv_file="$results_folder/data-$dataset_name.csv"

  dataset_error="0"
  echo -n "Invocation: "
  echo -n "${python} scripts/util.py formula ${dataset} |" \
    "ltlcross --stop-on-error --strength " \
    "--csv=\"${csv_file}\" --grind=\"$grind_file\" --timeout=\"$timeout_sec\" " \
    "${additional_args} " \
    "--reference \"{$reference_tool_name} $reference_tool_invocation >%O\""
  for invocation in "${tools_invocation[@]}"; do
    echo -n " \"$invocation\""
  done
  echo ""

  if ! ${python} scripts/util.py formula "${dataset}" |
    ltlcross --stop-on-error --strength \
      --csv="$csv_file" --grind="$grind_file" --timeout="$timeout_sec" \
      ${additional_args} \
      --reference "{$reference_tool_name} $reference_tool_invocation >%O" \
      "${tools_invocation[@]}" 2> >(tee "$ltlcross_output_file"); then
    dataset_error="1"
  fi

  if [ ${dataset_error} = "0" ]; then
    # Keep the directory clean
    rm -f "$grind_file"
  else
    any_error=1

    faulty_formula="$(tail -n1 "$grind_file")"
    if [ -z "$faulty_formula" ]; then
      echo "Not generating automaton image as no faulty formula is present"
      exit 1
    fi

    echo "Generating automaton image for $faulty_formula"
    for i in "${!tool_names[@]}"; do
      # Find the tool which caused the problem
      declare -a pos_highlight
      declare -a neg_highlight
      tool_error="0"
      tool_index=$((i + 1))
      tested_tool_invocation=${tool_invocations[$i]}
      tested_tool_name=${tool_names[$i]}

      # Intersection checks

      function find_intersection_error() {
        pos="$1"
        neg="$2"
        pattern="($pos\*$neg|$neg\*$pos|Comp\($pos\)\*$neg|$neg\*Comp\($pos\)|$pos\*Comp\($neg\)|"
        pattern+="Comp\($neg\)\*$pos|Comp\($pos\)\*Comp\($neg\)|Comp\($neg\)\*Comp\($pos\))"
        pattern+=" is nonempty;"
        grep -A 1 -E "$pattern" "${ltlcross_output_file}" | tail -n1 | tr -d '[:space:]' || true
      }

      # P0 is always the reference tool
      neg_err=$(find_intersection_error "P0" "N${tool_index}")
      pos_err=$(find_intersection_error "P${tool_index}" "N0")
      int_err=$(find_intersection_error "P${tool_index}" "N${tool_index}")

      if [ -n "$neg_err" ]; then
        # reference_pos * test_neg not empty, test_neg is faulty
        neg_highlight+=("--highlight-word=0,$neg_err")
        tool_error="1"
      fi
      if [ -n "$pos_err" ]; then
        # reference_neg * test_pos not empty, test_pos is faulty
        pos_highlight+=("--highlight-word=0,$pos_err")
        tool_error="1"
      fi
      if [ -n "$int_err" ]; then
        # test_pos * test_neg not empty, one of them is faulty
        pos_highlight+=("--highlight-word=1,$int_err")
        neg_highlight+=("--highlight-word=1,$int_err")
        tool_error="1"
      fi

      # Cross comparison checks

      pos_cross_err=$(grep "P${tool_index} accepts: " "${ltlcross_output_file}" |
        tail -n1 | sed 's/P'${tool_index}' accepts\: //' | tr -d '[:space:]' || true)
      neg_cross_err=$(grep "N${tool_index} accepts: " "${ltlcross_output_file}" |
        tail -n1 | sed 's/N'${tool_index}' accepts\: //' | tr -d '[:space:]' || true)

      if [ -n "$pos_cross_err" ]; then
        pos_highlight+=("--highlight-word=2,$pos_cross_err")
        tool_error="1"
      fi
      if [ -n "$neg_cross_err" ]; then
        neg_highlight+=("--highlight-word=2,$neg_cross_err")
        tool_error="1"
      fi

      # Create images

      function make_image() {
        formula="$1"
        destination="$2"
        shift 2
        declare -a highlight=( "$@" )

        if ! destination_file=$(mktemp -t owl.tmp.XXXXXXXXXX); then
          echo "Failed to obtain temprorary file"
          exit 1
        fi

        formula_invocation="${tested_tool_invocation/\%f/\"${formula}\"}"
        if ! err_output=$(eval timeout -s KILL -k 1s "${timeout_sec}s" \
          "${formula_invocation}" 2>&1 >"$destination_file"); then
          rm "${destination_file}"
          echo "$tested_tool_name failed for formula $formula"
          echo "Invocation: ${formula_invocation}"
          echo "Output:"
          echo "$err_output"
          return 0
        fi
        if ! [ -s "$destination_file" ]; then
          rm -f "$destination_file"
          echo "$tested_tool_name produced no output for formula $formula"
          echo "Invocation: ${formula_invocation}"
          return 0
        fi

        cp "${destination_file}" "$destination.hoa"

        # b: Bullets for acceptance, a: Print acceptance, h: Horizontal layout,
        # R: Colours for acceptance, C: State colour, f: Font, k: Use state labels, n: Display name
        # s: Show SCCs
        export SPOT_DOTDEFAULT="BahRC(#ffffa0)f(Lato)ksn"
        export SPOT_DOTEXTRA="edge[arrowhead=vee, arrowsize=.7]"

        # highlight-word fails for fin acceptance, have to strip it
        if [ ${#} -eq 0 ]; then
          autfilt --dot <"${destination_file}" >"$destination.dot"
        elif ! autfilt --dot "${highlight[@]}" <"${destination_file}" \
          >"$destination.dot" 2>/dev/null; then
          autfilt --dot <"${destination_file}" >"$destination.dot"
          autfilt --dot --strip-acceptance "${highlight[@]}" <"${destination_file}" \
            >"$destination-trace.dot"
        fi

        if [ "$has_dot" == "1" ]; then
          dot -Tsvg "$destination.dot" >"$destination.svg"
          if [ -f "$destination-trace.dot" ]; then
            dot -Tsvg "$destination-trace.dot" >"$destination-trace.svg"
          fi
        fi
      }

      if [ ${tool_error} = "1" ]; then
        make_image "$faulty_formula" "$results_folder/error-pos" ${pos_highlight[@]}
        make_image "!($faulty_formula)" "$results_folder/error-neg" ${neg_highlight[@]}
      fi
    done
  fi

  if [ ${num_datasets} -gt 1 ]; then
    echo ""
    echo "Stats for dataset $dataset_name"
    ! ${python} "$evaluation_script" "ltl" "$csv_file" |
      tee "$results_folder/stats-$dataset_name.txt"
    echo ""
  fi

  csv_files+=( "$csv_file" )

  if [ "$dataset_error" = "1" ]; then
    # If this dataset failed, don't evaluate more sets
    break
  fi
done

[ ${num_datasets} -eq 1 ] || echo "Overall stats"
! ${python} "$evaluation_script" "ltl" "${csv_files[@]}" | tee "$results_folder/stats.txt"

# Propagate the error
exit ${any_error}
