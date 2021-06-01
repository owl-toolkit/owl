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

# This is a benchmark framework to evaluate which
# tlsf files we can efficiently translate into ldbas or dpa

# This directory:
DIR=`dirname $0`/

# Time limit in seconds:
TIME_LIMIT=120
# Memory limit in kB:
MEMORY_LIMIT=2000000

# Maybe change the following line to point to GNU time:
GNU_TIME="gtime"

# The directory where the benchmarks are located:
BM_DIR="${DIR}../tlsf-benchmarks/"

# Building the tool call
TIMESTAMP=`date +%s`
RES_TXT_FILE="${DIR}tests/results_${TIMESTAMP}.txt"
RES_DIR="${DIR}tests/results_${TIMESTAMP}/"
mkdir -p "${DIR}tests/"
mkdir -p ${RES_DIR}

ulimit -m ${MEMORY_LIMIT} -v ${MEMORY_LIMIT} -t ${TIME_LIMIT}
for infile_path in ${BM_DIR}/*.tlsf
do
     file_name=$(basename $infile_path)
     CALL_TOOL="$1 ${BM_DIR}${file_name}"
     echo "Translating ${file_name} ..."
     echo "=====================  $file_name =====================" 1>> $RES_TXT_FILE

     #------------------------------------------------------------------------------
     # BEGIN execution of tool
     echo " Running the translator ... "
     ${GNU_TIME} --output=${RES_TXT_FILE} -a -f "Translation time: %e sec (Real time) / %U sec (User CPU time)" ${CALL_TOOL} >> ${RES_TXT_FILE}
     exit_code=$?
     echo "  Done running the translator. "
     # END execution of tool

     if [[ $exit_code == 137 ]];
     then
         echo "  Timeout!"
         echo "Timeout: 1" 1>> $RES_TXT_FILE
         continue
     else
         echo "Timeout: 0" 1>> $RES_TXT_FILE
     fi

     if [[ $exit_code != 0 ]];
     then
         echo "  Strange exit code: $exit_code (crash or out-of-memory)!"
         echo "Crash or out-of-mem: 1 (Exit code: $exit_code)" 1>> $RES_TXT_FILE
         continue
     else
         echo "Crash or out-of-mem: 0" 1>> $RES_TXT_FILE
     fi
done
