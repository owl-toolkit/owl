package owl.ltl.ltlf;


import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;

import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.parser.LtlParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class LtlfParserTest {
  private static final String[] INPUT = {
    "!a",
    "G a",
    "F a & X b",
    "(a -> b) U c",
    "tt U b",
    "a M b",
    "a R b",
    "!(a R b)",
    "a W b U c R a",
    "!(X !a)",
    "tt & a",
    "tt | a"
  };
  private static final Formula[] OUTPUT = {
    new NegOperator(Literal.of(0)),
    new GOperator(Literal.of(0)),
    new Conjunction(new Formula[]{new FOperator(Literal.of(0)),new XOperator(Literal.of(1))}),
    new UOperator(new Disjunction(new Formula[]{new NegOperator(Literal.of(0)),Literal.of(1)}),Literal.of(2)),
    new UOperator(BooleanConstant.TRUE,Literal.of(0)),
    new MOperator(Literal.of(0),Literal.of(1)),
    new ROperator(Literal.of(0),Literal.of(1)),
    new NegOperator(new ROperator(Literal.of(0),Literal.of(1))),
    new WOperator(Literal.of(0),new UOperator(Literal.of(1),new ROperator(Literal.of(2),Literal.of(0)))),
    new NegOperator(new XOperator(new NegOperator(Literal.of(0)))),
    new Conjunction(new Formula[]{BooleanConstant.TRUE,Literal.of(0)}),
    new Disjunction(new Formula[]{BooleanConstant.TRUE,Literal.of(0)})
  };

  @Test
  void ParserTest(){
    for (int i = 0;i< INPUT.length;i++){
      assertEquals(OUTPUT[i],LtlfParser.syntax(INPUT[i]));
    }
  }

  @Test
  void testSingleQuotedLiteralParsing() {
    LabelledFormula formula = LtlParser.parse("'a b c'");
    assertEquals(List.of("a b c"), formula.variables());
    assertEquals(Literal.of(0), formula.formula());
  }

  @Test
  void testDoubleQuotedLiteralParsing() {
    LabelledFormula formula = LtlParser.parse("\"a b c\"");
    assertEquals(List.of("a b c"), formula.variables());
    assertEquals(Literal.of(0), formula.formula());
  }

  @Test
  void testParseRegression1() {
    assertThrows(ParseCancellationException.class, () -> LtlParser.parse("FF"));
  }

  @Test
  void testParseRegression2() {
    assertThrows(ParseCancellationException.class, () -> LtlParser.parse("Fa!"));
  }

  @Test
  void testParseRegression3() {
    assertThrows(ParseCancellationException.class, () -> LtlParser.parse("F+"));
  }
}
