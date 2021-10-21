#!/usr/bin/env bash

set -Eeuo pipefail
trap cleanup SIGINT SIGTERM ERR EXIT

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd -P)

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-v] [--TYPE] formula

A simple tool for constructing small (limit-)deterministic automata from linear temporal logic (LTL) formulas. By default formulas are translated to deterministic generalized Rabin automata with the acceptane condition defined on the transations. Please refer to `owl --help` to access options for fine-tuning the translations and to find relevant bibliographic information.

Available options:

-h, --help      Print this help and exit.
-v, --verbose   Print script debug info.
--LDBA          Translate into a limit-deterministic Büchi automaton with an acceptance condition defined on the states.
--LDGBA         Translate into a limit-deterministic generalized Büchi automaton with an acceptance condition defined on the states.
--DPA           Translate into a deterministic parity automaton with an acceptance condition defined on the states.
--DRA           Translate into a deterministic Rabin automaton with an acceptance condition defined on the states.
--DGRA          Translate into a deterministic generalized Rabin automaton with an acceptance condition defined on the states.
--DELA          Translate into a deterministic Emerson-Lei automaton with an acceptance condition defined on the states.
--tLDBA         Translate into a limit-deterministic Büchi automaton with an acceptance condition defined on the transitions.
--tLDGBA        Translate into a limit-deterministic generalized Büchi automaton with an acceptance condition defined on the transitions.
--tDPA          Translate into a deterministic parity automaton with an acceptance condition defined on the transitions.
--tDRA          Translate into a deterministic Rabin automaton with an acceptance condition defined on the transitions.
--tDGRA         Translate into a deterministic generalized Rabin automaton with an acceptance condition defined on the transitions.
--tDELA         Translate into a deterministic Emerson-Lei automaton with an acceptance condition defined on the transitions.
EOF
  exit
}

cleanup() {
  trap - SIGINT SIGTERM ERR EXIT
  # script cleanup here
}

setup_colors() {
  if [[ -t 2 ]] && [[ -z "${NO_COLOR-}" ]] && [[ "${TERM-}" != "dumb" ]]; then
    NOFORMAT='\033[0m' RED='\033[0;31m' GREEN='\033[0;32m' ORANGE='\033[0;33m' BLUE='\033[0;34m' PURPLE='\033[0;35m' CYAN='\033[0;36m' YELLOW='\033[1;33m'
  else
    NOFORMAT='' RED='' GREEN='' ORANGE='' BLUE='' PURPLE='' CYAN='' YELLOW=''
  fi
}

msg() {
  echo >&2 -e "${1-}"
}

die() {
  local msg=$1
  local code=${2-1} # default exit status 1
  msg "$msg"
  exit "$code"
}

parse_params() {
  # default values of variables set from params
  subcommand="ltl2dgra"
  stateacc=0
  formula="undefined"

  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -v | --verbose) set -x ;;
    --LDBA)   subcommand="ltl2ldba"  stateacc=1 ;;
    --LDGBA)  subcommand="ltl2ldgba" stateacc=1 ;;
    --DRA)    subcommand="ltl2dra"   stateacc=1 ;;
    --DGRA)   subcommand="ltl2dgra"  stateacc=1 ;;
    --DPA)    subcommand="ltl2dpa"   stateacc=1 ;;
    --DELA)   subcommand="ltl2dela"  stateacc=1 ;;
    --tLDBA)  subcommand="ltl2ldba"  stateacc=0 ;;
    --tLDGBA) subcommand="ltl2ldgba" stateacc=0 ;;
    --tDRA)   subcommand="ltl2dra"   stateacc=0 ;;
    --tDGRA)  subcommand="ltl2dgra"  stateacc=0 ;;
    --tDPA)   subcommand="ltl2dpa"   stateacc=0 ;;
    --tDELA)  subcommand="ltl2dela"  stateacc=0 ;;
    -?*) die "Unknown option: $1" ;;
    *) break ;;
    esac
    shift
  done

  args=("$@")

  # check required params and arguments
  [[ ${#args[@]} -eq 0 ]] && die "Please specify an LTL formula to translate, e.g., 'GFa'."
  [[ ${#args[@]} -gt 1 ]] && die "Please specify only one LTL formula."

  formula=${args[0]}

  return 0
}

parse_params "$@"
setup_colors

# script logic here

if [[ ${stateacc} -eq 0 ]]; then
  ./owl ${subcommand} -f "${args}" --state-labels
else
  ./owl ${subcommand} -f "${args}" --state-acceptance --state-labels
fi
