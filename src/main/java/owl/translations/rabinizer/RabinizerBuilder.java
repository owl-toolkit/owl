package owl.translations.rabinizer;

import static owl.automaton.AutomatonUtil.toHoa;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import de.tum.in.naturals.set.NatCartesianProductSet;
import de.tum.in.naturals.set.PowerSetIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.GeneralizedRabinPair;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetMapUtil;
import owl.factories.EquivalenceClassFactory;
import owl.factories.ValuationSetFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.util.IntBiConsumer;

/**
 * Central class handling the Rabinizer construction.
 *
 * @see owl.translations.rabinizer
 */
public class RabinizerBuilder {
  private static final MonitorState[] EMPTY = new MonitorState[0];
  private static final Logger logger = Logger.getLogger(RabinizerBuilder.class.getName());

  private final RabinizerConfiguration configuration;
  private final EquivalenceClassFactory eqFactory;
  private final EquivalenceClass formula;
  private final MasterStateFactory masterStateFactory;
  private final ProductStateFactory productStateFactory;
  private final ValuationSetFactory vsFactory;

  RabinizerBuilder(RabinizerConfiguration configuration, EquivalenceClass formula) {
    this.configuration = configuration;
    this.formula = formula;
    if (configuration.removeFormulaRepresentative()) {
      formula.freeRepresentative();
    }
    vsFactory = configuration.factories().valuationSetFactory;
    eqFactory = configuration.factories().equivalenceClassFactory;
    masterStateFactory = new MasterStateFactory(configuration.eager());
    productStateFactory = new ProductStateFactory(configuration.eager());
  }

  private static ValuationSet[][] computeMonitorPriorities(MonitorAutomaton[] monitors,
    MonitorState[] monitorStates, GSet activeSet) {
    int monitorCount = monitors.length;
    ValuationSet[][] monitorPriorities = new ValuationSet[monitorCount][];
    for (int relevantIndex = 0; relevantIndex < monitorCount; relevantIndex++) {
      MonitorState monitorState = monitorStates[relevantIndex];

      // Get the corresponding monitor for this gSet.
      Automaton<MonitorState, ParityAcceptance> subsetMonitor =
        monitors[relevantIndex].getAutomaton(activeSet);
      int monitorAcceptanceSets = subsetMonitor.getAcceptance().getAcceptanceSets();

      // Cache the priorities of the edge
      ValuationSet[] edgePriorities = new ValuationSet[monitorAcceptanceSets];
      for (LabelledEdge<MonitorState> monitorLabelledEdge :
        subsetMonitor.getLabelledEdges(monitorState)) {
        Edge<MonitorState> monitorEdge = monitorLabelledEdge.getEdge();
        if (!monitorEdge.hasAcceptanceSets()) {
          continue;
        }
        int priority = monitorEdge.acceptanceSetIterator().nextInt();
        ValuationSet oldValuations = edgePriorities[priority];
        if (oldValuations == null) {
          // Need to free again later on
          edgePriorities[priority] = monitorLabelledEdge.valuations.copy();
        } else {
          // This happens if the monitor has two different transitions but the same acceptance
          oldValuations.addAll(monitorLabelledEdge.valuations);
        }
      }
      monitorPriorities[relevantIndex] = edgePriorities;
    }
    return monitorPriorities;
  }

  private static RabinizerState emptyProductState(EquivalenceClass masterState) {
    return new RabinizerState(masterState, EMPTY);
  }

  private static int entries(boolean[] array) {
    int count = 0;
    for (boolean val : array) {
      if (val) {
        count += 1;
      }
    }
    return count;
  }

  private static void freeMonitorPriorities(ValuationSet[][] monitorPriorities) {
    for (int relevantIndex = 0; relevantIndex < monitorPriorities.length; relevantIndex++) {
      ValuationSet[] monitorPriority = monitorPriorities[relevantIndex];
      if (monitorPriority == null) {
        continue;
      }
      //noinspection AssignmentToNull
      monitorPriorities[relevantIndex] = null;
      for (ValuationSet priorityValuations : monitorPriority) {
        if (priorityValuations != null) {
          priorityValuations.free();
        }
      }
    }
  }

  private static String printOperatorSets(ActiveSet[] activeSets) {
    StringBuilder tableBuilder = new StringBuilder(60 + activeSets.length * 20);
    tableBuilder.append("Acceptance mapping (GSet -> Ranking -> Pair):");

    for (ActiveSet activeSet : activeSets) {
      GSet subset = activeSet.activeSet;

      tableBuilder.append("\n ").append(subset);
      Iterator<int[]> rankingIterator = activeSet.rankings.iterator();
      int index = 0;
      while (rankingIterator.hasNext()) {
        GeneralizedRabinPair pair = activeSet.getPairForRanking(index);
        tableBuilder.append("\n  ").append(RabinizerUtil.printRanking(rankingIterator.next()))
          .append(" -> ").append(pair);
        index += 1;
      }
    }

    return tableBuilder.toString();
  }

  public static MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> rabinize(Formula phi,
    RabinizerConfiguration configuration) {
    // TODO Check if the formula only has a single G
    // TODO Check for safety languages?

    logger.log(Level.FINE, "Creating rabinizer automaton for formula {0}", phi);
    EquivalenceClassFactory eqFactory = configuration.factories().equivalenceClassFactory;
    EquivalenceClass initialClass = eqFactory.createEquivalenceClass(phi);
    return new RabinizerBuilder(configuration, initialClass).build();
  }

  private MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> build() {
    // TODO Fully implement the computeAcceptance switch

    /* Build master automaton
     *
     * The master automaton models a simple transition system based on the global formula and keeps
     * track of how the formula evolves along the finite prefix seen so far. */

    EquivalenceClass initialClass = masterStateFactory.getInitialState(formula);

    Automaton<EquivalenceClass, AllAcceptance> masterAutomaton =
      MutableAutomatonFactory.createMutableAutomaton(new AllAcceptance(), vsFactory,
        ImmutableSet.of(initialClass), masterStateFactory::getMasterSuccessor, state -> null);
    logger.log(Level.FINEST, () -> String.format("Master automaton for %s:%n%s", initialClass,
      toHoa(masterAutomaton)));

    /* Determine the SCC decomposition and build the sub-automata separately.
     *
     * We determine which monitors are relevant for each SCC separately. For example, consider
     * the formula a & X G b | !a & X G c. Depending on whether a is true or false in the first
     * step we end up in the G b or G c SCC, respectively. Obviously, we do not need to track both
     * G operators in both SCCs. */
    MasterStatePartition masterStatePartition = MasterStatePartition.create(masterAutomaton);
    logger.log(Level.FINEST, masterStatePartition::toString);

    // Determine all relevant G sub-formulas in the partitions
    // TODO Here, we can perform skeleton analysis and, if a G set is not element of any set
    // after the analysis, we can remove it from the relevantSubFormulas set?

    int partitionSize = masterStatePartition.partitionSize();
    List<Set<GOperator>> partitionRelevantGList = new ArrayList<>(partitionSize);
    Set<GOperator> allRelevantGFormulas = new HashSet<>();
    for (Set<EquivalenceClass> stateSubset : masterStatePartition.partition) {
      ImmutableSet.Builder<GOperator> relevantSubFormulasBuilder = ImmutableSet.builder();
      stateSubset.forEach(state ->
        forEachRelevantSubFormula(state, relevantSubFormulasBuilder::add));

      ImmutableSet<GOperator> partitionRelevantSubFormulas = relevantSubFormulasBuilder.build();
      partitionRelevantGList.add(partitionRelevantSubFormulas);
      allRelevantGFormulas.addAll(partitionRelevantSubFormulas);
    }
    logger.log(Level.FINE, "Identified relevant sub-formulas: {0}", allRelevantGFormulas);

    // Assign arbitrary numbering to all relevant sub-formulas. Throughout the construction, we will
    // use this numbering to identify the sub-formulas.
    int numberOfGFormulas = allRelevantGFormulas.size();
    GOperator[] gFormulas = allRelevantGFormulas.toArray(new GOperator[numberOfGFormulas]);

    /* Build monitors for all formulas which are relevant somewhere. */
    MonitorAutomaton[] monitors = new MonitorAutomaton[numberOfGFormulas];
    Arrays.setAll(monitors, gIndex -> buildMonitor(gFormulas[gIndex]));

    /* Build the product
     *
     * To construct the acceptance, we do the following. For all tracked GSets |G| and all possible
     * rankings of the contained operators (i.e. all mappings from |G| to {1..n}, where n is the
     * number of rabin pairs in the corresponding monitor), we create an acceptance condition
     *
     *   M^|G|_r & AND_{G psi \in |G|} Acc^G_r(psi)
     *
     * where Acc^G_r(psi) is the rabin pair of the corresponding monitor (R(psi, |G|)) with number
     * r(psi). Since the monitors are stored as min odd parity automata, these are all edges with
     * a) even priority less than or equal to r(psi) / 2 (fin) and b) odd priority equal to
     * r(psi) / 2 + 1. Further, M^|G|_r is a co-Buchi condition on the master automaton edge,
     * requiring that finitely often the current master state may not be entailed by |G| and the
     * monitor states. */
    GeneralizedRabinAcceptance acceptance = new GeneralizedRabinAcceptance();
    Collection<Set<GOperator>> relevantSets = Sets.powerSet(allRelevantGFormulas);
    assert relevantSets.contains(ImmutableSet.<GOperator>of());

    // -1 since we handle |G| = {} separately
    int gSetCount = relevantSets.size() - 1;
    // Mapping (|G|, r) to their corresponding pair
    ActiveSet[] activeSets = new ActiveSet[gSetCount];
    int activeSetIndex = 0;
    for (Set<GOperator> subset : relevantSets) {
      if (subset.isEmpty()) {
        continue;
      }
      GSet gSet = new GSet(subset, eqFactory);
      activeSets[activeSetIndex] = ActiveSet.create(gFormulas, gSet, monitors, acceptance);
      activeSetIndex += 1;
    }

    MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> rabinizerAutomaton =
      MutableAutomatonFactory.createMutableAutomaton(acceptance, vsFactory);
    rabinizerAutomaton.setName(String.format("Automaton for %s", initialClass));

    // Process each subset separately
    ImmutableList<Set<EquivalenceClass>> partition = masterStatePartition.partition;
    for (int sccIndex = 0; sccIndex < partition.size(); sccIndex++) {
      // Preliminary work: Only some sub-formulas are relevant a particular SCC (consider again
      // the previous example of "a & X G b | !a & X G c"). While in the SCC, all indexing is done
      // relative to the list of G operators relevant in the SCC.
      Set<EquivalenceClass> scc = partition.get(sccIndex);
      Set<GOperator> relevantGFormulasSet = partitionRelevantGList.get(sccIndex);
      int relevantGFormulasCount = relevantGFormulasSet.size();

      boolean[] relevantFormulas = new boolean[numberOfGFormulas];
      int subsetRelevantIndex = 0;
      MonitorAutomaton[] sccMonitors = new MonitorAutomaton[relevantGFormulasCount];
      for (int gIndex = 0; gIndex < numberOfGFormulas; gIndex++) {
        boolean indexRelevant = relevantGFormulasSet.contains(gFormulas[gIndex]);
        relevantFormulas[gIndex] = indexRelevant;
        if (indexRelevant) {
          sccMonitors[subsetRelevantIndex] = monitors[gIndex];
          subsetRelevantIndex += 1;
        }
      }

      logger.log(Level.FINER, "Building product of subset {0}, relevant formulas: {1}",
        new Object[] {scc, relevantGFormulasSet});

      /* Simple part first: Compute evolution of the transition system. We only have to evolve
       * according to the product construction. */
      Map<RabinizerState, Map<RabinizerTransition, ValuationSet>> transitionSystem =
        exploreTransitionSystem(scc, masterAutomaton, sccMonitors);

      // TODO Some state space analysis / optimization is possible here?

      if (!configuration.computeAcceptance()) {
        transitionSystem.forEach((state, successors) ->
          createEdges(state, successors, rabinizerAutomaton));
        continue;
      }

      // We now explored the transition system of this SCC. Now, we have to generate the acceptance
      // condition. We determine the currently relevant formulas and then iterate over all possible
      // subsets.
      transitionSystem.forEach((state, successors) -> {
        // TODO Can we do skeleton analysis here, too?
        Set<GOperator> stateRelevantSubFormulas = new HashSet<>(numberOfGFormulas);
        forEachRelevantSubFormula(state.masterState, stateRelevantSubFormulas::add);
        logger.log(Level.FINEST, "Product transitions for {0}: {1}; relevant formulas: {2}",
          new Object[] {state, successors, stateRelevantSubFormulas});

        // We iterate over all (|G|, r) pairs which are relevant to this subset. We do not
        // need to add Fin transitions for subsets which are ignored here (i.e. all those which are
        // a strict superset of relevantSubFormulaSet): Since we do not add any Inf edge and any
        // pair belonging to a (|G|, r) pair with |G| != {} has at least on corresponding Inf set,
        // they implicitly will not accept in this SCC.

        PowerSetIterator activeSubFormulasIterator = new PowerSetIterator(relevantFormulas);
        boolean[] empty = activeSubFormulasIterator.next(); // Empty set is handled separately
        assert entries(empty) == 0;
        while (activeSubFormulasIterator.hasNext()) {
          boolean[] activeSubFormulas = activeSubFormulasIterator.next();
          ActiveSet activeSet =
            activeSets[activeSubFormulasIterator.currentIndex() - 1];
          GSet activeSubFormulasSet = activeSet.activeSet;

          // Pre-compute the monitor transition priorities, as they are independent of the ranking.
          // The first dimension of this matrix is the monitor index, the second the priority. To
          // determine which priority a particular valuation has for a monitor with index i, one
          // simply has to find a j such that priorities[i][j] contains the valuation.
          ValuationSet[][] monitorPriorities =
            computeMonitorPriorities(sccMonitors, state.monitorStates, activeSubFormulasSet);

          // Iterate over all possible rankings
          Iterator<int[]> rankingIterator = activeSet.rankings.iterator();
          int rankingIndex = -1;
          while (rankingIterator.hasNext()) {
            rankingIndex += 1;
            int[] ranking = rankingIterator.next();
            assert ranking.length == entries(activeSubFormulas);
            GeneralizedRabinPair pair = activeSet.getPairForRanking(rankingIndex);

            GSetRanking rankingPair = new GSetRanking(relevantFormulas, activeSubFormulas,
              activeSubFormulasSet, pair, ranking, eqFactory, monitorPriorities);

            // Check if the current master state is entailed by the current |G| and r pair
            if (!configuration.eager() && !rankingPair.monitorsEntail(state)) {
              // Bad transition for this (|G|, r) pair - all edges are Fin
              int finiteIndex = pair.getFiniteIndex();
              successors.forEach((transition, valuations) ->
                transition.addAcceptance(valuations, finiteIndex));
              continue;
            }

            // Now, check the priorities of the monitor edges. If an edge has a fail or merge(rank),
            // the overall edge is Fin, otherwise, the edge is Inf for all monitors which
            // succeed(rank).
            successors.forEach((transition, valuations) -> valuations.forEach(valuation -> {
              // N.B.: These valuations are a subset of the sensitive alphabet by construction.
              // Hence, we again don't create the full valuation set.

              ValuationSet edgeValuation = vsFactory.createValuationSet(valuation);
              if (configuration.eager() && !rankingPair.monitorsEntailEager(state, valuation)) {
                transition.addAcceptance(edgeValuation, pair.getFiniteIndex());
              } else {
                transition.addAcceptance(edgeValuation, rankingPair.getAcceptance(valuation));
              }
              edgeValuation.free();
            }));
          }

          // Free the priority cache
          freeMonitorPriorities(monitorPriorities);
        }

        // Create the edges in the result automaton. The successors now contain for each edge in the
        // product system the partition of the sensitive alphabet according to the acceptance -
        // exactly what we need to create edges.
        createEdges(state, successors, rabinizerAutomaton);
        successors.clear();
      });
    }

    // Now, we need to take care of connecting the partitioned state space, set the initial state
    // and free all used temporary resources

    // Which rabinizer states belong to which master state
    Multimap<EquivalenceClass, RabinizerState> statesPerClass = HashMultimap.create();
    rabinizerAutomaton.getStates().forEach(state -> statesPerClass.put(state.masterState, state));
    masterStatePartition.transientStates.forEach(state ->
      statesPerClass.put(state, emptyProductState(state)));

    Function<EquivalenceClass, RabinizerState> getAnyState = masterState ->
      masterStatePartition.transientStates.contains(masterState)
        ? emptyProductState(masterState)
        : statesPerClass.get(masterState).iterator().next();

    // For each edge A -> B between SCCs in the master, connect all states of the product system
    // with A as master state to an arbitrary state with B as master state
    masterStatePartition.partitionOutgoingTransitions.rowMap().forEach(
      (masterState, masterSuccessors) -> {
        assert !masterSuccessors.isEmpty();

        Collection<RabinizerState> rabinizerStates = statesPerClass.get(masterState);
        masterSuccessors.forEach((masterSuccessor, valuations) -> {
          assert !masterSuccessor.isFalse();

          RabinizerState rabinizerSuccessor = getAnyState.apply(masterSuccessor);
          Edge<RabinizerState> edge = Edges.create(rabinizerSuccessor);
          rabinizerStates.forEach(state -> rabinizerAutomaton.addEdge(state, valuations, edge));
        });
      });
    // Properly choose the initial state
    rabinizerAutomaton.setInitialState(getAnyState.apply(initialClass));

    // Handle the |G| = {} case
    RabinizerState trueState = emptyProductState(eqFactory.getTrue());
    if (rabinizerAutomaton.containsState(trueState)) {
      assert Objects.equals(Iterables.getOnlyElement(rabinizerAutomaton.getSuccessors(trueState)),
        trueState);

      GeneralizedRabinPair truePair = acceptance.createPair(1);
      rabinizerAutomaton.removeEdges(trueState, trueState);
      Edge<RabinizerState> edge = Edges.create(trueState, truePair.getInfiniteIndex(0));
      rabinizerAutomaton.addEdge(trueState, vsFactory.createUniverseValuationSet(), edge);
    }

    masterAutomaton.free();
    for (MonitorAutomaton monitor : monitors) {
      monitor.free();
    }
    for (ActiveSet activeSet : activeSets) {
      activeSet.activeSet.free();
    }

    // If the initial states of the monitors are not optimized, there might be unreachable states
    Set<RabinizerState> unreachableStates = rabinizerAutomaton.removeUnreachableStates();
    logger.log(Level.FINEST, "Removed unreachable states: {0}", unreachableStates);

    logger.log(Level.FINER, () -> String.format("Result:%n%s", toHoa(rabinizerAutomaton)));
    logger.log(Level.FINER, () -> printOperatorSets(activeSets));

    return rabinizerAutomaton;
  }

  private MonitorAutomaton buildMonitor(GOperator subFormula) {
    logger.log(Level.FINE, "Building monitor for sub-formula {0}", subFormula);

    EquivalenceClass formula = eqFactory.createEquivalenceClass(subFormula.operand);
    if (configuration.removeFormulaRepresentative()) {
      formula.freeRepresentative();
    }

    Set<GOperator> relevantOperators = new HashSet<>();
    forEachRelevantSubFormula(formula, relevantOperators::add);
    List<GSet> relevantGSets = Sets.powerSet(relevantOperators).stream()
      .map(gSet -> new GSet(gSet, eqFactory))
      .collect(Collectors.toList());

    MonitorAutomaton monitor = MonitorBuilder.create(formula, relevantGSets, vsFactory,
      configuration.eager());

    // Postprocessing and logging
    relevantGSets.forEach(GSet::free);
    logger.log(Level.FINER, () -> String.format("Monitor for %s:%n%s", subFormula, toHoa(monitor)));
    if (logger.isLoggable(Level.FINEST)) {
      monitor.getAutomata().forEach((set, automaton) -> logger.log(Level.FINEST,
        "For set {0}\n{1}", new Object[] {set, toHoa(automaton)}));
    }
    return monitor;
  }

  private void createEdges(RabinizerState state, Map<RabinizerTransition, ValuationSet>
    successors, MutableAutomaton<RabinizerState, ?> rabinizerAutomaton) {
    if (successors.isEmpty()) {
      // Needed for corner cases (states without outgoing transitions)
      rabinizerAutomaton.addState(state);
      return;
    }

    BitSet sensitiveAlphabet = new BitSet(vsFactory.getSize());
    productStateFactory.addSensitiveAlphabet(sensitiveAlphabet, state);
    successors.forEach((cache, valuations) -> {
      RabinizerState rabinizerSuccessor = cache.getRabinizerSuccessor();
      ValuationSet[] acceptanceCache = cache.getSuccessorAcceptance();

      for (BitSet valuation : valuations) {
        int acceptanceIndices = acceptanceCache.length;
        BitSet edgeAcceptanceSet = new BitSet(acceptanceIndices);

        // Gather all sets active for this valuation
        for (int acceptanceIndex = 0; acceptanceIndex < acceptanceIndices; acceptanceIndex++) {
          ValuationSet acceptanceValuations = acceptanceCache[acceptanceIndex];
          if (acceptanceValuations == null) {
            continue;
          }
          if (acceptanceValuations.contains(valuation)) {
            edgeAcceptanceSet.set(acceptanceIndex);
          }
        }

        // Create the edge
        Edge<RabinizerState> rabinizerEdge = Edges.create(rabinizerSuccessor, edgeAcceptanceSet);
        // Expand valuation to the full alphabet
        ValuationSet edgeValuation = vsFactory.createValuationSet(valuation, sensitiveAlphabet);
        // Add edge to result
        rabinizerAutomaton.addEdge(state, edgeValuation, rabinizerEdge);
      }

      for (ValuationSet cachedValuations : acceptanceCache) {
        if (cachedValuations != null) {
          cachedValuations.free();
        }
      }
      valuations.free();
    });
  }

  private Map<RabinizerState, Map<RabinizerTransition, ValuationSet>>
  exploreTransitionSystem(Set<EquivalenceClass> stateSubset,
    Automaton<EquivalenceClass, AllAcceptance> masterAutomaton, MonitorAutomaton[] monitors) {
    int relevantFormulaCount = monitors.length;
    EquivalenceClass subsetMasterInitialState = stateSubset.iterator().next();
    MonitorState[] subsetMonitorInitialStates = new MonitorState[relevantFormulaCount];
    for (int relevantIndex = 0; relevantIndex < relevantFormulaCount; relevantIndex++) {
      subsetMonitorInitialStates[relevantIndex] = monitors[relevantIndex].getInitialState();
    }

    RabinizerState initialState =
      new RabinizerState(subsetMasterInitialState, subsetMonitorInitialStates);

    // The distinct edges in the product transition graph
    Map<RabinizerState, Map<RabinizerTransition, ValuationSet>> transitionSystem =
      new HashMap<>();

    // BFS work list
    Set<RabinizerState> exploredStates =
      new HashSet<>(Collections.singletonList(initialState));
    Queue<RabinizerState> workQueue = new ArrayDeque<>(exploredStates);

    BitSet sensitiveAlphabet = new BitSet(vsFactory.getSize());
    while (!workQueue.isEmpty()) {
      RabinizerState currentState = workQueue.poll();
      assert currentState.monitorStates.length == relevantFormulaCount;
      productStateFactory.addSensitiveAlphabet(sensitiveAlphabet, currentState);

      assert !transitionSystem.containsKey(currentState);
      Map<RabinizerTransition, ValuationSet> rabinizerSuccessors = new HashMap<>();
      transitionSystem.put(currentState, rabinizerSuccessors);

      for (BitSet valuation : BitSets.powerSet(sensitiveAlphabet)) {
        // Get the edge in the master automaton
        Edge<EquivalenceClass> masterEdge =
          masterAutomaton.getEdge(currentState.masterState, valuation);
        if (masterEdge == null) {
          // A null master edge means the master automaton moves into the "ff" state - a sure
          // failure and we don't need to investigate further.
          continue;
        }
        EquivalenceClass masterSuccessor = masterEdge.getSuccessor();
        if (!stateSubset.contains(masterSuccessor)) {
          // The successor is not part of this partition
          continue;
        }

        // Evolve each monitor
        MonitorState[] monitorStates = currentState.monitorStates;
        MonitorState[] monitorSuccessors = new MonitorState[monitorStates.length];
        for (int relevantIndex = 0; relevantIndex < relevantFormulaCount; relevantIndex++) {
          MonitorState currentMonitorState = monitorStates[relevantIndex];
          assert currentMonitorState != null;
          MonitorAutomaton monitor = monitors[relevantIndex];
          monitorSuccessors[relevantIndex] = monitor.getSuccessor(currentMonitorState, valuation);
        }

        // Create product successor
        RabinizerState rabinizerSuccessor =
          new RabinizerState(masterSuccessor, monitorSuccessors);

        // Deliberately don't create the "full" valuation set here - this makes iteration faster
        // later on when determining edge acceptance, but we need to take care of free()ing.
        ValuationSetMapUtil.add(rabinizerSuccessors,
          new RabinizerTransition(rabinizerSuccessor),
          vsFactory.createValuationSet(valuation));

        // Update exploration queue
        if (exploredStates.add(rabinizerSuccessor)) {
          workQueue.add(rabinizerSuccessor);
        }
      }

      sensitiveAlphabet.clear();
    }
    return transitionSystem;
  }

  private void forEachRelevantSubFormula(EquivalenceClass equivalenceClass,
    Consumer<GOperator> action) {
    if (configuration.supportBasedRelevantFormulaAnalysis()) {
      RabinizerUtil.forEachSupportingSubFormula(equivalenceClass, action);
    } else {
      RabinizerUtil.forEachSubFormula(equivalenceClass, action);
    }
  }

  private static final class ActiveSet {
    final GSet activeSet;
    final Set<int[]> rankings;
    private final GeneralizedRabinPair[] rankingPairs;

    @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
    ActiveSet(GSet activeSet, Set<int[]> rankings, GeneralizedRabinPair[] rankingPairs) {
      this.rankings = rankings;
      this.activeSet = activeSet;
      this.rankingPairs = rankingPairs;
    }

    static ActiveSet create(GOperator[] operators, GSet subset, MonitorAutomaton[] monitors,
      GeneralizedRabinAcceptance acceptance) {
      int gCount = operators.length;
      int[] maximalRanks = new int[subset.size()];

      int relevantIndex = 0;
      for (int gIndex = 0; gIndex < gCount; gIndex++) {
        if (!subset.contains(operators[gIndex])) {
          continue;
        }

        // Get the corresponding monitor for this gSet.
        MonitorAutomaton monitor = monitors[gIndex];
        Automaton<MonitorState, ParityAcceptance> gSetMonitor = monitor.getAutomaton(subset);

        // Acceptance sets = n means priorities range from 0 to n-1 inclusive
        // When interpreting the parity automaton as Rabin automaton, each Rabin pair is
        // represented by two priorities.
        int acceptanceSets = gSetMonitor.getAcceptance().getAcceptanceSets();
        // TODO If acceptanceSets == 0, the monitor is monitoring an effectively false formula,
        // for example F(G a & !a) - how to handle this?
        maximalRanks[relevantIndex] = acceptanceSets == 0 ? 0 : (acceptanceSets - 1) / 2;
        relevantIndex += 1;
      }

      // Allocate the acceptance caches
      Set<int[]> rankings = new NatCartesianProductSet(maximalRanks);
      GeneralizedRabinPair[] rankingPairs = new GeneralizedRabinPair[rankings.size()];
      Arrays.setAll(rankingPairs, i -> acceptance.createPair(subset.size()));

      return new ActiveSet(subset, rankings, rankingPairs);
    }

    GeneralizedRabinPair getPairForRanking(int rankingIndex) {
      return rankingPairs[rankingIndex];
    }
  }

  private static final class GSetRanking {
    final GSet activeFormulaSet;
    final GeneralizedRabinPair pair;
    final int[] ranking;
    private final boolean[] activeFormulas;
    private final EquivalenceClassFactory eqFactory;
    private final ValuationSet[][] monitorPriorities;
    private final boolean[] relevantFormulas;

    @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
    GSetRanking(boolean[] relevantFormulas, boolean[] activeFormulas, GSet activeFormulaSet,
      GeneralizedRabinPair pair, int[] ranking, EquivalenceClassFactory eqFactory,
      ValuationSet[][] monitorPriorities) {
      assert activeFormulas.length == relevantFormulas.length;

      this.activeFormulaSet = activeFormulaSet;
      this.pair = pair;
      this.ranking = ranking;
      this.eqFactory = eqFactory;
      this.monitorPriorities = monitorPriorities;
      this.activeFormulas = activeFormulas;
      this.relevantFormulas = relevantFormulas;
    }

    private void forEachRelevantAndActive(IntBiConsumer action) {
      int relevantIndex = -1;
      int activeIndex = -1;
      for (int gIndex = 0; gIndex < activeFormulas.length; gIndex++) {
        if (!relevantFormulas[gIndex]) {
          continue;
        }
        relevantIndex += 1;
        if (!activeFormulas[gIndex]) {
          continue;
        }
        activeIndex += 1;
        action.accept(relevantIndex, activeIndex);
      }
    }

    IntSet getAcceptance(BitSet valuation) {
      // TODO Pre-compute into a Map<IntSet, ValuationSet>

      // Lazy allocate the acceptance set, often we don't need it
      @Nullable
      IntSet edgeAcceptanceSet = null;

      // Iterate over all enabled sub formulas and check acceptance for each monitor
      int relevantIndex = -1;
      int activeIndex = -1;
      for (int gIndex = 0; gIndex < activeFormulas.length; gIndex++) {
        if (!relevantFormulas[gIndex]) {
          continue;
        }
        relevantIndex += 1;
        if (!activeFormulas[gIndex]) {
          continue;
        }
        activeIndex += 1;

        // Find priority of monitor edge in cache
        int priority = -1;
        ValuationSet[] monitorEdgePriorities = monitorPriorities[relevantIndex];
        for (int i = 0; i < monitorEdgePriorities.length; i++) {
          ValuationSet priorityValuation = monitorEdgePriorities[i];
          if (priorityValuation == null) {
            continue;
          }
          if (priorityValuation.contains(valuation)) {
            priority = i;
            break;
          }
        }

        // Evaluate according to the priority
        if (priority == -1) {
          // Nothing happened
          continue;
        }
        if (priority == 0) {
          // This edge is fail or merge(0) - definitely Fin
          return IntSets.singleton(pair.getFiniteIndex());
        }
        int succeedPriority = 2 * ranking[activeIndex] + 1;
        if (priority > succeedPriority) {
          // Nothing relevant happened
          continue;
        }
        if (priority % 2 == 0) {
          // Merged at some lower rank
          return IntSets.singleton(pair.getFiniteIndex());
        }
        if (priority == succeedPriority) {
          // Succeeded at the current rank
          if (edgeAcceptanceSet == null) {
            edgeAcceptanceSet = new IntOpenHashSet();
          }
          edgeAcceptanceSet.add(pair.getInfiniteIndex(activeIndex));
        }
      }
      return edgeAcceptanceSet == null ? IntSets.EMPTY_SET : edgeAcceptanceSet;
    }

    boolean monitorsEntail(RabinizerState state) {
      return monitorsEntail(state.monitorStates, null, state.masterState);
    }

    private boolean monitorsEntail(MonitorState[] monitorStates, @Nullable BitSet valuation,
      EquivalenceClass consequent) {
      boolean eager = valuation != null;

      // Check the M^|G|_r condition for this particular edge. More precisely, we need to check if
      // the operators together with the monitor states with at least the given ranking imply the
      // consequent (i.e. the master state).  We can omit the conjunction of the active operators
      // for the antecedent, since we will inject it anyway

      // Use an array so we can modify it in the lambda
      EquivalenceClass[] antecedentArray = {eqFactory.getTrue()};
      forEachRelevantAndActive((relevantIndex, activeIndex) -> {
        MonitorState monitorState = monitorStates[relevantIndex];

        EquivalenceClass[] monitorStateRanking = monitorState.formulaRanking;
        int rank = ranking[activeIndex];
        for (int stateIndex = rank; stateIndex < monitorStateRanking.length; stateIndex++) {
          EquivalenceClass rankEntry = monitorStateRanking[stateIndex];
          EquivalenceClass state = eager ? rankEntry.temporalStep(valuation) : rankEntry;
          antecedentArray[0] = antecedentArray[0].andWith(state);
        }
      });
      EquivalenceClass antecedent = antecedentArray[0];

      if (eager) {
        // In the eager construction, we need to add some more knowledge to the antecedent
        antecedent = antecedent.andWith(activeFormulaSet.conjunction());
      }

      // Important: We need to inject the state of the G operators into the monitor states,
      // otherwise the assumption is too weak. This was an error in the first version of the
      // Rabinizer paper. For example, if the monitor states were [Ga | Fb] and the |G| = {}, we
      // must be able to "prove" Fb.
      // TODO Cached substitution
      // TODO Can we apply the substitution to the consequent, too?
      Function<Formula, Formula> strengthening = formula -> formula instanceof GOperator
        ? BooleanConstant.get(activeFormulaSet.contains(formula))
        : formula;
      EquivalenceClass strengthenedAntecedent = antecedent.substitute(strengthening);

      Function<Formula, Formula> weakening = formula ->
        formula instanceof GOperator && activeFormulaSet.contains(formula)
          ? BooleanConstant.TRUE
          : formula;

      EquivalenceClass testedConsequent = eager ? consequent.temporalStep(valuation) : consequent;
      EquivalenceClass weakenedConsequent = testedConsequent.substitute(weakening);

      boolean result = strengthenedAntecedent.implies(weakenedConsequent);

      if (logger.isLoggable(Level.FINEST)) {
        List<EquivalenceClass> activeMonitorStates = new ArrayList<>(activeFormulaSet.size());

        forEachRelevantAndActive((relevantIndex, activeIndex) -> {
          EquivalenceClass[] monitorStateRanking = monitorStates[relevantIndex].formulaRanking;
          int rank = ranking[activeIndex];
          if (rank <= monitorStateRanking.length) {
            List<EquivalenceClass> rankingList = Arrays.asList(monitorStateRanking);
            activeMonitorStates.addAll(rankingList.subList(rank, monitorStateRanking.length));
          }
        });
        String rankingString = RabinizerUtil.printRanking(ranking);
        String log = String.format("Subset %s, ranking %s, and monitor states %s (strengthened: "
            + "%s), valuation %s; entails %s: %s", activeFormulaSet, rankingString,
          activeMonitorStates, strengthenedAntecedent, valuation, testedConsequent, result);
        logger.log(Level.FINEST, log);
      }

      // Deliberately not calling the varargs free, since this part of the code is really hot
      antecedent.free();
      strengthenedAntecedent.free();
      weakenedConsequent.free();
      if (eager) {
        testedConsequent.free();
      }

      return result;
    }

    boolean monitorsEntailEager(RabinizerState state, BitSet valuation) {
      return monitorsEntail(state.monitorStates, valuation, state.masterState);
    }
  }
}
