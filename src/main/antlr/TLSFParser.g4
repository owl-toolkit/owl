parser grammar TLSFParser;

options {
  language = Java;
  tokenVocab = TLSFLexer;
}

@header {
package owl.grammar;
}

tlsf :
  INFO INFO_START
    TITLE title=INFO_STRING
    DESCRIPTION description=INFO_STRING
    SEMANTICS semantics
    TARGET target
  INFO_END
  MAIN MAIN_START
    input
    output
    specification+
  MAIN_END
  EOF
  ;

semantics
  : MEALY | MOORE | MEALY_STRICT | MOORE_STRICT
  ;

target
  : MEALY | MOORE
  ;

input
  : INPUTS IO_START (in=VAR_ID ID_SEP)* IO_END
  ;

output
  : OUTPUTS IO_START (out=VAR_ID ID_SEP)* IO_END
  ;

specification
  : (INITIALLY | PRESET | REQUIRE | ASSERT | ASSUME | GUARANTEE)
    SPEC_START formula=SPEC_LTL+ SPEC_END
  ;
