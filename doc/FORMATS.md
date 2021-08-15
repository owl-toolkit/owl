# Input and Output Formats

`Owl` understands several text-based input and output formats, summarized in the following table:

| Format | In  | Out |
|:-------|:---:|:---:|
| [LTL](#LTL) (+ [rLTL](#rLTL)) | X |  X |
| [HOA](#HOA) | X |  X |
| [PGSolver](#pgsolver) | | X |

A more detailed description and links to relevant papers are provided below.

## <a name="LTL" /> Linear Temporal Logic (LTL)

The grammar for LTL aims to support *Spot-style* LTL.
For further details on temporal logic, e.g., semantics, see [here](https://spot.lrde.epita.fr/tl.pdf).

The following constructs are supported:

### Propositional Operators

  * True: `tt`, `true`, `1`
  * False: `ff`, `false`, `0`
  * Atom: `[a-zA-Z_][a-zA-Z_0-9]*` or quoted `"[^"]+"`
  * Negation: `!`, `NOT`
  * Implication: `->`, `=>`, `IMP`
  * Bi-implication: `<->`, `<=>`, `BIIMP`
  * Exclusive Disjunction: `^`, `XOR`
  * Conjunction: `&&`, `&`, `AND`
  * Disjunction: `||`, `|`, `OR`
  * Parenthesis: `(`, `)`

###  Temporal Operators

  * Finally: `F`
  * Globally: `G`
  * Next: `X`
  * (Strong) Until: `U`
  * Weak Until: `W`
  * (Weak) Release: `R`
  * Strong Release: `M`

### Precedence Rules

The parser uses the following precedence:

`OR` < `AND` < Binary Expressions < Unary Expressions < Literals, Constants, Parentheses

For chained binary expressions (without parentheses), the rightmost binary operator takes precedence.
For example, `a -> b U c` is parsed as `a -> (b U c)`.


### <a name="rLTL" /> Robust LTL (rLTL)

`Owl` also supports parsing [*robust LTL*](https://arxiv.org/abs/1510.08970), immediately translating it to LTL, given particular truth values.


## <a name="HOA" /> Hanoi Omega-Automaton Format (HOA)

`Owl` supports most of the [HOA format](http://adl.github.io/hoaf/), using [jhoafparser](http://automata.tools/hoa/jhoafparser/) as back-end.

Caveats:
  * Alternation is not supported
  * Internally, acceptance is encoded as transition acceptance, hence parsing and serializing an automaton with state acceptance may blow up the state space.

## <a name="TLSF" /> Temporal Logic Synthesis Format (TLSF)

Use [syfco](https://github.com/reactive-systems/syfco) to transform TLSF to LTL. For example: `syfco FILE -f ltlxba -m fully`.

## <a name="pgsolver" /> PGSolver Format

Serialization of parity games into the format used by [PGSolver](https://github.com/tcsprojects/pgsolver) and other parity game solvers like [Oink](https://arxiv.org/abs/1801.03859) is also supported. See Sec. 3.5 [here](https://github.com/tcsprojects/pgsolver/blob/master/doc/pgsolver.pdf) for a description of the format.
