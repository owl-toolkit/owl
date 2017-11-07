#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

if [[ ${#} -eq 0 ]]; then
  echo "No arguments specified"
  exit 1
fi

source "$(dirname $0)/vars.sh"

# Set default timing method
if hash perf 2>/dev/null; then
  METHOD="perf"
else
  METHOD="time"
fi

declare -a FORMULAS=()
declare -a FORMULA_FILES=()
declare -a TOOL_EXEC=()

UPDATE="0"
REPEATS="0"
READ_STDIN="0"

while [ ${#} -gt 0 ] && [ "$1" != "--" ]; do
  OPTION="$1"
  case ${OPTION} in
    "-u"|"--update")
      UPDATE="1"
      shift
    ;;
    "-f"|"--formula")
      FORMULAS+=("$2")
      shift 2
    ;;
    "-F"|"--file")
      FORMULA_FILES+=("$2")
      shift 2
    ;;
    "--time")
      METHOD="time"
      shift
    ;;
    "--perf")
      METHOD="perf"
      shift
    ;;
    "--stdin")
      READ_STDIN="1"
      shift
    ;;
    "-r"|"--repeat")
      REPEATS="$2"
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

TOOL_EXECUTABLE="$1"
if ! [ -x ${TOOL_EXECUTABLE} ] && ! hash ${TOOL_EXECUTABLE} 2>/dev/null; then
  echo "Specified tool executable $TOOL_EXECUTABLE not found"
  exit 1
fi
while [ ${#} -gt 0 ]; do TOOL_EXEC+=("$1"); shift; done
echo "Tested tool: $TOOL_EXEC"

if [ "$UPDATE" = "1" ]; then
  echo "Updating binaries"
  (
    cd ${PROJECT_FOLDER}
    if ! OUTPUT=$(./gradlew --no-daemon buildBin); then
      echo "Gradle failed, output:"
      echo ${OUTPUT}
      exit 1
    fi
  )
fi

if [ -z "$REPEATS" ] || [[ "$REPEATS" -le 0 ]]; then
  REPEATS="1"
fi
if [ "$REPEATS" -gt 1 ] && [ "$METHOD" != "perf" ]; then
  echo "Specifying repeats only supported together with using perf; ignoring"
fi

if ! FORMULA_FILE=$(mktemp -t owl.tmp.XXXXXXXXXX); then
  echo "Failed to obtain temporary file"
  exit 1
fi

function remove_temp {
  rm -f ${FORMULA_FILE}
}
trap remove_temp EXIT

echo "Building formula file"

if [ ${#FORMULA_FILES[@]} -gt 0 ]; then
  for FILE in "${FORMULA_FILES[@]}"; do
    cat ${FILE} >> ${FORMULA_FILE}
  done
fi
if [ ${#FORMULAS[@]} -gt 0 ]; then
  for FORMULA in "${FORMULAS[@]}"; do
    echo ${FORMULA} >> ${FORMULA_FILE}
  done
fi
if [ "$READ_STDIN" = "1" ]; then
  cat - >> ${FORMULA_FILE}
fi

if ! [ -s ${FORMULA_FILE} ]; then
  echo "No input formulas available in aggregation file ${FORMULA_FILE}"
  exit 1
fi

echo -n "Running benchmark with $METHOD and formulas from $FORMULA_FILE "
echo    "(total of $(wc -l < ${FORMULA_FILE}) formulas)"

TOOL_EXEC=( "${TOOL_EXEC[@]/\%F/${FORMULA_FILE}}" )

if [ "$METHOD" = "time" ]; then
  /usr/bin/time -v ${TOOL_EXEC[@]} 2>&1 >/dev/null
elif [ "$METHOD" = "perf" ]; then
  perf stat -d -r ${REPEATS} ${TOOL_EXEC[@]} 2>&1 >/dev/null
fi
