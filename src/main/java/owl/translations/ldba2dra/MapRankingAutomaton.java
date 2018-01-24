package owl.translations.ldba2dra;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.translations.ldba2dpa.AbstractBuilder;
import owl.translations.ldba2dpa.LanguageLattice;
import owl.translations.ltl2ldba.breakpointfree.BooleanLattice;

public class MapRankingAutomaton {
  public static <S, T, A, L> Automaton<MapRankingState<S, A, T>, GeneralizedRabinAcceptance> of(
    LimitDeterministicAutomaton<S, T, GeneralizedBuchiAcceptance, A> ldba,
    LanguageLattice<T, A, L> lattice, Predicate<S> isAcceptingState, boolean resetAfterSccSwitch,
    boolean optimizeInitialState) {
    checkArgument(lattice instanceof BooleanLattice, "Only the Boolean lattice is supported.");
    checkArgument(ldba.getAcceptingComponent().getInitialStates().isEmpty()
        && ldba.getInitialComponent().getInitialStates().size() == 1,
      "Exactly one initial state expected.");

    GeneralizedRabinAcceptance acceptance =
      ldba.getAcceptingComponent().getAcceptance().getAcceptanceSets() == 1
      ? new RabinAcceptance()
      : new GeneralizedRabinAcceptance();

    Builder<S, T, A, L, ?, ?> builder = new Builder<>(ldba, resetAfterSccSwitch, lattice,
      isAcceptingState, acceptance);

    Automaton<MapRankingState<S, A, T>, GeneralizedRabinAcceptance> automaton = AutomatonFactory
      .createStreamingAutomaton(builder.acceptance, builder.initialState,
        ldba.getAcceptingComponent().getFactory(), builder::getSuccessor);

    return optimizeInitialState ? AbstractBuilder.optimizeInitialState(automaton) : automaton;
  }

  static final class Builder<S, T, A, L, B extends GeneralizedBuchiAcceptance,
    R extends GeneralizedRabinAcceptance>
    extends AbstractBuilder<S, T, A, L, B> {
    private static final Logger logger = Logger.getLogger(Builder.class.getName());

    final R acceptance;
    final Map<A, RabinPair> pairs;
    final RabinPair truePair;
    final MapRankingState<S, A, T> initialState;

    Builder(LimitDeterministicAutomaton<S, T, B, A> ldba, boolean resetAfterSccSwitch,
      LanguageLattice<T, A, L> lattice, Predicate<S> isAcceptingState, R acceptance) {
      super(ldba, lattice, isAcceptingState, resetAfterSccSwitch);
      logger.log(Level.FINER, "Safety Components: {0}", safetyComponents);
      this.acceptance = acceptance;
      pairs = new HashMap<>();
      int infSets = ldba.getAcceptingComponent().getAcceptance().getAcceptanceSets();
      sortingOrder.forEach(x -> pairs.put(x, acceptance.createPair(infSets)));
      truePair = acceptance.createPair(1);
      initialState = buildEdge(ldba.getInitialComponent().getInitialState(), Map.of(), null)
        .getSuccessor();
    }

    Edge<MapRankingState<S, A, T>> buildEdge(S state, Map<A, T> previousRanking,
      @Nullable BitSet valuation) {
      if (isAcceptingState.test(state)) {
        return Edge.of(MapRankingState.of(state), truePair.infSet());
      }

      Map<A, T> ranking = new HashMap<>();
      ldba.getEpsilonJumps(state).forEach(x -> ranking.put(ldba.getAnnotation(x), x));

      BitSet acceptance = new BitSet();
      acceptance.set(truePair.finSet());

      previousRanking.forEach((annotation, x) -> {
        assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
        Edge<T> edge = ldba.getAcceptingComponent().getEdge(x, valuation);
        RabinPair pair = pairs.get(annotation);

        if (edge == null || !ranking.containsKey(annotation)) {
          acceptance.set(pair.finSet());
        } else {
          ranking.put(annotation, edge.getSuccessor());
          edge.acceptanceSetIterator().forEachRemaining((int i) -> acceptance.set(pair.infSet(i)));
        }
      });

      return Edge.of(MapRankingState.of(state, ranking), acceptance);
    }

    @Nullable
    Edge<MapRankingState<S, A, T>> getSuccessor(MapRankingState<S, A, T> state, BitSet valuation) {
      if (state.state == null) {
        return null;
      }

      S successor;

      { // We obtain the successor of the state in the initial component.
        Edge<S> edge = ldba.getInitialComponent().getEdge(state.state, valuation);

        // The initial component moved to a rejecting sink. Thus all runs die.
        if (edge == null) {
          return null;
        }

        successor = edge.getSuccessor();
      }

      // If a SCC switch occurs, the componentMap and the safety progress is reset.
      if (sccSwitchOccurred(state.state, successor)) {
        return Edge.of(buildEdge(successor, Map.of(), valuation).getSuccessor());
      }

      return buildEdge(successor, state.componentMap, valuation);
    }
  }
}
