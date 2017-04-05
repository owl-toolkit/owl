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