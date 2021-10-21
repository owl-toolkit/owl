/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.translations.rabinizer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance.RabinPair;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaWriter;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.EquivalenceClassFactory;
import owl.bdd.Factories;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.BitSet2;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Converter;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.translations.rabinizer.RabinizerStateFactory.MasterStateFactory;
import owl.translations.rabinizer.RabinizerStateFactory.ProductStateFactory;

/**
 * Central class handling the Rabinizer construction.
 *
 * @see owl.translations.rabinizer
 */
public final class RabinizerBuilder {
  private static final MonitorAutomaton[] EMPTY_MONITORS = new MonitorAutomaton[0];

  private static final Logger logger = Logger.getLogger(RabinizerBuilder.class.getName());

  private final RabinizerConfiguration configuration;
  private final EquivalenceClassFactory eqFactory;
  private final EquivalenceClass initialClass;
  private final MasterStateFactory masterStateFactory;
  private final ProductStateFactory productStateFactory;
  private final BddSetFactory vsFactory;

  private RabinizerBuilder(RabinizerConfiguration configuration, Factories factories,
    Formula formula) {
    EquivalenceClass initialClass = factories.eqFactory.of(formula);

    this.configuration = configuration;
    this.initialClass = initialClass;
    boolean fairnessFragment = configuration.eager()
      && initialClass.support(false).stream().allMatch(
        support -> SyntacticFragments.isInfinitelyOften(support)
          || SyntacticFragments.isAlmostAll(support));

    vsFactory = factories.vsFactory;
    eqFactory = factories.eqFactory;
    masterStateFactory = new MasterStateFactory(configuration.eager(), fairnessFragment);
    productStateFactory = new ProductStateFactory(configuration.eager());
  }

  private static BddSet[][] computeMonitorPriorities(MonitorAutomaton[] monitors,
    List<MonitorState> monitorStates, GSet activeSet) {
    int monitorCount = monitors.length;
    BddSet[][] monitorPriorities = new BddSet[monitorCount][];
    for (int relevantIndex = 0; relevantIndex < monitorStates.size(); relevantIndex++) {
      MonitorState monitorState = monitorStates.get(relevantIndex);

      // Get the corresponding monitor for this gSet.
      Automaton<MonitorState, ParityAcceptance> monitor =
        monitors[relevantIndex].getAutomaton(activeSet);
      int monitorAcceptanceSets = monitor.acceptance().acceptanceSets();

      // Cache the priorities of the edge
      BddSet[] edgePriorities = new BddSet[monitorAcceptanceSets];

      monitor.edgeMap(monitorState).forEach((edge, valuations) -> {
        var colours = edge.colours();

        var priority = colours.first();

        if (priority.isEmpty()) {
          return;
        }

        assert priority.equals(colours.last());
        edgePriorities[priority.getAsInt()] = edgePriorities[priority.getAsInt()] == null
          ? valuations
          : valuations.union(edgePriorities[priority.getAsInt()]);
      });

      monitorPriorities[relevantIndex] = edgePriorities;
    }
    return monitorPriorities;
  }

  private static boolean isSuspendableScc(Set<EquivalenceClass> scc,
    Set<GOperator> relevantOperators) {
    // Check if we need external atoms for acceptance. To this end, we replace all relevant Gs
    // with true (note that formulas are in NNF, so setting everything to true is "maximal")
    // and then check if there are some atoms left which are not in any formula

    BitSet internalAtoms = new BitSet();
    relevantOperators.forEach(x1 -> internalAtoms.or(x1.atomicPropositions(true)));

    for (EquivalenceClass state : scc) {
      EvaluateVisitor visitor = new EvaluateVisitor(relevantOperators, state);
      EquivalenceClass substitute = state.substitute(visitor);
      BitSet externalAtoms = substitute.atomicPropositions(false);
      substitute.temporalOperators().forEach(x -> externalAtoms.or(x.atomicPropositions(true)));

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
      Iterator<List<Integer>> rankingIterator = activeSet.rankings.iterator();
      int index = 0;
      while (rankingIterator.hasNext()) {
        RabinPair pair = activeSet.getPairForRanking(index);
        tableBuilder.append("\n  ").append(rankingIterator.next())
          .append(" -> ").append(pair);
        index += 1;
      }
    }

    return tableBuilder.toString();
  }

  public static MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> build(
    LabelledFormula formula, RabinizerConfiguration configuration) {
    Factories factories = FactorySupplier.defaultSupplier()
      .getFactories(formula.atomicPropositions());
    Formula phiNormalized = formula.formula().nnf().accept(
      new UnabbreviateVisitor(Set.of(WOperator.class, ROperator.class)));
    // TODO Check if the formula only has a single G
    // TODO Check for safety languages?
    logger.log(Level.FINE, "Creating rabinizer automaton for formula {0}",
      LabelledFormula.of(phiNormalized, formula.atomicPropositions()).toString());

    var automaton = new RabinizerBuilder(configuration, factories, phiNormalized).build();
    automaton.trim();
    return automaton;
  }

  private MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> build() {
    // TODO Fully implement the computeAcceptance switch

    /* Build master automaton
     *
     * The master automaton models a simple transition system based on the global formula and keeps
     * track of how the formula evolves along the finite prefix seen so far. */

    Automaton<EquivalenceClass, AllAcceptance> masterAutomaton =
      new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
        eqFactory.atomicPropositions(),
        vsFactory,
        Set.of(masterStateFactory.initialState(this.initialClass)),
        AllAcceptance.INSTANCE) {

        @Override
        protected MtBdd<Edge<EquivalenceClass>> edgeTreeImpl(
          EquivalenceClass state) {
          return masterStateFactory.edgeTree(state);
        }
      };

    if (logger.isLoggable(Level.FINER)) {
      logger.log(Level.FINER, "Master automaton for {0}:\n{1}",
        new Object[] {this.initialClass, HoaWriter.toString(masterAutomaton)});
    } else {
      logger.log(Level.FINE, "Master automaton for {0} has {1} states",
        new Object[] {this.initialClass, masterAutomaton.states().size()});
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

    int partitionSize = masterSccPartition.sccs.size();
    @SuppressWarnings({"unchecked"})
    Set<GOperator>[] sccRelevantGList = new Set[partitionSize];

    for (int i = 0, s = masterSccPartition.sccs.size(); i < s; i++) {
      sccRelevantGList[i] = masterSccPartition.sccs.get(i)
        .stream()
        .map(this::relevantSubFormulas)
        .flatMap(Collection::stream)
        .collect(Collectors.toUnmodifiableSet());
    }

    Set<GOperator> allRelevantGFormulas = Arrays
      .stream(sccRelevantGList)
      .flatMap(Collection::stream)
      .collect(Collectors.toUnmodifiableSet());

    logger.log(Level.FINE, "Identified relevant sub-formulas: {0}", allRelevantGFormulas);

    // Assign arbitrary numbering to all relevant sub-formulas. Throughout the construction, we will
    // use this numbering to identify the sub-formulas.
    GOperator[] gFormulas = allRelevantGFormulas.toArray(GOperator[]::new);

    /* Build monitors for all formulas which are relevant somewhere.
     *
     * For each G which is relevant in some SCC we construct the respective monitor. The monitor
     * array is indexed by the same numbering as the gFormulas array.
     */

    // TODO We could detect effectively false G operators here (i.e. monitors never accept)
    // But this rarely happens
    MonitorAutomaton[] monitors = new MonitorAutomaton[gFormulas.length];
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
    GeneralizedRabinAcceptance.Builder builder = new GeneralizedRabinAcceptance.Builder();
    Set<Set<GOperator>> relevantSets = Sets.powerSet(allRelevantGFormulas);
    assert relevantSets.contains(Set.of());

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
      activeSets[activeSetIndex] = ActiveSet.create(gFormulas, gSet, monitors, builder);
      activeSetIndex += 1;
    }

    MutableAutomaton<RabinizerState, GeneralizedRabinAcceptance> rabinizerAutomaton =
      HashMapAutomaton.create(eqFactory.atomicPropositions(), vsFactory, builder.build());

    // Process each subset separately
    // TODO Parallel
    List<Set<EquivalenceClass>> partition = masterSccPartition.sccs;
    Multimap<EquivalenceClass, RabinizerState> statesPerClass = HashMultimap.create();

    for (int sccIndex = 0; sccIndex < partition.size(); sccIndex++) {
      // Preliminary work: Only some sub-formulas are relevant a particular SCC (consider again
      // the previous example of "a & X G b | !a & X G c"). While in the SCC, all indexing is done
      // relative to the list of G operators relevant in the SCC.
      Set<EquivalenceClass> scc = partition.get(sccIndex);
      Set<GOperator> sccRelevantOperators = sccRelevantGList[sccIndex];

      boolean suspendable = configuration.suspendableFormulaDetection()
        && isSuspendableScc(scc, sccRelevantOperators);

      BitSet relevantFormulas;
      MonitorAutomaton[] sccMonitors;

      if (suspendable) {
        sccMonitors = EMPTY_MONITORS;
        relevantFormulas = new BitSet();
        logger.log(Level.FINE, "Suspending all Gs on SCC {0}", sccIndex);
      } else {
        relevantFormulas = new BitSet();
        sccMonitors = new MonitorAutomaton[sccRelevantOperators.size()];
        int subsetRelevantIndex = 0;
        for (int gIndex = 0; gIndex < gFormulas.length; gIndex++) {
          boolean indexRelevant = sccRelevantOperators.contains(gFormulas[gIndex]);
          relevantFormulas.set(gIndex, indexRelevant);
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
      Map<RabinizerState, Map<RabinizerProductEdge, BddSet>> transitionSystem =
        exploreTransitionSystem(scc, masterAutomaton, sccMonitors);

      // TODO Some state space analysis / optimization is possible here?

      if (sccRelevantOperators.isEmpty()) {
        transitionSystem.forEach((state, successors) -> {
          statesPerClass.put(state.masterState(), state);
          createEdges(state, successors, rabinizerAutomaton);
        });

        continue;
      }

      // We now explored the transition system of this SCC. Now, we have to generate the acceptance
      // condition. We determine the currently relevant formulas and then iterate over all possible
      // subsets.
      logger.log(Level.FINER, "Computing acceptance on SCC {0}", sccIndex);
      transitionSystem.forEach((state, successors) -> {
        // TODO Can we do skeleton analysis here, too?
        Set<GOperator> stateRelevantSubFormulas = relevantSubFormulas(state.masterState());
        logger.log(Level.FINEST, "Product transitions for {0}: {1}; relevant formulas: {2}",
          new Object[] {state, successors, stateRelevantSubFormulas});

        // We iterate over all (|G|, r) pairs which are relevant to this subset. We do not
        // need to add Fin transitions for subsets which are ignored here (i.e. all those which are
        // a strict superset of relevantSubFormulaSet): Since we do not add any Inf edge and any
        // pair belonging to a (|G|, r) pair with |G| != {} has at least on corresponding Inf set,
        // they implicitly will not accept in this SCC.

        BitSet sensitiveAlphabet = productStateFactory.getSensitiveAlphabet(state);
        Iterator<BitSet> activeSubFormulasIterator = BitSet2.powerSet(relevantFormulas).iterator();
        activeSubFormulasIterator.next(); // Empty set is handled separately

        while (activeSubFormulasIterator.hasNext()) {
          BitSet activeSubFormulas = activeSubFormulasIterator.next();
          ActiveSet activeSet = activeSets[BitSet2.toInt(activeSubFormulas) - 1];
          GSet activeSubFormulasSet = activeSet.set;

          // Pre-compute the monitor transition priorities, as they are independent of the ranking.
          // The first dimension of this matrix is the monitor index, the second the priority. To
          // determine which priority a particular valuation has for a monitor with index i, one
          // simply has to find a j such that priorities[i][j] contains the valuation. Note that
          // thus it is guaranteed that for each i priorities[i][j] are disjoint for all j.
          BddSet[][] monitorPriorities =
            computeMonitorPriorities(sccMonitors, state.monitorStates(), activeSubFormulasSet);

          // Iterate over all possible rankings
          Iterator<List<Integer>> rankingIterator = activeSet.rankings.iterator();
          int rankingIndex = -1;
          while (rankingIterator.hasNext()) {
            rankingIndex += 1;
            List<Integer> ranking = rankingIterator.next();
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
              for (BitSet valuation : BitSet2.powerSet(sensitiveAlphabet)) {
                if (!valuations.contains(valuation)) {
                  continue;
                }

                BddSet edgeValuation = vsFactory.of(valuation, sensitiveAlphabet);
                if (configuration.eager() && !rankingPair.monitorsEntailEager(state, valuation)) {
                  transition.addAcceptance(edgeValuation, pair.finSet());
                } else {
                  rankingPair.getAcceptance(valuation).stream().forEach(
                    acceptance -> transition.addAcceptance(edgeValuation, acceptance));
                }
              }
            });
          }
        }

        // Which rabinizer states belong to which master state
        statesPerClass.put(state.masterState(), state);

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
    masterSccPartition.transientStates.forEach(
      state -> statesPerClass.put(state, RabinizerState.of(state, List.of())));

    // CSOFF: Indentation
    Function<EquivalenceClass, RabinizerState> getAnyState =
      masterState -> masterSccPartition.transientStates.contains(masterState)
        ? RabinizerState.of(masterState, List.of())
        : statesPerClass.get(masterState).iterator().next();
    // CSON: Indentation

    // For each edge A -> B between SCCs in the master, connect all states of the product system
    // with A as master state to an arbitrary state with B as master state
    masterSccPartition.outgoingTransitions.rowMap().forEach(
      (masterState, masterSuccessors) -> {
        assert !masterSuccessors.isEmpty();
        Collection<RabinizerState> rabinizerStates = statesPerClass.get(masterState);

        masterSuccessors.forEach((masterSuccessor, valuations) -> {
          assert !masterSuccessor.isFalse();
          Edge<RabinizerState> edge = Edge.of(getAnyState.apply(masterSuccessor));

          for (RabinizerState state : rabinizerStates) {
            rabinizerAutomaton.addState(state);
            rabinizerAutomaton.addEdge(state, valuations, edge);
          }
        });
      });

    // Properly choose the initial state
    rabinizerAutomaton.initialStates(Set.of(getAnyState.apply(
      masterStateFactory.initialState(this.initialClass))));
    rabinizerAutomaton.trim();

    // Handle the |G| = {} case
    // TODO: Piggyback on an existing RabinPair.
    RabinizerState trueState = RabinizerState.of(eqFactory.of(BooleanConstant.TRUE), List.of());
    if (rabinizerAutomaton.states().contains(trueState)) {
      assert Objects.equals(Iterables.getOnlyElement(rabinizerAutomaton.successors(trueState)),
        trueState);

      RabinPair truePair = builder.add(1);
      rabinizerAutomaton.removeEdge(trueState, rabinizerAutomaton.factory().of(true), trueState);
      rabinizerAutomaton.addEdge(trueState, vsFactory.of(true),
        Edge.of(trueState, truePair.infSet()));
      rabinizerAutomaton.acceptance(builder.build());
      rabinizerAutomaton.trim();
    }

    logger.log(Level.FINER,
      () -> String.format("Result:%n%s", HoaWriter.toString(rabinizerAutomaton)));
    logger.log(Level.FINER, () -> printOperatorSets(activeSets));

    return rabinizerAutomaton;
  }

  private MonitorAutomaton buildMonitor(GOperator gOperator) {
    logger.log(Level.FINE, "Building monitor for sub-formula {0}", gOperator);

    EquivalenceClass operand = eqFactory.of(gOperator.operand());

    Set<GOperator> relevantOperators = relevantSubFormulas(operand);
    Set<Set<GOperator>> powerSets = Sets.powerSet(relevantOperators);
    List<GSet> relevantGSets = new ArrayList<>(powerSets.size());
    powerSets.forEach(gSet -> relevantGSets.add(new GSet(gSet, eqFactory)));

    MonitorAutomaton monitor = MonitorBuilder
      .create(gOperator, operand, relevantGSets, vsFactory, configuration.eager());

    // Postprocessing and logging
    logger.log(Level.FINER,
      () -> String.format("Monitor for %s:%n%s", gOperator, HoaWriter.toString(monitor)));
    if (logger.isLoggable(Level.FINEST)) {
      monitor.getAutomata().forEach((set, automaton) -> logger.log(Level.FINEST,
        "For set {0}\n{1}", new Object[] {set, HoaWriter.toString(automaton)}));
    }
    return monitor;
  }

  private void createEdges(RabinizerState state, Map<RabinizerProductEdge, BddSet>
    successors, MutableAutomaton<RabinizerState, ?> rabinizerAutomaton) {
    rabinizerAutomaton.addState(state);

    BitSet sensitiveAlphabet = productStateFactory.getSensitiveAlphabet(state);
    successors.forEach((cache, valuations) -> {
      RabinizerState rabinizerSuccessor = cache.getRabinizerSuccessor();
      BddSet[] acceptanceCache = cache.getSuccessorAcceptance();

      // Gather all sets active for this valuation
      // Create the edge
      // Expand valuation to the full alphabet
      // Add edge to result
      for (BitSet valuation : BitSet2.powerSet(sensitiveAlphabet)) {
        if (valuations.contains(valuation)) {
          int acceptanceIndices = acceptanceCache.length;
          BitSet edgeAcceptanceSet = new BitSet(acceptanceIndices);

          // Gather all sets active for this valuation
          for (int acceptanceIndex = 0; acceptanceIndex < acceptanceIndices; acceptanceIndex++) {
            BddSet acceptanceValuations = acceptanceCache[acceptanceIndex];
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
          BddSet edgeValuation = vsFactory.of(valuation, sensitiveAlphabet);
          // Add edge to result
          rabinizerAutomaton.addEdge(state, edgeValuation, rabinizerEdge);
        }
      }
    });
  }

  private Map<RabinizerState, Map<RabinizerProductEdge, BddSet>>
  exploreTransitionSystem(Set<EquivalenceClass> stateSubset,
    Automaton<EquivalenceClass, ?> masterAutomaton, MonitorAutomaton[] monitors) {
    int relevantFormulaCount = monitors.length;
    EquivalenceClass masterInitialState = stateSubset.iterator().next();
    MonitorState[] monitorInitialStates = new MonitorState[relevantFormulaCount];
    Arrays.setAll(monitorInitialStates, i -> monitors[i].initialState());

    RabinizerState initialState = RabinizerState.of(masterInitialState, monitorInitialStates);

    // The distinct edges in the product transition graph
    Map<RabinizerState, Map<RabinizerProductEdge, BddSet>> transitionSystem = new HashMap<>();

    // BFS work list
    Set<RabinizerState> exploredStates = Sets.newHashSet(initialState);
    Deque<RabinizerState> workQueue = new ArrayDeque<>(exploredStates);

    while (!workQueue.isEmpty()) {
      RabinizerState currentState = workQueue.remove();
      logger.log(Level.FINEST, "Exploring {0}", currentState);
      assert currentState.monitorStates().size() == relevantFormulaCount;
      assert !transitionSystem.containsKey(currentState);

      EquivalenceClass masterState = currentState.masterState();
      List<MonitorState> monitorStates = currentState.monitorStates();

      Set<EquivalenceClass> masterSuccessors = masterAutomaton.successors(masterState);
      if (masterSuccessors.isEmpty()) {
        transitionSystem.put(currentState, Map.of());
        continue;
      }

      Map<RabinizerProductEdge, BddSet> rabinizerSuccessors = new HashMap<>();
      transitionSystem.put(currentState, rabinizerSuccessors);

      // Compute the successor matrix for all monitors. Basically, we assign a arbitrary ordering
      // on all successors for each monitor.
      MonitorState[][] monitorSuccessorMatrix = new MonitorState[relevantFormulaCount][];
      BddSet[][] monitorValuationMatrix = new BddSet[relevantFormulaCount][];
      int[] successorCounts = new int[relevantFormulaCount];

      for (int monitorIndex = 0; monitorIndex < relevantFormulaCount; monitorIndex++) {
        MonitorState monitorState = monitorStates.get(monitorIndex);

        Map<MonitorState, BddSet> successors = new HashMap<>();
        monitors[monitorIndex].edgeMap(monitorState).forEach((edge, valuations) ->
          successors.merge(edge.successor(), valuations, BddSet::union));

        int monitorSuccessorCount = successors.size();
        successorCounts[monitorIndex] = monitorSuccessorCount - 1;

        MonitorState[] successorStates = new MonitorState[monitorSuccessorCount];
        BddSet[] successorValuations = new BddSet[monitorSuccessorCount];
        int index = 0;
        for (Map.Entry<MonitorState, BddSet> element : successors.entrySet()) {
          successorStates[index] = element.getKey();
          successorValuations[index] = element.getValue();
          index += 1;
        }
        monitorSuccessorMatrix[monitorIndex] = successorStates;
        monitorValuationMatrix[monitorIndex] = successorValuations;
      }

      // Heuristics to check which approach is faster
      BitSet sensitiveAlphabet = productStateFactory.getSensitiveAlphabet(currentState);
      long powerSetSize = (1L << (sensitiveAlphabet.size() + 2));
      // This is an over-approximation, since a lot of branches might be "empty"
      long totalSuccessorCounts = masterSuccessors.size()
        * NatCartesianProductIterator.numberOfElements(successorCounts);

      if (totalSuccessorCounts > (1L << (powerSetSize + 2))) {
        // Approach 1: Simple power set iteration

        for (BitSet valuation : BitSet2.powerSet(sensitiveAlphabet)) {
          // Get the edge in the master automaton
          Edge<EquivalenceClass> masterEdge = masterAutomaton.edge(masterState, valuation);
          if (masterEdge == null) {
            // A null master edge means the master automaton moves into the "ff" state - a sure
            // failure and we don't need to investigate further.
            continue;
          }

          EquivalenceClass masterSuccessor = masterEdge.successor();
          if (!stateSubset.contains(masterSuccessor)) {
            // The successor is not part of this partition
            continue;
          }

          // Evolve each monitor
          MonitorState[] monitorSuccessors = new MonitorState[monitorStates.size()];
          Arrays.setAll(monitorSuccessors, relevantIndex -> {
            MonitorState currentMonitorState = monitorStates.get(relevantIndex);
            MonitorAutomaton monitor = monitors[relevantIndex];
            return monitor.successor(currentMonitorState, valuation);
          });

          // Create product successor
          RabinizerState rabinizerSuccessor = RabinizerState.of(masterSuccessor, monitorSuccessors);

          rabinizerSuccessors.merge(new RabinizerProductEdge(rabinizerSuccessor),
            vsFactory.of(valuation, sensitiveAlphabet), BddSet::union);

          // Update exploration queue
          if (exploredStates.add(rabinizerSuccessor)) {
            workQueue.add(rabinizerSuccessor);
          }
        }
      } else {
        // Approach 2: Use the partition of the monitors to avoid computation if
        // monitors aren't too "fragmented".
        masterAutomaton.edgeMap(masterState).forEach((edge, valuationSet) -> {
          // The successor is not part of this partition
          if (!stateSubset.contains(edge.successor())) {
            return;
          }

          NatCartesianProductIterator productIterator =
            new NatCartesianProductIterator(successorCounts);

          //noinspection LabeledStatement
          product:
          while (productIterator.hasNext()) {
            int[] successorSelection = productIterator.next();
            BddSet productValuation = valuationSet;

            // Evolve each monitor
            MonitorState[] monitorSuccessors = new MonitorState[monitorStates.size()];

            for (int monitorIndex = 0; monitorIndex < relevantFormulaCount; monitorIndex++) {
              MonitorState currentMonitorState = monitorStates.get(monitorIndex);
              assert currentMonitorState != null;
              int monitorMatrixIndex = successorSelection[monitorIndex];
              monitorSuccessors[monitorIndex] =
                monitorSuccessorMatrix[monitorIndex][monitorMatrixIndex];
              BddSet monitorSuccessorValuation =
                monitorValuationMatrix[monitorIndex][monitorMatrixIndex];
              productValuation = productValuation.intersection(monitorSuccessorValuation);

              // TODO Forget about this whole subtree
              if (productValuation.isEmpty()) {
                continue product;
              }
            }

            // Create product successor
            RabinizerState successor = RabinizerState.of(edge.successor(), monitorSuccessors);
            rabinizerSuccessors.merge(new RabinizerProductEdge(successor), productValuation,
              BddSet::union);

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

  private static void findSupportingSubFormulas(EquivalenceClass equivalenceClass,
    Set<GOperator> gOperators) {
    // Due to the BDD representation, we have to do a somewhat weird construction. The problem is
    // that we can't simply do a class.getSupport(G) to determine the relevant G operators in the
    // formula. For example, to the BDD "X G a" and "G a" have no relation, hence the G-support
    // of "X G a" is empty, although "G a" certainly is important for the formula. So, instead,
    // we determine all relevant temporal operators in the support and for all of those collect the
    // G operators.

    // TODO Can we optimize for eager?

    for (Formula.TemporalOperator temporalOperator : equivalenceClass.temporalOperators()) {
      if (temporalOperator instanceof GOperator) {
        gOperators.add((GOperator) temporalOperator);
      } else {
        Formula unwrapped = temporalOperator;

        while (unwrapped instanceof Formula.UnaryTemporalOperator) {
          unwrapped = ((Formula.UnaryTemporalOperator) unwrapped).operand();

          if (unwrapped instanceof GOperator) {
            break;
          }
        }

        EquivalenceClassFactory factory = equivalenceClass.factory();

        if (unwrapped instanceof GOperator gOperator) {
          gOperators.add(gOperator);
        } else if (unwrapped instanceof Formula.BinaryTemporalOperator binaryOperator) {
          findSupportingSubFormulas(factory.of(binaryOperator.leftOperand()), gOperators);
          findSupportingSubFormulas(factory.of(binaryOperator.rightOperand()), gOperators);
        } else {
          findSupportingSubFormulas(factory.of(unwrapped), gOperators);
        }
      }
    }
  }

  private Set<GOperator> relevantSubFormulas(EquivalenceClass clazz) {
    if (clazz.isTrue() || clazz.isFalse()) {
      return Set.of();
    }

    Set<GOperator> operators = new HashSet<>();

    if (configuration.supportBasedRelevantFormulaAnalysis()) {
      findSupportingSubFormulas(clazz, operators);
    } else {
      clazz.temporalOperators().forEach(x -> operators.addAll(x.subformulas(GOperator.class)));
    }

    return operators;
  }

  private static final class ActiveSet {
    final GSet set;
    final List<List<Integer>> rankings;
    private final RabinPair[] rankingPairs;

    @SuppressWarnings({"PMD.ArrayIsStoredDirectly"
                      })
    ActiveSet(GSet set, List<List<Integer>> rankings, RabinPair[] rankingPairs) {
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
        int acceptanceSets = gSetMonitor.acceptance().acceptanceSets();
        // TODO If acceptanceSets == 0 this monitor can't accept - can we use this?
        maximalRanks[relevantIndex] = acceptanceSets == 0 ? 0 : (acceptanceSets - 1) / 2;
        relevantIndex += 1;
      }

      // Allocate the acceptance caches
      List<List<Integer>> preRankings = Arrays.stream(maximalRanks)
        .mapToObj(i -> IntStream.rangeClosed(0, i).boxed().toList())
        .toList();
      List<List<Integer>> rankings = Lists.cartesianProduct(preRankings);
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
    final List<Integer> ranking;
    private final BitSet activeFormulas;
    private final EquivalenceClassFactory eqFactory;
    private final BddSet[][] monitorPriorities;
    private final BitSet relevantFormulas;

    @SuppressWarnings({"PMD.ArrayIsStoredDirectly"
                      })
    GSetRanking(BitSet relevantFormulas, BitSet activeFormulas, GSet activeFormulaSet,
      RabinPair pair, List<Integer> ranking, EquivalenceClassFactory eqFactory,
      BddSet[][] monitorPriorities) {
      assert activeFormulas.length() <= relevantFormulas.length();

      this.activeFormulaSet = activeFormulaSet;
      this.pair = pair;
      this.ranking = ranking;
      this.eqFactory = eqFactory;
      this.monitorPriorities = monitorPriorities;
      this.activeFormulas = activeFormulas;
      this.relevantFormulas = relevantFormulas;
    }

    BitSet getAcceptance(BitSet valuation) {
      // TODO Pre-compute into a Map<IntSet, ValuationSet>

      // Lazy allocate the acceptance set, often we don't need it
      BitSet edgeAcceptanceSet = new BitSet();

      // Iterate over all enabled sub formulas and check acceptance for each monitor
      int relevantIndex = -1;
      int activeIndex = -1;

      for (int gIndex = 0; gIndex < activeFormulas.length(); gIndex++) {
        if (!relevantFormulas.get(gIndex)) {
          continue;
        }
        relevantIndex += 1;
        if (!activeFormulas.get(gIndex)) {
          continue;
        }
        activeIndex += 1;

        // Find priority of monitor edge in cache
        int priority = -1;
        BddSet[] monitorEdgePriorities = monitorPriorities[relevantIndex];
        for (int i = 0; i < monitorEdgePriorities.length; i++) {
          BddSet priorityValuation = monitorEdgePriorities[i];
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
          edgeAcceptanceSet.set(pair.finSet());
          return edgeAcceptanceSet;
        }
        int succeedPriority = 2 * ranking.get(activeIndex) + 1;
        if (priority > succeedPriority) {
          // Nothing relevant happened
          continue;
        }
        if (priority % 2 == 0) {
          // Merged at some lower rank
          edgeAcceptanceSet.set(pair.finSet());
          return edgeAcceptanceSet;
        }
        if (priority == succeedPriority) {
          // Succeeded at the current rank
          edgeAcceptanceSet.set(pair.infSet(activeIndex));
        }
      }

      return edgeAcceptanceSet;
    }

    boolean monitorsEntail(RabinizerState state) {
      return monitorsEntail(state.monitorStates(), null, state.masterState());
    }

    private boolean monitorsEntail(List<MonitorState> monitorStates, @Nullable BitSet valuation,
      EquivalenceClass consequent) {
      boolean eager = valuation != null;

      // Check the M^|G|_r condition for this particular edge. More precisely, we need to check if
      // the operators together with the monitor states with at least the given ranking imply the
      // consequent (i.e. the master state).  We can omit the conjunction of the active operators
      // for the antecedent, since we will inject it anyway

      AtomicReference<EquivalenceClass> antecedent = new AtomicReference<>(
          eqFactory.of(BooleanConstant.TRUE));
      int relevantIndex2 = -1;
      int activeIndex2 = -1;
      for (int gIndex1 = 0; gIndex1 < activeFormulas.length(); gIndex1++) {
        if (!relevantFormulas.get(gIndex1)) {
          continue;
        }
        relevantIndex2 += 1;
        if (!activeFormulas.get(gIndex1)) {
          continue;
        }
        activeIndex2 += 1;
        MonitorState monitorState = monitorStates.get(relevantIndex2);

        List<EquivalenceClass> monitorStateRanking = monitorState.formulaRanking();
        int rank = ranking.get(activeIndex2);
        for (int stateIndex = rank; stateIndex < monitorStateRanking.size(); stateIndex++) {
          EquivalenceClass rankEntry = monitorStateRanking.get(stateIndex);
          EquivalenceClass state = eager ? rankEntry.temporalStep(valuation) : rankEntry;
          antecedent.updateAndGet(clazz -> clazz.and(state));
        }
      }

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

        int relevantIndex1 = -1;
        int activeIndex1 = -1;
        for (int gIndex = 0; gIndex < activeFormulas.length(); gIndex++) {
          if (!relevantFormulas.get(gIndex)) {
            continue;
          }
          relevantIndex1 += 1;
          if (!activeFormulas.get(gIndex)) {
            continue;
          }
          activeIndex1 += 1;
          List<EquivalenceClass> monitorStateRanking =
            monitorStates.get(relevantIndex1).formulaRanking();
          int rank = ranking.get(activeIndex1);

          int size = monitorStateRanking.size();
          if (rank <= size) {
            activeMonitorStates.addAll(monitorStateRanking.subList(rank, size));
          }
        }
        String rankingString = ranking.toString();
        String log = String.format("Subset %s, ranking %s, and monitor states %s (strengthened: "
            + "%s), valuation %s; entails %s (weakened: %s): %s", activeFormulaSet, rankingString,
          activeMonitorStates, strengthenedAntecedent, valuation, consequent,
          weakenedConsequent, result);
        logger.log(Level.FINEST, log);
      }

      return result;
    }

    boolean monitorsEntailEager(RabinizerState state, BitSet valuation) {
      return monitorsEntail(state.monitorStates(), valuation, state.masterState());
    }
  }

  static final class EvaluateVisitor extends Converter {
    private final EquivalenceClass environment;
    private final EquivalenceClassFactory factory;

    EvaluateVisitor(Collection<GOperator> gMonitors, EquivalenceClass label) {
      super(SyntacticFragment.FGMU);
      this.factory = label.factory();
      this.environment = label.and(factory.of(
        Conjunction
          .of(Stream.concat(gMonitors.stream(), gMonitors.stream().map(
            Formula.UnaryTemporalOperator::operand)))));
    }

    private boolean isImplied(Formula formula) {
      return environment.implies(factory.of(formula));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      if (isImplied(disjunction)) {
        return BooleanConstant.TRUE;
      }

      return Disjunction.of(disjunction.map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
      if (isImplied(fOperator)) {
        return BooleanConstant.TRUE;
      }

      return FOperator.of(fOperator.operand().accept(this));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      if (isImplied(gOperator)) {
        return BooleanConstant.TRUE;
      }

      return BooleanConstant.of(gOperator.operand().accept(this).equals(BooleanConstant.TRUE));
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

      return MOperator.of(mOperator.leftOperand().accept(this),
        mOperator.rightOperand().accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      if (isImplied(uOperator)) {
        return BooleanConstant.TRUE;
      }

      return UOperator.of(uOperator.leftOperand().accept(this),
        uOperator.rightOperand().accept(this));
    }

    @Override
    public Formula visit(XOperator xOperator) {
      if (isImplied(xOperator)) {
        return BooleanConstant.TRUE;
      }

      return XOperator.of(xOperator.operand().accept(this));
    }
  }
}
