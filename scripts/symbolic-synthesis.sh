#!/bin/bash
error=0
while read -r testcase; do
  file=./data/syntcomp-reference/specifications/realizable/$testcase
  printf "\"%s\"," "$file"
    formula="$(syfco -f ltl -q double -m fully $file | sed 's/"\([^"]*\)"/\L\1/g')"
    controllable="$(syfco -outs $file)"
    uncontrollable="$(syfco -ins $file)"
    start=$(date +%s%3N)
    #out=$(LD_LIBRARY_PATH=../strix/bin timeout -k 5 180 ../strix/bin/strix -f "$formula" --ins "$(sed 's/ //g' <<< "$uncontrollable" | tr "[:upper:]" "[:lower:]")" --outs "$(sed 's/ //g' <<< "$controllable" | tr "[:upper:]" "[:lower:]")")
    out=$(timeout -k 5 86400 ./build/owl-native -i "$formula" ltl --- simplify-ltl --- ltl2aig -c="$(sed 's/ //g' <<< "$controllable" | tr "[:upper:]" "[:lower:]")" --- string)
    exit=$?
    time=$(( "$(date +%s%3N)" - start))
    if [ $exit -eq 124 ] || [ $exit -eq 137 ]; then
      error=1
      printf "\"TIMEOUT\",\"%d\"\n" "$time"
    elif [ $exit -eq 0 ]; then
      if [[ $out == unrealizable* ]]; then
        error=1
        printf "\"UNREALIZABLE\",\"%d\"\n" "$time"
      else
        out=$(sed '1d' <<< "$out")
        for ap in $(sed 's/,//g' <<< "$controllable, $uncontrollable")
        do
          out=$(sed "s/^\([io][[:digit:]]*\) $(tr "[:upper:]" "[:lower:]" <<< "$ap")$/\1 $ap/g" <<< "$out")
        done
        filename=/tmp/$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)
        printf "$out\n" > "$filename"
        ./scripts/syntcomp-reference/verify.sh "$filename" "$file" realizable 180 > /dev/null 2>&1
        exit=$?
        rm "$filename"
        if [ $exit -eq 0 ]; then
          printf "\"SUCCESS\",\"%d\"\n" "$time"
        elif [ $exit -eq 2 ]; then
          error=1
          printf "\"FAILURE\",\"%d\"\n" "$time"
        elif [ $exit -eq 3 ]; then
          error=1
          printf "\"MC_TIMEOUT\",\"%d\"\n" "$time"
        else
          error=1
          printf "\"MC_ERROR\",\"%d\"\n" "$time"
        fi
      fi
    else
      error=1
      printf "ERROR\n"
    fi
done < "./scripts/symbolic-synthesis-syntcomp-selection.txt"
exit $error
