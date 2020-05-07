# 2020.XX (unreleased)

Modules:

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

* Removed unused and unmaintained `FrequencyG` class and forbid subclassing
  of `GOperator`.

* Addition of `Negation` as a syntactic element for LTL formulas.

* OmegaAcceptanceCast enables casting and conversion of different types of
  omega-acceptance.

* EquivalenceClass always maintains the representative. This is made
  possible by major performance improvements in the EquivalenceClass
  implementation.

* Addition of the `BooleanOperations` utility class that provides Boolean
  operations (complementation, union, and intersection) on automata.

* Addition of utility classes for determinization of NCW and minimisation
  of DCW to good-for-games NCW.

* Various API simplifications in the automata package.

Bugfixes:

* Fixed several bugs affecting the LD(G)BA, D(G)RA, and DPA constructions.
  The translations based on the LICS'18 Master theorem and its predecessors
  have been affected.

* Fixed a bug in the `UpwardClosedSet` class: sets that were subsumed by other
  sets have not been removed in all circumstances.

# 2019.06.03

Bugfixes:

* Fixed a compilation issues in the native components. Thanks to Philipp Meyer
  for reporting and fixing this issue.

# 2019.06.02

Bugfixes:

* Fixed a pattern matching exhaustiveness bug in the ltl2n{ba,gba} modules.
  Thanks to Alexandre Duret-Lutz for reporting this issue.
* Correctly parse HOA-files without a well-known acceptance type.

# 2019.06.01

Bugfixes:

* Fixed a small soundness bug in the ltl2n{a,ba,gba} modules. Thanks to
  Alexandre Duret-Lutz for reporting this issue.

# 2019.06.00

Modules:

* Implemented all LICS'18 translations for LTL fragments. Including a symbolic
  successor / edge computation. The translations can be found in the canonical
  package and are exposed via `ltl2da` and `ltl2na`.

* Removed TLSF support. Ensuring the correct implementation of the TLSF
  specification posed a too large maintenance burden. Users of the TLSF format
  can use Syfco (https://github.com/reactive-systems/syfco) to translate it to
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


# 2018.06.00

TBD (see gitlog)

# 1.2-SNAPSHOT

Library:

 * Support the `xor` operator in the LTL input.

# 1.1 (2017-03-28)

Tools:

 * `fgx2dga` (Preview): A translation of LTL-fairness formulas (`FG`/`GF`) to deterministic automata with a generic acceptance condition, meaning the acceptance condition is an arbitrary boolean combination of `Inf` and `Fin` sets. The tool supports all LTL operators using a fallback mechanism. This is a preview, thus functionality as well as the name is subject to change.

Library:

 * Extended simplifier for Fairness Formulas
 * Redesigned automaton classes.
 * Implement an emptiness check for a given SCC.

# 1.0 (2017-02-03)

Tools:

 * `ltl2ldba`: A translator of LTL to limit-deterministic Büchi automata.
 * `ltl2dpa`: A translator of LTL to deterministic parity automata via LDBAs.
 * `ltl2da`: A translator of LTL to any deterministic automata. The tool is a wrapper for the before mentioned tools and returns the smallest automaton.
 * `nba2ldba`: A translator of non-deterministic Büchi to limit-deterministic Büchi automata.
