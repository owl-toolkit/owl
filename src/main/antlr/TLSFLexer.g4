lexer grammar TLSFLexer;

@lexer::header {
package owl.grammar;
}

fragment
LCPAR        : '{';
fragment
RCPAR        : '}';
fragment
SEMIC        : ';';
fragment
WS           : [ \t\r\f\n]+;
fragment
LINE_COMMENT : '//' ~[\r\n]*;

SKIP_DEF     : (WS | LINE_COMMENT) -> skip;

INFO         : 'INFO' -> pushMode(MODE_INFO);
MAIN         : 'MAIN' -> pushMode(MODE_MAIN);

mode MODE_INFO;

SKIP_INFO    : (WS | LINE_COMMENT) -> skip;

INFO_START   : '{';

TITLE        : 'TITLE:';
DESCRIPTION  : 'DESCRIPTION:';
SEMANTICS    : 'SEMANTICS:';
TARGET       : 'TARGET:';
TAGS         : 'TAGS: ';

INFO_STRING  : '"' (~'"'*) '"';

MEALY        : 'Mealy';
MOORE        : 'Moore';
MEALY_STRICT : 'Mealy,Strict';
MOORE_STRICT : 'Moore,Strict';

INFO_END     : RCPAR -> popMode;

mode MODE_MAIN;

MAIN_START   : LCPAR;

SKIP_MAIN    : (WS | LINE_COMMENT) -> skip;

INPUTS       : 'INPUTS' -> pushMode(MODE_IO);
OUTPUTS      : 'OUTPUTS' -> pushMode(MODE_IO);
INITIALLY    : 'INITIALLY' -> pushMode(MODE_SPEC);
PRESET       : 'PRESET' -> pushMode(MODE_SPEC);
REQUIRE      : 'REQUIRE' -> pushMode(MODE_SPEC);
ASSERT       : ('ASSERT' | 'INVARIANTS') -> pushMode(MODE_SPEC);
ASSUME       : ('ASSUME' | 'ASSUMPTIONS') -> pushMode(MODE_SPEC);
GUARANTEE    : ('GUARANTEE' | 'GUARANTEES') -> pushMode(MODE_SPEC);

MAIN_END     : RCPAR -> popMode;

mode MODE_IO;

SKIP_IO     : (WS | LINE_COMMENT) -> skip;

IO_START    : LCPAR;
VAR_ID      : [a-zA-Z_@][a-zA-Z0-9_@']*;
ID_SEP      : SEMIC;
IO_END      : RCPAR -> popMode;

mode MODE_SPEC;

fragment
LTL_TOKEN   : [a-zA-Z0-9\-_&|<>!()];

SPEC_START  : LCPAR;
SPEC_END    : RCPAR -> popMode;
SPEC_LTL    : LTL_TOKEN (LTL_TOKEN | WS)* SEMIC;
SKIP_SPEC   : (WS | LINE_COMMENT) -> skip;