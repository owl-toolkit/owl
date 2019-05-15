# 2019.06 (unreleased)

Translations:

* Implemented all LICS'18 translations for LTL fragments. Including a symbolic
  successor / edge computation. The translations can be found in the canonical
  package and are exposed via `ltl2da` and `ltl2na`.

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

* Overhaul of the C++-API. Most notably there is an API for approximative realisability checks for a
  state in the decomposed DPA.

Bugfixes:

* Throw an exception on malformed LTL input such as `FF`, `Fa!` and `F+`. Thanks 
  to Alexandre Duret-Lutz for reporting this issue.

# 2018.06

TBD

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
