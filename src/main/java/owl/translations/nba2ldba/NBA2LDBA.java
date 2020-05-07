/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.nba2ldba;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.TwoPartAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.determinization.Determinization;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Either;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class NBA2LDBA
  implements Function<Automaton<?, ?>, Automaton<?, BuchiAcceptance>> {

  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "nba2ldba",
    "Converts a non-deterministic Büchi automaton into a limit-deterministic Büchi "
      + "automaton",
    (commandLine, environment) -> OwlModule.AutomatonTransformer
      .of(automaton -> new NBA2LDBA().apply(automaton)));

  @Override
  public Automaton<?, BuchiAcceptance> apply(Automaton<?, ?> automaton) {
    return applyLDBA(automaton).automaton();
  }

  public static AutomatonUtil.LimitDeterministicGeneralizedBuchiAutomaton<?, BuchiAcceptance>
    applyLDBA(Automaton<?, ?> automaton) {
    Automaton<?, ? extends GeneralizedBuchiAcceptance> ngba;

    if (automaton.acceptance() instanceof AllAcceptance) {
      // Use subset construction to get a deterministic automaton and use double cast to take the
      // LDBA-shortcut.
      ngba = OmegaAcceptanceCast.cast(
        Determinization.determinizeAllAcceptance(
          OmegaAcceptanceCast.cast(automaton, AllAcceptance.class)),
        BuchiAcceptance.class);
    } else {
      ngba = OmegaAcceptanceCast.cast(automaton, GeneralizedBuchiAcceptance.class);
    }

    if (automaton.acceptance() instanceof BuchiAcceptance) {
      var ldba = AutomatonUtil.ldbaSplit(OmegaAcceptanceCast.cast(ngba, BuchiAcceptance.class));

      if (ldba.isPresent()) {
        return ldba.get();
      }
    }

    MutableAutomaton<Either<?, ?>, BuchiAcceptance> ldba =
      (MutableAutomaton) MutableAutomatonUtil.asMutable(new BreakpointAutomaton<>(ngba));
    AcceptanceOptimizations.removeDeadStates(ldba);
    return AutomatonUtil.LimitDeterministicGeneralizedBuchiAutomaton.of(ldba,
      ldba.states().stream().filter(x -> x.type() == Either.Type.LEFT).collect(toSet()));
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.HOA_INPUT_MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  static final class BreakpointAutomaton<S>
    extends TwoPartAutomaton<S, BreakpointState<S>, BuchiAcceptance>  {

    private final Automaton<S, ? extends GeneralizedBuchiAcceptance> ngba;
    private final List<Set<S>> sccs;
    private final int acceptanceSets;

    BreakpointAutomaton(Automaton<S, ? extends GeneralizedBuchiAcceptance> ngba) {
      this.ngba = ngba;
      this.sccs = SccDecomposition.of(ngba).sccsWithoutTransient();
      this.acceptanceSets = Math.max(ngba.acceptance().acceptanceSets(), 1);
    }

    @Override
    protected Set<S> initialStatesA() {
      return ngba.initialStates();
    }

    @Override
    protected Set<BreakpointState<S>> initialStatesB() {
      return Set.of();
    }

    @Override
    protected ValuationTree<Edge<S>> edgeTreeA(S state) {
      return ngba.edgeTree(state).map(
        x -> x.stream().map(Edge::withoutAcceptance).collect(toUnmodifiableSet()));
    }

    @Override
    protected ValuationTree<Edge<BreakpointState<S>>> edgeTreeB(BreakpointState<S> state) {
      ValuationSetFactory factory = factory();
      Map<Edge<BreakpointState<S>>, ValuationSet> labelledEdges = new HashMap<>();

      for (BitSet valuation : BitSets.powerSet(factory.alphabetSize())) {
        for (Edge<BreakpointState<S>> edge : edgesB(state, valuation)) {
          labelledEdges.merge(edge, factory.of(valuation), ValuationSet::union);
        }
      }

      return factory.inverse(labelledEdges);
    }

    @Override
    protected Set<Edge<BreakpointState<S>>> edgesB(BreakpointState<S> ldbaState, BitSet valuation) {
      Optional<Set<S>> optionalScc = sccs.stream().filter(x -> x.containsAll(ldbaState.mx()))
        .findAny();

      if (optionalScc.isEmpty()) {
        return Set.of();
      }

      Set<S> scc = optionalScc.get();
      Set<Edge<S>> outEdgesM = ldbaState.mx()
        .stream()
        .flatMap(x -> ngba.edges(x, valuation).stream())
        .filter(x -> scc.contains(x.successor()))
        .collect(toSet());

      if (outEdgesM.isEmpty()) {
        return Set.of();
      }

      Set<Edge<S>> outEdgesN = ldbaState.nx()
        .stream()
        .flatMap(x -> ngba.edges(x, valuation).stream())
        .filter(x -> scc.contains(x.successor()))
        .collect(toSet());

      Set<Edge<S>> intersection = outEdgesM.stream()
        .filter(x -> x.inSet(ldbaState.ix() % acceptanceSets)).collect(toSet());

      outEdgesN.addAll(intersection);

      Set<S> n1;
      int i1;

      if (outEdgesM.equals(outEdgesN)) {
        i1 = (ldbaState.ix() + 1) % acceptanceSets;
        n1 = Edges.successors(Sets.filter(outEdgesM, x -> x.inSet(i1)));
      } else {
        i1 = ldbaState.ix();
        n1 = Edges.successors(outEdgesN);
      }

      BreakpointState<S> successor = BreakpointState.of(i1, Edges.successors(outEdgesM), n1);
      return Set.of(
        i1 == 0 && outEdgesM.equals(outEdgesN) ? Edge.of(successor, 0) : Edge.of(successor));
    }

    @Override
    protected Set<BreakpointState<S>> moveAtoB(S state) {
      for (Set<S> scc : sccs) {
        if (scc.contains(state)) {
          return Set.of(BreakpointState.of(0, Set.of(state), Set.of()));
        }
      }

      return Set.of();
    }

    @Override
    public BuchiAcceptance acceptance() {
      return BuchiAcceptance.INSTANCE;
    }

    @Override
    public ValuationSetFactory factory() {
      return ngba.factory();
    }
  }
}
