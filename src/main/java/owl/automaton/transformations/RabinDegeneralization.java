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
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.concurrent.Immutable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.Views;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetMapUtil;
import owl.run.PipelineExecutionContext;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;

public final class RabinDegeneralization extends Transformers.SimpleTransformer {
  public static final RabinDegeneralization INSTANCE = new RabinDegeneralization();

  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("dgra2dra")
    .description("Converts a generalized rabin automaton into a regular one")
    .parser(settings -> environment -> INSTANCE)
    .build();

  private static final Logger logger = Logger.getLogger(RabinDegeneralization.class.getName());

  public static <S> Automaton<?, RabinAcceptance> degeneralize(
    Automaton<S, ? extends GeneralizedRabinAcceptance> automaton) {
    if (automaton.getAcceptance() instanceof RabinAcceptance) {
      return AutomatonUtil.cast(automaton, RabinAcceptance.class);
    }

    // TODO parallel
    logger.log(Level.FINER, "De-generalising automaton with {0} states", automaton.size());

    // Generalized Rabin pair condition is Fin & /\ Inf(i), if the big AND is empty, it's true.
    // This means the condition translates to "don't visit the Fin set". Hence, as long as a
    // transition is not contained in the fin set, it's a good transition. We don't need to
    // track that index at all then and can save some space.
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();
    Collection<RabinPair> pairs = acceptance.getPairs();

    // Filter out the obviously irrelevant pairs
    List<RabinPair> trackedPairs = new ArrayList<>();
    List<RabinPair> noInfPairs = new ArrayList<>();

    pairs.forEach(pair -> {
      if (pair.hasInfSet()) {
        trackedPairs.add(pair);
      } else {
        noInfPairs.add(pair);
      }
    });

    // General setup, allocate used pairs, the result automaton, etc.
    int trackedPairsCount = trackedPairs.size();
    int rabinCount = trackedPairsCount + noInfPairs.size();
    RabinAcceptance.Builder builder = new RabinAcceptance.Builder();
    RabinAcceptance.RabinPair[] rabinPairs = new RabinAcceptance.RabinPair[rabinCount];
    for (int i = 0; i < rabinPairs.length; i++) {
      rabinPairs[i] = builder.add();
    }

    // Arbitrary correspondence map for each original state
    Map<S, DegeneralizedRabinState<S>> stateMap = new HashMap<>(automaton.getStates().size());
    // Table containing all transient edges
    Table<DegeneralizedRabinState<S>, S, ValuationSet> transientEdgesTable =
      HashBasedTable.create();

    MutableAutomaton<DegeneralizedRabinState<S>, RabinAcceptance> resultAutomaton =
      MutableAutomatonFactory.create(builder.build(), automaton.getFactory());

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
        automaton.getLabelledEdges(state).forEach(
          labelledEdge -> ValuationSetMapUtil
            .add(successors, labelledEdge.getEdge().getSuccessor(), labelledEdge.valuations));
        continue;
      }

      // Determine the pairs which can accept in this SCC (i.e. those which have all their Inf in
      // this SCC)
      IntSet indices = new IntAVLTreeSet();

      Views.filter(automaton, scc)
        .forEachLabelledEdge((x, y, z) -> y.acceptanceSetIterator()
          .forEachRemaining((IntConsumer) indices::add));

      IntList sccTrackedPairs = new IntArrayList(trackedPairsCount);
      Collections3.forEachIndexed(trackedPairs, (pairIndex, pair) -> {
        assert pair.hasInfSet();
        if (IntIterators.all(pair.infSetIterator(), indices::contains)) {
          sccTrackedPairs.add(pairIndex);
        }
      });

      assert sccTrackedPairs.size() <= trackedPairsCount;
      int[] awaitedIndices = new int[sccTrackedPairs.size()];

      // Pick an arbitrary starting state for the exploration
      DegeneralizedRabinState<S> initialSccState =
        new DegeneralizedRabinState<>(Iterables.getFirst(scc, null), awaitedIndices);

      Set<DegeneralizedRabinState<S>> exploredStates =
        AutomatonUtil.exploreWithLabelledEdge(resultAutomaton, Set.of(initialSccState), state -> {
          S generalizedState = state.generalizedState;
          Collection<LabelledEdge<S>> labelledEdges = automaton.getLabelledEdges(generalizedState);

          Map<S, ValuationSet> transientSuccessors = transientEdgesTable.row(state);
          Collection<LabelledEdge<DegeneralizedRabinState<S>>> successors =
            new ArrayList<>(labelledEdges.size());

          for (LabelledEdge<S> labelledEdge : labelledEdges) {
            Edge<S> edge = labelledEdge.edge;
            S generalizedSuccessor = edge.getSuccessor();
            if (!scc.contains(generalizedSuccessor)) {
              // This is a transient edge, add to the table and ignore it
              ValuationSetMapUtil.add(transientSuccessors, generalizedSuccessor,
                labelledEdge.valuations);
              continue;
            }

            // The index of the next awaited inf set of each generalized pair in the successor
            int[] successorAwaitedIndices = new int[sccTrackedPairs.size()];

            // The acceptance on this edge. If a the Fin set of a generalized pair is encountered on
            // the original edge, this edge will have the corresponding Fin bit set. If otherwise an
            // Inf-breakpoint is reached, i.e. the awaited indices wrapped around for a particular
            // generalized pair, the corresponding Inf index will be set.
            BitSet edgeAcceptance = new BitSet(rabinCount);

            // First handle the non-trivial case of pairs with Fin and Inf sets.
            for (int sccPairIndex = 0; sccPairIndex < sccTrackedPairs.size(); sccPairIndex++) {
              int currentPairIndex = sccTrackedPairs.getInt(sccPairIndex);
              RabinPair currentPair =
                trackedPairs.get(currentPairIndex);
              int awaitedInfSet = state.awaitedInfSet(sccPairIndex);

              if (edge.inSet(currentPair.finSet())) {
                // We have seen the fin set, put this transition into the fin set and restart
                // the wait
                awaitedInfSet = 0;
                edgeAcceptance.set(rabinPairs[currentPairIndex].finSet());
              } else {
                // We did not see the fin set, check which inf sets have been seen
                // Check all inf sets of the rabin pair, starting from the awaited index.
                int infiniteIndexCount = currentPair.infSetCount();
                int currentInfNumber = awaitedInfSet;
                for (int i = 0; i < infiniteIndexCount; i++) {
                  currentInfNumber = (awaitedInfSet + i) % infiniteIndexCount;
                  int currentInfIndex =
                    currentPair.infSet(currentInfNumber);
                  if (!edge.inSet(currentInfIndex)) {
                    break;
                  }
                  if (currentInfNumber == infiniteIndexCount - 1) {
                    // We reached a breakpoint and can add the transition to the inf set
                    RabinAcceptance.RabinPair rabinPair = rabinPairs[currentPairIndex];
                    int infiniteIndex = rabinPair.infSet();
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
              RabinAcceptance.RabinPair currentPair = rabinPairs[currentPairIndex];

              edgeAcceptance.set(edge.inSet(pair.finSet())
                                 ? currentPair.finSet()
                                 : currentPair.infSet());
            });

            DegeneralizedRabinState<S> successor =
              new DegeneralizedRabinState<>(generalizedSuccessor, successorAwaitedIndices);
            ValuationSet valuations = labelledEdge.valuations;
            successors.add(LabelledEdge.of(Edge.of(successor, edgeAcceptance), valuations));
          }
          return successors;
        });

      List<Set<DegeneralizedRabinState<S>>> resultSccs = SccDecomposition.computeSccs(
        Views.filter((Automaton<DegeneralizedRabinState<S>, ?>) resultAutomaton,
          exploredStates), exploredStates, false);
      Set<DegeneralizedRabinState<S>> resultBscc = resultSccs.stream()
        .filter(resultScc -> SccDecomposition.isTrap(resultAutomaton, resultScc))
        .findAny().orElseThrow(AssertionError::new);

      resultAutomaton.removeStates(state ->
        exploredStates.contains(state) && !resultBscc.contains(state));
      resultBscc.forEach(state -> stateMap.putIfAbsent(state.generalizedState, state));
    }

    assert Objects.equals(stateMap.keySet(), automaton.getStates());

    // Add transient edges
    transientEdgesTable.rowMap().forEach((state, successors) ->
      successors.forEach((generalizedSuccessor, valuations) -> {
        DegeneralizedRabinState<S> successor = stateMap.get(generalizedSuccessor);
        resultAutomaton.addEdge(state, valuations, Edge.of(successor));
      }));
    // Free transient table
    transientEdgesTable.values().forEach(valuationSet -> {
    });

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
    final int[] awaitedSets;
    final S generalizedState;
    final int hashCode;

    @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
    DegeneralizedRabinState(S generalizedState, int[] awaitedSets) { // NOPMD
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
