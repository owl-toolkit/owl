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
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.immutables.value.Value;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.TwoPartAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.optimizations.AcceptanceOptimizations;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.Either;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.util.annotation.HashedTuple;

public final class NBA2LDBA implements Function<Automaton<?, ?>, Automaton<?, BuchiAcceptance>> {

  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("nba2ldba")
    .description("Converts a non-deterministic Büchi automaton into a limit-deterministic Büchi "
      + "automaton")
    .parser(settings -> environment
    -> (input, context) -> new NBA2LDBA().apply(AutomatonUtil.cast(input)))
    .build();

  @Override
  public Automaton<?, BuchiAcceptance> apply(Automaton<?, ?> automaton) {
    return applyLDBA(automaton).automaton();
  }

  public static LDBA<?> applyLDBA(Automaton<?, ?> automaton) {
    Automaton<Object, GeneralizedBuchiAcceptance> ngba;

    if (automaton.acceptance() instanceof AllAcceptance) {
      var allAutomaton = Views.createPowerSetAutomaton(automaton, AllAcceptance.INSTANCE, true);
      var castedAutomaton = AutomatonUtil.cast(allAutomaton, Object.class, AllAcceptance.class);
      ngba = Views.viewAs(castedAutomaton, GeneralizedBuchiAcceptance.class);
    } else if (automaton.acceptance() instanceof GeneralizedBuchiAcceptance) {
      ngba = AutomatonUtil.cast(automaton, Object.class, GeneralizedBuchiAcceptance.class);
    } else {
      throw new UnsupportedOperationException(automaton.acceptance() + " is unsupported.");
    }

    if (automaton.acceptance() instanceof BuchiAcceptance) {
      var nba = AutomatonUtil.cast(ngba, BuchiAcceptance.class);
      var initialComponentOptional = AutomatonUtil.ldbaSplit(nba);

      if (initialComponentOptional.isPresent()) {
        return LDBA.of(nba, initialComponentOptional.orElseThrow());
      }
    }

    var ldba = MutableAutomatonUtil.asMutable(new BreakpointAutomaton<>(ngba));
    AcceptanceOptimizations.removeDeadStates(ldba);
    return LDBA.of(ldba, ldba.states().stream().filter(Either::isLeft).collect(toSet()));
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("nba2ldba")
      .reader(InputReaders.HOA)
      .addTransformer(CLI)
      .writer(OutputWriters.HOA)
      .build());
  }

  @Value.Immutable
  @HashedTuple
  public abstract static class LDBA<S> {
    public abstract Automaton<S, BuchiAcceptance> automaton();

    public abstract Set<S> initialComponent();

    static <S> LDBA<S> of(Automaton<S, BuchiAcceptance> automaton, Set<S> initialComponent) {
      return LDBATuple.create(automaton, initialComponent);
    }
  }

  static final class BreakpointAutomaton<S>
    extends TwoPartAutomaton<S, BreakpointState<S>, BuchiAcceptance>  {

    private final Automaton<S, GeneralizedBuchiAcceptance> ngba;
    private final List<Set<S>> sccs;
    private final int acceptanceSets;

    BreakpointAutomaton(Automaton<S, GeneralizedBuchiAcceptance> ngba) {
      this.ngba = ngba;
      this.sccs = SccDecomposition.computeSccs(ngba, false);
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

      if (!optionalScc.isPresent()) {
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
