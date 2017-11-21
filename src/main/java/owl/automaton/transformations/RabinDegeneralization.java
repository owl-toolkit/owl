package owl.automaton.transformations;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.cli.CommandLine;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.GeneralizedRabinPair;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.RabinAcceptance.RabinPair;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetMapUtil;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.PipelineExecutionContext;
import owl.run.Transformer;
import owl.run.env.Environment;

public final class RabinDegeneralization implements Transformer {
  public static final TransformerSettings settings = new TransformerSettings() {
    @Override
    public Transformer create(CommandLine settings, Environment environment) {
      return new RabinDegeneralization();
    }

    @Override
    public String getDescription() {
      return "Converts a generalized rabin automaton into a regular one";
    }

    @Override
    public String getKey() {
      return "dgra2dra";
    }
  };

  private static final Logger logger = Logger.getLogger(RabinDegeneralization.class.getName());

  public static <S> MutableAutomaton<DegeneralizedRabinState<S>, RabinAcceptance> degeneralize(
    Automaton<S, GeneralizedRabinAcceptance> automaton) {
    // TODO parallel
    logger.log(Level.FINER, "De-generalising automaton with {0} states", automaton.stateCount());

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
      MutableAutomatonFactory
        .createMutableAutomaton(degeneralizedAcceptance, automaton.getFactory());

    // Build the transition structure for each SCC separately
    for (Set<S> scc : SccDecomposition.computeSccs(automaton, true)) {
      if (SccDecomposition.isTransient(automaton::getSuccessors, scc)) {
        // Transient SCCs never accept - ignore potential acceptance
        S state = Iterables.getOnlyElement(scc);
        assert !stateMap.containsKey(state);

        DegeneralizedRabinState<S> degeneralizedState =
          new DegeneralizedRabinState<>(state, IntArrays.EMPTY_ARRAY);
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
      IntSet indices = new IntAVLTreeSet();

      Automaton<S, ?> filtered = AutomatonFactory.filter(automaton, scc);
      filtered.forEachLabelledEdge((x, y, z) -> y.acceptanceSetStream().forEach(indices::add));

      IntList sccTrackedPairs = new IntArrayList(trackedPairsCount);
      Collections3.forEachIndexed(trackedPairs, (pairIndex, pair) -> {
        assert pair.hasInfinite();
        if (IntIterators.all(pair.infiniteIndexIterator(), indices::contains)) {
          sccTrackedPairs.add(pairIndex);
        }
      });

      int sccTrackedPairsCount = sccTrackedPairs.size();
      assert sccTrackedPairsCount <= trackedPairsCount;
      int[] awaitedIndices = new int[sccTrackedPairsCount];

      // Pick an arbitrary starting state for the exploration
      DegeneralizedRabinState<S> initialSccState =
        new DegeneralizedRabinState<>(Iterables.getFirst(scc, null), awaitedIndices);

      Set<DegeneralizedRabinState<S>> exploredStates =
        AutomatonUtil.exploreWithLabelledEdge(resultAutomaton, Set.of(initialSccState), state -> {
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
            Collections3.forEachIndexed(noInfPairs, (noInfIndex, pair) -> {
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

      List<Set<DegeneralizedRabinState<S>>> resultSccs = SccDecomposition.computeSccs(
        AutomatonFactory.filter((Automaton<DegeneralizedRabinState<S>, ?>) resultAutomaton,
          exploredStates), exploredStates, false);
      Set<DegeneralizedRabinState<S>> resultBscc = resultSccs.stream()
        .filter(resultScc -> SccDecomposition.isTrap(resultAutomaton, resultScc))
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

  @Override
  public Object transform(Object object, PipelineExecutionContext context) {
    checkArgument(object instanceof Automaton<?, ?>);
    Automaton<?, ?> automaton = (Automaton<?, ?>) object;
    checkArgument(automaton.getAcceptance() instanceof GeneralizedRabinAcceptance);
    //noinspection unchecked
    return degeneralize((Automaton<Object, GeneralizedRabinAcceptance>) automaton);
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
      hashCode = Arrays.hashCode(awaitedSets) ^ generalizedState.hashCode();
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
      return awaitedSets.length == 0
             ? String.format("{%s}", generalizedState)
             : String.format("{%s|%s}", generalizedState, Arrays.toString(awaitedSets));
    }
  }
}
