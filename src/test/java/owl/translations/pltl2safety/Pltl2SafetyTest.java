package owl.translations.pltl2safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static owl.util.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;

import jhoafparser.parser.generated.ParseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import owl.automaton.AutomatonReader;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.algorithms.LanguageAnalysis;
import owl.automaton.output.HoaPrinter;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.SpectraParser;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

class Pltl2SafetyTest {
  private static LTL2DAFunction translator = new LTL2DAFunction(DefaultEnvironment.standard(),
    true, EnumSet.of(LTL2DAFunction.Constructions.PAST_SAFETY,
    LTL2DAFunction.Constructions.SAFETY));

  private static final List<String> INPUT = List.of(
    "sys boolean a;\n"
    + "gar G H a;",
    "env boolean a;\n"
    + "env boolean b;\n"
    + "asm G a S b;",
    "sys boolean a;\n"
    + "sys boolean b;\n"
    + "gar G a S (H b);"
  );

  @Test
  void test() {
    for (String in : INPUT) {
      List<LabelledFormula> safety = SpectraParser.parse(in).getSafety();
      safety.forEach(translator::apply);
    }
  }

  @TestInstance(PER_CLASS)
  @Nested
  class Safety {
    private List<Arguments> provider() {
      return List.of(
        Arguments.of(
          "env boolean a;\n"
            + "asm G Y a;",
          1,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[]\"\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean a;\n"
            + "asm G !(Y !a);",
          1,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[p0, Zp0], [Zp0, !p0]]\"\n"
            + "[0] 0\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean a;\n"
            + "asm G H a;",
          1,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[Hp0, p0]]\"\n"
            + "[0] 0\n"
            + "--END--\n"
        ),
        Arguments.of(
          "sys boolean a;\n"
            + "gar G O a;",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[p0, Op0]]\"\n"
            + "[0] 1\n"
            + "State: 1 \"[[p0, Op0], [!p0, Op0]]\"\n"
            + "[t] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean a;\n"
            + "env boolean b;\n"
            + "asm G H a;",
          1,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[Hp0, p0]]\"\n"
            + "[0] 0\n"
            + "--END--\n"
        ),
        Arguments.of(
          "sys boolean a;\n"
            + "sys boolean b;\n"
            + "gar G a S b;",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"a\" \"b\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[!p0, p1, (p0Sp1)], [p0, p1, (p0Sp1)]]\"\n"
            + "[1] 1\n"
            + "State: 1 \"[[p0, !p1, (p0Sp1)], [!p0, p1, (p0Sp1)], [p0, p1, (p0Sp1)]]\"\n"
            + "[1] 1\n"
            + "[0 & !1] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "sys boolean a;\n"
            + "sys boolean b;\n"
            + "gar G a T b;",
          1,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"a\" \"b\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[!p0, p1, (p0Tp1)], [p0, p1, (p0Tp1)]]\"\n"
            + "[1] 0\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean a;\n"
            + "env boolean b;\n"
            + "asm G (H a) & b;",
          1,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"a\" \"b\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[p0, p1, (Hp0&p1), Hp0]]\"\n"
            + "[0 & 1] 0\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean a;\n"
            + "env boolean b;\n"
            + "asm G (H a) | b;",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"a\" \"b\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[p0, p1, Hp0, (Hp0|p1)], [!p0, p1, (Hp0|p1), O!p0], "
            + "[!p1, p0, (Hp0|p1), Hp0]]\"\n"
            + "[0] 0\n"
            + "[!0 & 1] 1\n"
            + "State: 1 \"[[!p0, p1, (Hp0|p1), O!p0], [p0, p1, (Hp0|p1), O!p0]]\"\n"
            + "[1] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "sys boolean a;\n"
            + "gar G a <-> H a;",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[p0, (Hp0|p0), (Hp0&p0), Hp0, ((Hp0&p0)|(O!p0&!p0))], "
            + "[!p0, (O!p0&!p0), O!p0, ((Hp0&p0)|(O!p0&!p0)), (O!p0|!p0)]]\"\n"
            + "[0] 0\n"
            + "[!0] 1\n"
            + "State: 1 \"[[!p0, (O!p0&!p0), O!p0, ((Hp0&p0)|(O!p0&!p0)), (O!p0|!p0)]]\"\n"
            + "[!0] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "sys boolean a;\n"
            + "sys boolean b;\n"
            + "gar G a -> Y b;",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"a\" \"b\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[!p0, !p1, Z!p1, (Yp1|!p0)], [!p0, p1, Z!p1, (Yp1|!p0)]]\"\n"
            + "[!0 & !1] 0\n"
            + "[!0 & 1] 1\n"
            + "State: 1 \"[[p0, p1, Yp1, (Yp1|!p0)], [!p0, !p1, Yp1, (Yp1|!p0)], "
            + "[!p1, p0, Yp1, (Yp1|!p0)], [!p0, p1, Yp1, (Yp1|!p0)]]\"\n"
            + "[1] 1\n"
            + "[!1] 0\n"
            + "--END--\n"
        ),
        Arguments.of(
          "sys Int(0..2) speed;\n"
            + "gar G (PREV(speed=0) -> speed=1);",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"speed_0\" \"speed_1\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[Z(p1|p0), !p1, p0, (p1|p0), ((!p1&p0)|Z(p1|p0)), (p0&!p1)], "
            + "[!p0, Z(p1|p0), p1, (p1|p0), (p1|!p0), ((!p1&p0)|Z(p1|p0))], "
            + "[Z(p1|p0), p0, p1, (p1|p0), (p1|!p0), ((!p1&p0)|Z(p1|p0))], "
            + "[!p0, Z(p1|p0), !p1, (p1|!p0), ((!p1&p0)|Z(p1|p0)), (!p1&!p0)]]\"\n"
            + "[0] 0\n"
            + "[1] 0\n"
            + "[!0 & !1] 1\n"
            + "State: 1 \"[[!p1, p0, (p1|p0), ((!p1&p0)|Z(p1|p0)), Y(!p1&!p0), (p0&!p1)]]\"\n"
            + "[0 & !1] 0\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean grant;\n"
            + "sys boolean request;\n"
            + "gar G (grant -> Y (!grant S request));",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"grant\" \"request\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[!p0, p1, (Y(!p0Sp1)|!p0), (!p0Sp1), Z(p0T!p1)], "
            + "[!p0, !p1, (p0T!p1), (Y(!p0Sp1)|!p0), Z(p0T!p1)]]\"\n"
            + "[!0 & !1] 0\n"
            + "[!0 & 1] 1\n"
            + "State: 1 \"[[(!p0Sp1), p0, Y(!p0Sp1), p1, (Y(!p0Sp1)|!p0)], "
            + "[p0, !p1, Y(!p0Sp1), (Y(!p0Sp1)|!p0), (p0T!p1)], "
            + "[!p0, (!p0Sp1), !p1, Y(!p0Sp1), (Y(!p0Sp1)|!p0)], "
            + "[!p0, (!p0Sp1), Y(!p0Sp1), p1, (Y(!p0Sp1)|!p0)]]\"\n"
            + "[!0] 1\n"
            + "[1] 1\n"
            + "[0 & !1] 0\n"
            + "--END--\n"
        )
      );
    }

    @ParameterizedTest
    @MethodSource("provider")
    void test(String formula, int expectedSize, String expectedHoa) throws ParseException {
      var labelledFormula = SpectraParser.parse(formula).getSafety().get(0);
      var automaton = translator.apply(labelledFormula);

      assertEquals(expectedSize, automaton.size(), () -> HoaPrinter.toString(automaton));
      assertThat(automaton.acceptance(), AllAcceptance.class::isInstance);

      var automaton1 = Views.viewAs(automaton, BuchiAcceptance.class);
      var automaton2 = Views.viewAs(
        AutomatonReader.readHoa(
          expectedHoa,
          DefaultEnvironment.annotated().factorySupplier()::getValuationSetFactory
        ),
        BuchiAcceptance.class
      );
      assertTrue(LanguageAnalysis.contains(automaton1, automaton2));
      assertTrue(LanguageAnalysis.contains(automaton2, automaton1));
    }
  }

  @TestInstance(PER_CLASS)
  @Nested
  class PastEqFuture {
    private List<Arguments> provider() {
      return List.of(
        Arguments.of(
          "env boolean failure;\n"
            + "sys boolean problem;\n"
            + "gar G problem -> Y (O failure);",
          "!(!failure U problem)"
        ),
        Arguments.of(
          "env boolean request;\n"
            + "sys boolean grant;\n"
            + "gar G (grant -> Y (!grant S request));",
          "(request R !grant) & G (grant -> (request | (X (request R !grant))))"
        )
      );
    }

    @ParameterizedTest
    @MethodSource("provider")
    void test(String spectraFormula, String ltlFormula) {
      var pastLabelledFormula = SpectraParser.parse(spectraFormula).getSafety().get(0);
      var pastAutomaton = translator.apply(pastLabelledFormula);

      var futureLabelledFormula = LtlParser.parse(ltlFormula);
      var futureAutomaton = translator.apply(futureLabelledFormula);

      var automaton1 = Views.viewAs(pastAutomaton, BuchiAcceptance.class);
      var automaton2 = Views.viewAs(futureAutomaton, BuchiAcceptance.class);

      assertTrue(LanguageAnalysis.contains(automaton1, automaton2));
      assertTrue(LanguageAnalysis.contains(automaton2, automaton1));
    }
  }
}
