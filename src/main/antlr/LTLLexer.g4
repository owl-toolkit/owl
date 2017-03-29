lexer grammar LTLLexer;

@lexer::header {
package owl.grammar;
}

// LOGIC
TRUE       : 'tt' | 'true' | '1';
FALSE      : 'ff' | 'false' | '0';

// Logical Unary
NOT        : '!' | 'NOT';

// Logical Binary
IMP        : '->' | '=>' | 'IMP';
BIIMP      : '<->' | '<=>' | 'BIIMP';
XOR        : '^' | 'XOR' | 'xor';

// Logical n-ary
AND        : '&&' | '&' | 'AND' ;
OR         : '||' | '|' | 'OR' ;

// Modal Unary
FINALLY    : 'F';
GLOBALLY   : 'G';
NEXT       : 'X';

// Modal Binary
UNTIL      : 'U';
WUNTIL     : 'W';
RELEASE    : 'R';
SRELEASE   : 'M';

// MISC
LPAREN     : '(';
RPAREN     : ')';

LCPAREN    : '{' -> mode(FREQUENCY_SPEC);

// Need to be at the bottom because of precedence rules
VARIABLE   : [a-z_][a-zA-Z_0-9]* ;

fragment
WHITESPACE : [ \t\n\r\f]+;

SKIP_DEF   : WHITESPACE -> skip;

mode FREQUENCY_SPEC;

// NUMBERS
// Constants
PROBABILITY   : '0' | '0'? '.' [0-9]+;
POS_NUMBER    : [0-9]+ | [0-9]* '.' [0-9]+;

// Binary
DIVISION   : '/';

GT         : '>' | 'GT';
GE         : '>=' | 'GE';
LT         : '<' | 'LT';
LE         : '<=' | 'LE';
EQ         : '=' | 'EQ';

// Limes
SUP        : 'sup' | 'SUP';
INF        : 'inf' | 'INF';

RCPAREN    : '}' -> mode(DEFAULT_MODE);

SKIP_FREQ  : WHITESPACE -> skip;