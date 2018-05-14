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

public final class MapRankingAutomaton {
  private MapRankingAutomaton() {}

  public static <S, T, A, L> Automaton<MapRankingState<S, A, T>, GeneralizedRabinAcceptance> of(
    LimitDeterministicAutomaton<S, T, GeneralizedBuchiAcceptance, A> ldba,
    LanguageLattice<T, A, L> lattice, Predicate<S> isAcceptingState, boolean resetAfterSccSwitch,
    boolean optimizeInitialState) {
    checkArgument(lattice instanceof BooleanLattice, "Only the Boolean lattice is supported.");
    checkArgument(ldba.acceptingComponent().initialStates().isEmpty()
        && ldba.initialComponent().initialStates().size() == 1,
      "Exactly one initial state expected.");


    int acceptanceSets = ldba.acceptingComponent().acceptance().acceptanceSets();
    Class<? extends GeneralizedRabinAcceptance> acceptanceClass =
      acceptanceSets == 1 ? RabinAcceptance.class : GeneralizedRabinAcceptance.class;

    Builder<S, T, A, L, ?, ?> builder =
      new Builder<>(ldba, resetAfterSccSwitch, lattice, isAcceptingState, acceptanceClass);

    Automaton<MapRankingState<S, A, T>, GeneralizedRabinAcceptance> automaton =
      AutomatonFactory.create(builder.initialState, ldba.acceptingComponent().factory(),
        builder::getSuccessor, builder.acceptance);

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
      LanguageLattice<T, A, L> lattice, Predicate<S> isAcceptingState, Class<R> acceptanceClass) {
      super(ldba, lattice, isAcceptingState, resetAfterSccSwitch);
      logger.log(Level.FINER, "Safety Components: {0}", safetyComponents);
      pairs = new HashMap<>();

      if (acceptanceClass.equals(RabinAcceptance.class)) {
        RabinAcceptance.Builder builder = new RabinAcceptance.Builder();
        sortingOrder.forEach(x -> pairs.put(x, builder.add()));
        truePair = builder.add();
        acceptance = (R) builder.build();
      } else if (acceptanceClass.equals(GeneralizedRabinAcceptance.class)) {
        GeneralizedRabinAcceptance.Builder builder = new GeneralizedRabinAcceptance.Builder();
        int infSets = ldba.acceptingComponent().acceptance().acceptanceSets();
        sortingOrder.forEach(x -> pairs.put(x, builder.add(infSets)));
        truePair = builder.add(0);
        acceptance = (R) builder.build();
      } else {
        throw new AssertionError();
      }

      initialState = buildEdge(ldba.initialComponent().initialState(), Map.of(), null)
        .successor();
    }

    Edge<MapRankingState<S, A, T>> buildEdge(S state, Map<A, T> previousRanking,
      @Nullable BitSet valuation) {
      if (isAcceptingState.test(state)) {
        if (truePair.hasInfSet()) {
          return Edge.of(MapRankingState.of(state), truePair.infSet());
        }

        return Edge.of(MapRankingState.of(state));
      }

      Map<A, T> ranking = new HashMap<>();
      ldba.epsilonJumps(state).forEach(x -> ranking.put(ldba.annotation(x), x));

      BitSet acceptance = new BitSet();
      acceptance.set(truePair.finSet());

      previousRanking.forEach((annotation, x) -> {
        assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
        Edge<T> edge = ldba.acceptingComponent().edge(x, valuation);
        RabinPair pair = pairs.get(annotation);

        if (edge == null || !ranking.containsKey(annotation)) {
          acceptance.set(pair.finSet());
        } else {
          ranking.put(annotation, edge.successor());
          edge.acceptanceSetIterator().forEachRemaining((int i) -> acceptance.set(pair.infSet(i)));
        }
      });

      return Edge.of(MapRankingState.of(state, ranking), acceptance);
    }

    @Nullable
    Edge<MapRankingState<S, A, T>> getSuccessor(MapRankingState<S, A, T> state, BitSet valuation) {
      S successor;

      { // We obtain the successor of the state in the initial component.
        Edge<S> edge = ldba.initialComponent().edge(state.state(), valuation);

        // The initial component moved to a rejecting sink. Thus all runs die.
        if (edge == null) {
          return null;
        }

        successor = edge.successor();
      }

      // If a SCC switch occurs, the componentMap and the safety progress is reset.
      if (sccSwitchOccurred(state.state(), successor)) {
        return Edge.of(buildEdge(successor, Map.of(), valuation).successor());
      }

      return buildEdge(successor, state.componentMap(), valuation);
    }
  }
}
