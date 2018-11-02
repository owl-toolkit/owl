/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.translations.ltl2ldba.breakpointfree;

import java.util.BitSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnegative;
import owl.automaton.ImplicitNonDeterministicEdgeTreeAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationTree;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.canonical.RoundRobinState;
import owl.translations.ltl2ldba.AbstractAcceptingComponentBuilder;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public abstract class AcceptingComponentBuilder<B extends GeneralizedBuchiAcceptance>
  extends AbstractAcceptingComponentBuilder<AcceptingComponentState, B, FGObligations> {

  @Nonnegative
  protected int acceptanceSets;

  private AcceptingComponentBuilder(Factories factories,
    Set<Configuration> optimisations) {
    super(optimisations, factories);
    acceptanceSets = 1;
  }

  public static final class Buchi
    extends AcceptingComponentBuilder<BuchiAcceptance> {

    public Buchi(Factories factories,
      Set<Configuration> optimisations) {
      super(factories, optimisations);
    }

    @Override
    public MutableAutomaton<AcceptingComponentState, BuchiAcceptance> build() {
      return MutableAutomatonFactory.copy(new ImplicitNonDeterministicEdgeTreeAutomaton<>(
        factories.vsFactory,
        anchors,
        BuchiAcceptance.INSTANCE,
        null,
        this::edgeTree));
    }
  }

  public static final class GeneralizedBuchi
    extends AcceptingComponentBuilder<GeneralizedBuchiAcceptance> {

    public GeneralizedBuchi(Factories factories,
      Set<Configuration> optimisations) {
      super(factories, optimisations);
    }

    @Override
    public MutableAutomaton<AcceptingComponentState, GeneralizedBuchiAcceptance> build() {
      return MutableAutomatonFactory.copy(new ImplicitNonDeterministicEdgeTreeAutomaton<>(
        factories.vsFactory,
        anchors,
        GeneralizedBuchiAcceptance.of(acceptanceSets),
        null,
        this::edgeTree));
    }
  }

  @Override
  protected AcceptingComponentState createState(EquivalenceClass remainder,
    FGObligations obligations) {
    EquivalenceClass safety = obligations.safetyAutomaton.onlyInitialStateWithRemainder(remainder);

    if (safety.isFalse()) {
      return null;
    }

    if (obligations.gfCoSafetyAutomaton == null) {
      return new AcceptingComponentState(safety, null, obligations);
    }

    acceptanceSets = Math.max(acceptanceSets,
      obligations.gfCoSafetyAutomaton.acceptance().acceptanceSets());

    return new AcceptingComponentState(
      safety, obligations.gfCoSafetyAutomaton.onlyInitialState(), obligations);
  }

  protected ValuationTree<Edge<AcceptingComponentState>> edgeTree(AcceptingComponentState state) {
    var obligation = Objects.requireNonNull(state.obligations);

    var safetyState = Objects.requireNonNull(state.safety);
    var safetyAutomaton = obligation.safetyAutomaton;
    var safetyEdgeTree = safetyAutomaton.edgeTree(safetyState);

    if (obligation.gfCoSafetyAutomaton == null) {
      Function<Edge<EquivalenceClass>, Edge<AcceptingComponentState>> mapper = (safetyEdge) -> {
        var successor = new AcceptingComponentState(safetyEdge.successor(), null, obligation);
        var acceptance = new BitSet();
        acceptance.set(0, acceptanceSets);
        return Edge.of(successor, acceptance);
      };

      return safetyEdgeTree.map(
        x -> x.stream().map(mapper).collect(Collectors.toUnmodifiableSet()));
    }

    var livenessState = Objects.requireNonNull(state.liveness);
    var livenessAutomaton = obligation.gfCoSafetyAutomaton;
    var livenessEdgeTree = livenessAutomaton.edgeTree(livenessState);

    assert safetyEdgeTree.values().stream().allMatch(x -> x.largestAcceptanceSet() == -1);
    assert livenessEdgeTree.values().stream().allMatch(
      x -> x.largestAcceptanceSet() < acceptanceSets);

    BiFunction<Edge<EquivalenceClass>,
      Edge<RoundRobinState<EquivalenceClass>>,
      Edge<AcceptingComponentState>> merger = (safetyEdge, livenessEdge) -> {

        var successor = new AcceptingComponentState(safetyEdge.successor(),
          livenessEdge.successor(), state.obligations);

        var acceptance = new BitSet();
        livenessEdge.acceptanceSetIterator().forEachRemaining((IntConsumer) acceptance::set);
        acceptance.set(livenessAutomaton.acceptance().acceptanceSets(), acceptanceSets);

        return Edge.of(successor, acceptance);
      };

    return ValuationTree.cartesianProduct(safetyEdgeTree, livenessEdgeTree, merger);
  }
}
