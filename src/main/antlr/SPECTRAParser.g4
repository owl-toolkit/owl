parser grammar SPECTRAParser;
options {
  language = Java;
  tokenVocab = SPECTRALexer;
}

@header {
package owl.grammar;
}

model:
  (IMPORT importURIS+=STRING)*
  (MODULE name=ID)?
  (elements+=decl)+
  EOF
  ;

decl:
  varDef
  | typeDef
  | weight
  | define
  | predicate
  | pattern
  | monitor
  | counter
  | ltl
  ;

varDef:
  kind=VAROWNER var=varDecl
  ;

typeDef:
  TYPE name=ID EQ type=varType SEMIC
  ;

varDecl:
  type=varType name=ID SEMIC
  ;

varType:
  (name=BOOLEAN
  | INT_S LPAREN subr=subrange RPAREN
  | LCPAR consts+=typeConstant (COMMA consts+=typeConstant)* RCPAR
  | type=ID /** TODO [TypeDef] */)
  (LBRACKET dimensions+=INT RBRACKET)*
  ;

typeConstant:
  name=typeConstantLiteral
  ;

typeConstantLiteral:
  ID | INT
  ;

weight:
  WEIGHT (name=ID COLON)? (value=val)? temporalExpression=temporalExpr SEMIC
  ;

val:
  MINUS? INT
  ;

define:
  DEFINE (defineList+=defineDecl)+
  ;

defineDecl:
  name=ID DECL simpleExpr=temporalExpr SEMIC
  ;

patternParamList:
  params+=patternParam (COMMA params+=patternParam)*
  ;

patternParam:
  name=ID
  ;

typedParamList:
  params+=typedParam (COMMA params+=typedParam)*
  ;

typedParam:
  type=varType name=ID
  ;

monitor:
  MONITOR type=varType name=ID LCPAR ((
    (INITIAL? initial+=temporalExpr) |
    (SAFETY safety+=temporalExpr) |
    (STATEINV stateInv+=temporalExpr)
  ) SEMIC)* RCPAR
  ;

pattern:
  PATTERN name=ID (LPAREN params=patternParamList RPAREN)?
  (LCPAR (VAR varDeclList+=varDecl)* ((
    (INITIAL? initial+=temporalExpr) |
    (SAFETY safety+=temporalExpr) |
    (STATEINV stateInv+=temporalExpr) |
    (JUSTICE justice+=temporalExpr)
  ) SEMIC)+ RCPAR)
  ;

counter:
  COUNTER name=ID (LPAREN subr=subrange RPAREN) LCPAR
    ID EQ initVAl=INT SEMIC
    (INC temporalExpression=temporalExpr SEMIC)?
    (DEC temporalExpression=temporalExpr SEMIC)?
    (RESET temporalExpression=temporalExpr SEMIC)?
    (OVERFLOW kind=(FALSE | CHOICE) SEMIC)?
    (UNDERFLOW kind=(FALSE | CHOICE) SEMIC)?
  RCPAR
  ;

predicate:
  PREDICATE name=ID (LPAREN params=typedParamList RPAREN)?
  ( (COLON body=temporalExpr SEMIC) |
    (LCPAR body=temporalExpr RCPAR)
  )
  ;

ltl:
  (GAR | ASM) (name=ID COLON)?
  (INITIAL? | safety=SAFETY | stateInv=STATEINV | justice=JUSTICE)
  (temporalExpression=temporalExpr SEMIC)
  ;

temporalExpr:
  temporalPrimaryExpr # Primary
  | pastUnaryOp right=temporalExpr # PastUnary
  | left=temporalExpr pastBinaryOp right=temporalExpr # PastBinary
  | left=temporalExpr multiplicativeOp right=temporalExpr # Multiplicative
  | left=temporalExpr additiveOp right=temporalExpr # Additive
  | left=temporalExpr REMAINDER right=temporalExpr # Remainder
  | left=temporalExpr relationalOp right=temporalExpr # Relational
  | left=temporalExpr AND right=temporalExpr # And
  | left=temporalExpr OR right=temporalExpr # Or
  | left=temporalExpr IFF right=temporalExpr # Iff
  | left=temporalExpr IMPLIES right=temporalExpr # Implies
  ;

temporalPrimaryExpr:
  constant # Const
  | LPAREN temporalExpr RPAREN # Nested
  | predPatt=ID LPAREN predPattParams+=temporalExpr (COMMA predPattParams+=temporalExpr)* RPAREN # PredPatt /** TODO: [predicateOrPatternReferrable] */
  | operator=negateOp temporalPrimaryExpr # Negate
  | pointer=ID (LBRACKET intvalue+=INT RBRACKET)* # Referable /** TODO: [referable] */
  | NEXT LPAREN temporalExpr RPAREN # SpecialNext
  ;

relationalOp:
  EQ | NE | LT | LE | GT | GE
  ;

additiveOp:
  PLUS | MINUS
  ;

multiplicativeOp:
  MUL | DIV
  ;

pastBinaryOp:
  SINCE | TRIGGERED
  ;

pastUnaryOp:
  ONCE | HISTORICALLY | PREV
  ;

negateOp:
  NOT | MINUS
  ;

constant:
  TRUE | FALSE | (MINUS)? INT
  ;

subrange:
  from=INT DOTDOT to=INT
  ;


predicateOrPatternReferrable:
  pattern | predicate
  ;

/*
referable:
  varDecl | typeConstant | defineDecl | typedParam | patternParam | monitor
  ;
 */
