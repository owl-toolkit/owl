package owl.translations.dra2dpa;

import static com.google.common.base.Preconditions.checkState;
import static owl.automaton.AutomatonUtil.toHoa;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.algorithms.SccAnalyser;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.RabinAcceptance.RabinPair;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;

final class IARBuilder<R> {
  private static final Logger logger = Logger.getLogger(IARBuilder.class.getName());

  private final Automaton<R, RabinAcceptance> rabinAutomaton;
  private final MutableAutomaton<IARState<R>, ParityAcceptance> resultAutomaton;

  public IARBuilder(Automaton<R, RabinAcceptance> rabinAutomaton) {
    this.rabinAutomaton = rabinAutomaton;
    resultAutomaton =
      AutomatonFactory.createMutableAutomaton(new ParityAcceptance(0), rabinAutomaton.getFactory());
  }

  private ImmutableMultimap<R, LabelledEdge<R>> addSccsToResult(
    List<SccProcessingResult<R>> processingResults) {
    ImmutableMultimap.Builder<R, LabelledEdge<R>> interSccConnectionsBuilder =
      ImmutableMultimap.builder();
    for (SccProcessingResult<R> result : processingResults) {
      interSccConnectionsBuilder.putAll(result.getInterSccConnections());
      Automaton<IARState<R>, ParityAcceptance> subAutomaton = result.getSubAutomaton();
      resultAutomaton.addAll(subAutomaton);
      subAutomaton.free();
    }
    return interSccConnectionsBuilder.build();
  }

  public Automaton<IARState<R>, ParityAcceptance> build() throws ExecutionException {
    logger.log(Level.FINE, "Building IAR automaton with SCC decomposition");

    // TODO Support parallelism here
    ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();

    Set<RabinPair> rabinPairs = ImmutableSet.copyOf(rabinAutomaton.getAcceptance().getPairs());

    // Start analysis
    List<Set<R>> rabinSccs = SccAnalyser.computeSccs(rabinAutomaton);
    logger.log(Level.FINER, "Found {0} SCCs", rabinSccs.size());

    // Start possibly parallel execution
    Collection<ListenableFuture<SccProcessingResult<R>>> processingFutures =
      Lists.newArrayListWithExpectedSize(rabinSccs.size());
    for (Set<R> rabinScc : rabinSccs) {
      processingFutures.add(executorService.submit(() -> processScc(rabinScc, rabinPairs)));
    }

    // Wait till all executions are finished

    // TODO Start to put the results into the result automaton when any finishes instead of waiting
    // for all
    List<SccProcessingResult<R>> processingResults = null;
    ListenableFuture<List<SccProcessingResult<R>>> processingFuture
      = Futures.allAsList(processingFutures);
    while (processingResults == null) {
      try {
        processingResults = processingFuture.get();
      } catch (InterruptedException e) {
        logger.log(Level.FINE, "Interrupted", e);
      } catch (ExecutionException e) {
        executorService.shutdownNow();
        throw e;
      }
    }

    int maximalSubAutomatonPriority = getMaximalSubAutomatonPriority(processingResults);

    // Log results
    logger.log(Level.FINE, "Built all {0} sub-automata with maximal priority {1}",
      new Object[] {processingResults.size(), maximalSubAutomatonPriority});
    if (logger.isLoggable(Level.FINEST)) {
      processingResults.forEach(result ->
        logger.log(Level.FINEST, "{0}", toHoa(result.getSubAutomaton())));
    }

    ImmutableMultimap<R, LabelledEdge<R>> interSccConnections = addSccsToResult(processingResults);

    // Arbitrary correspondence map
    Map<R, IARState<R>> rabinToIarStateMap =
      Maps.newHashMapWithExpectedSize(rabinAutomaton.getStates().size());
    resultAutomaton.getStates().forEach(iarState ->
      rabinToIarStateMap.put(iarState.getOriginalState(), iarState));
    assert Objects.equals(rabinToIarStateMap.keySet(), rabinAutomaton.getStates());

    logger.log(Level.FINE, "Connecting the SCCs");

    // Connect all SCCs back together
    for (IARState<R> iarState : resultAutomaton.getStates()) {
      R rabinState = iarState.getOriginalState();
      ImmutableCollection<LabelledEdge<R>> labelledEdges = interSccConnections.get(rabinState);

      if (labelledEdges.isEmpty()) {
        // This state has no "outgoing" transitions
        continue;
      }
      for (LabelledEdge<R> labelledEdge : labelledEdges) {
        R successorState = labelledEdge.edge.getSuccessor();
        assert rabinToIarStateMap.containsKey(successorState) :
          String.format("No successor present for %s", successorState);
        IARState<R> iarSuccessor = rabinToIarStateMap.get(successorState);
        // TODO instead of 0 we should use any which is actually used
        Edge<IARState<R>> iarEdge = Edges.create(iarSuccessor, 0);
        resultAutomaton.addEdge(iarState, labelledEdge.valuations, iarEdge);
      }
    }

    ImmutableSet.Builder<IARState<R>> initialStateBuilder = ImmutableSet.builder();
    for (R initialRabinState : rabinAutomaton.getInitialStates()) {
      initialStateBuilder.add(rabinToIarStateMap.get(initialRabinState));
    }
    resultAutomaton.setInitialStates(initialStateBuilder.build());
    resultAutomaton.getAcceptance().setAcceptanceSets(maximalSubAutomatonPriority + 1);

    assert rabinSccs.size() == SccAnalyser.computeSccs(resultAutomaton).size();

    executorService.shutdown();
    return resultAutomaton;
  }

  private int getMaximalSubAutomatonPriority(List<SccProcessingResult<R>> processingResults) {
    int maximalSubAutomatonPriority = 0;
    for (SccProcessingResult<R> result : processingResults) {
      Automaton<IARState<R>, ParityAcceptance> subAutomaton = result.getSubAutomaton();
      maximalSubAutomatonPriority = Math.max(maximalSubAutomatonPriority,
        subAutomaton.getAcceptance().getAcceptanceSets() - 1);
    }
    return maximalSubAutomatonPriority;
  }

  private SccProcessingResult<R> getTrivialSccResult(Set<R> simpleScc,
    Multimap<R, LabelledEdge<R>> interSccConnections) {
    Map<R, IARState<R>> mapping = new HashMap<>(simpleScc.size());
    simpleScc.forEach(rabinState -> mapping.put(rabinState, IARState.trivial(rabinState)));

    MutableAutomaton<IARState<R>, ParityAcceptance> resultTransitionSystem =
      AutomatonFactory.createMutableAutomaton(new ParityAcceptance(1), rabinAutomaton.getFactory());

    for (Map.Entry<R, IARState<R>> iarStateEntry : mapping.entrySet()) {
      R rabinState = iarStateEntry.getKey();
      IARState<R> iarState = iarStateEntry.getValue();
      Collection<LabelledEdge<R>> successors = rabinAutomaton.getLabelledEdges(rabinState);

      for (LabelledEdge<R> labelledEdge : successors) {
        R successor = labelledEdge.edge.getSuccessor();
        IARState<R> successorIARState = mapping.get(successor);
        if (successorIARState == null) {
          // The transition leads outside of the SCC
          continue;
        }
        Edge<IARState<R>> iarEdge = Edges.create(successorIARState, 0);
        resultTransitionSystem.addEdge(iarState, labelledEdge.valuations, iarEdge);
      }
    }
    // Arbitrary initial state to have nice logging
    resultTransitionSystem.setInitialState(resultTransitionSystem.getStates().iterator().next());

    return new SccProcessingResult<>(interSccConnections, resultTransitionSystem);
  }

  private SccProcessingResult<R> processScc(Set<R> scc, Set<RabinPair> rabinPairs) {
    Set<RabinPair> remainingPairsToCheck = new HashSet<>(rabinPairs);
    ImmutableMultimap.Builder<R, LabelledEdge<R>> interSccConnectionsBuilder =
      ImmutableMultimap.builder();

    boolean sccHasALoop = false;
    boolean seenAnyInfSet = false;
    boolean seenAllInfSets = false;

    // Analyse the SCC
    // TODO This could be done while doing Tarjan
    for (R state : scc) {
      for (LabelledEdge<R> labelledEdge : rabinAutomaton.getLabelledEdges(state)) {
        Edge<R> edge = labelledEdge.edge;
        if (scc.contains(edge.getSuccessor())) {
          // This transition is inside this scc
          sccHasALoop = true;
          if (seenAllInfSets) {
            continue;
          }
          // Check which of the Inf sets is active here
          Iterator<RabinPair> iterator = remainingPairsToCheck.iterator();
          while (iterator.hasNext()) {
            RabinPair pair = iterator.next();
            if (pair.containsInfinite(edge)) {
              // There is some Inf set active, remove it from the awaited list
              iterator.remove();
              seenAnyInfSet = true;
            }
          }
          // When remaining is create, have seen all sets
          seenAllInfSets = remainingPairsToCheck.isEmpty();
        } else {
          // This transition leads outside the SCC
          interSccConnectionsBuilder.put(state, labelledEdge);
        }
      }
    }
    ImmutableMultimap<R, LabelledEdge<R>> interSccConnections = interSccConnectionsBuilder.build();

    if (!sccHasALoop) {
      // SCC has no transition inside, it's a transient one
      checkState(scc.size() == 1);
      R transientSccState = scc.iterator().next();
      IARState<R> iarState = IARState.trivial(transientSccState);
      // TODO Single-state automaton class
      MutableAutomaton<IARState<R>, ParityAcceptance> subAutomaton = AutomatonFactory
        .createMutableAutomaton(new ParityAcceptance(0), rabinAutomaton.getFactory());
      subAutomaton.addState(iarState);
      subAutomaton.setInitialState(iarState);
      return new SccProcessingResult<>(interSccConnections, subAutomaton);
    }

    if (!seenAnyInfSet) {
      // The SCC has some internal structure, but no transitions are relevant for
      // acceptance
      return getTrivialSccResult(scc, interSccConnections);
    }

    Set<RabinPair> activeRabinPairs;
    if (seenAllInfSets) {
      activeRabinPairs = ImmutableSet.copyOf(rabinPairs);
    } else {
      activeRabinPairs = ImmutableSet.copyOf(Sets.difference(rabinPairs, remainingPairsToCheck));
    }
    R initialState = scc.iterator().next();
    Automaton<IARState<R>, ParityAcceptance> subAutomaton = SccIARBuilder.from(rabinAutomaton,
      ImmutableSet.of(initialState), scc, activeRabinPairs).build();
    return new SccProcessingResult<>(interSccConnections, subAutomaton);
  }

  private static final class SccProcessingResult<R> {
    private final Multimap<R, LabelledEdge<R>> interSccConnections;
    private final Automaton<IARState<R>, ParityAcceptance> subAutomaton;

    SccProcessingResult(Multimap<R, LabelledEdge<R>> interSccConnections,
      Automaton<IARState<R>, ParityAcceptance> subAutomaton) {
      this.interSccConnections = interSccConnections;
      this.subAutomaton = subAutomaton;
    }

    private Multimap<R, LabelledEdge<R>> getInterSccConnections() {
      return interSccConnections;
    }

    private Automaton<IARState<R>, ParityAcceptance> getSubAutomaton() {
      return subAutomaton;
    }
  }
}