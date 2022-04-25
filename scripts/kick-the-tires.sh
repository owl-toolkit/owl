#!/bin/sh

./owl aut2parity -i rabin4.hoa -i rabin4.hoa -o parity4.hoa # Check some generic flags.
./owl ltl2dela -f "a" -f "b" | ./owl aut2parity -i - -o -   # Check some generic flags.
./rabinizer.sh "a"
./rabinizer.sh --LDBA "a"
./owl --version
./owl --help
