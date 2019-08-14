package owl.ltl.ltlf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragments;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.ReplaceBiCondVisitor;
import owl.ltl.visitors.PrintVisitor;

public class LtlfToLtlVisitorTest {
  private static final List<String> Literals = List.of("a", "b", "c", "d", "t");
  private static final List<Formula> LtlfFORMULAS = List.of(
    LtlfParser.syntax("false", Literals),
    LtlfParser.syntax("true", Literals),
    LtlfParser.syntax("a", Literals),

    LtlfParser.syntax("! a", Literals),
    LtlfParser.syntax("a & b", Literals),
    LtlfParser.syntax("a | b", Literals),
    LtlfParser.syntax("a -> b", Literals),
    LtlfParser.syntax("a <-> b", Literals),
    LtlfParser.syntax("a xor b", Literals),

    LtlfParser.syntax("F a", Literals),
    LtlfParser.syntax("G a", Literals),
    LtlfParser.syntax("X a", Literals),

    LtlfParser.syntax("a M b", Literals),
    LtlfParser.syntax("a R b", Literals),
    LtlfParser.syntax("a U b", Literals),
    LtlfParser.syntax("a W b", Literals),

    LtlfParser.syntax("(a <-> b) xor (c <-> d)", Literals),

    LtlfParser.syntax("F ((a R b) & c)", Literals),
    LtlfParser.syntax("F ((a W b) & c)", Literals),
    LtlfParser.syntax("G ((a M b) | c)", Literals),
    LtlfParser.syntax("G ((a U b) | c)", Literals),
    LtlfParser.syntax("G (X (a <-> b))", Literals),
    LtlfParser.syntax("G (X (a xor b))", Literals),
    LtlfParser.syntax("!(X(!(a U b) & ! G(a R c))| !(!(a M b)))", Literals));

  private final List<Formula> formulas = List.of(
    (LtlfParser.syntax("!(!(a))", Literals)),
    (LtlfParser.syntax("!(G a)", Literals)),
    (LtlfParser.syntax("!(F a)", Literals)),
    (LtlfParser.syntax("!(a U b)", Literals)),
    (LtlfParser.syntax("!(a R b)", Literals)),
    (LtlfParser.syntax("!(a W b)", Literals)),
    (LtlfParser.syntax("!(a M b)", Literals)),
    (LtlfParser.syntax("!F(G(a))", Literals)),
    (LtlfParser.syntax("!G(F(a))", Literals)),
    (LtlfParser.syntax("G(!G(a))", Literals)),
    (LtlfParser.syntax("F(!F(a))", Literals)),
    (LtlfParser.syntax("!X(X(X(a)))", Literals)),
    (LtlfParser.syntax("X(!X(X(a)))", Literals)),
    (LtlfParser.syntax("X(X(!X(a)))", Literals)));

  private final List<Formula> translatedFormulas = List.of(
    (LtlParser.syntax("a", Literals)),
    (LtlParser.syntax("F(!a & t)", Literals)),
    (LtlParser.syntax("!a U !t", Literals)),
    (LtlParser.syntax("(!a|X(!t)) M !b", Literals)),
    (LtlParser.syntax("!a U (!b & t)", Literals)),
    (LtlParser.syntax("(!a &t)M !b", Literals)),
    (LtlParser.syntax("!a U (!b| !t)", Literals)),
    (LtlParser.syntax("F(t & X(!t) & !a)", Literals)),
    (LtlParser.syntax("F(t & X(!t) & !a)", Literals)),
    (LtlParser.syntax("F(t & X(!t) & !a)", Literals)),
    (LtlParser.syntax("F(t & X(!t) & !a)", Literals)),
    (LtlParser.syntax("X(X(X(!t |  !a)))", Literals)),
    (LtlParser.syntax("X(t & X(X(!t |!a)))", Literals)),
    (LtlParser.syntax("X(X(t& X(!t | !a)))", Literals)));

  @Test
  void coSafetyTest() {

    LtlfToLtlTranslator.LtlfToLtlVisitor transformer =
      new LtlfToLtlTranslator.LtlfToLtlVisitor(Literal.of(4));
    ReplaceBiCondVisitor b = new ReplaceBiCondVisitor();
    LtlfFORMULAS.forEach(x -> assertTrue(SyntacticFragments
      .isCoSafety(List.of(transformer.apply(b.apply(x))))));
  }

  @Test
  void manualFormulaTest() {
    Formula f = LtlfParser.syntax("X!X!X(a)", Literals);
    Formula f1 = LtlfParser.syntax("G((X a))", Literals);

    LtlfToLtlTranslator.LtlfToLtlVisitor transformer =
      new LtlfToLtlTranslator.LtlfToLtlVisitor(Literal.of(4));
    ReplaceBiCondVisitor b = new ReplaceBiCondVisitor();
    PrintVisitor p = new PrintVisitor(false, Literals);
    p.apply(transformer.apply(b.apply(f)));
    p.apply(transformer.apply(b.apply(f1)));
    //System.out.println(p.apply(LtlfToLtlTranslator.translate(f,Literal.of(4))));
    //System.out.println(p.apply(Translator.translate(f1,Literal.of(4))));
    //System.out.println(p.apply(SimplifierFactory.apply(Translator.translate(f1,Literal.of(4)),
    //  SimplifierFactory.Mode.PUSH_DOWN_X, SimplifierFactory.Mode.SYNTACTIC)));

  }


  @Test
  void carefulNegationPropagationTest() {
    LtlfToLtlTranslator.LtlfToLtlVisitor transformer =
      new LtlfToLtlTranslator.LtlfToLtlVisitor(Literal.of(4));

    for (int i = 0; i < formulas.size(); i++) {
      assertEquals(translatedFormulas.get(i), transformer.apply(formulas.get(i)));
    }
  }


}
