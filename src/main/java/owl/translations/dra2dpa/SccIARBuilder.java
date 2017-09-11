package owl.translations.dra2dpa;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Table;
import de.tum.in.naturals.IntPreOrder;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import owl.algorithms.SccAnalyser;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.RabinAcceptance.RabinPair;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.transformations.ParityUtil;

/**
 * Constructs the IAR parity automaton from the given Rabin automaton SCC.
 * <p>The appearance record tracks the relative age of interesting events - in our case the
 * occurring Fin sets of the Rabin condition. Since there might be multiple events happening at
 * once, the ordering is not encoded as a permutation (of {@code {1,..,n}}) but rather as total
 * pre-orders of {@code {1,..n}}.</p>
 * <p>The corresponding equivalence classes can be interpreted as "age classes" of events: An
 * element of the first class are of indistinguishable age, but all of them are younger than each
 * element of the next class and so on.</p>
 */
final class SccIARBuilder<R> {
  private static final Logger logger = Logger.getLogger(SccIARBuilder.class.getName());
  private final Table<R, IntPreOrder, IARState<R>> iarStates;
  private final RabinPair[] indexToPair;
  private final ImmutableSet<R> initialRabinStates;
  private final Automaton<R, RabinAcceptance> rabinAutomaton;
  private final ImmutableSet<R> restriction;
  private final MutableAutomaton<IARState<R>, ParityAcceptance> resultAutomaton;
  private final BitSet usedPriorities;

  private SccIARBuilder(Automaton<R, RabinAcceptance> rabinAutomaton,
    ImmutableSet<R> initialRabinStates, ImmutableSet<R> restriction,
    ImmutableSet<RabinPair> trackedPairs) {
    this.initialRabinStates = initialRabinStates;
    this.rabinAutomaton = rabinAutomaton;
    this.restriction = restriction;
    this.usedPriorities = new BitSet(trackedPairs.size() * 2);
    this.usedPriorities.set(0);

    iarStates = HashBasedTable.create(rabinAutomaton.getStates().size(),
      rabinAutomaton.getStates().size() * trackedPairs.size());
    indexToPair = new RabinPair[trackedPairs.size()];

    int pairIndex = 0;
    for (RabinPair trackedPair : trackedPairs) {
      indexToPair[pairIndex] = trackedPair;
      pairIndex += 1;
    }

    this.resultAutomaton =
      AutomatonFactory.createMutableAutomaton(new ParityAcceptance(0), rabinAutomaton.getFactory());
  }

  static <R> SccIARBuilder<R> from(Automaton<R, RabinAcceptance> rabinAutomaton,
    Set<R> initialStates,Set<R> restriction, Set<RabinPair> trackedPairs) {
    return new SccIARBuilder<>(rabinAutomaton, ImmutableSet.copyOf(initialStates),
      ImmutableSet.copyOf(restriction), ImmutableSet.copyOf(trackedPairs));
  }

  Automaton<IARState<R>, ParityAcceptance> build() {
    assert initialRabinStates.stream().noneMatch(this::isStateRestricted);
    logger.log(Level.FINE, "Building IAR automaton");

    Set<IARState<R>> initialStates = getInitialStates();
    resultAutomaton.setInitialStates(initialStates);
    logger.log(Level.FINEST, "Starting state space generation from {0}", initialStates);

    AutomatonUtil.exploreWithLabelledEdge(resultAutomaton, initialStates, state -> {
      logger.log(Level.FINER, "Computing successors of {0}", state);

      // Compute the successors of the current state
      R rabinState = state.getOriginalState();

      IntPreOrder currentRecord = state.getRecord();
      Collection<LabelledEdge<R>> rabinSuccessors = rabinAutomaton.getLabelledEdges(rabinState);

      // Iterate over each edge
      Set<LabelledEdge<IARState<R>>> successors = new HashSet<>(rabinSuccessors.size());
      for (LabelledEdge<R> rabinLabelledEdge : rabinSuccessors) {
        Edge<R> rabinEdge = rabinLabelledEdge.edge;
        R rabinSuccessor = rabinEdge.getSuccessor();
        if (isStateRestricted(rabinSuccessor)) {
          logger.log(Level.FINEST, "Successor {0} is prohibited, ignoring it", rabinSuccessor);
          continue;
        }
        Edge<IARState<R>> iarEdge = computeSuccessorEdge(currentRecord, rabinEdge);
        successors.add(new LabelledEdge<>(iarEdge, rabinLabelledEdge.valuations));
      }
      return successors;
    });

    assert checkGeneratedStates();

    optimizeSuccessorRecord();
    optimizeInitialStates();
    ParityUtil.minimizePriorities(resultAutomaton);

    int maximalUsedPriority = getMaximalUsedPriority();
    resultAutomaton.getAcceptance().setAcceptanceSets(maximalUsedPriority + 1);
    logger.log(Level.FINER, "Built automaton with {0} states and {1} priorities for input "
      + "automaton with {2} states and {3} pairs", new Object[] {
      resultAutomaton.getStates().size(), maximalUsedPriority, getRelevantStates().size(),
      numberOfTrackedPairs()});

    return resultAutomaton;
  }

  private boolean checkGeneratedStates() {
    Set<R> stateSet = getRelevantStates();
    Set<R> exploredRabinStates = resultAutomaton.getStates().stream()
      .map(IARState::getOriginalState)
      .collect(Collectors.toSet());
    assert stateSet.equals(exploredRabinStates) :
      String.format("Explored: %s%nExpected: %s", stateSet, exploredRabinStates);
    return true;
  }

  private Edge<IARState<R>> computeSuccessorEdge(IntPreOrder currentRecord, Edge<R> rabinEdge) {
    R rabinSuccessor = rabinEdge.getSuccessor();
    IntSet visitedFinSetIndices = new IntOpenHashSet();

    int classes = currentRecord.classes();
    int maximumPriority = classes * 2;
    int priority = maximumPriority;
    for (int currentClass = 0; currentClass < classes; currentClass++) {
      for (int rabinPairInClass : currentRecord.equivalenceClass(currentClass)) {
        RabinAcceptance.RabinPair rabinPair = indexToPair[rabinPairInClass];
        if (rabinPair.hasFinite() && rabinEdge.inSet(rabinPair.getFiniteIndex())) {
          visitedFinSetIndices.add(rabinPairInClass);
          priority = Math.min(priority, maximumPriority - 2 * currentClass - 2);
        } else if (rabinPair.hasInfinite() && rabinEdge.inSet(rabinPair.getInfiniteIndex())) {
          priority = Math.min(priority, maximumPriority - 2 * currentClass - 1);
        }
      }
    }

    usedPriorities.set(priority);

    IntPreOrder successorRecord = currentRecord.generation(visitedFinSetIndices);
    IARState<R> iarSuccessor = iarStates.row(rabinSuccessor)
      .computeIfAbsent(successorRecord, record -> IARState.active(rabinSuccessor, record));
    return Edges.create(iarSuccessor, priority);
  }

  private Set<IARState<R>> getInitialStates() {
    IntPreOrder initialRecord = IntPreOrder.coarsest(numberOfTrackedPairs());

    ImmutableSet.Builder<IARState<R>> initialStateBuilder = ImmutableSet.builder();
    for (R initialRabinState : initialRabinStates) {
      initialStateBuilder.add(IARState.active(initialRabinState, initialRecord));
    }
    return initialStateBuilder.build();
  }

  private int getMaximalUsedPriority() {
    return usedPriorities.length() - 1;
  }

  private Set<R> getRelevantStates() {
    return restriction;
  }

  private boolean isStateRestricted(R state) {
    return !restriction.contains(state);
  }

  private int numberOfTrackedPairs() {
    return indexToPair.length;
  }

  private void optimizeInitialStates() {
    /* Idea: Pick good initial permutations for the initial states and remove unreachable states */

    // Iterate in reverse topological order - the "best" SCCs should be further down the ordering
    List<Set<IARState<R>>> sccs = Lists.reverse(SccAnalyser.computeSccs(resultAutomaton));
    int rabinStateCount = getRelevantStates().size();

    // We want to find the "optimal" SCC for each initial state. If we find a maximal SCC, we remove
    // the state from the search.
    Set<R> initialStatesToSearch = new HashSet<>(initialRabinStates);
    // Map each initial rabin state to the candidate IAR state
    Map<R, IARState<R>> initialStateCandidate = new HashMap<>(initialRabinStates.size());

    // Loop data structures:
    // Set of all rabin states in the current SCC
    Set<R> rabinStatesInScc = new HashSet<>();
    // Candidates for the initial states in the current SCC
    Map<R, IARState<R>> potentialInitialStates = new HashMap<>();

    for (Set<IARState<R>> scc : sccs) {
      if (SccAnalyser.isTransient(resultAutomaton::getSuccessors, scc)) {
        continue;
      }
      rabinStatesInScc.clear();
      potentialInitialStates.clear();

      // Gather all the rabin states in this SCC and see which initial states could be mapped here
      for (IARState<R> sccState : scc) {
        R originalState = sccState.getOriginalState();
        rabinStatesInScc.add(originalState);
        if (initialStatesToSearch.contains(originalState)) {
          potentialInitialStates.put(originalState, sccState);
        }
      }

      if (potentialInitialStates.isEmpty()) {
        // This SCC does not contain any initial states - No point in continuing the search
        continue;
      }

      assert rabinStatesInScc.size() <= rabinStateCount;

      if (rabinStatesInScc.size() < rabinStateCount) {
        // Since we investigate an SCC in the Rabin automaton, we are guaranteed to find an SCC
        // containing all Rabin states. Hence, we can wait for the above case to occur
        continue;
      }

      // This definitely is the biggest SCC we can find (in terms of contained Rabin states)
      // -> We can stop the search for all contained initial states
      initialStateCandidate.putAll(potentialInitialStates);
      initialStatesToSearch.removeAll(potentialInitialStates.keySet());
      if (initialStatesToSearch.isEmpty()) {
        break;
      }
    }
    assert initialStateCandidate.size() == initialRabinStates.size();

    resultAutomaton.setInitialStates(initialStateCandidate.values());
    resultAutomaton.removeUnreachableStates();
  }

  private void optimizeSuccessorRecord() {
    /* Idea: The IAR records have a notion of "refinement". We now eliminate all states which are
     * refined by some other state. */

    // TODO: This could be done on-the-fly: While constructing the automaton, check for each
    // newly explored state if it refines / is refined by some state and act accordingly

    // In this table, we store for each pair (rabin state, record) the most refined state existing
    Table<R, IntPreOrder, IARState<R>> refinementTable = HashBasedTable.create();

    // Loop variables:
    // States for which we don't know where they are in the hierarchy
    Set<IARState<R>> unknownStates = new HashSet<>();
    // States which are not refined by any other state
    Set<IARState<R>> topElements = new HashSet<>();
    for (R rabinState : rabinAutomaton.getStates()) {
      assert topElements.isEmpty() && unknownStates.isEmpty();
      unknownStates.addAll(iarStates.row(rabinState).values());
      // Found refinements (mapping a particular record to its refining state)
      Map<IntPreOrder, IARState<R>> foundRefinements = refinementTable.row(rabinState);

      Iterator<IARState<R>> iterator = unknownStates.iterator();
      while (iterator.hasNext()) {
        IARState<R> currentState = iterator.next();
        IntPreOrder currentRecord = currentState.getRecord();
        iterator.remove();

        // First, see if this record is refined by a known top element
        Optional<IARState<R>> refiningTopStateOptional = topElements.parallelStream()
          .filter(state -> state.getRecord().refines(currentRecord)).findAny();
        if (refiningTopStateOptional.isPresent()) {
          // This state is refined by a top element - add it to the table and remove it from search
          // space
          foundRefinements.put(currentRecord, refiningTopStateOptional.get());
          continue;
        }

        // We did not find a existing refinement to attach to - search through all other records
        // Only search in the unrefined states - if this state would be refined by any state for
        // which a refinement was found already, we would have picked up that refinement in the
        // previous step
        Optional<IARState<R>> refiningUnknownStateOptional = unknownStates.parallelStream()
          .filter(state -> state.getRecord().refines(currentRecord)).findAny();

        if (refiningUnknownStateOptional.isPresent()) {
          IARState<R> refiningUnknownState = refiningUnknownStateOptional.get();
          foundRefinements.put(currentRecord, refiningUnknownState);
        } else {
          // This state is not refined by any other - it's a top element
          topElements.add(currentState);
        }
      }
      assert unknownStates.isEmpty();

      // All values of the refinement map should be top elements. But now, we might have
      // a -> b and b -> c in the table. Do a second pass to eliminate all such transitive entries.
      // Strategy: For each non-top value, search a top value which refines it (has to exist)

      Map<IntPreOrder, IARState<R>> refinementReplacement = new HashMap<>();
      for (Map.Entry<IntPreOrder, IARState<R>> refinementEntry :
        foundRefinements.entrySet()) {
        IARState<R> refiningState = refinementEntry.getValue();
        if (topElements.contains(refiningState)) {
          continue;
        }

        Optional<IARState<R>> refiningTopElement = topElements.parallelStream().filter(
          topElement -> topElement.getRecord().refines(refiningState.getRecord())).findAny();
        assert refiningTopElement.isPresent();
        refinementReplacement.put(refinementEntry.getKey(), refiningTopElement.get());
      }
      foundRefinements.putAll(refinementReplacement);

      topElements.clear();
    }

    // Now each refined state is mapped to a top refining state. Remap all edges to their refined
    // destination.

    if (refinementTable.isEmpty()) {
      logger.log(Level.FINE, "No refinements found");
    } else {
      if (logger.isLoggable(Level.FINEST)) {
        StringBuilder refinementTableStringBuilder = new StringBuilder("Refinements:\n");
        Iterator<R> rabinStateIterator = rabinAutomaton.getStates().iterator();
        while (rabinStateIterator.hasNext()) {
          R rabinState = rabinStateIterator.next();
          Map<IntPreOrder, IARState<R>> foundRefinements = refinementTable.row(rabinState);
          if (foundRefinements.isEmpty()) {
            continue;
          }
          refinementTableStringBuilder.append(rabinState).append(":\n");
          Multimap<IARState<R>, IntPreOrder> inverseRefinements =
            Multimaps.invertFrom(Multimaps.forMap(foundRefinements), ArrayListMultimap.create());
          Iterator<Map.Entry<IARState<R>, Collection<IntPreOrder>>> refinementIterator =
            inverseRefinements.asMap().entrySet().iterator();

          while (refinementIterator.hasNext()) {
            Map.Entry<IARState<R>, Collection<IntPreOrder>> entry = refinementIterator.next();
            refinementTableStringBuilder.append("  ").append(entry.getKey().getRecord())
              .append(" <- ");

            Iterator<IntPreOrder> refinementMappingIterator = entry.getValue().iterator();
            while (refinementMappingIterator.hasNext()) {
              IntPreOrder refinedRecord = refinementMappingIterator.next();
              refinementTableStringBuilder.append(refinedRecord);
              if (refinementMappingIterator.hasNext()) {
                refinementTableStringBuilder.append(' ');
              }
            }
            if (refinementIterator.hasNext()) {
              refinementTableStringBuilder.append('\n');
            }
          }
          if (rabinStateIterator.hasNext()) {
            refinementTableStringBuilder.append('\n');
          }
        }
        logger.log(Level.FINEST, refinementTableStringBuilder.toString());
        logger.log(Level.FINEST, "Automaton before refinement:\n{0}",
          AutomatonUtil.toHoa(resultAutomaton));
      }

      // Update initial states
      Set<IARState<R>> newInitialStates = new HashSet<>(initialRabinStates.size());
      for (IARState<R> initialState : resultAutomaton.getInitialStates()) {
        // For each initial state, pick its refinement (if there is any)
        IARState<R> refinedInitialState =
          refinementTable.get(initialState.getOriginalState(), initialState.getRecord());
        if (refinedInitialState == null) {
          newInitialStates.add(initialState);
        } else {
          newInitialStates.add(refinedInitialState);
        }
      }
      resultAutomaton.setInitialStates(newInitialStates);

      // Update edges
      resultAutomaton.remapEdges(resultAutomaton.getStates(), (state, edge) -> {
        // For each edge, pick the refined successor (if there is a refinement)
        IARState<R> successor = edge.getSuccessor();
        R originalSuccessorState = successor.getOriginalState();
        IARState<R> refinedSuccessor =
          refinementTable.get(originalSuccessorState, successor.getRecord());
        if (refinedSuccessor == null) {
          // This successor is a top state - don't change the edge
          return edge;
        }
        return Edges.create(refinedSuccessor, edge.acceptanceSetIterator());
      });

      // Remove stale states
      resultAutomaton.removeStates(state ->
        refinementTable.contains(state.getOriginalState(), state.getRecord()));

      logger.log(Level.FINEST, () -> String.format("Automaton after refinement:%n%s",
        AutomatonUtil.toHoa(resultAutomaton)));
    }
  }
}