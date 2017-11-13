package owl.arena;

import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.Test;
import owl.arena.Arena.Owner;
import owl.arena.ArenaFactory.Node;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.Optimisation;
import owl.translations.ldba2dpa.RankingState;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public class ArenaFactoryTest {
  private static final LTL2DPAFunction TRANSLATION;

  static {
    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    optimisations.remove(Optimisation.PARALLEL);
    TRANSLATION = new LTL2DPAFunction(optimisations, false);
  }

  @Test
  public void testTransform() throws Exception {
    LabelledFormula formula = LtlParser.parse("G (a <-> X b) & G F (!a | b | c)");
    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);
    Arena<Node<Object>, ParityAcceptance> arena =
      ArenaFactory.copyOf(ArenaFactory.split(automaton, ImmutableList.of("a","c")));

    for (Node<Object> state : arena.getStates()) {
      for (Node<Object> predecessor : arena.getPredecessors(state)) {
        assertThat(state, Matchers.isIn(arena.getSuccessors(predecessor)));
      }

      for (Node<Object> successors : arena.getSuccessors(state)) {
        assertThat(state, Matchers.isIn(arena.getPredecessors(successors)));
      }
    }
  }

  @Test
  public void testAttractor() throws Exception {
    LabelledFormula formula = LtlParser.parse("F (a <-> X b)");

    Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(
      TRANSLATION.apply(formula), Object.class, ParityAcceptance.class);
    Arena<Node<Object>, ParityAcceptance> arena =
      ArenaFactory.copyOf(ArenaFactory.split(automaton, ImmutableList.of("a")));

    Stream<Node<Object>> trueSinks = arena.getStates().stream().filter(x -> {
      RankingState<EquivalenceClass, ?> state = (RankingState<EquivalenceClass, ?>) x.state;
      return state.state != null && state.state.isTrue();
    });

    Set<Node<Object>> winningStates = trueSinks.collect(Collectors.toSet());

    // Player 2 can win by matching the action of Player 1 one step delayed.
    assertThat(arena.getInitialState(),
      Matchers.isIn(arena.getAttractorFixpoint(winningStates, Owner.PLAYER_2)));

    // Player 1 can never win...
    assertThat(arena.getInitialState(),
      Matchers.not(Matchers.isIn(arena.getAttractorFixpoint(winningStates, Owner.PLAYER_1))));
  }
}