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
  | LSQUOTE variable=SINGLE_QUOTED_VARIABLE RSQUOTE # singleQuotedVariable
  | LDQUOTE variable=DOUBLE_QUOTED_VARIABLE RDQUOTE # doubleQuotedVariable
  | LPAREN nested=expression RPAREN # nested
  ;

unaryOp
  : NOT | FINALLY | GLOBALLY | NEXT
  ;

binaryOp
  : BIIMP | IMP | XOR | UNTIL | WUNTIL | RELEASE | SRELEASE
  ;

bool
  : TRUE | FALSE
  ;