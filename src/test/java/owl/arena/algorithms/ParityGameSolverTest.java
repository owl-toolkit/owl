package owl.arena.algorithms;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import owl.arena.Arena;
import owl.arena.Views;
import owl.arena.Views.Node;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;

public class ParityGameSolverTest {
  private static final LTL2DPAFunction TRANSLATION = new LTL2DPAFunction(
    DefaultEnvironment.annotated(),
    Sets.union(LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG, Set.of(Configuration.COMPLETE)));

  @Test
  public void ltl2zielonkaTest1() {
    LabelledFormula formula = LtlParser.parse("F (a <-> X b)");

    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Arena<Node<Object>, ParityAcceptance> arena =
      Views.split(automaton, List.of("a"));

    assertThat(ParityGameSolver.zielonkaRealizability(arena), equalTo(true));
  }

  @Test
  public void ltl2zielonkaTest2() {
    LabelledFormula formula =
      LtlParser.parse("((((G (F (r_0))) && (G (F (r_1)))) <-> "
                      + "(G (F (g)))) && (G ((((r_0) && (r_1)) -> "
                      + "(G (! (g)))) && (true))))");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Arena<Node<Object>, ParityAcceptance> arena =
      Views.split(automaton, List.of("r_0", "r_1"));

    assertThat(ParityGameSolver.zielonkaRealizability(arena), equalTo(false));
  }

  @Test
  public void ltl2zielonkaTest3() {
    LabelledFormula formula =
      LtlParser.parse("(G ((((req) -> (X ((grant) && (X ((grant) "
                      + "&& (X (grant))))))) && ((grant) -> "
                      + "(X (! (grant))))) && ((cancel) -> "
                      + "(X ((! (grant)) U (go))))))");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Arena<Node<Object>, ParityAcceptance> arena =
      Views.split(automaton, List.of("go", "cancel", "req"));

    assertThat(ParityGameSolver.zielonkaRealizability(arena), equalTo(false));
  }

  @Test
  public void ltl2zielonkaTest4() {
    LabelledFormula formula =
      LtlParser.parse("(((G (F (r_0))) && (G (F (r_1)))) <-> (G (F (g))))");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Arena<Node<Object>, ParityAcceptance> arena =
      Views.split(automaton, List.of("r_0", "r_1"));

    assertThat(ParityGameSolver.zielonkaRealizability(arena), equalTo(true));
  }
}
