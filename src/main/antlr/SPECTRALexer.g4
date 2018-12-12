lexer grammar SPECTRALexer;

@lexer::header {
package owl.grammar;
}

//Buzzwords
MODULE        : 'module' | 'spec' ;
IMPORT        : 'import' ;
DEFINE        : 'define' ;
MONITOR       : 'monitor' ;
PATTERN       : 'pattern' ;
PREDICATE     : 'predicate' ;
COUNTER       : 'counter' ;

INITIAL       : 'ini' | 'initially' ;
SAFETY        : 'G' | 'trans' ;
STATEINV      : 'always' | 'alw' ;
JUSTICE       : 'GF' | 'alwEv' | 'alwaysEventually' ;
ASM           : 'assumption' | 'asm' ;
GAR           : 'guarantee' | 'gar' ;

VAR           : 'var' ;
TYPE          : 'type' ;
WEIGHT        : 'weight' ;

BOOLEAN       : 'boolean' ;
INT_S         : 'Int' ;

INC           : 'inc:' ;
DEC           : 'dec:' ;
RESET         : 'reset:' ;
OVERFLOW      : 'overflow:' ;
UNDERFLOW     : 'underflow:' ;
CHOICE        : 'modulo' | 'keep' ;


VAROWNER      : (SYS|ENV|AUX) {setText(getText().matches("input|in|envvar|env") ? "in" : "out");} ;
fragment
SYS           : 'output' | 'out' | 'sysvar' | 'sys' ;
fragment
ENV           : 'input' | 'in' | 'envvar' | 'env' ;
fragment
AUX           : 'auxvar' | 'aux' ;


fragment
WS            : [ \t\r\f\n]+ ;
fragment
COMMENT       : ('//' ~[\r\n]* | '/*' .*? '*/' | '--' ~[\r\n]*) ;


//Lpgic
TRUE          : 'TRUE' | 'true' ;
FALSE         : 'FALSE' | 'false' ;

//Operators
IMPLIES       : '->' | 'implies' ;
IFF           : '<->' | 'iff' ;
OR            : '|' | 'or' | 'xor' ;
AND           : '&' | 'and' ;
REMAINDER     : 'mod' ;
NOT           : '!' ;

//Past Binary
SINCE         : 'S' | 'SINCE' ;
TRIGGERED     : 'T' | 'TRIGGERED' ;
PREV          : 'Y' | 'PREV' ;
HISTORICALLY  : 'H' | 'HISTORICALLY' ;
ONCE          : 'O' | 'ONCE' ;

//Special
NEXT          : 'next' ;

//Relational
EQ            : '=' ;
NE            : '!=' ;
LT            : '<' ;
LE            : '<=' ;
GT            : '>' ;
GE            : '>=' ;

//Additive
PLUS          : '+' ;
MINUS         : '-' ;

//Multiplicative
MUL           : '*' ;
DIV           : '/' ;

//Structure
LPAREN        : '(' ;
RPAREN        : ')' ;
LBRACKET      : '[' ;
RBRACKET      : ']' ;
LCPAR         : '{' ;
RCPAR         : '}' ;
DECL          : ':=';
COMMA         : ',' ;
COLON         : ':' ;
SEMIC         : ';' ;
DOTDOT        : '..' ;


//Types
STRING        : ('"'~["']*'"') | ('\''~["']*'\'');
ID            : ('^')?[a-zA-Z_][a-zA-Z_0-9]* ;
INT           : [0-9]+ ;

SKIP_DEFAULT  : (WS | COMMENT) -> skip ;
