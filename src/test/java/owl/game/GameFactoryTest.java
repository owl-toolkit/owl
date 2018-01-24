package owl.game;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.Game.Owner;
import owl.game.Views.Node;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.ldba2dpa.AnnotatedState;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;

public class GameFactoryTest {
  private static final LTL2DPAFunction TRANSLATION = new LTL2DPAFunction(
    DefaultEnvironment.annotated(),
    Sets.union(LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG, Set.of(Configuration.COMPLETE)));

  @Test
  public void testTransform() throws Exception {
    LabelledFormula formula = LtlParser.parse("G (a <-> X b) & G F (!a | b | c)");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);
    Game<Node<Object>, ParityAcceptance> game =
      GameFactory.copyOf(Views.split(automaton, List.of("a", "c")));

    for (Node<Object> state : game.getStates()) {
      for (Node<Object> predecessor : game.getPredecessors(state)) {
        assertThat(state, isIn(game.getSuccessors(predecessor)));
      }

      for (Node<Object> successors : game.getSuccessors(state)) {
        assertThat(state, isIn(game.getPredecessors(successors)));
      }
    }
  }

  @Test
  public void testAttractor() throws Exception {
    LabelledFormula formula = LtlParser.parse("F (a <-> X b)");

    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);

    Game<Node<Object>, ParityAcceptance> game =
      GameFactory.copyOf(Views.split(automaton, List.of("a")));

    Set<Node<Object>> winningStates = game.getStates().stream()
      .filter(x -> {
        @SuppressWarnings("unchecked")
        AnnotatedState<EquivalenceClass> state = (AnnotatedState<EquivalenceClass>) x.state;
        return state.state != null && state.state.isTrue();
      }).collect(Collectors.toSet());
    assertThat(winningStates, not(empty()));

    // Player 2 can win by matching the action of Player 1 one step delayed.
    assertThat(game.getAttractorFixpoint(winningStates, Owner.PLAYER_2),
      hasItem(game.getInitialState()));

    // Player 1 can never win...
    assertThat(game.getAttractorFixpoint(winningStates, Owner.PLAYER_1),
      not(hasItem(game.getInitialState())));
  }
}
