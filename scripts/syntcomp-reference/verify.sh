#!/bin/bash

# This is a copy of data/syntcomp-reference/scripts/verify.sh
# (copied for security reasons)

# verifies an AIG against a TLSF specification
# uses SyfCo, smvtoaig, ltl2smv, combine-aiger and nuXmv

# exit on error
set -e
# break when pipe fails
set -o pipefail

if [ "$#" -lt 4 ]; then
    echo "Usage: $0 <implementation.aag> <specification.tlsf> <realizable/unrealizable> <timelimit/0> [output.combined.aag]"
    exit 1
fi

IMPLEMENTATION=$1
SPECIFICATION=$2
REALIZABLE=$3
TIMELIMIT=$4

if [ ! -f $IMPLEMENTATION ]; then
    echo "ERROR: Implementation not found"
    exit 1
fi
if [ ! -f $SPECIFICATION ]; then
    echo "ERROR: Specification not found"
    exit 1
fi

# temporary files
BASE=$(basename ${SPECIFICATION%.tlsf})
TLSF_IN=/tmp/$BASE.monitor.in
TLSF_OUT=/tmp/$BASE.monitor.out
MONITOR_FILE=/tmp/$BASE.monitor.aag
if [ "$#" -lt 5 ]; then
    clean_combined=true
    COMBINED_FILE=/tmp/$BASE.combined.aag
else
    clean_combined=false
    COMBINED_FILE=$5
fi
RESULT_FILE=/tmp/$BASE.result

function clean_exit {
    exit_code=$1

    # clean temporary files
    rm -f $TLSF_IN
    rm -f $TLSF_OUT
    rm -f $MONITOR_FILE
    if [ "$clean_combined" == true ]; then
        rm -f $COMBINED_FILE
    fi
    rm -f $RESULT_FILE

    exit $exit_code
}

# verify if inputs and outputs match
syfco --print-input-signals $SPECIFICATION | sed -e 's/\s*,\s*/\n/g' | sort >$TLSF_IN
syfco --print-output-signals $SPECIFICATION | sed -e 's/\s*,\s*/\n/g' | sort >$TLSF_OUT
if [ "$REALIZABLE" == 'unrealizable' ]; then
    tmp=$TLSF_IN
    TLSF_IN=$TLSF_OUT
    TLSF_OUT=$tmp
fi
if ! diff -q $TLSF_IN <(grep '^i[0-9]* ' $IMPLEMENTATION | sed -e 's/^i[0-9]* //' | sort) >/dev/null; then
    echo "ERROR: Inputs don't match"
    clean_exit 1
fi
if ! diff -q $TLSF_OUT <(grep '^o[0-9]* ' $IMPLEMENTATION | sed -e 's/^o[0-9]* //' | sort) >/dev/null; then
    echo "ERROR: Outputs don't match"
    clean_exit 1
fi

# build a monitor for the formula
if [ "$REALIZABLE" == 'unrealizable' ]; then
    syfco_format='smv'
    combine_aiger_options='--moore'
    rewrite_rule='s/LTLSPEC \(.*\)$/LTLSPEC !(\1)/'
else
    syfco_format='smv-decomp'
    combine_aiger_options=''
    rewrite_rule='/^/n'
fi
syfco -f $syfco_format -m fully $SPECIFICATION | sed -e "$rewrite_rule" | smvtoaig -L ltl2smv -a >$MONITOR_FILE 2>/dev/null

# combine monitor with implementation
combine-aiger $combine_aiger_options $MONITOR_FILE $IMPLEMENTATION >$COMBINED_FILE

if [ $TIMELIMIT -le 0 ]; then
    # only output combined file
    echo "COMBINED"
    clean_exit 0
fi

# model check each justice constraint of the combined file in parallel
num_justice=$(head -n 1 $COMBINED_FILE | cut -d' ' -f9);

if [ $num_justice -le 1 ]; then
    # sequential check
    set +e
    echo "read_aiger_model -i ${COMBINED_FILE}; encode_variables; build_boolean_model; check_ltlspec_ic3; quit" | timeout -k 10 $TIMELIMIT nuXmv -int >$RESULT_FILE 2>&1
    result=$?
    set -e
else
    # parallel check
    set +e
    seq 0 $((num_justice - 1)) | parallel --halt now,fail=1 "echo 'read_aiger_model -i ${COMBINED_FILE}; encode_variables; build_boolean_model; check_ltlspec_ic3 -n {}; quit' | timeout -k 10 $TIMELIMIT nuXmv -int" >$RESULT_FILE 2>&1
    result=$?
    set -e
fi
# check result
if [ $result -eq 0 ]; then
    num_true=$(grep -c 'specification .* is true' $RESULT_FILE || true)
    num_false=$(grep -c 'specification .* is false' $RESULT_FILE || true)
    if [ $num_false -ge 1 ]; then
        echo "FAILURE"
        clean_exit 2
    elif [ $num_true -lt $num_justice ]; then
        echo "ERROR: Unknown model checking result"
        clean_exit 1
    else
        echo "SUCCESS"
        clean_exit 0
    fi
elif [ $result -eq 124 ] || [ $result -eq 137 ]; then
    echo "TIMEOUT"
    clean_exit 3
else
    echo "ERROR: Model checking error"
    clean_exit 1
fi
