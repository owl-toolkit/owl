package owl.translations.pltl2safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static owl.util.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

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
import owl.factories.ValuationSetFactory;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.SpectraParser;
import owl.run.DefaultEnvironment;

class Pltl2SafetyTest {
  private final PLTL2Safety translator = new PLTL2Safety(DefaultEnvironment.standard());
  private final Function<List<String>, ValuationSetFactory> FACTORY_SUPPLIER =
    DefaultEnvironment.annotated().factorySupplier()::getValuationSetFactory;

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
      List<LabelledFormula> safety = SpectraParser.parse(in).getLabelledSafety();
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
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[p0], []]\"\n"
            + "[!0] 0\n"
            + "[0] 1\n"
            + "State: 1 \"[[Yp0], [p0, Yp0], [], [p0]]\"\n"
            + "[!0] 0\n"
            + "[0] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean a;\n"
            + "asm G !(Y !a);",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[Zp0, p0], [Zp0]]\"\n"
            + "[0] 0\n"
            + "[!0] 1\n"
            + "State: 1 \"[[p0], []]\"\n"
            + "[0] 0\n"
            + "[!0] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean a;\n"
            + "asm G H a;",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[Hp0, p0], []]\"\n"
            + "[0] 0\n"
            + "[!0] 1\n"
            + "State: 1 \"[[p0],[]]\"\n"
            + "[t] 1\n"
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
            + "State: 0 \"[[Op0, p0], []]\"\n"
            + "[0] 1\n"
            + "[!0] 0\n"
            + "State: 1 \"[[Op0, p0],[Op0]]\"\n"
            + "[t] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean a;\n"
          + "env boolean b;\n"
          + "asm G H a;",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"a\" \"b\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[Hp0, p0], []]\"\n"
            + "[0] 0\n"
            + "[!0] 1\n"
            + "State: 1 \"[[p0],[]]\"\n"
            + "[t] 1\n"
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
            + "State: 0 \"[[(p0Sp1), p0, p1], [(p0Sp1), p1], [p0], []]\"\n"
            + "[!1] 0\n"
            + "[1] 1\n"
            + "State: 1 \"[[(p0Sp1), p0, p1], [(p0Sp1), p1], [(p0Sp1), p0], []]\"\n"
            + "[1] 1\n"
            + "[0] 1\n"
            + "[!0 & !1] 0\n"
            + "--END--\n"
        ),
        Arguments.of(
          "sys boolean a;\n"
            + "sys boolean b;\n"
            + "gar G a T b;",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"a\" \"b\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[(p0Tp1), p0, p1], [(p0Tp1), p1], [p0], []]\"\n"
            + "[!1] 0\n"
            + "[1] 1\n"
            + "State: 1 \"[[(p0Tp1), p0, p1], [p1], [p0], []]\"\n"
            + "[!1] 0\n"
            + "[!0 & 1] 0\n"
            + "[0 & 1] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean a;\n"
            + "env boolean b;\n"
            + "asm G (H a) & b;",
          3,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"a\" \"b\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[Hp0, p0, p1], [p1]]\"\n"
            + "[0 & 1] 2\n"
            + "[!0 & 1] 1\n"
            + "State: 1 \"[[p0, p1], [p0], [p1], []]\"\n"
            + "[t] 1\n"
            + "State: 2 \"[[Hp0, p0, p1], [Hp0, p0], [p1], []]\"\n"
            + "[0] 2\n"
            + "[!0] 1\n"
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
            + "State: 0 \"[[Hp0, p0, p1], [Hp0, p0], [p1], []]\"\n"
            + "[0] 0\n"
            + "[!0] 1\n"
            + "State: 1 \"[[p0, p1], [p0], [p1], []]\"\n"
            + "[t] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "sys boolean a;\n"
          + "gar G a <-> H a;",
          3,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 1 \"a\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[Hp0, p0]]\"\n"
            + "[0] 1\n"
            + "State: 1 \"[[Hp0, p0],[]]\"\n"
            + "[0] 1\n"
            + "[!0] 2\n"
            + "State: 2 \"[[p0], []]\"\n"
            + "[t] 2\n"
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
            + "State: 0 \"[[p0, p1], [p0], [p1], []]\"\n"
            + "[!1] 0\n"
            + "[1] 1\n"
            + "State: 1 \"[[Yp1, p0, p1], [Yp1, p1], [Yp1, p0], [Yp1]]\"\n"
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
            + "State: 0 \"[[Z(speed0|speed1), speed0, speed1], [Z(speed0|speed1)]," +
            "[Z(speed0|speed1), speed0], [Z(speed0|speed1)]\"\n"
            + "[0] 0\n"
            + "[1] 0\n"
            + "[!0 & !1] 1\n"
            + "State: 1 \"[[speed0, speed1], [speed0], [speed1], []]\"\n"
            + "[0] 0\n"
            + "[1] 0\n"
            + "[!0 & !1] 1\n"
            + "--END--\n"
        ),
        Arguments.of(
          "env boolean grant;\n"
          + "sys boolean request;\n"
          + "asm G (grant -> (!grant S request));",
          2,
          "HOA: v1\n"
            + "Start: 0\n"
            + "AP: 2 \"grant\" \"request\"\n"
            + "acc-name: all\n"
            + "Acceptance: 0 t\n"
            + "--BODY--\n"
            + "State: 0 \"[[(!p0Sp1, p0, p1], [(!p0Sp1), p1], [p0], []]\"\n"
            + "[!1] 0\n"
            + "[1] 1\n"
            + "State: 1 \"[[(!p0Sp1, p0, p1], [(!p0Sp1), p1], [(!p0Sp1)], [p0]]\"\n"
            + "[0 & !1] 0\n"
            + "[!0 & !1] 1\n"
            + "[1] 1\n"
            + "--END--\n"
        )
      );
    }

    @ParameterizedTest
    @MethodSource("provider")
    void test(String formula, int expectedSize, String expectedHoa) throws ParseException {
      var labelledFormula = SpectraParser.parse(formula).getLabelledSafety().get(0);
      var automaton = translator.apply(labelledFormula);
      assertEquals(expectedSize, automaton.size(), () -> HoaPrinter.toString(automaton));
      assertThat(automaton.acceptance(), AllAcceptance.class::isInstance);

      var automaton1 = Views.viewAs(automaton, BuchiAcceptance.class);
      var automaton2 = Views.viewAs(
        AutomatonReader.readHoa(expectedHoa, FACTORY_SUPPLIER),
        BuchiAcceptance.class
      );
      assertTrue(LanguageAnalysis.contains(automaton1, automaton2));
      assertTrue(LanguageAnalysis.contains(automaton2, automaton1));
    }
  }
}
