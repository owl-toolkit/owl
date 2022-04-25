# Owl

A tool collection and library for <b>O</b>mega-<b>w</b>ords, ω-automata and <b>L</b>inear Temporal
Logic (LTL).

## Citing

If you want to cite Owl or one of the implemented constructions, then please refer to the help
messages (`./owl <subcommand> --help`, `./owl bibliography --help`) to find the correct reference
to cite.

## Quick Start

Download the latest release for your platform from the
official [website](https://owl.model.in.tum.de/) and perform the following steps:

1. Unzip the distribution with: `unzip -d <destination> owl-<platform>-<version>.zip`
2. Change into the directory containing the executable with: `cd <destination>/bin`

Usage Examples:

- Translate a LTL formula to a deterministic Rabin automaton:
  ```
  $ ./owl ltl2dra -t SE20 -f 'F (a & G b)'
  HOA: v1
  tool: "owl ltl2dra" "<version string>"
  Start: 0
  acc-name: parity min odd 2
  Acceptance: 2 Fin(0) & Inf(1)
  properties: trans-acc no-univ-branch
  properties: deterministic unambiguous
  properties: complete
  AP: 2 "a" "b"
  --BODY--
  State: 0
  [0 & !1 | !0] 0 {0}
  [0 & 1] 1 {1}
  State: 1
  [!1] 0 {0}
  [1] 1 {1}
  --END--
  ```
- Access the bibliography using:
  ```
  $ ./owl bibliography SE20
  [SE20]:
  Salomon Sickert, Javier Esparza:
  "An Efficient Normalisation Procedure for Linear Temporal Logic and Very Weak Alternating Automata". LICS 2020
  DOI: https://doi.org/10.1145/3373718.3394743
  BibTeX: https://dblp.uni-trier.de/rec/bibtex/conf/lics/SickertE20
  ```
- Display an overview of all available subcommands:
  ```
  $ ./owl --help
  Usage: owl [-hV] COMMAND
  A tool collection and library for ω-words, ω-automata and linear temporal logic.
    -h, --help      Show this help message and exit.
    -V, --version   Print version information and exit.
  Commands:
    ltl2nba           Translate a linear temporal logic (LTL) formula into a
                        non-deterministic Büchi automaton (NBA).
    ltl2ngba          Translate a linear temporal logic (LTL) formula into a
                        non-deterministic generalized Büchi automaton (NGBA).
    ltl2ldba          Translate a linear temporal logic (LTL) formula into a
                        limit-deterministic Büchi automaton (LDBA).
    ltl2ldgba         Translate a linear temporal logic (LTL) formula into a
                        limit-deterministic generalized Büchi automaton (LDGBA).
    ltl2dpa           Translate a linear temporal logic (LTL) formula into a
                        deterministic parity automaton (DPA).
    ltl2dra           Translate a linear temporal logic (LTL) formula into a
                        deterministic Rabin automaton (DRA).
    ltl2dgra          Translate a linear temporal logic (LTL) formula into a
                        deterministic generalized Rabin automaton (DGRA).
    ltl2dela          Translate a linear temporal logic (LTL) formula into a
                        deterministic Emerson-Lei automaton (DELA).
    ltl2delta2        Rewrite a linear temporal logic (LTL) formula into the
                        Δ₂-normal-form using the construction of [SE20].
    ngba2ldba         Convert a non-deterministic (generalised) Büchi automaton
                        to a limit-deterministic Büchi automaton.
    nba2dpa, nbadet   Convert a non-deterministic Büchi automaton to a
                        deterministic parity automaton.
    nbasim            Computes the quotient automaton based on a computed set of
                        similar state pairs.
    aut2parity        Convert any type of automaton into a parity automaton. The
                        branching mode of the automaton is preserved, e.g., if
                        the input automaton is deterministic then the output
                        automaton is also deterministic.
    gfg-minimisation  Compute the minimal, equivalent, transition-based
                        Good-for-Games Co-Büchi automaton for the given
                        deterministic Co-Büchi automaton. The polynomial
                        construction is described in [AK19].
    bibliography      Print the bibliography of all implemented algorithms and
                        constructions. Single references can be looked up by
                        listing them, e.g. 'owl bibliography SE20'. If you want
                        to cite Owl as a whole, it is recommended to use
                        reference [KMS18].
    license           Print the license of Owl and the licenses of all linked
                        (non-system) libraries.
    delag             The functionality of the 'delag' subcommand has been moved
                        to the 'ltl2dela' subcommand. You can use 'owl ltl2dela
                        -t=MS17' to access the original 'delag' construction.
    ltl-utilities     A collection of various linear temporal logic related
                        rewriters.
    rltl2ltl          Convert a robust linear temporal logic (rLTL) formula into
                        a linear temporal logic formula.
    aut-utilities     A collection of various automata related utilities.
  ```
- Display a specific help message for a subcommand:
  ```
  $ ./owl ltl2dpa --help
  Usage: owl ltl2dpa [-hV] [--complete] [--EKRS17-skip-complement]
                     [--skip-acceptance-simplifier] [--skip-formula-simplifier]
                     [--skip-translation-portfolio] [--state-acceptance] [--state-labels]
                     [-o=<automatonFile>] [--SLM21-lookahead=<lookahead>]
                     [-t=<translation>] [-f=<formula> | -i=<formulaFile>]
  Translate a linear temporal logic (LTL) formula into a deterministic parity automaton
  (DPA).
  Usage Examples:
    owl ltl2dpa -f 'F (a & G b)'
    owl ltl2dpa -t SEJK16_EKRS17 -i input-file -o output-file
  To lookup a reference, e.g [SE20], used in this help message please use 'owl
  bibliography'.
        --complete            Output an automaton with a complete transition relation.
        --EKRS17-skip-complement
                              Bypass the parallel computation of a DPA for the negation of
                                the formula. If the parallel computation is enabled, then
                                two DPAs are computed and the smaller one (in terms of
                                number of states) is returned.
    -f, --formula=<formula>   Use the argument of the option as the input formula.
    -h, --help                Show this help message and exit.
    -i, --input-file=<formulaFile>
                              Input file (default: read from stdin). The file is read
                                line-by-line and it is assumed that each line contains a
                                formula. Empty lines are skipped.
    -o, --output-file=<automatonFile>
                              Output file (default: write to stdout).
        --skip-acceptance-simplifier
                              Bypass the automatic simplification of automata acceptance
                                conditions.
        --skip-formula-simplifier
                              Bypass the automatic simplification of formulas.
        --skip-translation-portfolio
                              Bypass the portfolio of constructions from [S19, SE20] that
                                directly translates 'simple' fragments of LTL to automata.
        --SLM21-lookahead=<lookahead>
                              The number of successor states that are explored in order to
                                (1) compute an exact semantic classification of a state, e.
                                g., weak accepting, and (2) in order to compute the
                                'Alternating Cycle Decomposition' [CCF21]. If the number of
                                explored states exceeds this bound, a sound approximations
                                are used as desribed in [SLM21]. If the value is 0, only
                                approximations are used. If the value is negative, then all
                                states are explored and exact semantic information is used.
                                The value is by default -1. If the construction times out,
                                try setting this value to 0 and then increase it again in
                                order to obtain smaller automata. This option only affects
                                the SLM21-translation.
        --state-acceptance    Output an automaton with a state-based acceptance condition
                                instead of one with a transition-based acceptance condition.
        --state-labels        Annotate each state of the automaton with the 'toString()'
                                method.
    -t, --translation=<translation>
                              The default translation is SLM21 and the following
                                translations are available: SEJK16_EKRS17, EKS20_EKRS17,
                                SYMBOLIC_SE20_BKS10, SLM21, SMALLEST_AUTOMATON.
                              SEJK16_EKRS17: Translate the formula to a deterministic
                                parity automaton by combining [SEJK16] with the LDBA-to-DPA
                                translation of [EKRS17]. This translation used to be
                                available through the '--asymmetric' option.
                              EKS20_EKRS17: Translate the formula to a deterministic parity
                                automaton by combining [EKS20] with the LDBA-to-DPA
                                translation of [EKRS17]. This translation used to be
                                available through the '--symmetric' option.
                              SYMBOLIC_SE20_BKS10: Translate the formula to a deterministic
                                parity automaton by combining the LTL-to-DRA translation of
                                [SE20] with DRAxDSA-to-DPA result of [BKS10]. This
                                translation has an _symbolic_ implementation and is
                                provided for testing purposes through this interface. In
                                order to benefit from the symbolic implementation users
                                _must_ use the 'SymbolicAutomaton'-interface.
                              SLM21: Translate the formula to a deterministic parity
                                automaton by combining the LTL-to-DELA translation of
                                [SLM21] with a DELW-to-DPW translation based on
                                Zielonka-trees. Depending on the lookahead either [CCF21]
                                or [SLM21] is used.
                              SMALLEST_AUTOMATON: Run all available DPA-translations with
                                all optimisations turned on in parallel and return the
                                smallest automaton.
    -V, --version             Print version information and exit.
  ```

### Content of the Distribution

Owl is distributed as platform-specific distributions. Note that the platform-specific distributions
contain a platform-independent Java library. A distribution contains the following folders:

* `bin` - Platform-specific command-line tool.
* `doc` - Additional documentation.
* `jar` - Platform-independent Java library, source-code, and documentation.
* `lib` - Platform-specific C library and headers.

See the [format descriptions](docs/FORMATS.md) for a description of accepted inputs. Owl contains a
variety of command-line tools originating from Rabinizer 4.0, Delag, and nbadet.

### Building a Distribution

If there is no precompiled distribution for your platform available or if you want to use the latest
snapshot, follow the [build instructions](BUILDING.md) to build your own distribution.