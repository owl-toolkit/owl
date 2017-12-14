package owl.arena;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import owl.arena.Arena.Owner;
import owl.arena.Views.Node;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.TestEnvironment;
import owl.translations.ldba2dpa.RankingState;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;

public class ArenaFactoryTest {
  private static final LTL2DPAFunction TRANSLATION;

  static {
    EnumSet<Configuration> optimisations = EnumSet.allOf(Configuration.class);
    optimisations.remove(Configuration.COMPLEMENT_CONSTRUCTION);
    TRANSLATION = new LTL2DPAFunction(TestEnvironment.INSTANCE, optimisations, false);
  }

  @Test
  public void testTransform() throws Exception {
    LabelledFormula formula = LtlParser.parse("G (a <-> X b) & G F (!a | b | c)");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);
    Arena<Node<Object>, ParityAcceptance> arena =
      ArenaFactory.copyOf(Views.split(automaton, List.of("a", "c")));

    for (Node<Object> state : arena.getStates()) {
      for (Node<Object> predecessor : arena.getPredecessors(state)) {
        assertThat(state, isIn(arena.getSuccessors(predecessor)));
      }

      for (Node<Object> successors : arena.getSuccessors(state)) {
        assertThat(state, isIn(arena.getPredecessors(successors)));
      }
    }
  }

  @Test
  public void testAttractor() throws Exception {
    LabelledFormula formula = LtlParser.parse("F (a <-> X b)");

    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Arena<Node<Object>, ParityAcceptance> arena =
      ArenaFactory.copyOf(Views.split(automaton, List.of("a")));

    Set<Node<Object>> winningStates = arena.getStates().stream()
      .filter(x -> {
        @SuppressWarnings("unchecked")
        RankingState<EquivalenceClass, ?> state = (RankingState<EquivalenceClass, ?>) x.state;
        return state.state != null && state.state.isTrue();
      }).collect(Collectors.toSet());
    assertThat(winningStates, not(empty()));

    // Player 2 can win by matching the action of Player 1 one step delayed.
    assertThat(arena.getAttractorFixpoint(winningStates, Owner.PLAYER_2),
      hasItem(arena.getInitialState()));

    // Player 1 can never win...
    assertThat(arena.getAttractorFixpoint(winningStates, Owner.PLAYER_1),
      not(hasItem(arena.getInitialState())));
  }
}
