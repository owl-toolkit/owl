# LTL Input Grammar

All tools based on `Owl` understand the same LTL input language.
The following constructs are supported:

## Propositional Logic

 * True: `tt`, `true`, `1`
 * False: `ff`, `false`, `0`
 * Literal: `[a-zA-Z_][a-zA-Z_0-9]*`
 * Negation: `!`, `NOT`
 * Implication: `->`, `=>`, `IMP`
 * Bi-implication: `<->`, `<=>`, `BIIMP`
 * Exclusive Disjunction: `^`, `XOR`
 * Conjunction: `&&`, `&`, `AND`
 * Disjunction: `||`, `|`, `OR`
 * Parenthesis: `(`, `)`

##  Modal Logic

 * Finally: `F`
 * Globally: `G`
 * Next: `X`
 * (Strong) Until: `U`
 * Weak Until: `W`
 * (Weak) Release: `R`
 * Strong Release: `M`

## Precedence Rules

The parser uses the following precedence:

`OR` < `AND` < Binary Expressions < Unary Expressions < Literals, Constants, Parentheses

For chained binary expressions (without parentheses), the rightmost binary operator takes precedence.
For example, `a -> b U c` is parsed as `a -> (b U c)`.