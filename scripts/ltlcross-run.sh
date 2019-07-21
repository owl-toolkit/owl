#!/usr/bin/env bash

set -euo pipefail
IFS=$'\n\t'

echo "Running test with args" $@

source "$(dirname $0)/vars.sh"
RESULTS_FOLDER="${PROJECT_FOLDER}/build/results"
EVALUATION_SCRIPT="${SCRIPT_FOLDER}/ltlcross-eval.py"
TIMEOUT_SEC="300"
ANY_ERROR=0

# This tool will be the "trusted" one - we assume that this is always correct
REFERENCE_TOOL_NAME="$1"
REFERENCE_TOOL_INVOCATION="$2"
shift 2

declare -a TOOL_NAMES
declare -a TOOL_INVOCATIONS
declare -a TOOLS_INVOCATION

# Read tools to test from args
while [ "$1" == "-t" ]; do
  TOOL_NAMES+=("$2")
  TOOL_INVOCATIONS+=("$3")
  TOOLS_INVOCATION+=("{$2} $3 >%O")
  echo "Testing tool $2 - $3"
  shift 3
done

declare -a CSV_FILES

mkdir -p "$RESULTS_FOLDER"

NUM_DATASETS=${#}
if [ ${NUM_DATASETS} -eq 0 ]; then
  echo "No datasets given"
  exit 1
fi

while [ ${#} -gt 0 ]; do
  ADDITIONAL_ARGS=""

  if [ "$1" = "-d" ]; then
    ADDITIONAL_ARGS="--determinize"
    if [ ${#} -eq 1 ]; then
      echo "Missing dataset after -d"
      exit 1
    fi
    shift 1
  fi

  # Iterate over all remaining arguments - these are formula specifications
  echo "Evaluating dataset $1"
  DATASET="$1"
  shift

  if [ "$DATASET" = "random" ]; then
    DATASET_NAME="random"
  else
    DATASET_BASENAME="$(basename ${DATASET})"
    DATASET_NAME="${DATASET_BASENAME%.*}"
  fi

  LTLCROSS_OUTPUT_FILE="$RESULTS_FOLDER/ltlcross-$DATASET_NAME.txt"
  GRIND_FILE="$RESULTS_FOLDER/debug-$DATASET_NAME.ltl"
  CSV_FILE="$RESULTS_FOLDER/data-$DATASET_NAME.csv"

  DATASET_ERROR="0"
  echo -n "Invocation: "
  echo -n "python3 scripts/util.py formula ${DATASET} |" \
    "ltlcross --stop-on-error --fail-on-timeout --strength " \
    "--csv=\"${CSV_FILE}\" --grind=\"$GRIND_FILE\" --timeout=\"$TIMEOUT_SEC\" " \
    "${ADDITIONAL_ARGS} " \
    "--reference \"{$REFERENCE_TOOL_NAME} $REFERENCE_TOOL_INVOCATION >%O\""
  for INVOCATION in "${TOOLS_INVOCATION[@]}"; do
    echo -n " \"$INVOCATION\""
  done
  echo ""

  if ! python3 scripts/util.py formula ${DATASET} | ltlcross --stop-on-error --strength \
    --csv="$CSV_FILE" ${ADDITIONAL_ARGS} --grind="$GRIND_FILE" --timeout="$TIMEOUT_SEC" \
    "{$REFERENCE_TOOL_NAME} $REFERENCE_TOOL_INVOCATION >%O" \
    ${TOOLS_INVOCATION[@]} 2> >(tee "$LTLCROSS_OUTPUT_FILE"); then
    DATASET_ERROR="1"
  fi

  if [ ${DATASET_ERROR} = "0" ]; then
    # Keep the directory clean
    rm -f "$GRIND_FILE"
  else
    ANY_ERROR=1

    FAULTY_FORMULA="$(tail -n1 "$GRIND_FILE")"
    if [ -z "$FAULTY_FORMULA" ]; then
      echo "Not generating automaton image as no faulty formula is present"
      exit 1
    fi

    echo "Generating automaton image for $FAULTY_FORMULA"
    for i in "${!TOOL_NAMES[@]}"; do
      # Find the tool which caused the problem
      # TODO For each tool, find the last problematic formula
      declare -a POS_HIGHLIGHT
      declare -a NEG_HIGHLIGHT
      TOOL_ERROR="0"
      TOOL_IND=$((i+1))
      TESTED_TOOL_INVOCATION=${TOOL_INVOCATIONS[$i]}
      TESTED_TOOL_NAME=${TOOL_NAMES[$i]}

      # Intersection checks

      function find_intersection_error() {
        POS="$1"
        NEG="$2"
        PATTERN="($POS\*$NEG|$NEG\*$POS|Comp\($POS\)\*$NEG|$NEG\*Comp\($POS\)|$POS\*Comp\($NEG\)|"
        PATTERN+="Comp\($NEG\)\*$POS|Comp\($POS\)\*Comp\($NEG\)|Comp\($NEG\)\*Comp\($POS\))"
        PATTERN+=" is nonempty;"
        grep -A 1 -E "$PATTERN" ${LTLCROSS_OUTPUT_FILE} | tail -n1 | tr -d '[:space:]' || true
      }

      # P0 is always the reference tool
      P0NT_ERR=$(find_intersection_error "P0" "N${TOOL_IND}")
      PTN0_ERR=$(find_intersection_error "P${TOOL_IND}" "N0")
      PTNT_ERR=$(find_intersection_error "P${TOOL_IND}" "N${TOOL_IND}")

      if [ -n "$P0NT_ERR" ]; then
        # reference_pos * test_neg not empty, test_neg is faulty
        NEG_HIGHLIGHT+=( "--highlight-word=0,$P0NT_ERR" )
        TOOL_ERROR="1"
      fi
      if [ -n "$PTN0_ERR" ]; then
        # reference_neg * test_pos not empty, test_pos is faulty
        POS_HIGHLIGHT+=( "--highlight-word=0,$PTN0_ERR" )
        TOOL_ERROR="1"
      fi
      if [ -n "$PTNT_ERR" ]; then
        # test_pos * test_neg not empty, one of them is faulty
        POS_HIGHLIGHT+=( "--highlight-word=1,$PTNT_ERR" )
        NEG_HIGHLIGHT+=( "--highlight-word=1,$PTNT_ERR" )
        TOOL_ERROR="1"
      fi

      # Cross comparison checks

      PT_ERR=$(grep "P${TOOL_IND} accepts: " ${LTLCROSS_OUTPUT_FILE} \
        | tail -n1 | sed 's/P'${TOOL_IND}' accepts\: //' | tr -d '[:space:]' || true)
      NT_ERR=$(grep "N${TOOL_IND} accepts: " ${LTLCROSS_OUTPUT_FILE} \
        | tail -n1 | sed 's/N'${TOOL_IND}' accepts\: //' | tr -d '[:space:]' || true)

      if [ -n "$PT_ERR" ]; then
        POS_HIGHLIGHT+=( "--highlight-word=2,$PT_ERR" )
        TOOL_ERROR="1"
      fi
      if [ -n "$NT_ERR" ]; then
        NEG_HIGHLIGHT+=( "--highlight-word=2,$NT_ERR" )
        TOOL_ERROR="1"
      fi

      # Create images

      function make_image() {
        FORMULA="$1"
        DESTINATION="$2"
        declare -a HIGHLIGHT=("${!3}")

        if ! DESTINATION_FILE=$(mktemp -t owl.tmp.XXXXXXXXXX); then
          echo "Failed to obtain temprorary file"
          exit 1
        fi

        FORMULA_INVOCATION="${TESTED_TOOL_INVOCATION/\%f/\"${FORMULA}\"}"
        if ! ERR_OUTPUT=$(eval timeout -s KILL -k 1s "${TIMEOUT_SEC}s" \
          ${FORMULA_INVOCATION} 2>&1 >"$DESTINATION_FILE"); then
          rm ${DESTINATION_FILE}
          echo "$TESTED_TOOL_NAME failed for formula $FORMULA"
          echo "Invocation: ${FORMULA_INVOCATION}"
          echo "Output:"
          echo "$ERR_OUTPUT"
          return 0
        fi
        if ! [ -s "$DESTINATION_FILE" ]; then
          rm -f "$DESTINATION_FILE"
          echo "$TESTED_TOOL_NAME produced no output for formula $FORMULA"
          echo "Invocation: ${FORMULA_INVOCATION}"
          return 0
        fi

        cp ${DESTINATION_FILE} "$DESTINATION.hoa"

        # b: Bullets for acceptance, a: Print acceptance, h: Horizontal layout,
        # R: Colours for acceptance, C: State colour, f: Font, k: Use state labels, n: Display name
        # s: Show SCCs
        export SPOT_DOTDEFAULT="BahRC(#ffffa0)f(Lato)ksn"
        export SPOT_DOTEXTRA="edge[arrowhead=vee, arrowsize=.7]"

        # highlight-word fails for fin acceptance, have to strip it
        if [ ${#HIGHLIGHT[@]} -eq 0 ]; then
         cat ${DESTINATION_FILE} | autfilt --dot > "$DESTINATION.dot"
        elif ! cat ${DESTINATION_FILE} | autfilt --dot "${HIGHLIGHT[@]}" > "$DESTINATION.dot" \
          2>/dev/null; then
          cat ${DESTINATION_FILE} | autfilt --dot > "$DESTINATION.dot"
          cat ${DESTINATION_FILE} | autfilt --dot --strip-acceptance "${HIGHLIGHT[@]}" \
            > "$DESTINATION-trace.dot"
        fi

        dot -Tsvg "$DESTINATION.dot" > "$DESTINATION.svg"
        if [ -f "$DESTINATION-trace.dot" ]; then
          dot -Tsvg "$DESTINATION-trace.dot" > "$DESTINATION-trace.svg"
        fi
      }

      if [ ${TOOL_ERROR} = "1" ]; then
        make_image "$FAULTY_FORMULA" "$RESULTS_FOLDER/error-pos" POS_HIGHLIGHT[@]
        make_image "!($FAULTY_FORMULA)" "$RESULTS_FOLDER/error-neg" NEG_HIGHLIGHT[@]
      fi
    done
  fi

  if [ ${NUM_DATASETS} -gt 1 ]; then
    echo ""
    echo "Stats for dataset $DATASET_NAME"
    python3 "$EVALUATION_SCRIPT" "$CSV_FILE" | tee "$RESULTS_FOLDER/stats-$DATASET_NAME.txt"
    echo ""
  fi

  CSV_FILES+=( "$CSV_FILE" )

  if [ "$DATASET_ERROR" = "1" ]; then
    # If this dataset failed, don't evaluate more sets
    break
  fi
done

[ ${NUM_DATASETS} -eq 1 ] || echo "Overall stats"
python3 "$EVALUATION_SCRIPT" "${CSV_FILES[@]}" | tee "$RESULTS_FOLDER/stats.txt"

# Propagate the error
exit ${ANY_ERROR}
