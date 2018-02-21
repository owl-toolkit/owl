package owl.translations.rabinizer;

import static owl.translations.rabinizer.MonitorStateFactory.isSink;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import de.tum.in.naturals.Arrays2;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.visitors.Collector;

final class MonitorBuilderNoAcceptance {
  private static final Predicate<Formula> NO_SUB_FORMULA = formula ->
    Collector.collectGOperators(formula).isEmpty();
  private static final Logger logger = Logger.getLogger(MonitorBuilderNoAcceptance.class.getName());
  private final GOperator gOperator;
  private final EquivalenceClass initialClass;
  private final boolean isSafety;
  private final Set<GSet> relevantSets;
  private final MonitorStateFactory stateFactory;
  private final ValuationSetFactory vsFactory;

  private MonitorBuilderNoAcceptance(GOperator gOperator, EquivalenceClass formula,
    Collection<GSet> relevantSets, ValuationSetFactory vsFactory, boolean eager) {
    this.gOperator = gOperator;
    this.vsFactory = vsFactory;

    isSafety = formula.testSupport(Fragments::isFinite);
    boolean noSubFormula = isSafety || formula.testSupport(NO_SUB_FORMULA);
    assert !isSafety || formula.testSupport(NO_SUB_FORMULA);

    logger.log(Level.FINE, "Creating builder for formula {0} and relevant sets {1}; "
        + "safety: {2}, no G-sub: {3}",
      new Object[] {formula, relevantSets, isSafety, noSubFormula});

    this.stateFactory = new MonitorStateFactory(eager, noSubFormula);
    this.relevantSets = ImmutableSet.copyOf(relevantSets);

    initialClass = stateFactory.getInitialState(formula);
  }

  static MonitorAutomaton create(GOperator gOperator, EquivalenceClass operand,
    Collection<GSet> relevantSets, ValuationSetFactory vsFactory, boolean eager) {
    return new MonitorBuilderNoAcceptance(gOperator, operand, relevantSets, vsFactory, eager)
      .build();
  }

  private static void optimizeInitialState(
    MutableAutomaton<MonitorState, ParityAcceptance> monitor) {
    // Since the monitors handle "F G <psi>", we can skip non-repeating prefixes
    logger.log(Level.FINER, "Optimizing initial state");
    List<Set<MonitorState>> sccs = SccDecomposition.computeSccs(monitor, false);
    MonitorState initialState = monitor.getInitialState();

    BitSet emptyBitSet = new BitSet(0);
    MonitorState optimizedInitialState = initialState;
    Predicate<MonitorState> isTransient = state ->
      sccs.parallelStream().noneMatch(scc -> scc.contains(state));
    while (isTransient.test(optimizedInitialState)) {
      assert optimizedInitialState != null;
      optimizedInitialState = monitor.getSuccessor(optimizedInitialState, emptyBitSet);
    }

    assert optimizedInitialState != null;
    if (!Objects.equals(optimizedInitialState, initialState)) {
      monitor.setInitialState(optimizedInitialState);
      monitor.removeUnreachableStates();
    }
  }

  private MonitorAutomaton build() {
    // We start with the (q0, bot, bot, ...) ranking
    MonitorState initialState = MonitorState.of(initialClass);

    MutableAutomaton<MonitorState, ParityAcceptance> monitor =
      MutableAutomatonFactory.create(new ParityAcceptance(0, Parity.MIN_ODD), vsFactory);
    monitor.setName(String.format("Monitor for %s", initialClass));
    monitor.addState(initialState);
    monitor.setInitialState(initialState);

    BiFunction<MonitorState, BitSet, Edge<MonitorState>> successorFunction =
      isSafety ? this::getSuccessorSafety : this::getSuccessor;
    AutomatonUtil.exploreDeterministic(monitor, Set.of(initialState),
      successorFunction, new AtomicInteger(0));

    if (!isSafety) {
      optimizeInitialState(monitor);
    }

    return new MonitorAutomaton(gOperator, Maps.asMap(relevantSets, set -> monitor));
  }

  private Edge<MonitorState> getSuccessor(MonitorState currentState, BitSet valuation) {
    List<EquivalenceClass> currentRanking = currentState.formulaRanking();
    int currentRankingSize = currentRanking.size();

    // Construct the successor ranking. At most one new class can be introduced (if the initial
    // formula is not ranked, it is re-created as youngest). If there are less, the array is shrunk
    // afterwards.
    EquivalenceClass[] successorRanking = new EquivalenceClass[currentRankingSize + 1];

    int successorRankingSize = 0;
    boolean successorContainsInitial = false;

    //noinspection LabeledStatement
    rank:
    for (int rank = 0; rank < currentRankingSize; rank++) {
      assert successorRankingSize <= rank;

      // Perform one step of "af_G(currentClass)" - we unfold all temporal operators except G
      EquivalenceClass currentClass = currentRanking.get(rank);
      EquivalenceClass successorClass = stateFactory.getRankSuccessor(currentClass, valuation);

      if (successorClass.isFalse() || isSink(successorClass)) {
        continue;
      }

      // Now, check if we already have this successor class
      for (int j = 0; j < successorRankingSize; j++) {
        // Iterate over all previous (older) classes and check for equality - in that case a merge
        // happened
        EquivalenceClass olderSuccessorClass = successorRanking[j];
        assert olderSuccessorClass != null && !isSink(olderSuccessorClass);

        if (successorClass.equals(olderSuccessorClass)) {
          // No need to further investigate this class - already did this for the older one
          continue rank;
        }
      }

      // This is a genuine successor - add it to the ranking
      successorContainsInitial = successorContainsInitial || initialClass.equals(successorClass);
      successorRanking[successorRankingSize] = successorClass;
      successorRankingSize += 1;
    }

    // We updated all rankings and computed some acceptance information. Now, do bookkeeping
    // and update the automata

    // If there is no initial state in the array, we need to recreate it as youngest token
    assert successorContainsInitial == List.of(successorRanking).contains(initialClass);
    if (!successorContainsInitial) {
      successorRanking[successorRankingSize] = initialClass;
      successorRankingSize += 1;
    }

    // If there were some removed classes, compact the array, otherwise take it as is
    successorRanking = Arrays2.trim(successorRanking, successorRankingSize);
    return Edge.of(MonitorState.of(successorRanking));
  }

  private Edge<MonitorState> getSuccessorSafety(MonitorState currentState, BitSet valuation) {
    EquivalenceClass currentRanking = Iterables.getOnlyElement(currentState.formulaRanking());

    EquivalenceClass successorClass = stateFactory.getRankSuccessor(currentRanking, valuation);
    EquivalenceClass successorRanking = successorClass.isFalse()
      ? this.initialClass
      : successorClass.and(initialClass);
    return Edge.of(MonitorState.of(successorRanking));
  }
}
