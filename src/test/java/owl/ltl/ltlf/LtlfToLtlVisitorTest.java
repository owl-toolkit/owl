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
    List<String> literale = List.of("decide","hlock_0","hlock_1",
      "hgrant_0","hgrant_1","locked","tail");
    Formula f1 = LtlfParser.syntax("(((G (((! (hgrant_0)) && (true)) ||"
      + " ((true) && (! (hgrant_1))))) && (G ((hgrant_0) || (hgrant_1)))) -> "
      + "(G (((((decide) && (X (hgrant_0))) -> ((X (locked)) <-> (X (hlock_0)))) && "
      + "(((decide) && (X (hgrant_1))) -> ((X (locked)) <-> (X (hlock_1))))) && ((! (decide)) "
      + "-> ((X (locked)) <-> (locked))))))", literale);
    PrintVisitor p1 = new PrintVisitor(false, literale);
    ReplaceBiCondVisitor r = new ReplaceBiCondVisitor();
    p1.apply(r.apply(f1));
    //System.out.println(p.apply(LtlfToLtlTranslator.translate(f,Literal.of(4))));
    //System.out.println(p1.apply(LtlfToLtlTranslator.translate(f1,Literal.of(6))));
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

  @Test
  void carefulNegationPropagationTest() {
    LtlfToLtlVisitor transformer = new LtlfToLtlVisitor();
    List<Formula> formulas = new LinkedList<>();
    List<Formula> translatedFormulas = new LinkedList<>();
    formulas.add(LtlfParser.syntax("!(!(a))",Literals));
    formulas.add(LtlfParser.syntax("!(G a)",Literals));
    formulas.add(LtlfParser.syntax("!(F a)",Literals));
    formulas.add(LtlfParser.syntax("!(a U b)",Literals));
    formulas.add(LtlfParser.syntax("!(a R b)",Literals));
    formulas.add(LtlfParser.syntax("!(a W b)",Literals));
    formulas.add(LtlfParser.syntax("!(a M b)",Literals));
    formulas.add(LtlfParser.syntax("!F(G(a))",Literals));
    formulas.add(LtlfParser.syntax("!G(F(a))",Literals));
    formulas.add(LtlfParser.syntax("G(!G(a))",Literals));
    formulas.add(LtlfParser.syntax("F(!F(a))",Literals));
    formulas.add(LtlfParser.syntax("!X(X(X(a)))",Literals));
    formulas.add(LtlfParser.syntax("X(!X(X(a)))",Literals));
    formulas.add(LtlfParser.syntax("X(X(!X(a)))",Literals));

    translatedFormulas.add(LtlParser.syntax("a",Literals));
    translatedFormulas.add(LtlParser.syntax("F(!a & t)",Literals));
    translatedFormulas.add(LtlParser.syntax("!a U !t",Literals));
    translatedFormulas.add(LtlParser.syntax("(!a|!t) M !b",Literals));
    translatedFormulas.add(LtlParser.syntax("!a U (!b & t)",Literals));
    translatedFormulas.add(LtlParser.syntax("(!a &t)M !b",Literals));
    translatedFormulas.add(LtlParser.syntax("!a U (!b| !t)",Literals));
    translatedFormulas.add(LtlParser.syntax("F(t & X(!t) & !a)",Literals));
    translatedFormulas.add(LtlParser.syntax("F(t & X(!t) & !a)",Literals));
    translatedFormulas.add(LtlParser.syntax("F(t & X(!t) & !a)",Literals));
    translatedFormulas.add(LtlParser.syntax("F(t & X(!t) & !a)",Literals));
    translatedFormulas.add(LtlParser.syntax("X(X(X(!t |  !a)))",Literals));
    translatedFormulas.add(LtlParser.syntax("X(t & X(X(!t |!a)))",Literals));
    translatedFormulas.add(LtlParser.syntax("X(X(t& X(!t | !a)))",Literals));
    for (int i = 0; i < formulas.size(); i++) {
      assertEquals(translatedFormulas.get(i),transformer.apply(formulas.get(i),Literal.of(4)));
    }

  }


}
