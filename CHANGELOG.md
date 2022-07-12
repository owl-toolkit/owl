# Changelog

## 22.0 (development, unreleased)

Major Changes:

* Remove IAR-construction which has been superseded by the ACD-construction.
* Owl now requires Java 17.
* Owl now maintains a customised fork of jhoafparser.
* Removed the `ltl2dela --SLM21-lookahead` option due to problematic semantic classification checks.
* Owl maintains a simple DPLL implementation and a Kissat backend.
* Migrated to GitHub Actions CI.
* Updated to GraalVM 22.1.
* Removed buggy optimisation from `ltl2dpa -t EKS20_EKRS17`.

## 21.0

Major Changes:

* New and improved command-line interface that is based on self-contained
  subcommands. Further, each subcommand explicitly lists relevant publications
  that can be accessed via the 'bibliography' subcommand. Thanks to Alexandre
  Duret-Lutz, Klara Meyer, Anton Pirogov and Tobias Meggendorfer for testing
  this new interface.

* Owl (as a CLI-tool) is now precompiled into native binaries (for Linux and
  macOS). Hence it is not required to install a matching JVM. Further, the
  start-up time is now considerably reduced which lead to the removal of the
  server mode. Owl can be still run on a JVM, but this is discouraged.

* Owl includes an implementation of the Alternating Cycle Decomposition for the
  translation into parity automata. See 'owl aut2parity --help' for more
  information. This supersedes the existing IAR-implementation which is
  scheduled to be removed in the next release.

* Add the LTL-to-DRW translation of [SE20] and a new LTL-to-DELW translation
  based also based on the [SE20] normalisation procedure. Further, a customised
  LTL-to-DPA translation based on Zielonka split-trees. These constructions will
  be described in forthcoming publication. To lookup [SE20] use
  'owl bibliography SE20'.

API:

* Add a symbolic representation of automata.

* Add rudimentary support for propositional logic and a SAT-solving
  infrastructure.

* Migrate OmegaAcceptance-classes to new propositional logic datatype
  and remove abstract super class. All types of OmegaAcceptance now
  extend EmersonLeiAcceptance.

* Disentangle `BitSet`-API from `Edge` datatype.

Bugfixes:

* Fixed several bugs affecting the LD(G)BA, D(G)RA, and DPA constructions.
  The issue was caused by faulty detection of temporal operators that need
  to be satisfied before jumping to the accepting component.

* Resolve the Alias fields in transition labels. Thanks to Pierre Ganty for
  reporting the issue.

* Fixed handling of rejecting sinks in the union operation implemented in
  BooleanOperations.

* Correctly propagate EdgeTree filters used in the DecomposedDPA C-API.
  Thanks to Lucas M. Tabajara for reporting the issue.

* Do not throw an exception if an empty automaton is passed to
  BooleanOperations#deterministicComplement. Thanks to Frederik Schmitt
  for reporting the issue.

* Several performance and correctness improvements to the [SE20] normalisation
  procedure. Thanks to Rubén Rafael Rubio Cuéllar for testing the implementation
  and reporting issues.

## 2020.06

Modules:

* Added new general determinization construction for NBA based on papers
  presented at ICALP'19 and ATVA'19. The construction supports multiple
  optimizations and can be invoked using the `nbadet` tool.

* Added simulations for NBA: direct, delayed, fair and also
  some other variants like with multi-pebble and lookahead simulations.
  The tool `nbasim` can be used to preprocess an automaton by quotienting
  states equivalent wrt. a suitable simulation.

* Added support for external parity game solver Oink.

* Added `ltl2normalform` that rewrites LTL formulas into the restricted
  alternation normal form described in our LICS'20 submission.

* Removed unused `--worker` flag and `OWL_{ANNOTATIONS,INPUT}` environment
  variables.

* De-duplicate fixed-point guesses in the "symmetric" constructions.

  Use extensions of the FG- and GF-advice functions (LICS'18) that use both
  guesses X and Y to rewrite the formula. Further, guesses are skipped that
  contain unused fixpoints.

* Removed unmaintained `fgx2dpa` translation. `ltl2dpa` produces almost always
  (on the test sets) smaller automata compared to `fgx2dpa`.

* `ltl2da` uses for the "safety-cosafety" and "cosafety-safety" fragment a
  optimised construction without invoking a fallback solution. This
  construction is based on our LICS'20 submission.

* `ltl2nba`, `ltl2ngba`, `ltl2ldba`, `ltl2ldgba`, `ltl2dra`, `ltl2dgra`,
  and `ltl2dpa` use a portfolio translator selecting simpler translations
  based on syntactic criteria, before applying the general purpose
  translation. This feature can be deactivated using `--disable-portfolio`.

* `ltl2dpa` by default now uses a complement translation to obtain (possibly)
  smaller DPAs. This feature can be deactivated using `--disable-complement`.

API:

* Replace the hand-written C++ API by an automatically generated C API that
  embeds Owl into a C application as a native library.

* Removed unused and unmaintained `FrequencyG` class and forbid subclassing
  of `GOperator`.

* Addition of `Negation` as a syntactic element for LTL formulas.

* OmegaAcceptanceCast enables casting and conversion of different types of
  omega-acceptance.

* EquivalenceClass always maintains the representative. This is made
  possible by major performance improvements in the EquivalenceClass
  implementation.

* Addition of the `BooleanOperations` utility class providing Boolean
  operations (complementation, union, and intersection) on automata.

* Addition of utility classes for determinization of NCW and minimisation
  of DCW to good-for-games NCW.

* Addition of `disjunctiveNormalForm` and `canonicalRepresentative` methods
  to the `EquivalenceClass` interface for retrieving fixed and well-defined
  representatives of an equivalence class.

* Various LTL rewrite rules.

* Various API simplifications in the automata package.

Bugfixes:

* Fixed several bugs affecting the LD(G)BA, D(G)RA, and DPA constructions.
  The translations based on the LICS'18 Master theorem and its predecessors
  have been affected. Thanks to Julian Brunner for reporting one of the
  issues.

* Fixed a bug in the `UpwardClosedSet` class: sets that were subsumed by other
  sets have not been removed in all circumstances.

* Fixes for non-deterministic behaviour of code implementing constructions of
  non-deterministic automata (`NonDeterministicConstructions`).

## 2019.06.03

Bugfixes:

* Fixed a compilation issues in the native components. Thanks to Philipp Meyer
  for reporting and fixing this issue.

## 2019.06.02

Bugfixes:

* Fixed a pattern matching exhaustiveness bug in the ltl2n{ba,gba} modules.
  Thanks to Alexandre Duret-Lutz for reporting this issue.
* Correctly parse HOA-files without a well-known acceptance type.

## 2019.06.01

Bugfixes:

* Fixed a small soundness bug in the ltl2n{a,ba,gba} modules. Thanks to
  Alexandre Duret-Lutz for reporting this issue.

## 2019.06.00

Modules:

* Implemented all LICS'18 translations for LTL fragments. Including a symbolic
  successor / edge computation. The translations can be found in the canonical
  package and are exposed via `ltl2da` and `ltl2na`.

* Removed TLSF support. Ensuring the correct implementation of the TLSF
  specification posed a too large maintenance burden. Users of the TLSF format
  can use [Syfco](https://github.com/reactive-systems/syfco) to translate it to
  a basic LTL formula.

  _Warning_: There are several specifications from Syntcomp in the TLSF (basic)
  format that have not been correctly parsed if they have not been properly
  parenthesised before.

* Renamed minimize-aut to optimize-aut to highlight that automata are not
  necessarily _minimal_. Implemented optimizations for Büchi-like Rabin pairs.

* Removed unmaintained and broken `safra` module.

API:

* Overhaul of the symbolic successor computation

  In addition to providing a mapping from `Edge<S>` to `ValuationSet` (renamed
  from `labelledEdges(S state)` to `edgeMap(S state)`) some automata can provide
  a direct computation of a decision tree mapping from valuations to sets of
  edges (`edgeTree(S state)`). This enable optimisation in the JNI-access.

  This feature is mostly used by the direct translation of the safety and
  co-safety fragment of LTL to deterministic automata.

* EquivalenceClass offers a `trueness` value giving the percentage of satisfying
  assignments for an EquivalenceClass. This value is exposed via the JNI as the
  quality score.

* Redesigned Formula classes offering substitution as part of the API instead
  of a separate visitor.

* `{M,R,U,W}Operator.of(...)` added the following simplification rules:
    1. (x M y) M y -> x M y
    2. (x R y) R y -> x R y
    3. x U (x U y) -> x U y
    4. x W (x W y) -> x W y

* The LTL simplifier added the following simplification rules:
    1. x M (!x | y) and y is pure universal -> F x & G (!x | y)
    2. x R (!x | y) and y is pure universal -> G (!x | y)
    3. x U (!x & y) and y is pure eventual -> F (!x & y)
    4. x W (!x & y) and y is pure eventual -> G x | F (!x & y)
    5. F a & a R b -> a M b
    6. F a & b W a -> b U a
    7. G a | a U b -> a W b
    8. G a | b M a -> b R a

* Overhaul of the C++-API. Most notably there is an API for approximative
  realisability checks for a state in the decomposed DPA.

* Add basic support for ultimately periodic words and add language membership
  tests.

Bugfixes:

* Throw an exception on malformed LTL input such as `FF`, `Fa!` and `F+`. Thanks
  to Alexandre Duret-Lutz for reporting this issue.

* The `hoa` module now correctly parsed the `-s` and `--state-acceptance`
  options.

## 2018.06.00

TBD (see gitlog)

## 1.2-SNAPSHOT

Library:

* Support the `xor` operator in the LTL input.

## 1.1 (2017-03-28)

Tools:

* `fgx2dga` (Preview): A translation of LTL-fairness formulas (`FG`/`GF`) to deterministic automata
  with a generic acceptance condition, meaning the acceptance condition is an arbitrary boolean
  combination of `Inf` and `Fin` sets. The tool supports all LTL operators using a fallback
  mechanism. This is a preview, thus functionality as well as the name is subject to change.

Library:

* Extended simplifier for Fairness Formulas
* Redesigned automaton classes.
* Implement an emptiness check for a given SCC.

## 1.0 (2017-02-03)

Tools:

* `ltl2ldba`: A translator of LTL to limit-deterministic Büchi automata.
* `ltl2dpa`: A translator of LTL to deterministic parity automata via LDBAs.
* `ltl2da`: A translator of LTL to any deterministic automata. The tool is a wrapper for the before
  mentioned tools and returns the smallest automaton.
* `nba2ldba`: A translator of non-deterministic Büchi to limit-deterministic Büchi automata.