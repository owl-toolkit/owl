parser grammar LTLParser;

options {
  language = Java;
  tokenVocab = LTLLexer;
}

@header {
package owl.grammar;
}

formula
  : root=expression EOF
  ;

expression
  : or=orExpression
  ;

orExpression
  : andExpression (OR andExpression)*
  ;

andExpression
  : binaryExpression (AND binaryExpression)*
  ;

binaryExpression
  : left=unaryExpression op=binaryOp right=binaryExpression # binaryOperation
  | unaryExpression # binaryUnary
  ;

unaryExpression
  : op=unaryOp inner=binaryExpression # unaryOperation
  | atomExpression # unaryAtom
  ;

atomExpression
  : constant=bool # boolean
  | variable=VARIABLE # variable
  | LPAREN nested=expression RPAREN # nested
  ;

unaryOp
  : NOT | FINALLY | GLOBALLY | NEXT | frequencyOp
  ;

binaryOp
  : BIIMP | IMP | XOR | UNTIL | WUNTIL | RELEASE | SRELEASE
  ;

bool
  : TRUE | FALSE
  ;

frequencyOp
  : op=(GLOBALLY | FINALLY) LCPAREN limes=(SUP | INF)? comp=comparison prob=frequencySpec RCPAREN
  ;

frequencySpec
  : numerator=POS_NUMBER DIVISION denominator=POS_NUMBER # fraction
  | value=PROBABILITY # probability
  ;

comparison
  : GT | GE | LT | LE
  ;
