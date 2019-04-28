# Literature

This directory contains LTL formulas, patterns and parametrised families, from the literature. Each file is named after the originating publication's DBLP key. The exception to this rule are `Parametrised.ltl` and `Parametrised-Hardness.ltl` which are a collections of instantiations of parametrised families. `Parametrised-Hardness.ltl` focusses on formulas used for proving hardness bounds, while `Parametrised.ltl` is a collection of formulas for which (ad-hoc) optimal translation are known. These are generated with the help of [`genltl`](https://spot.lrde.epita.fr/genltl.html) and the following two commands:

```
   genltl --u-left=3..5 --r-left=3..5 --u-right=3..5 --r-right=3..5 --tv-uu=1..3 --gh-q=2..4 --and-fg=3..5 --and-gf=3..5 --or-fg=3..5 --or-gf=3..5 --gf-implies=2..4 --gf-equiv=2..4 --gh-r=2..4 --gf-equiv-xn=2..4 --ms-phi-h=1..3 | ltlfilt --relabel=abc --nnf > Parametrised.ltl
```

```
   genltl --kr-n=1..2 --kr-nlogn=1..2 --kv-psi=1..2 --ms-phi-r=1..3 --ms-phi-s=1..3 --rv-counter=1..2 --rv-counter-carry=1..2 --rv-counter-carry-linear=1..2 --rv-counter-linear=1..2 | ltlfilt --relabel=abc --nnf > Parametrised-Hardness.ltl
```

These sets include families of the following publications:

* J. Cichoń, A. Czubak, and A. Jasiński: Minimal Büchi Automata for Certain Classes of LTL Formulas. Proceedings of DepCoS’09.
* M. B. Dwyer and G. S. Avrunin and J. C. Corbett: Property Specification Patterns for Finite-state Verification. Proceedings of FMSP’98.
* K. Etessami and G. J. Holzmann: Optimizing Büchi Automata. Proceedings of Concur’00.
* J. Geldenhuys and H. Hansen: Larger automata and less work for LTL model checking. Proceedings of Spin’06.
* J. Holeček, T. Kratochvila, V. Řehák, D. Šafránek, and P. Šimeček: Verification Results in Liberouter Project. Tech. Report 03, CESNET, 2004.
* P. Gastin and D. Oddoux: Fast LTL to Büchi Automata Translation. Proceedings of CAV’01.
* O. Kupferman and A. Rosenberg: The Blow-Up in Translating LTL to Deterministic Automata. Proceedings of MoChArt’10.
* O. Kupferman and M. Y. Vardi: From Linear Time to Branching Time. ACM Transactions on Computational Logic, 6(2):273-294, 2005.
* D. Müller and S. Sickert: LTL to Deterministic Emerson-Lei Automata. Proceedings of GandALF’17.
* R. Pelánek: BEEM: benchmarks for explicit model checkers Proceedings of Spin’07.
* K. Rozier and M. Vardi: LTL Satisfiability Checking. Proceedings of Spin’07.
* F. Somenzi and R. Bloem: Efficient Büchi Automata for LTL Formulae. Proceedings of CAV’00.
* S. Sickert, J. Esparza, S. Jaax, and J. Křetínský: Limit-Deterministic Büchi Automata for Linear Temporal Logic. Proceedings of CAV’16.
* D. Tabakov and M. Y. Vardi: Optimized Temporal Monitors for SystemC. Proceedings of RV’10.
