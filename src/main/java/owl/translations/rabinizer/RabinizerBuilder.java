package owl.translations.rabinizer;

import static owl.automaton.AutomatonUtil.toHoa;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import de.tum.in.naturals.set.NatCartesianProductIterator;
import de.tum.in.naturals.set.NatCartesianProductSet;
import de.tum.in.naturals.set.PowerSetIterator;
import it.unimi.dsi.fastutil.booleans.BooleanArrays;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.Builder;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetMapUtil;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Collector;
import owl.ltl.visitors.DefaultConverter;
import owl.ltl.visitors.PrintVisitor;
import owl.run.Environment;
import owl.translations.rabinizer.RabinizerStateFactory.MasterStateFactory;
import owl.translations.rabinizer.RabinizerStateFactory.ProductStateFactory;
import owl.util.IntBiConsumer;

/**
 * Central class handling the Rabinizer construction.
 *
 * @see owl.translations.rabinizer
 */
public class RabinizerBuilder {
  private static final MonitorAutomaton[] EMPTY_MONITORS = new MonitorAutomaton[0];
  private static final GOperator[] EMPTY_G_OPERATORS = new GOperator[0];

  static final Logger logger = Logger.getLogger(RabinizerBuilder.class.getName());

  private final RabinizerConfiguration configuration;
  private final EquivalenceClassFactory eqFactory;
  private final EquivalenceClass initialClass;
  private final MasterStateFactory masterStateFactory;
  private final ProductStateFactory productStateFactory;
  private final ValuationSetFactory vsFactory;

  RabinizerBuilder(RabinizerConfiguration configuration, Factories factories, Formula formula) {
    EquivalenceClass initialClass = factories.eqFactory.of(formula);

    this.configuration = configuration;
    this.initialClass = initialClass;
    boolean fairnessFragment = configuration.eager() && initialClass.testSupport(support ->
      Fragments.isInfinitelyOften(support) || Fragments.isAlmostAll(support));

    vsFactory = factories.vsFactory;
    eqFactory = factories.eqFactory;
    masterStateFactory = new MasterStateFactory(configuration.eager(),
      configuration.completeAutomaton(), fairnessFragment);
    productStateFactory = new ProductStateFactory(configuration.eager());
  }

  private static ValuationSet[][] computeMonitorPriorities(MonitorAutomaton[] monitors,
    MonitorState[] monitorStates, GSet activeSet) {
    int monitorCount = monitors.length;
    ValuationSet[][] monitorPriorities = new ValuationSet[monitorCount][];
    for (int relevantIndex = 0; relevantIndex < monitorStates.length; relevantIndex++) {
      MonitorState monitorState = monitorStates[relevantIndex];

      // Get the corresponding monitor for this gSet.
      Automaton<MonitorState, ParityAcceptance> monitor =
        monitors[relevantIndex].getAutomaton(activeSet);
      int monitorAcceptanceSets = monitor.getAcceptance().getAcceptanceSets();

      // Cache the priorities of the edge
      ValuationSet[] edgePriorities = new ValuationSet[monitorAcceptanceSets];

      monitor.forEachLabelledEdge(monitorState, (edge, valuations) -> {
        if (!edge.hasAcceptanceSets()) {
          return;
        }

        int priority = edge.smallestAcceptanceSet();
        assert priority == edge.largestAcceptanceSet();

        edgePriorities[priority] = edgePriorities[priority] == null
            ? valuations
            : valuations.union(edgePriorities[priority]);
      });

      monitorPriorities[relevantIndex] = edgePriorities;
    }
    return monitorPriorities;
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

  private static boolean isSuspendableScc(Set<EquivalenceClass> scc,
    Set<GOperator> relevantOperators) {
    // Check if we need external atoms for acceptance. To this end, we replace all relevant Gs
    // with true (note that formulas are in NNF, so setting everything to true is "maximal")
    // and then check if there are some atoms left which are not in any formula

    BitSet internalAtoms = Collector.collectAtoms(relevantOperators);

    for (EquivalenceClass state : scc) {
      EvaluateVisitor visitor = new EvaluateVisitor(relevantOperators, state);
      EquivalenceClass substitute = state.substitute(visitor);
      BitSet externalAtoms = Collector.collectAtoms(substitute.getSupport());
      // Check if external atoms are non-empty and disjoint.
      if (externalAtoms.isEmpty() || externalAtoms.intersects(internalAtoms)) {
        return false;
      }
    }

    return true;
  }

  private static String printOperatorSets(ActiveSet[] activeSets) {
    StringBuilder tableBuilder = new StringBuilder(60 + activeSets.length * 20);
    tableBuilder.append("Acceptance mapping (GSet -> Ranking -> Pair):");

    for (ActiveSet activeSet : activeSets) {
      GSet subset = activeSet.set;

      tableBuilder.append("\n ").append(subset);
      Iterator<int[]> rankingIterator = activeSet.rankings.iterator();
      int index = 0;
      while (rankingIterator.hasNext()) {
        RabinPair pair = activeSet.getPairForRanking(index);
        tableBuilder.append("\n  ").append(RabinizerUtil.printRanking(rankingIterator.next()))
          .append(" -> ").append(pair);
        index += 1;
      }
    }

    return tableBuilder.toString();
  }

  public static MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> rabinize(
    Formula phi, Factories factories, RabinizerConfiguration configuration,
    Environment env) {
    // TODO Check if the formula only has a single G
    // TODO Check for safety languages?

    String formulaString = PrintVisitor.toString(phi, factories.eqFactory.variables());
    logger.log(Level.FINE, "Creating rabinizer automaton for formula {0}", formulaString);
    MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> rabinizerAutomaton =
      new RabinizerBuilder(configuration, factories, phi).build();
    rabinizerAutomaton.setName("Rabinizer automaton for " + formulaString);
    return rabinizerAutomaton;
  }

  private MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> build() {
    // TODO Fully implement the computeAcceptance switch

    /* Build master automaton
     *
     * The master automaton models a simple transition system based on the global formula and keeps
     * track of how the formula evolves along the finite prefix seen so far. */

    EquivalenceClass initialClass = masterStateFactory.getInitialState(this.initialClass);

    // TODO Copy Rabinizer3.1 behaviour for computing the successors - there a partitioning of the
    // valuation space is computed.

    /*
    Map<EquivalenceClass, Map<EquivalenceClass, ValuationSet>> partitioning = new HashMap<>();

    BiFunction<EquivalenceClass, BitSet, Edge<EquivalenceClass>> successorFunction =
      (state, valuation) -> {
        Map<EquivalenceClass, ValuationSet> successorMap = partitioning
          .computeIfAbsent(state, s -> {
            Map<EquivalenceClass, ValuationSet> map = new HashMap<>();
            s.forEachAssignment((solution, support) -> {
              ValuationSet valuations = vsFactory.createValuationSet(solution, support);
              BitSet any = valuations.iterator().next();
              Edge<EquivalenceClass> successor = masterStateFactory.getMasterSuccessor(s, any);
              if (successor != null) {
                ValuationSetMapUtil.add(map, successor.getSuccessor(), valuations);
              }
            });
            return map;
          });
        EquivalenceClass first = ValuationSetMapUtil.findFirst(successorMap, valuation);
        if (first == null) {
          return null;
        }
        return Edges.create(first);
      };*/

    Automaton<EquivalenceClass, AllAcceptance> masterAutomaton =
      MutableAutomatonFactory.create(AllAcceptance.INSTANCE, vsFactory,
        Set.of(initialClass), masterStateFactory::getSuccessor,
        masterStateFactory::getClassSensitiveAlphabet);
    if (logger.isLoggable(Level.FINER)) {
      logger.log(Level.FINER, "Master automaton for {0}:\n{1}",
        new Object[] {this.initialClass, toHoa(masterAutomaton)});
    } else {
      logger.log(Level.FINE, "Master automaton for {0} has {1} states",
        new Object[] {this.initialClass, masterAutomaton.size()});
    }

    /* Determine the SCC decomposition of the master.
     *
     * We determine which monitors are relevant for each SCC separately. For example, consider
     * the formula a & X G b | !a & X G c. Depending on whether a is true or false in the first
     * step we end up in the G b or G c SCC, respectively. Obviously, we do not need to track both
     * G operators in both SCCs. */
    MasterStatePartition masterSccPartition = MasterStatePartition.create(masterAutomaton);
    logger.log(Level.FINER, masterSccPartition::toString);

    // Determine all relevant G sub-formulas in the partitions
    // TODO Here, we can perform skeleton analysis and, if a G set is not element of any set
    // after the analysis, we can remove it from the relevantSubFormulas set?

    int partitionSize = masterSccPartition.partitionSize();
    @SuppressWarnings({"unchecked", "rawtypes"})
    Set<GOperator>[] sccRelevantGList = new Set[partitionSize];

    Collections3.forEachIndexed(masterSccPartition.sccs, (index, stateSubset) ->
      sccRelevantGList[index] = stateSubset.stream()
        .map(this::relevantSubFormulas).flatMap(Collection::stream)
        .collect(ImmutableSet.toImmutableSet()));
    Set<GOperator> allRelevantGFormulas =
      Collections3.immutableUnion(Arrays.asList(sccRelevantGList));

    logger.log(Level.FINE, "Identified relevant sub-formulas: {0}", allRelevantGFormulas);

    // Assign arbitrary numbering to all relevant sub-formulas. Throughout the construction, we will
    // use this numbering to identify the sub-formulas.
    int numberOfGFormulas = allRelevantGFormulas.size();
    GOperator[] gFormulas = allRelevantGFormulas.toArray(EMPTY_G_OPERATORS);

    /* Build monitors for all formulas which are relevant somewhere.
     *
     * For each G which is relevant in some SCC we construct the respective monitor. The monitor
     * array is indexed by the same numbering as the gFormulas array.
     */

    // TODO We could detect effectively false G operators here (i.e. monitors never accept)
    // But this rarely happens
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
    boolean computeAcceptance = configuration.computeAcceptance();
    GeneralizedRabinAcceptance.Builder builder = new Builder();
    Set<Set<GOperator>> relevantSets = Sets.powerSet(allRelevantGFormulas);
    assert relevantSets.contains(Set.of());

    @Nullable
    ActiveSet[] activeSets;
    if (computeAcceptance) {
      // -1 since we handle |G| = {} separately
      int gSetCount = relevantSets.size() - 1;
      // Mapping (|G|, r) to their corresponding pair
      activeSets = new ActiveSet[gSetCount];
      int activeSetIndex = 0;
      for (Set<GOperator> subset : relevantSets) {
        if (subset.isEmpty()) {
          continue;
        }
        GSet gSet = new GSet(subset, eqFactory);
        activeSets[activeSetIndex] = ActiveSet.create(gFormulas, gSet, monitors, builder);
        activeSetIndex += 1;
      }
    } else {
      activeSets = null;
    }

    MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> rabinizerAutomaton =
      MutableAutomatonFactory.create(builder.build(), vsFactory);

    // Process each subset separately
    // TODO Parallel
    ImmutableList<Set<EquivalenceClass>> partition = masterSccPartition.sccs;
    for (int sccIndex = 0; sccIndex < partition.size(); sccIndex++) {
      // Preliminary work: Only some sub-formulas are relevant a particular SCC (consider again
      // the previous example of "a & X G b | !a & X G c"). While in the SCC, all indexing is done
      // relative to the list of G operators relevant in the SCC.
      Set<EquivalenceClass> scc = partition.get(sccIndex);
      Set<GOperator> sccRelevantOperators = sccRelevantGList[sccIndex];

      boolean suspendable = configuration.suspendableFormulaDetection()
        && isSuspendableScc(scc, sccRelevantOperators);

      boolean[] relevantFormulas;
      MonitorAutomaton[] sccMonitors;
      if (suspendable) {
        sccMonitors = EMPTY_MONITORS;
        relevantFormulas = BooleanArrays.EMPTY_ARRAY;
        logger.log(Level.FINE, "Suspending all Gs on SCC {0}", sccIndex);
      } else {
        relevantFormulas = new boolean[numberOfGFormulas];
        sccMonitors = new MonitorAutomaton[sccRelevantOperators.size()];
        int subsetRelevantIndex = 0;
        for (int gIndex = 0; gIndex < numberOfGFormulas; gIndex++) {
          boolean indexRelevant = sccRelevantOperators.contains(gFormulas[gIndex]);
          relevantFormulas[gIndex] = indexRelevant;
          if (indexRelevant) {
            sccMonitors[subsetRelevantIndex] = monitors[gIndex];
            subsetRelevantIndex += 1;
          }
        }
        logger.log(Level.FINE, "Building product of SCC {0}, size: {1}, formulas: {2}",
          new Object[] {sccIndex, scc.size(), sccRelevantOperators});
      }

      /* Simple part first: Compute evolution of the transition system. We only have to evolve
       * according to the product construction. */
      // TODO Maye make this an automaton?
      Map<RabinizerState, Map<RabinizerProductEdge, ValuationSet>> transitionSystem =
        exploreTransitionSystem(scc, masterAutomaton, sccMonitors);

      // TODO Some state space analysis / optimization is possible here?

      if (!computeAcceptance || sccRelevantOperators.isEmpty()) {
        transitionSystem.forEach((state, successors) ->
          createEdges(state, successors, rabinizerAutomaton));
        continue;
      }

      // We now explored the transition system of this SCC. Now, we have to generate the acceptance
      // condition. We determine the currently relevant formulas and then iterate over all possible
      // subsets.
      logger.log(Level.FINER, "Computing acceptance on SCC {0}", sccIndex);
      transitionSystem.forEach((state, successors) -> {
        // TODO Can we do skeleton analysis here, too?
        Set<GOperator> stateRelevantSubFormulas = relevantSubFormulas(state.masterState);
        logger.log(Level.FINEST, "Product transitions for {0}: {1}; relevant formulas: {2}",
          new Object[] {state, successors, stateRelevantSubFormulas});

        // We iterate over all (|G|, r) pairs which are relevant to this subset. We do not
        // need to add Fin transitions for subsets which are ignored here (i.e. all those which are
        // a strict superset of relevantSubFormulaSet): Since we do not add any Inf edge and any
        // pair belonging to a (|G|, r) pair with |G| != {} has at least on corresponding Inf set,
        // they implicitly will not accept in this SCC.

        BitSet sensitiveAlphabet = productStateFactory.getSensitiveAlphabet(state);
        PowerSetIterator activeSubFormulasIterator = new PowerSetIterator(relevantFormulas);
        boolean[] empty = activeSubFormulasIterator.next(); // Empty set is handled separately
        assert entries(empty) == 0;

        while (activeSubFormulasIterator.hasNext()) {
          boolean[] activeSubFormulas = activeSubFormulasIterator.next();
          ActiveSet activeSet = activeSets[activeSubFormulasIterator.currentIndex() - 1];
          GSet activeSubFormulasSet = activeSet.set;

          // Pre-compute the monitor transition priorities, as they are independent of the ranking.
          // The first dimension of this matrix is the monitor index, the second the priority. To
          // determine which priority a particular valuation has for a monitor with index i, one
          // simply has to find a j such that priorities[i][j] contains the valuation. Note that
          // thus it is guaranteed that for each i priorities[i][j] are disjoint for all j.
          ValuationSet[][] monitorPriorities =
            computeMonitorPriorities(sccMonitors, state.monitorStates, activeSubFormulasSet);

          // Iterate over all possible rankings
          Iterator<int[]> rankingIterator = activeSet.rankings.iterator();
          int rankingIndex = -1;
          while (rankingIterator.hasNext()) {
            rankingIndex += 1;
            int[] ranking = rankingIterator.next();
            assert ranking.length == entries(activeSubFormulas);
            RabinPair pair = activeSet.getPairForRanking(rankingIndex);

            GSetRanking rankingPair = new GSetRanking(relevantFormulas, activeSubFormulas,
              activeSubFormulasSet, pair, ranking, eqFactory, monitorPriorities);

            // Check if the current master state is entailed by the current |G| and r pair
            if (!configuration.eager() && !rankingPair.monitorsEntail(state)) {
              // Bad transition for this (|G|, r) pair - all edges are Fin
              int finiteIndex = pair.finSet();
              successors.forEach((transition, valuations) ->
                transition.addAcceptance(valuations, finiteIndex));
              continue;
            }

            // Now, check the priorities of the monitor edges. If an edge has a fail or merge(rank),
            // the overall edge is Fin, otherwise, the edge is Inf for all monitors which
            // succeed(rank).
            successors.forEach((transition, valuations) -> {
              // TODO Can we be even smarter here? This usually is the costliest part of the code
              // due to the call to monitors entail
              valuations.forEach(sensitiveAlphabet, valuation -> {
                ValuationSet edgeValuation =
                  vsFactory.of(valuation, sensitiveAlphabet);
                if (configuration.eager() && !rankingPair.monitorsEntailEager(state, valuation)) {
                  transition.addAcceptance(edgeValuation, pair.finSet());
                } else {
                  IntSet edgeAcceptance = rankingPair.getAcceptance(valuation);
                  transition.addAcceptance(edgeValuation, edgeAcceptance);
                }
              });
            });
          }
        }

        // Create the edges in the result automaton. The successors now contain for each edge in the
        // product system the partition of the sensitive alphabet according to the acceptance -
        // exactly what we need to create edges.
        createEdges(state, successors, rabinizerAutomaton);
        successors.clear();
      });
    }

    /* Now, we need to take care of connecting the partitioned state space, set the initial state
     * and free all used temporary resources.
     */

    logger.log(Level.FINE, "Connecting the SCCs");
    // Which rabinizer states belong to which master state
    Multimap<EquivalenceClass, RabinizerState> statesPerClass = HashMultimap.create();
    rabinizerAutomaton.forEachState(state -> statesPerClass.put(state.masterState, state));
    masterSccPartition.transientStates.forEach(state ->
      statesPerClass.put(state, RabinizerState.empty(state)));

    // CSOFF: Indentation
    Function<EquivalenceClass, RabinizerState> getAnyState =
      masterState -> masterSccPartition.transientStates.contains(masterState)
        ? RabinizerState.empty(masterState)
        : statesPerClass.get(masterState).iterator().next();
    // CSON: Indentation

    // For each edge A -> B between SCCs in the master, connect all states of the product system
    // with A as master state to an arbitrary state with B as master state
    masterSccPartition.outgoingTransitions.rowMap().forEach(
      (masterState, masterSuccessors) -> {
        assert !masterSuccessors.isEmpty();

        Collection<RabinizerState> rabinizerStates = statesPerClass.get(masterState);
        masterSuccessors.forEach((masterSuccessor, valuations) -> {
          assert configuration.completeAutomaton() || !masterSuccessor.isFalse();

          RabinizerState rabinizerSuccessor = getAnyState.apply(masterSuccessor);
          Edge<RabinizerState> edge = Edge.of(rabinizerSuccessor);
          rabinizerStates.forEach(state -> rabinizerAutomaton.addEdge(state, valuations, edge));
        });
      });
    // Properly choose the initial state
    rabinizerAutomaton.setInitialState(getAnyState.apply(initialClass));

    // Handle the |G| = {} case
    // TODO: Piggyback on an existing RabinPair.
    RabinizerState trueState = RabinizerState.empty(eqFactory.getTrue());
    if (rabinizerAutomaton.containsState(trueState)) {
      assert Objects.equals(Iterables.getOnlyElement(rabinizerAutomaton.getSuccessors(trueState)),
        trueState);

      RabinPair truePair = builder.add(1);
      rabinizerAutomaton.removeEdges(trueState, trueState);
      rabinizerAutomaton.addEdge(trueState, vsFactory.universe(),
        Edge.of(trueState, truePair.infSet()));
      rabinizerAutomaton.setAcceptance(builder.build());
    }

    // If the initial states of the monitors are not optimized, there might be unreachable states
    Set<RabinizerState> unreachableStates = rabinizerAutomaton.removeUnreachableStates();
    logger.log(Level.FINER, "Removed unreachable states: {0}", unreachableStates);
    logger.log(Level.FINER, () -> String.format("Result:%n%s", toHoa(rabinizerAutomaton)));
    if (activeSets != null) {
      logger.log(Level.FINER, () -> printOperatorSets(activeSets));
    }

    return rabinizerAutomaton;
  }

  private MonitorAutomaton buildMonitor(GOperator gOperator) {
    logger.log(Level.FINE, "Building monitor for sub-formula {0}", gOperator);

    EquivalenceClass operand = eqFactory.of(gOperator.operand);

    Set<GOperator> relevantOperators = relevantSubFormulas(operand);
    Set<Set<GOperator>> powerSets = Sets.powerSet(relevantOperators);
    List<GSet> relevantGSets = new ArrayList<>(powerSets.size());
    powerSets.forEach(gSet -> relevantGSets.add(new GSet(gSet, eqFactory)));

    MonitorAutomaton monitor = configuration.computeAcceptance()
      ? MonitorBuilder.create(gOperator, operand, relevantGSets, vsFactory, configuration.eager())
      : MonitorBuilderNoAcceptance.create(gOperator, operand, relevantGSets, vsFactory,
        configuration.eager());

    // Postprocessing and logging
    logger.log(Level.FINER, () -> String.format("Monitor for %s:%n%s", gOperator, toHoa(monitor)));
    if (logger.isLoggable(Level.FINEST)) {
      monitor.getAutomata().forEach((set, automaton) -> logger.log(Level.FINEST,
        "For set {0}\n{1}", new Object[] {set, toHoa(automaton)}));
    }
    return monitor;
  }

  private void createEdges(RabinizerState state, Map<RabinizerProductEdge, ValuationSet>
    successors, MutableAutomaton<RabinizerState, ?> rabinizerAutomaton) {
    if (successors.isEmpty()) {
      // Needed for corner cases (states without outgoing transitions)
      rabinizerAutomaton.addState(state);
      return;
    }

    BitSet sensitiveAlphabet = productStateFactory.getSensitiveAlphabet(state);
    successors.forEach((cache, valuations) -> {
      RabinizerState rabinizerSuccessor = cache.getRabinizerSuccessor();
      ValuationSet[] acceptanceCache = cache.getSuccessorAcceptance();

      valuations.forEach(sensitiveAlphabet, valuation -> {
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
        Edge<RabinizerState> rabinizerEdge = Edge.of(rabinizerSuccessor, edgeAcceptanceSet);
        // Expand valuation to the full alphabet
        ValuationSet edgeValuation = vsFactory.of(valuation, sensitiveAlphabet);
        // Add edge to result
        rabinizerAutomaton.addEdge(state, edgeValuation, rabinizerEdge);
      });
    });
  }

  private Map<RabinizerState, Map<RabinizerProductEdge, ValuationSet>>
  exploreTransitionSystem(Set<EquivalenceClass> stateSubset,
    Automaton<EquivalenceClass, ?> masterAutomaton, MonitorAutomaton[] monitors) {
    int relevantFormulaCount = monitors.length;
    EquivalenceClass masterInitialState = stateSubset.iterator().next();
    MonitorState[] monitorInitialStates = new MonitorState[relevantFormulaCount];
    Arrays.setAll(monitorInitialStates, i -> monitors[i].getInitialState());

    RabinizerState initialState = RabinizerState.of(masterInitialState, monitorInitialStates);

    // The distinct edges in the product transition graph
    Map<RabinizerState, Map<RabinizerProductEdge, ValuationSet>> transitionSystem = new HashMap<>();

    // BFS work list
    Set<RabinizerState> exploredStates = Sets.newHashSet(initialState);
    Queue<RabinizerState> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      RabinizerState currentState = workQueue.poll();
      logger.log(Level.FINEST, "Exploring {0}", currentState);
      assert currentState.monitorStates.length == relevantFormulaCount;
      assert !transitionSystem.containsKey(currentState);

      Map<EquivalenceClass, ValuationSet> masterSuccessors =
        masterAutomaton.getSuccessorMap(currentState.masterState);

      if (masterSuccessors.isEmpty()) {
        transitionSystem.put(currentState, Map.of());
        continue;
      }

      Map<RabinizerProductEdge, ValuationSet> rabinizerSuccessors = new HashMap<>();
      transitionSystem.put(currentState, rabinizerSuccessors);

      MonitorState[] monitorStates = currentState.monitorStates;

      // Compute the successor matrix for all monitors. Basically, we assign a arbitrary ordering
      // on all successors for each monitor.
      MonitorState[][] monitorSuccessorMatrix = new MonitorState[relevantFormulaCount][];
      ValuationSet[][] monitorValuationMatrix = new ValuationSet[relevantFormulaCount][];
      int[] successorCounts = new int[relevantFormulaCount];

      for (int monitorIndex = 0; monitorIndex < relevantFormulaCount; monitorIndex++) {
        MonitorState monitorState = monitorStates[monitorIndex];
        Map<MonitorState, ValuationSet> monitorSuccessorMap =
          monitors[monitorIndex].getSuccessorMap(monitorState);

        int monitorSuccessorCount = monitorSuccessorMap.size();
        successorCounts[monitorIndex] = monitorSuccessorCount - 1;

        MonitorState[] successorStates = new MonitorState[monitorSuccessorCount];
        ValuationSet[] successorValuations = new ValuationSet[monitorSuccessorCount];
        Collections3.forEachIndexed(monitorSuccessorMap.entrySet(), (successorIndex, entry) -> {
          successorStates[successorIndex] = entry.getKey();
          successorValuations[successorIndex] = entry.getValue();
        });
        monitorSuccessorMatrix[monitorIndex] = successorStates;
        monitorValuationMatrix[monitorIndex] = successorValuations;
      }

      // Heuristics to check which approach is faster
      BitSet sensitiveAlphabet = productStateFactory.getSensitiveAlphabet(currentState);
      long powerSetSize = (1L << (sensitiveAlphabet.size() + 2));
      // This is an over-approximation, since a lot of branches might be "empty"
      long totalSuccessorCounts = masterSuccessors.size()
        * NatCartesianProductSet.numberOfElements(successorCounts);

      if (totalSuccessorCounts > (1L << (powerSetSize + 2))) {
        // Approach 1: Simple power set iteration

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
          MonitorState[] monitorSuccessors = new MonitorState[monitorStates.length];
          Arrays.setAll(monitorSuccessors, relevantIndex -> {
            MonitorState currentMonitorState = monitorStates[relevantIndex];
            MonitorAutomaton monitor = monitors[relevantIndex];
            return monitor.getSuccessor(currentMonitorState, valuation);
          });

          // Create product successor
          RabinizerState rabinizerSuccessor = RabinizerState.of(masterSuccessor, monitorSuccessors);

          ValuationSetMapUtil.add(rabinizerSuccessors, new RabinizerProductEdge(rabinizerSuccessor),
            vsFactory.of(valuation, sensitiveAlphabet));

          // Update exploration queue
          if (exploredStates.add(rabinizerSuccessor)) {
            workQueue.add(rabinizerSuccessor);
          }
        }
      } else {
        // Approach 2: Use the partition of the monitors to avoid computation if monitors aren't too
        // "fragmented".

        masterSuccessors.forEach((masterSuccessor, masterSuccessorValuation) -> {
          if (!stateSubset.contains(masterSuccessor)) {
            // The successor is not part of this partition
            return;
          }

          NatCartesianProductIterator productIterator =
            new NatCartesianProductIterator(successorCounts);

          //noinspection LabeledStatement
          product:
          while (productIterator.hasNext()) {
            int[] successorSelection = productIterator.next();
            ValuationSet productValuation = masterSuccessorValuation;

            // Evolve each monitor
            MonitorState[] monitorSuccessors = new MonitorState[monitorStates.length];

            for (int monitorIndex = 0; monitorIndex < relevantFormulaCount; monitorIndex++) {
              MonitorState currentMonitorState = monitorStates[monitorIndex];
              assert currentMonitorState != null;
              int monitorMatrixIndex = successorSelection[monitorIndex];
              monitorSuccessors[monitorIndex] =
                monitorSuccessorMatrix[monitorIndex][monitorMatrixIndex];
              ValuationSet monitorSuccessorValuation =
                monitorValuationMatrix[monitorIndex][monitorMatrixIndex];
              productValuation = productValuation.intersection(monitorSuccessorValuation);

              // TODO Forget about this whole subtree
              if (productValuation.isEmpty()) {
                continue product;
              }
            }

            // Create product successor
            RabinizerState successor = RabinizerState.of(masterSuccessor, monitorSuccessors);
            RabinizerProductEdge edge = new RabinizerProductEdge(successor);
            ValuationSetMapUtil.add(rabinizerSuccessors, edge, productValuation);

            // Update exploration queue
            if (exploredStates.add(successor)) {
              workQueue.add(successor);
            }
          }
        });
      }
    }
    return transitionSystem;
  }

  private Set<GOperator> relevantSubFormulas(EquivalenceClass equivalenceClass) {
    return configuration.supportBasedRelevantFormulaAnalysis()
      ? RabinizerUtil.getSupportSubFormulas(equivalenceClass)
      : RabinizerUtil.getRelevantSubFormulas(equivalenceClass);
  }

  private static final class ActiveSet {
    final GSet set;
    final Set<int[]> rankings;
    private final RabinPair[] rankingPairs;

    @SuppressWarnings({"PMD.ArrayIsStoredDirectly",
                        "AssignmentToCollectionOrArrayFieldFromParameter"})
    ActiveSet(GSet set, Set<int[]> rankings, RabinPair[] rankingPairs) {
      this.set = set;
      this.rankings = rankings;
      this.rankingPairs = rankingPairs;
    }

    static ActiveSet create(GOperator[] operators, GSet subset, MonitorAutomaton[] monitors,
      GeneralizedRabinAcceptance.Builder builder) {
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
        // TODO If acceptanceSets == 0 this monitor can't accept - can we use this?
        maximalRanks[relevantIndex] = acceptanceSets == 0 ? 0 : (acceptanceSets - 1) / 2;
        relevantIndex += 1;
      }

      // Allocate the acceptance caches
      Set<int[]> rankings = new NatCartesianProductSet(maximalRanks);
      RabinPair[] rankingPairs = new RabinPair[rankings.size()];
      Arrays.setAll(rankingPairs, i -> builder.add(subset.size()));
      return new ActiveSet(subset, rankings, rankingPairs);
    }

    RabinPair getPairForRanking(int rankingIndex) {
      return rankingPairs[rankingIndex];
    }
  }

  private static final class GSetRanking {
    final GSet activeFormulaSet;
    final RabinPair pair;
    final int[] ranking;
    private final boolean[] activeFormulas;
    private final EquivalenceClassFactory eqFactory;
    private final ValuationSet[][] monitorPriorities;
    private final boolean[] relevantFormulas;

    @SuppressWarnings({"PMD.ArrayIsStoredDirectly",
                        "AssignmentToCollectionOrArrayFieldFromParameter"})
    GSetRanking(boolean[] relevantFormulas, boolean[] activeFormulas, GSet activeFormulaSet,
      RabinPair pair, int[] ranking, EquivalenceClassFactory eqFactory,
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
          // This edge is fail - definitely Fin
          return IntSets.singleton(pair.finSet());
        }
        int succeedPriority = 2 * ranking[activeIndex] + 1;
        if (priority > succeedPriority) {
          // Nothing relevant happened
          continue;
        }
        if (priority % 2 == 0) {
          // Merged at some lower rank
          return IntSets.singleton(pair.finSet());
        }
        if (priority == succeedPriority) {
          // Succeeded at the current rank
          if (edgeAcceptanceSet == null) {
            edgeAcceptanceSet = new IntOpenHashSet();
          }
          edgeAcceptanceSet.add(pair.infSet(activeIndex));
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

      AtomicReference<EquivalenceClass> antecedent = new AtomicReference<>(eqFactory.getTrue());
      forEachRelevantAndActive((relevantIndex, activeIndex) -> {
        MonitorState monitorState = monitorStates[relevantIndex];

        EquivalenceClass[] monitorStateRanking = monitorState.formulaRanking;
        int rank = ranking[activeIndex];
        for (int stateIndex = rank; stateIndex < monitorStateRanking.length; stateIndex++) {
          EquivalenceClass rankEntry = monitorStateRanking[stateIndex];
          EquivalenceClass state = eager ? rankEntry.temporalStep(valuation) : rankEntry;
          antecedent.updateAndGet(clazz -> clazz.and(state));
        }
      });

      if (eager) {
        // In the eager construction, we need to add some more knowledge to the antecedent
        antecedent.updateAndGet(clazz -> clazz.and(activeFormulaSet.operatorConjunction()));
      }

      // Important: We need to inject the state of the G operators into the monitor states,
      // otherwise the assumption is too weak. This was an error in the first version of the
      // Rabinizer paper. For example, if the monitor states were [Ga | Fb] and the |G| = {}, we
      // must be able to "prove" Fb.
      // TODO Cached substitution
      // TODO Can we apply the substitution to the consequent, too?
      Function<Formula, Formula> strengthening = formula -> formula instanceof GOperator
        ? BooleanConstant.of(activeFormulaSet.contains(formula))
        : formula;
      EquivalenceClass strengthenedAntecedent = antecedent.get().substitute(strengthening);

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
            + "%s), valuation %s; entails %s (weakened: %s): %s", activeFormulaSet, rankingString,
          activeMonitorStates, strengthenedAntecedent, valuation, consequent,
          weakenedConsequent, result);
        logger.log(Level.FINEST, log);
      }

      return result;
    }

    boolean monitorsEntailEager(RabinizerState state, BitSet valuation) {
      return monitorsEntail(state.monitorStates, valuation, state.masterState);
    }
  }

  static final class EvaluateVisitor extends DefaultConverter {
    private final EquivalenceClass environment;
    private final EquivalenceClassFactory factory;

    EvaluateVisitor(Collection<GOperator> gMonitors, EquivalenceClass label) {
      this.factory = label.getFactory();
      this.environment = label.and(factory.of(
        Conjunction.of(Stream.concat(gMonitors.stream(), gMonitors.stream().map(x -> x.operand)))));
    }

    private boolean isImplied(Formula formula) {
      return environment.implies(factory.of(formula));
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      // Implication check not necessary for conjunctions.
      return Conjunction.of(conjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      if (isImplied(disjunction)) {
        return BooleanConstant.TRUE;
      }

      return Disjunction.of(disjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
      if (isImplied(fOperator)) {
        return BooleanConstant.TRUE;
      }

      return FOperator.of(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(FrequencyG freq) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Formula visit(GOperator gOperator) {
      if (isImplied(gOperator)) {
        return BooleanConstant.TRUE;
      }
      return BooleanConstant.of(BooleanConstant.TRUE == gOperator.operand.accept(this));
    }

    @Override
    public Formula visit(Literal literal) {
      return isImplied(literal) ? BooleanConstant.TRUE : literal;
    }

    @Override
    public Formula visit(MOperator mOperator) {
      if (isImplied(mOperator)) {
        return BooleanConstant.TRUE;
      }

      return MOperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (isImplied(rOperator)) {
        return BooleanConstant.TRUE;
      }

      if (BooleanConstant.TRUE == rOperator.right.accept(this)) {
        return BooleanConstant.TRUE;
      }

      return MOperator.of(rOperator.left, rOperator.right).accept(this);
    }

    @Override
    public Formula visit(UOperator uOperator) {
      if (isImplied(uOperator)) {
        return BooleanConstant.TRUE;
      }

      return UOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (isImplied(wOperator)) {
        return BooleanConstant.TRUE;
      }

      if (BooleanConstant.TRUE == wOperator.left.accept(this)) {
        return BooleanConstant.TRUE;
      }

      return UOperator.of(wOperator.left, wOperator.right).accept(this);
    }

    @Override
    public Formula visit(XOperator xOperator) {
      if (isImplied(xOperator)) {
        return BooleanConstant.TRUE;
      }

      return XOperator.of(xOperator.operand.accept(this));
    }
  }
}
