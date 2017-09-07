package owl.translations.rabinizer;

import static owl.translations.rabinizer.MonitorStateFactory.isAccepting;
import static owl.translations.rabinizer.MonitorStateFactory.isSink;

import com.google.common.collect.ImmutableMap;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.algorithms.SccAnalyser;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.visitors.Collector;

final class MonitorBuilder {
  private static final Predicate<Formula> NO_SUB_FORMULA = formula ->
    Collector.collectGOperators(formula).isEmpty();
  private static final Logger logger = Logger.getLogger(MonitorBuilder.class.getName());
  private final EquivalenceClass initialClass;
  private final boolean isSafety;
  private final MutableAutomaton<MonitorState, ParityAcceptance>[] monitorAutomata;
  private final GSet[] relevantSets;
  private final MonitorStateFactory stateFactory;
  private final ValuationSetFactory vsFactory;

  private MonitorBuilder(EquivalenceClass formula, Collection<GSet> relevantSets,
    ValuationSetFactory vsFactory, boolean eager) {
    this.vsFactory = vsFactory;

    isSafety = formula.testSupport(Fragments::isX);
    boolean noSubFormula = isSafety || formula.testSupport(NO_SUB_FORMULA);
    assert !isSafety || formula.testSupport(NO_SUB_FORMULA);

    logger.log(Level.FINE, "Creating builder for formula {0} and relevant sets {1}; "
        + "safety: {2}, no G-sub: {3}",
      new Object[] {formula, relevantSets, isSafety, noSubFormula});

    this.stateFactory = new MonitorStateFactory(eager, noSubFormula);

    this.relevantSets = relevantSets.toArray(new GSet[relevantSets.size()]);
    assert !noSubFormula || this.relevantSets.length == 1;

    //noinspection unchecked,rawtypes
    this.monitorAutomata = new MutableAutomaton[this.relevantSets.length];
    initialClass = stateFactory.getInitialState(formula);
  }


  static MonitorAutomaton create(EquivalenceClass formula, Collection<GSet> relevantSets,
    ValuationSetFactory vsFactory, boolean eager) {
    return new MonitorBuilder(formula, relevantSets, vsFactory, eager).build();
  }

  private static int fail() {
    return 0;
  }

  private static int merge(int i) {
    return 2 * i;
  }

  private static int succeed(int i) {
    return 2 * i + 1;
  }

  private MonitorAutomaton build() {
    // We start with the (q0, bot, bot, ...) ranking
    MonitorState initialState = new MonitorState(new EquivalenceClass[] {initialClass});

    for (int i = 0; i < monitorAutomata.length; i++) {
      MutableAutomaton<MonitorState, ParityAcceptance> monitor = AutomatonFactory
        .createMutableAutomaton(new ParityAcceptance(0), vsFactory);
      monitor.setName(String.format("Monitor for %s with %s", initialClass, this.relevantSets[i]));
      monitor.addState(initialState);
      monitor.setInitialState(initialState);
      monitorAutomata[i] = monitor;
    }

    // Initialize some cached values
    int alphabetSize = vsFactory.getSize();
    BitSet sensitiveAlphabet = new BitSet(alphabetSize);

    int numberOfRelevantSets = relevantSets.length;
    // Cache if q0 is accepting under some context
    boolean[] initialAccepting = new boolean[numberOfRelevantSets];
    for (int gIndex = 0; gIndex < relevantSets.length; gIndex++) {
      initialAccepting[gIndex] = isAccepting(initialClass, relevantSets[gIndex]);
    }
    // Tracks the maximal priority used by each relevant set
    int[] maximalPriority = new int[numberOfRelevantSets];
    Arrays.fill(maximalPriority, -1);

    // Now we explore the transition system (with BFS)
    logger.log(Level.FINER, "Exploring monitor transition system");

    Set<MonitorState> exploredStates = new HashSet<>(Collections.singletonList(initialState));
    Queue<MonitorState> workQueue = new ArrayDeque<>(exploredStates);

    // Use one array throughout the loop to store the edge priorities to avoid re-allocation
    // Since we store the relevant sets as immutable list, we have an (arbitrary) numbering of them
    // and can assign the fail, succeed(i) and merge(i) information to these indices.
    int[] priorities = new int[numberOfRelevantSets];

    while (!workQueue.isEmpty()) {
      MonitorState currentState = workQueue.poll();
      stateFactory.addSensitiveAlphabet(sensitiveAlphabet, currentState);
      EquivalenceClass[] currentRanking = currentState.formulaRanking;
      int currentRankingSize = currentRanking.length;

      // TODO Maybe only re-allocate if too small
      boolean[] classIsInitial = new boolean[currentRankingSize];
      for (int i = 0; i < currentRankingSize; i++) {
        classIsInitial[i] = currentRanking[i].equals(initialClass);
      }

      for (BitSet valuation : BitSets.powerSet(sensitiveAlphabet)) {
        MonitorState successorState;

        if (isSafety) {
          successorState = getSuccessorSafety(currentState, valuation, priorities);
        } else {
          // Reset loop data structures
          Arrays.fill(priorities, Integer.MAX_VALUE);
          successorState =
            getSuccessor(currentState, valuation, priorities, classIsInitial, initialAccepting);
        }

        if (exploredStates.add(successorState)) {
          workQueue.add(successorState);
        }

        // Create the edges for each automaton
        ValuationSet valuationSet = vsFactory.createValuationSet(valuation, sensitiveAlphabet);
        for (int contextIndex = 0; contextIndex < relevantSets.length; contextIndex++) {
          int priority = priorities[contextIndex];
          Edge<MonitorState> edge;
          if (priority == Integer.MAX_VALUE) {
            // No event occurred
            edge = Edges.create(successorState);
          } else {
            edge = Edges.create(successorState, priority);
            if (maximalPriority[contextIndex] < priority) {
              // Found new maximal priority
              maximalPriority[contextIndex] = priority;
            }
          }
          monitorAutomata[contextIndex].addEdge(currentState, valuationSet.copy(), edge);
        }
        valuationSet.free();
      }
      sensitiveAlphabet.clear();
    }

    if (!isSafety) {
      optimizeInitialState();
    }

    ImmutableMap.Builder<GSet, Automaton<MonitorState, ParityAcceptance>> builder
      = ImmutableMap.builder();
    for (int contextIndex = 0; contextIndex < relevantSets.length; contextIndex++) {
      MutableAutomaton<MonitorState, ParityAcceptance> monitor = monitorAutomata[contextIndex];
      monitor.getAcceptance().setAcceptanceSets(maximalPriority[contextIndex] + 1);
      builder.put(relevantSets[contextIndex], monitor);

      assert monitor.isDeterministic() : String.format("%s monitor for %s is not deterministic",
        relevantSets[contextIndex], initialClass);
    }

    return new MonitorAutomaton(builder.build());
  }

  private MonitorState getSuccessor(MonitorState currentState, BitSet valuation, int[] priorities,
    boolean[] classIsInitial, boolean[] initialAccepting) {
    EquivalenceClass[] currentRanking = currentState.formulaRanking;
    int currentRankingSize = currentRanking.length;
    int numberOfRelevantSets = relevantSets.length;

    /*
     * We compute all transitions of the current ranking and ignore all sinks. We also delete
     * those entries which are duplicates (only keep the seniors). Simultaneously we compute
     * acceptance information - fail, succeed(i) and merge(i) - for each gSet.
     *
     * fail: We moved to a non-accepting sink.
     * succeed(i): qi was not accepting and moved to an accepting state OR q0 is accepting and has
     *   rank i in the source ranking.
     * merge(i): There exist q, q1 and q2 (with rank(q1) < i and succ(q2) != null)) such that
     *   succ(q1) == q == succ(q2) and q is not accepting OR q0 is not accepting and there exists
     *   q with succ(q) = q0 and sr(q) < i.
     *
     * Note that since this is an appearance-record type construction, we only need to compute the
     * smallest event index - if the transition is failed, we don't need to compute anything else.
     */

    // Construct the successor ranking. At most one new class can be introduced (if the initial
    // formula is not ranked, it is re-created as youngest). If there are less, the array is shrunk
    // afterwards.
    EquivalenceClass[] successorRanking = new EquivalenceClass[currentRankingSize + 1];

    int successorRankingSize = 0;
    boolean successorContainsInitial = false;

    for (int rank = 0; rank < currentRankingSize; rank++) {
      assert successorRankingSize <= rank;

      // Perform one step of "af_G(currentClass)" - we unfold all temporal operators except G
      EquivalenceClass currentClass = currentRanking[rank];
      EquivalenceClass successorClass =
        stateFactory.getRankSuccessor(currentClass, valuation);

      for (int contextIndex = 0; contextIndex < numberOfRelevantSets; contextIndex++) {
        // Final states are closed under successor - if the initial state is accepting, any other
        // state is, too, and we have a trivial succeed in any case
        if (initialAccepting[contextIndex]) {
          assert isAccepting(successorClass, relevantSets[contextIndex]);
          priorities[contextIndex] = succeed(0);
        }
      }

      if (successorClass.isFalse()) {
        // This class is a non-accepting sink for any context, hence this transition fails.
        assert isSink(successorClass) && Arrays.stream(relevantSets).noneMatch(
          set -> isAccepting(successorClass, set));
        Arrays.fill(priorities, fail());
        continue;
      }

      // TODO Smartly cache isAccepting(successorClass) and isAccepting(currentClass)

      // Now, check if we already have this successor class
      boolean merged = false;
      for (int j = 0; j < successorRankingSize; j++) {
        // Iterate over all previous (older) classes and check for equality - in that case a merge
        // happened
        EquivalenceClass olderSuccessorClass = successorRanking[j];
        assert olderSuccessorClass != null && !isSink(olderSuccessorClass);

        if (successorClass.equals(olderSuccessorClass)) {
          // This class merges into the older one - determine merge(i)
          for (int contextIndex = 0; contextIndex < numberOfRelevantSets; contextIndex++) {
            if (priorities[contextIndex] < Integer.MAX_VALUE) {
              // Some event occurred earlier for this context - no need to check for merge(i)
              continue;
            }
            // Condition for merge(i) is that the successor is not accepting - otherwise it is a
            // succeed.
            if (isAccepting(successorClass, relevantSets[contextIndex])) {
              priorities[contextIndex] = succeed(rank);
            } else {
              assert !isAccepting(currentClass, relevantSets[contextIndex]);
              priorities[contextIndex] = merge(rank);
            }
          }
          merged = true;
          break;
        }
      }
      if (merged) {
        // No need to further investigate this class - already did this for the older one
        continue;
      }

      // Check if this state is a sink (and thus maybe the whole transition is failing)
      if (isSink(successorClass)) {
        // If we move to a non-accepting sink, the transition is a fail transition, if instead we
        // move from a non-accepting state to an accepting sink, it is succeed(i). Also, as a
        // special case, we also succeed(i) if q0 is accepting, is at rank i and the sink is
        // non-rejecting.
        for (int contextIndex = 0; contextIndex < relevantSets.length; contextIndex++) {
          if (!isAccepting(successorClass, relevantSets[contextIndex])) {
            priorities[contextIndex] = fail();
          } else if (priorities[contextIndex] == Integer.MAX_VALUE) {
            // Successor is accepting and we had no event previously
            //noinspection IfStatementWithIdenticalBranches
            if (initialAccepting[contextIndex] && classIsInitial[rank]) {
              // q0 is accepting and at rank(i)
              priorities[contextIndex] = succeed(rank);
            } else if (!isAccepting(currentClass, relevantSets[contextIndex])) {
              // We move from non-accepting to accepting
              priorities[contextIndex] = succeed(rank);
            }
          }
        }

        // If we move to a sink, we don't add the class to the ranking
        continue;
      }

      // No merge occurred and the successor is not a sink - check for succeed(i)
      for (int contextIndex = 0; contextIndex < relevantSets.length; contextIndex++) {
        if (priorities[contextIndex] < Integer.MAX_VALUE) {
          // Some event occurred earlier for this context - no need to check for succeed(i)
          continue;
        }
        // For succeed(i) we have to either move into an accepting initial state at rank i
        // or move from non-accepting rank i to some accepting state
        if (initialAccepting[contextIndex] && classIsInitial[rank]
          || !isAccepting(currentClass, relevantSets[contextIndex])
          && isAccepting(successorClass, relevantSets[contextIndex])) {
          // First case: q0 is accepting and at rank(i)
          // Second case: We move from non-accepting to accepting
          priorities[contextIndex] = succeed(rank);
        }
      }

      // This is a genuine successor - add it to the ranking
      successorContainsInitial |= initialClass.equals(successorClass);
      successorRanking[successorRankingSize] = successorClass;
      successorRankingSize += 1;
    }

    // We updated all rankings and computed some acceptance information. Now, do bookkeeping
    // and update the automata

    // If there is no initial state in the array, we need to recreate it as youngest token
    assert successorContainsInitial == Arrays.asList(successorRanking).contains(initialClass);
    if (!successorContainsInitial) {
      successorRanking[successorRankingSize] = initialClass;
      successorRankingSize += 1;
    }

    // If there were some removed classes, compact the array, otherwise take it as is
    EquivalenceClass[] trimmedSuccessorRanking =
      successorRankingSize < successorRanking.length
        ? Arrays.copyOf(successorRanking, successorRankingSize)
        : successorRanking;

    return new MonitorState(trimmedSuccessorRanking);
  }

  private MonitorState getSuccessorSafety(MonitorState currentState, BitSet valuation,
    int[] priorities) {
    EquivalenceClass[] currentRanking = currentState.formulaRanking;
    assert currentRanking.length == 1 && priorities.length == 1;

    EquivalenceClass successorClass =
      stateFactory.getRankSuccessor(currentRanking[0], valuation);
    EquivalenceClass[] successorRanking = new EquivalenceClass[1];
    if (successorClass.isFalse()) {
      priorities[0] = fail();
      successorRanking[0] = initialClass;
    } else {
      priorities[0] = succeed(0);
      successorRanking[0] = successorClass.and(initialClass);
    }

    return new MonitorState(successorRanking);
  }

  private void optimizeInitialState() {
    // Since the monitors handle "F G <psi>", we can skip non-repeating prefixes
    logger.log(Level.FINER, "Optimizing initial state");
    MutableAutomaton<MonitorState, ParityAcceptance> anyMonitor = monitorAutomata[0];
    List<Set<MonitorState>> sccs = SccAnalyser.computeSccs(anyMonitor, false);
    MonitorState initialState = anyMonitor.getInitialState();

    BitSet emptyBitSet = new BitSet(0);
    MonitorState optimizedInitialState = initialState;
    Predicate<MonitorState> isTransient = state ->
      sccs.parallelStream().noneMatch(scc -> scc.contains(state));
    while (isTransient.test(optimizedInitialState)) {
      assert optimizedInitialState != null;
      optimizedInitialState = anyMonitor.getSuccessor(optimizedInitialState, emptyBitSet);
    }

    assert optimizedInitialState != null;
    if (!Objects.equals(optimizedInitialState, initialState)) {
      anyMonitor.setInitialState(optimizedInitialState);
      Set<MonitorState> unreachableStates = anyMonitor.removeUnreachableStates();
      for (int index = 1; index < monitorAutomata.length; index++) {
        MutableAutomaton<MonitorState, ParityAcceptance> monitor = monitorAutomata[index];
        monitor.setInitialState(optimizedInitialState);
        monitor.removeStates(unreachableStates);
      }
    }
  }
}
