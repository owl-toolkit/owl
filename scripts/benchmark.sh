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

if [[ ${#} -eq 0 ]]; then
  echo "No arguments specified"
  exit 1
fi

# shellcheck source=./vars.sh
source "$(dirname "$0")/vars.sh"

# Set default timing method
if command -v perf 1>/dev/null 2>&1; then
  method="perf"
  perf_supported="1"
else
  method="time"
  perf_supported="0"
fi

declare -a formulas=()
declare -a formula_files=()
declare -a tool_executions=()

update="0"
repeats="0"
read_stdin="0"

while [ ${#} -gt 0 ] && [ "$1" != "--" ]; do
  option="$1"
  case ${option} in
    "-u"|"--update")
      update="1"
      shift
    ;;
    "-f"|"--formula")
      formulas+=("$2")
      shift 2
    ;;
    "-F"|"--file")
      formula_files+=("$2")
      shift 2
    ;;
    "--time")
      method="time"
      shift
    ;;
    "--perf")
      method="perf"
      shift
    ;;
    "--stdin")
      read_stdin="1"
      shift
    ;;
    "-r"|"--repeat")
      repeats="$2"
      shift 2
    ;;
    *)
      echo "Unknown option $1"
      exit 1
    ;;
  esac
done

if [ ${#} -eq 0 ]; then
  echo "Missing tool specification"
  exit 1
fi
shift

tool_executable="$1"
if ! [ -e "${tool_executable}" ] && ! command -v "${tool_executable}" 1>/dev/null 2>&1; then
  echo "Specified tool executable $tool_executable not found"
  exit 1
fi
tool_executions+=("$tool_executable")
shift;
while [ ${#} -gt 0 ]; do tool_executions+=("$1"); shift; done
# shellcheck disable=SC2145
echo "Tested tool: ${tool_executions[@]}"

if [ "$update" = "1" ]; then
  echo "Updating binaries"
  (
    cd "${project_folder}"
    if ! output=$(./gradlew --no-daemon buildBin); then
      echo "Gradle failed, output:"
      echo "${output}"
      exit 1
    fi
  )
fi

if [ -z "$repeats" ] || [[ "$repeats" -le 0 ]]; then
  repeats="1"
fi
if [ "$method" == "perf" ] && [ "$perf_supported" != "1" ]; then
  echo "Perf not supported; ignoring"
  method="time"
fi
if [ "$repeats" -gt 1 ] && [ "$method" != "perf" ]; then
  echo "Specifying repeats only supported together with using perf; ignoring"
fi

if ! formula_file=$(mktemp -t owl.tmp.XXXXXXXXXX); then
  echo "Failed to obtain temporary file"
  exit 1
fi

function remove_temp {
  rm -f "${formula_file}"
}
trap remove_temp EXIT

echo "Building formula file"

if [ ${#formula_files[@]} -gt 0 ]; then
  for file in "${formula_files[@]}"; do
    cat "${file}" >> "${formula_file}"
  done
fi
if [ ${#formulas[@]} -gt 0 ]; then
  for formula in "${formulas[@]}"; do
    echo "${formula}" >> "${formula_file}"
  done
fi
if [ "$read_stdin" = "1" ]; then
  cat - >> "${formula_file}"
fi

if ! [ -s "${formula_file}" ]; then
  echo "No input formulas available in aggregation file ${formula_file}"
  exit 1
fi

echo -n "Running benchmark with $method and formulas from $formula_file "
echo    "(total of $(wc -l < "${formula_file}") formulas)"
# shellcheck disable=SC2145
echo    "Command: ${tool_executions[@]}"

tool_executions=( "${tool_executions[@]/\%F/"${formula_file}"}" )


if [ "$method" = "time" ]; then
  if [ -e "/usr/bin/time" ]; then
    { /usr/bin/time "${tool_executions[@]}" >/dev/null; } 2>&1
  else
    time { "${tool_executions[@]}" >/dev/null; } 2>&1
  fi
elif [ "$method" = "perf" ]; then
  { perf stat -d -r ${repeats} ${tool_executions[@]} >/dev/null; } 2>&1
fi
