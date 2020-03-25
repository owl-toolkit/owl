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

package owl.translations.dra2dpa;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.SingletonAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaWriter;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.run.PipelineException;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class IARBuilder<R> {
  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "dra2dpa",
    "Converts a Rabin automaton into a parity automaton",
    (commandLine, environment) -> OwlModule.AutomatonTransformer.of(
      automaton -> new IARBuilder<>(automaton).build(), RabinAcceptance.class));

  private static final Logger logger = Logger.getLogger(IARBuilder.class.getName());
  private final Automaton<R, RabinAcceptance> rabinAutomaton;
  private final MutableAutomaton<IARState<R>, ParityAcceptance> resultAutomaton;
  private final ValuationSetFactory vsFactory;

  public IARBuilder(Automaton<R, RabinAcceptance> rabinAutomaton) {
    this.rabinAutomaton = rabinAutomaton;
    vsFactory = rabinAutomaton.factory();
    ParityAcceptance acceptance = new ParityAcceptance(0, Parity.MIN_ODD);
    resultAutomaton = HashMapAutomaton.of(acceptance, vsFactory);
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.HOA_INPUT_MODULE,
      List.of(),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  public Automaton<IARState<R>, ParityAcceptance> build() {
    logger.log(Level.FINE, "Building IAR automaton with SCC decomposition");
    logger.log(Level.FINEST, () -> "Input automaton is\n" + HoaWriter.toString(rabinAutomaton));

    if (rabinAutomaton.initialStates().isEmpty()) {
      return EmptyAutomaton.of(
        rabinAutomaton.factory(),
        new ParityAcceptance(3, Parity.MIN_ODD));
    }

    List<RabinPair> rabinPairs = List.copyOf(rabinAutomaton.acceptance().pairs());
    if (rabinPairs.isEmpty()) {
      IARState<R> state = IARState.of(rabinAutomaton.initialStates().iterator().next());
      return SingletonAutomaton.of(rabinAutomaton.factory(), state,
        new ParityAcceptance(1, Parity.MIN_ODD), Set.of(0));
    }

    // Start analysis
    List<Set<R>> rabinSccs = SccDecomposition.of(rabinAutomaton).sccs();
    logger.log(Level.FINER, "Found {0} SCCs", rabinSccs.size());

    // TODO Threading once we have a thread safe BDD library
    CompletionService<SccProcessingResult<R>> completionService =
      new ExecutorCompletionService<>(MoreExecutors.directExecutor());

    // Start possibly parallel execution
    for (Set<R> rabinScc : rabinSccs) {
      completionService.submit(() -> processScc(rabinScc, rabinPairs));
    }

    // Wait till all executions are finished

    logger.log(Level.FINE, "Waiting for completion");
    ImmutableTable.Builder<R, Edge<R>, ValuationSet> interSccConnectionsBuilder =
      ImmutableTable.builder();
    int completedSccs = 0;
    int maximalSubAutomatonPriority = 0;
    while (completedSccs < rabinSccs.size()) {
      Future<SccProcessingResult<R>> currentResultFuture;
      try {
        logger.log(Level.FINE, "Waiting for completion");
        currentResultFuture = completionService.take();
      } catch (InterruptedException e) {
        // TODO Stop if environment.isStopped()
        logger.log(Level.FINE, "Interrupted", e);
        continue;
      }
      assert currentResultFuture.isDone();

      SccProcessingResult<R> result;
      try {
        result = Uninterruptibles.getUninterruptibly(currentResultFuture);
      } catch (ExecutionException e) {
        throw PipelineException.propagate(e);
      }

      OmegaAcceptance subAutomatonAcceptance = result.subAutomaton.acceptance();
      if (subAutomatonAcceptance instanceof ParityAcceptance) {
        maximalSubAutomatonPriority = Math.max(maximalSubAutomatonPriority,
          subAutomatonAcceptance.acceptanceSets() - 1);
      }

      interSccConnectionsBuilder.putAll(result.interSccConnections);
      MutableAutomatonUtil.copyInto(result.subAutomaton, resultAutomaton);
      completedSccs += 1;
    }

    Table<R, Edge<R>, ValuationSet> interSccConnections = interSccConnectionsBuilder.build();

    // Arbitrary correspondence map
    Map<R, IARState<R>> rabinToIarStateMap = Maps.newHashMapWithExpectedSize(rabinAutomaton.size());
    resultAutomaton.states().forEach(state -> rabinToIarStateMap.put(state.state(), state));
    assert Objects.equals(rabinToIarStateMap.keySet(), rabinAutomaton.states());

    logger.log(Level.FINE, "Connecting the SCCs");

    // Connect all SCCs back together
    resultAutomaton.states().forEach(iarState ->
      interSccConnections.row(iarState.state()).forEach((edge, valuations) -> {
        IARState<R> successor = rabinToIarStateMap.get(edge.successor());
        // TODO instead of 0 we should use any which is actually used
        resultAutomaton.addEdge(iarState, valuations, Edge.of(successor, 0));
      }));

    resultAutomaton.initialStates(rabinAutomaton.initialStates().stream()
      .map(rabinToIarStateMap::get)
      .collect(Collectors.toSet()));
    resultAutomaton.trim();

    int sets = maximalSubAutomatonPriority + 1;
    resultAutomaton.updateAcceptance(x -> x.withAcceptanceSets(sets));
    assert rabinSccs.size() == SccDecomposition.of(resultAutomaton).sccs().size();

    return resultAutomaton;
  }

  private SccProcessingResult<R> getTrivialSccResult(Set<R> simpleScc,
    Table<R, Edge<R>, ValuationSet> interSccConnections) {
    // TODO If it is bottom, we can just replace it by single rejecting state
    MutableAutomaton<IARState<R>, ParityAcceptance> resultTransitionSystem =
      HashMapAutomaton.of(new ParityAcceptance(1, Parity.MIN_ODD), vsFactory);

    for (R state : simpleScc) {
      IARState<R> iarState = IARState.of(state);
      // We ensure that the state is reachable and not removed from the automaton. We're not
      // interested in the language, only in the transition system!
      resultTransitionSystem.addInitialState(iarState);
      rabinAutomaton.edgeMap(state).forEach((edge, valuations) -> {
        if (simpleScc.contains(edge.successor())) {
          Edge<IARState<R>> iarEdge = Edge.of(IARState.of(edge.successor()), 0);
          resultTransitionSystem.addEdge(iarState, valuations, iarEdge);
        }
      });
    }

    return new SccProcessingResult<>(interSccConnections, resultTransitionSystem);
  }

  private SccProcessingResult<R> processScc(Set<R> scc, Collection<RabinPair> rabinPairs) {
    assert !rabinPairs.isEmpty();
    Set<RabinPair> remainingPairsToCheck = new HashSet<>(rabinPairs);
    ImmutableTable.Builder<R, Edge<R>, ValuationSet> interSccConnectionsBuilder =
      ImmutableTable.builder();

    AtomicBoolean sccHasALoop = new AtomicBoolean(false);
    AtomicBoolean seenAnyInfSet = new AtomicBoolean(false);
    AtomicBoolean seenAllInfSets = new AtomicBoolean(false);

    // Analyse the SCC
    // TODO This could be done while doing Tarjan
    scc.forEach(state -> rabinAutomaton.edgeMap(state).forEach((edge, valuations) -> {
      if (scc.contains(edge.successor())) {
        // This transition is inside this scc
        sccHasALoop.lazySet(true);
        if (remainingPairsToCheck.removeIf(pair -> edge.inSet(pair.infSet()))) {
          seenAnyInfSet.lazySet(true);
          seenAllInfSets.lazySet(remainingPairsToCheck.isEmpty());
        }
      } else {
        // This transition leads outside the SCC
        interSccConnectionsBuilder.put(state, edge, valuations);
      }
    }));

    Table<R, Edge<R>, ValuationSet> interSccConnections = interSccConnectionsBuilder.build();

    if (!sccHasALoop.get()) {
      // SCC has no transition inside, it's a transient one
      checkState(scc.size() == 1);
      R transientSccState = scc.iterator().next();
      IARState<R> iarState = IARState.of(transientSccState);
      return new SccProcessingResult<>(interSccConnections,
        SingletonAutomaton.of(vsFactory, iarState, AllAcceptance.INSTANCE));
    }

    if (!seenAnyInfSet.get()) {
      // The SCC has some internal structure, but no transitions are relevant for acceptance
      return getTrivialSccResult(scc, interSccConnections);
    }

    Set<RabinPair> activeRabinPairs = seenAllInfSets.get()
      ? Set.copyOf(rabinPairs)
      : Set.copyOf(Sets.difference(Set.copyOf(rabinPairs), remainingPairsToCheck));

    // TODO This might access the factory in parallel... Maybe we can return a lazy-explore type
    // of automaton that can be evaluated by the main thread?
    R newInitialState = scc.iterator().next();
    var filtered = Views.filtered(rabinAutomaton,
      Views.Filter.of(Set.of(newInitialState), scc::contains));
    var subAutomaton = new SccIARBuilder<>(filtered, activeRabinPairs).build();
    return new SccProcessingResult<>(interSccConnections, subAutomaton);
  }

  static final class SccProcessingResult<R> {
    final Table<R, Edge<R>, ValuationSet> interSccConnections;
    final Automaton<IARState<R>, ?> subAutomaton;

    SccProcessingResult(Table<R, Edge<R>, ValuationSet> interSccConnections,
      Automaton<IARState<R>, ?> subAutomaton) {
      this.interSccConnections = ImmutableTable.copyOf(interSccConnections);
      this.subAutomaton = subAutomaton;
    }
  }
}