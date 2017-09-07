package owl.automaton.transformations;

import static owl.algorithms.SccAnalyser.computeSccs;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.concurrent.Immutable;
import owl.algorithms.SccAnalyser;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.TransitionUtil;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.GeneralizedRabinPair;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.RabinAcceptance.RabinPair;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.Lists2;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetMapUtil;

public final class RabinUtil {
  public static final int[] EMPTY_INTS = new int[0];
  private static final Logger logger = Logger.getLogger(RabinUtil.class.getName());

  private RabinUtil() {}

  public static <S> MutableAutomaton<DegeneralizedRabinState<S>, RabinAcceptance>
  fromGeneralizedRabin(Automaton<S, GeneralizedRabinAcceptance> automaton) {
    logger.log(Level.FINER, "De-generalising automaton with {0} states",
      automaton.stateCount());

    // Generalized Rabin pair condition is Fin & /\ Inf(i), if the big AND is empty, it's true.
    // This means the condition translates to "don't visit the Fin set". Hence, as long as a
    // transition is not contained in the fin set, it's a good transition. We don't need to
    // track that index at all then and can save some space.
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
    Collection<GeneralizedRabinPair> pairs = acceptance.getPairs();

    // Filter out the obviously irrelevant pairs
    List<GeneralizedRabinPair> trackedPairs = new ArrayList<>();
    List<GeneralizedRabinPair> noInfPairs = new ArrayList<>();

    pairs.forEach(pair -> {
      if (pair.hasInfinite()) {
        trackedPairs.add(pair);
      } else if (pair.hasFinite()) {
        noInfPairs.add(pair);
      }
    });

    // General setup, allocate used pairs, the result automaton, etc.
    int trackedPairsCount = trackedPairs.size();
    int rabinCount = trackedPairsCount + noInfPairs.size();
    RabinAcceptance degeneralizedAcceptance = new RabinAcceptance();
    RabinPair[] rabinPairs = new RabinPair[rabinCount];
    for (int i = 0; i < rabinPairs.length; i++) {
      rabinPairs[i] = degeneralizedAcceptance.createPair();
    }

    // Arbitrary correspondence map for each original state
    Map<S, DegeneralizedRabinState<S>> stateMap = new HashMap<>(automaton.stateCount());
    // Table containing all transient edges
    Table<DegeneralizedRabinState<S>, S, ValuationSet> transientEdgesTable =
      HashBasedTable.create();

    MutableAutomaton<DegeneralizedRabinState<S>, RabinAcceptance> resultAutomaton =
      AutomatonFactory.createMutableAutomaton(degeneralizedAcceptance, automaton.getFactory());

    // Build the transition structure for each SCC separately
    List<Set<S>> sccs = computeSccs(automaton, true);
    for (Set<S> scc : sccs) {
      if (SccAnalyser.isTransient(automaton::getSuccessors, scc)) {
        // Transient SCCs never accept - ignore potential acceptance
        S state = Iterables.getOnlyElement(scc);
        assert !stateMap.containsKey(state);

        DegeneralizedRabinState<S> degeneralizedState =
          new DegeneralizedRabinState<>(state, EMPTY_INTS);
        // This catches corner cases, where there are transient states with no successors
        resultAutomaton.addState(degeneralizedState);
        stateMap.put(state, degeneralizedState);

        Map<S, ValuationSet> successors = transientEdgesTable.row(degeneralizedState);
        automaton.getLabelledEdges(state).forEach(labelledEdge ->
          ValuationSetMapUtil.add(successors, labelledEdge));
        continue;
      }

      // Determine the pairs which can accept in this SCC (i.e. those which have all their Inf in
      // this SCC)
      IntSet activeIndices = new IntAVLTreeSet();
      TransitionUtil.forEachEdgeInSet(automaton::getEdges, scc, (state, edge) ->
        edge.acceptanceSetIterator().forEachRemaining((IntConsumer) activeIndices::add));

      IntList sccTrackedPairs = new IntArrayList(trackedPairsCount);
      Lists2.forEachIndexed(trackedPairs, (pairIndex, pair) -> {
        assert pair.hasInfinite();
        if (IntIterators.all(pair.infiniteIndexIterator(), activeIndices::contains)) {
          sccTrackedPairs.add(pairIndex);
        }
      });

      int sccTrackedPairsCount = sccTrackedPairs.size();
      assert sccTrackedPairsCount <= trackedPairsCount;
      int[] awaitedIndices = new int[sccTrackedPairsCount];

      // Pick an arbitrary starting state for the exploration
      S sccState = scc.iterator().next();
      DegeneralizedRabinState<S> initialSccState =
        new DegeneralizedRabinState<>(sccState, awaitedIndices);

      ImmutableSet<DegeneralizedRabinState<S>> initialStates = ImmutableSet.of(initialSccState);
      Set<DegeneralizedRabinState<S>> exploredStates =
        AutomatonUtil.exploreWithLabelledEdge(resultAutomaton, initialStates, state -> {
          S generalizedState = state.getGeneralizedState();
          Collection<LabelledEdge<S>> labelledEdges = automaton.getLabelledEdges(generalizedState);

          Map<S, ValuationSet> transientSuccessors = transientEdgesTable.row(state);
          Collection<LabelledEdge<DegeneralizedRabinState<S>>> successors =
            new ArrayList<>(labelledEdges.size());

          for (LabelledEdge<S> labelledEdge : labelledEdges) {
            Edge<S> edge = labelledEdge.getEdge();
            S generalizedSuccessor = edge.getSuccessor();
            if (!scc.contains(generalizedSuccessor)) {
              // This is a transient edge, add to the table and ignore it
              ValuationSetMapUtil.add(transientSuccessors, generalizedSuccessor,
                labelledEdge.valuations.copy());
              continue;
            }

            // The index of the next awaited inf set of each generalized pair in the successor
            int[] successorAwaitedIndices = new int[sccTrackedPairsCount];

            // The acceptance on this edge. If a the Fin set of a generalized pair is encountered on
            // the original edge, this edge will have the corresponding Fin bit set. If otherwise an
            // Inf-breakpoint is reached, i.e. the awaited indices wrapped around for a particular
            // generalized pair, the corresponding Inf index will be set.
            BitSet edgeAcceptance = new BitSet(rabinCount);

            // First handle the non-trivial case of pairs with Fin and Inf sets.
            for (int sccPairIndex = 0; sccPairIndex < sccTrackedPairsCount; sccPairIndex++) {
              int currentPairIndex = sccTrackedPairs.getInt(sccPairIndex);
              GeneralizedRabinPair currentGeneralizedRabinPair =
                trackedPairs.get(currentPairIndex);
              int awaitedInfSet = state.awaitedInfSet(sccPairIndex);

              if (currentGeneralizedRabinPair.hasFinite()
                && edge.inSet(currentGeneralizedRabinPair.getFiniteIndex())) {
                // We have seen the fin set, put this transition into the fin set and restart
                // the wait
                awaitedInfSet = 0;
                edgeAcceptance.set(rabinPairs[currentPairIndex].getFiniteIndex());
              } else {
                // We did not see the fin set, check which inf sets have been seen
                // Check all inf sets of the rabin pair, starting from the awaited index.
                int infiniteIndexCount = currentGeneralizedRabinPair.getInfiniteIndexCount();
                int currentInfNumber = awaitedInfSet;
                for (int i = 0; i < infiniteIndexCount; i++) {
                  currentInfNumber = (awaitedInfSet + i) % infiniteIndexCount;
                  int currentInfIndex =
                    currentGeneralizedRabinPair.getInfiniteIndex(currentInfNumber);
                  if (!edge.inSet(currentInfIndex)) {
                    break;
                  }
                  if (currentInfNumber == infiniteIndexCount - 1) {
                    // We reached a breakpoint and can add the transition to the inf set
                    RabinPair rabinPair = rabinPairs[currentPairIndex];
                    int infiniteIndex = rabinPair.getInfiniteIndex();
                    edgeAcceptance.set(infiniteIndex);
                  }
                }
                awaitedInfSet = currentInfNumber;
              }
              successorAwaitedIndices[sccPairIndex] = awaitedInfSet;
            }

            // Deal with sets which have no Fin set separately
            Lists2.forEachIndexed(noInfPairs, (noInfIndex, pair) -> {
              int currentPairIndex = trackedPairsCount + noInfIndex;
              RabinPair currentPair = rabinPairs[currentPairIndex];

              edgeAcceptance.set(pair.containsFinite(edge)
                ? currentPair.getFiniteIndex()
                : currentPair.getInfiniteIndex());
            });

            DegeneralizedRabinState<S> successor =
              new DegeneralizedRabinState<>(generalizedSuccessor, successorAwaitedIndices);
            ValuationSet valuations = labelledEdge.valuations.copy();
            successors.add(new LabelledEdge<>(Edges.create(successor, edgeAcceptance), valuations));
          }
          return successors;
        });

      List<Set<DegeneralizedRabinState<S>>> resultSccs = computeSccs(
        new FilteredAutomaton<>((Automaton<DegeneralizedRabinState<S>, ?>) resultAutomaton,
          exploredStates::contains), exploredStates, false);
      Set<DegeneralizedRabinState<S>> resultBscc = resultSccs.stream()
        .filter(resultScc -> SccAnalyser.isBscc(resultAutomaton, resultScc))
        .findAny().orElseThrow(AssertionError::new);

      resultAutomaton.removeStates(state ->
        exploredStates.contains(state) && !resultBscc.contains(state));
      resultBscc.forEach(state -> stateMap.putIfAbsent(state.getGeneralizedState(), state));
    }

    assert Objects.equals(stateMap.keySet(), automaton.getStates());

    // Add transient edges
    transientEdgesTable.rowMap().forEach((state, successors) ->
      successors.forEach((generalizedSuccessor, valuations) -> {
        DegeneralizedRabinState<S> successor = stateMap.get(generalizedSuccessor);
        resultAutomaton.addEdge(state, valuations, Edges.create(successor));
      }));
    // Free transient table
    transientEdgesTable.values().forEach(ValuationSet::free);

    // Set initial states
    Set<DegeneralizedRabinState<S>> initialStates = automaton.getInitialStates().stream()
      .map(stateMap::get).collect(Collectors.toSet());
    resultAutomaton.setInitialStates(initialStates);

    return resultAutomaton;
  }

  @Immutable
  public static class DegeneralizedRabinState<S> {
    private final int[] awaitedSets;
    private final S generalizedState;
    private final int hashCode;

    @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
    DegeneralizedRabinState(S generalizedState, int[] awaitedSets) {
      this.generalizedState = generalizedState;
      this.awaitedSets = awaitedSets;
      hashCode = Arrays.hashCode(awaitedSets) + generalizedState.hashCode() * 31;
    }

    int awaitedInfSet(int generalizedPairIndex) {
      return awaitedSets[generalizedPairIndex];
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof DegeneralizedRabinState)) {
        return false;
      }

      DegeneralizedRabinState<?> that = (DegeneralizedRabinState<?>) o;
      return generalizedState.equals(that.generalizedState)
        && Arrays.equals(awaitedSets, that.awaitedSets);
    }

    public S getGeneralizedState() {
      return generalizedState;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public String toString() {
      if (awaitedSets.length == 0) {
        return String.format("{%s}", generalizedState);
      }
      return String.format("{%s|%s}", generalizedState, Arrays.toString(awaitedSets));
    }
  }
}
