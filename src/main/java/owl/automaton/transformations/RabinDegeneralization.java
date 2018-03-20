package owl.automaton.transformations;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.google.common.primitives.ImmutableIntArray;
import de.tum.in.naturals.Indices;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
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
import org.immutables.value.Value;
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
import owl.automaton.util.AnnotatedState;
import owl.collections.ValuationSet;
import owl.run.PipelineExecutionContext;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;
import owl.util.annotation.HashedTuple;

public final class RabinDegeneralization extends Transformers.SimpleTransformer {
  public static final RabinDegeneralization INSTANCE = new RabinDegeneralization();

  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("dgra2dra")
    .description("Converts a generalized Rabin automaton into a regular one")
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
    RabinPair[] rabinPairs = new RabinPair[rabinCount];
    for (int i = 0; i < rabinPairs.length; i++) {
      rabinPairs[i] = builder.add();
    }

    // Arbitrary correspondence map for each original state
    Map<S, DegeneralizedRabinState<S>> stateMap = new HashMap<>(automaton.size());
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

        DegeneralizedRabinState<S> degeneralizedState = DegeneralizedRabinState.of(state);
        // This catches corner cases, where there are transient states with no successors
        resultAutomaton.addState(degeneralizedState);
        stateMap.put(state, degeneralizedState);

        Map<S, ValuationSet> successors = transientEdgesTable.row(degeneralizedState);
        automaton.forEachLabelledEdge(state, (edge, valuations) ->
          successors.merge(edge.getSuccessor(), valuations, ValuationSet::union));
        continue;
      }

      // Determine the pairs which can accept in this SCC (i.e. those which have all their Inf in
      // this SCC)
      IntSet indices = new IntAVLTreeSet();

      Views.filter(automaton, scc).forEachLabelledEdge((state, edge, valuations) ->
        edge.acceptanceSetIterator().forEachRemaining((IntConsumer) indices::add));

      IntList sccTrackedPairs = new IntArrayList(trackedPairsCount);
      Indices.forEachIndexed(trackedPairs, (pairIndex, pair) -> {
        assert pair.hasInfSet();
        if (IntIterators.all(pair.infSetIterator(), indices::contains)) {
          sccTrackedPairs.add(pairIndex);
        }
      });

      assert sccTrackedPairs.size() <= trackedPairsCount;

      // Pick an arbitrary starting state for the exploration
      DegeneralizedRabinState<S> initialSccState =
        DegeneralizedRabinState.of(Iterables.get(scc, 0), new int[sccTrackedPairs.size()]);

      Set<DegeneralizedRabinState<S>> exploredStates =
        AutomatonUtil.exploreWithLabelledEdge(resultAutomaton, Set.of(initialSccState), state -> {
          S generalizedState = state.state();
          Collection<LabelledEdge<S>> labelledEdges = automaton.getLabelledEdges(generalizedState);

          Map<S, ValuationSet> transientSuccessors = transientEdgesTable.row(state);
          Collection<LabelledEdge<DegeneralizedRabinState<S>>> successors =
            new ArrayList<>(labelledEdges.size());

          for (LabelledEdge<S> labelledEdge : labelledEdges) {
            Edge<S> edge = labelledEdge.edge;
            S generalizedSuccessor = edge.getSuccessor();
            if (!scc.contains(generalizedSuccessor)) {
              // This is a transient edge, add to the table and ignore it
              transientSuccessors.merge(generalizedSuccessor, labelledEdge.valuations,
                ValuationSet::union);
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
              RabinPair currentPair = trackedPairs.get(currentPairIndex);
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
                  int currentInfIndex = currentPair.infSet(currentInfNumber);
                  if (!edge.inSet(currentInfIndex)) {
                    break;
                  }
                  if (currentInfNumber == infiniteIndexCount - 1) {
                    // We reached a breakpoint and can add the transition to the inf set
                    RabinPair rabinPair = rabinPairs[currentPairIndex];
                    int infiniteIndex = rabinPair.infSet();
                    edgeAcceptance.set(infiniteIndex);
                  }
                }
                awaitedInfSet = currentInfNumber;
              }
              successorAwaitedIndices[sccPairIndex] = awaitedInfSet;
            }

            // Deal with sets which have no Fin set separately
            Indices.forEachIndexed(noInfPairs, (noInfIndex, pair) -> {
              int currentPairIndex = trackedPairsCount + noInfIndex;
              RabinPair currentPair = rabinPairs[currentPairIndex];

              edgeAcceptance.set(edge.inSet(pair.finSet())
                ? currentPair.finSet()
                : currentPair.infSet());
            });

            DegeneralizedRabinState<S> successor =
              DegeneralizedRabinState.of(generalizedSuccessor, successorAwaitedIndices);
            successors.add(LabelledEdge.of(Edge.of(successor, edgeAcceptance),
              labelledEdge.valuations));
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
      resultBscc.forEach(state -> stateMap.putIfAbsent(state.state(), state));
    }

    assert Objects.equals(stateMap.keySet(), automaton.getStates());

    // Add transient edges
    transientEdgesTable.rowMap().forEach((state, successors) ->
      successors.forEach((generalizedSuccessor, valuations) -> {
        DegeneralizedRabinState<S> successor = stateMap.get(generalizedSuccessor);
        resultAutomaton.addEdge(state, valuations, Edge.of(successor));
      }));

    // Set initial states
    resultAutomaton.setInitialStates(automaton.getInitialStates().stream()
      .map(stateMap::get).collect(Collectors.toSet()));

    return resultAutomaton;
  }

  @Override
  public Object transform(Object object, PipelineExecutionContext context) {
    return degeneralize(AutomatonUtil.cast(object, GeneralizedRabinAcceptance.class));
  }

  @Value.Immutable
  @HashedTuple
  abstract static class DegeneralizedRabinState<S> implements AnnotatedState<S> {
    @Override
    public abstract S state();

    public abstract ImmutableIntArray awaitedSets();


    static <S> DegeneralizedRabinState<S> of(S state) {
      return DegeneralizedRabinStateTuple.create(state, ImmutableIntArray.of());
    }

    static <S> DegeneralizedRabinState<S> of(S state, int[] awaitedSets) {
      return DegeneralizedRabinStateTuple.create(state, ImmutableIntArray.copyOf(awaitedSets));
    }


    int awaitedInfSet(int generalizedPairIndex) {
      return awaitedSets().get(generalizedPairIndex);
    }

    @Override
    public String toString() {
      return awaitedSets().isEmpty()
        ? String.format("{%s}", state())
        : String.format("{%s|%s}", state(), awaitedSets());
    }
  }
}
