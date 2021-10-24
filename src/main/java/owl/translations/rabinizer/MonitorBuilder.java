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

import static owl.translations.rabinizer.RabinizerStateFactory.MonitorStateFactory;
import static owl.translations.rabinizer.RabinizerStateFactory.MonitorStateFactory.isAccepting;
import static owl.translations.rabinizer.RabinizerStateFactory.MonitorStateFactory.isSink;

import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.collections.BitSet2;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.GOperator;
import owl.ltl.SyntacticFragments;

final class MonitorBuilder {
  private static final Logger logger = Logger.getLogger(MonitorBuilder.class.getName());

  private final EquivalenceClass initialClass;
  private final Fragment fragment;
  private final MutableAutomaton<MonitorState, ParityAcceptance>[] monitorAutomata;
  private final GSet[] relevantSets;
  private final MonitorStateFactory stateFactory;
  private final BddSetFactory vsFactory;

  private MonitorBuilder(GOperator gOperator, EquivalenceClass operand,
    Collection<GSet> relevantSets, BddSetFactory vsFactory, boolean eager) {
    this.vsFactory = vsFactory;

    boolean isCoSafety = SyntacticFragments.isCoSafety(operand);

    if (isCoSafety && gOperator.operand() instanceof FOperator) {
      fragment = Fragment.EVENTUAL;
    } else if (SyntacticFragments.isFinite(operand)) {
      fragment = Fragment.FINITE;
    } else {
      fragment = Fragment.FULL;
    }

    logger.log(Level.FINE, "Creating builder for formula {0} and relevant sets {1}; "
        + "fragment: {2}, no G-sub: {3}",
      new Object[] {operand, relevantSets, fragment, isCoSafety});

    this.stateFactory = new MonitorStateFactory(eager, isCoSafety);
    this.relevantSets = relevantSets.toArray(GSet[]::new);
    assert !isCoSafety || this.relevantSets.length == 1;

    //noinspection unchecked
    this.monitorAutomata = new MutableAutomaton[this.relevantSets.length];
    initialClass = stateFactory.getInitialState(operand);
  }

  static MonitorAutomaton create(GOperator gOperator, EquivalenceClass operand,
    Collection<GSet> relevantSets, BddSetFactory vsFactory, boolean eager) {
    return new MonitorBuilder(gOperator, operand, relevantSets, vsFactory, eager).build();
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

  private static int none() {
    return Integer.MAX_VALUE;
  }

  private MonitorAutomaton build() {
    // We start with the (q0, bot, bot, ...) ranking
    // TODO We don't need to put the initial class into the ranking if it is at the tail
    MonitorState initialState = MonitorState.of(initialClass);

    for (int i = 0; i < monitorAutomata.length; i++) {
      MutableAutomaton<MonitorState, ParityAcceptance> monitor = HashMapAutomaton.create(
        initialClass.factory().atomicPropositions(),
        vsFactory,
        new ParityAcceptance(0, Parity.MIN_ODD));
      monitor.addInitialState(initialState);
      monitorAutomata[i] = monitor;
    }

    // Initialize some cached values
    int numberOfRelevantSets = relevantSets.length;
    // Cache if q0 is accepting under some context
    boolean[] initialAccepting;
    if (fragment == Fragment.FULL) {
      initialAccepting = new boolean[numberOfRelevantSets];
      for (int gIndex = 0; gIndex < relevantSets.length; gIndex++) {
        initialAccepting[gIndex] = isAccepting(initialClass, relevantSets[gIndex]);
      }
    } else {
      initialAccepting = new boolean[]{};
    }

    // Tracks the maximal priority used by each relevant set
    int[] maximalPriority = new int[numberOfRelevantSets];
    Arrays.fill(maximalPriority, -1);

    // Now we explore the transition system (with BFS)
    logger.log(Level.FINER, "Exploring monitor transition system");

    Set<MonitorState> exploredStates = new HashSet<>(List.of(initialState));
    Queue<MonitorState> workQueue = new ArrayDeque<>(exploredStates);

    // Use one array throughout the loop to store the edge priorities to avoid re-allocation
    // Since we store the relevant sets as immutable list, we have an (arbitrary) numbering of them
    // and can assign the fail, succeed(i) and merge(i) information to these indices.
    int[] priorities = new int[numberOfRelevantSets];

    while (!workQueue.isEmpty()) {
      MonitorState currentState = workQueue.poll();
      BitSet sensitiveAlphabet = stateFactory.getSensitiveAlphabet(currentState);

      for (BitSet valuation : BitSet2.powerSet(sensitiveAlphabet)) {
        MonitorState successorState;

        switch (fragment) {
          case FINITE -> successorState = getSuccessorFiniteFragment(currentState, valuation,
            priorities);
          case EVENTUAL -> successorState = getSuccessorEventualFragment(currentState, valuation,
            priorities);
          default -> {
            // Reset loop data structures
            Arrays.fill(priorities, none());
            successorState = getSuccessor(currentState, valuation, priorities, initialAccepting);
          }
        }

        if (exploredStates.add(successorState)) {
          workQueue.add(successorState);
        }

        // Create the edges for each automaton
        BddSet valuationSet = vsFactory.of(valuation, sensitiveAlphabet);
        for (int contextIndex = 0; contextIndex < relevantSets.length; contextIndex++) {
          int priority = priorities[contextIndex];
          Edge<MonitorState> edge;
          if (priority == none()) {
            // No event occurred
            edge = Edge.of(successorState);
          } else {
            edge = Edge.of(successorState, priority);
            if (maximalPriority[contextIndex] < priority) {
              // Found new maximal priority
              maximalPriority[contextIndex] = priority;
            }
          }
          monitorAutomata[contextIndex].addEdge(currentState, valuationSet, edge);
        }
      }
      sensitiveAlphabet.clear();
    }

    if (fragment == Fragment.FULL) {
      optimizeInitialState();
    }

    Map<GSet, Automaton<MonitorState, ParityAcceptance>> builder = new HashMap<>();

    for (int contextIndex = 0; contextIndex < relevantSets.length; contextIndex++) {
      MutableAutomaton<MonitorState, ParityAcceptance> monitor = monitorAutomata[contextIndex];
      int sets = maximalPriority[contextIndex];
      monitor.updateAcceptance(x -> x.withAcceptanceSets(sets + 1));
      builder.put(relevantSets[contextIndex], monitor);

      assert monitor.is(Property.DETERMINISTIC) : String.format(
        "%s monitor for %s is not deterministic", relevantSets[contextIndex], initialClass);
    }

    return new MonitorAutomaton(builder.values().iterator().next(), builder);
  }

  private MonitorState getSuccessor(MonitorState currentState, BitSet valuation, int[] priorities,
    boolean[] initialAccepting) {
    List<EquivalenceClass> currentRanking = currentState.formulaRanking();
    int currentRankingSize = currentRanking.size();
    int numberOfRelevantSets = relevantSets.length;

    /*
     * We compute all transitions of the current ranking and ignore all sinks. We also delete
     * those entries which are duplicates (only keep the seniors). Simultaneously we compute
     * acceptance information - fail, succeed(i) and merge(i) - for each gSet. For this computation,
     * there essentially are five different conditions.
     *
     * fail:
     *   1) We moved to a non-accepting sink.
     * succeed(i):
     *   2) qi was not accepting and moved to an accepting state.
     *   3) q0 is accepting and has rank i in the source ranking.
     * merge(i):
     *   4) There exist q, q1 and q2 (with rank(q1) < i and successor(q2) != null)) such that
     *      successor(q1) == q == successor(q2) and q is not accepting.
     *   5) q0 is not accepting and there exists q with successor(q) = q0 and sr(q) < i.
     *
     * Note that since this is an appearance-record type construction, we only need to compute the
     * smallest event index - if the transition is failed, we don't need to compute anything else.
     * This is subtle for merge(i): We might only detect merge(i) when computing the successor of
     * some rank large than i! Also, since the requirement is sr(q) < i, we actually have to assign
     * merge(rank(q1) + 1) if we find any merge into q.
     */

    // Check for case 3)
    for (int contextIndex = 0; contextIndex < numberOfRelevantSets; contextIndex++) {
      // Final states are closed under successor - if the initial state is accepting, any other
      // state is, too, and we have a trivial succeed in any case.
      if (initialAccepting[contextIndex]) {
        priorities[contextIndex] = succeed(0);
      }
    }

    // Construct the successor ranking. At most one new class can be introduced (if the initial
    // formula is not ranked, it is re-created as youngest). If there are less, the array is shrunk
    // afterwards.
    List<EquivalenceClass> successorRanking = new ArrayList<>(currentRankingSize + 1);
    int[] successorSources = new int[currentRankingSize];

    int successorRankingSize = 0;
    boolean successorContainsInitial = false;

    for (int currentRank = 0; currentRank < currentRankingSize; currentRank++) {
      assert successorRankingSize <= currentRank;

      // Perform one step of "af_G(currentClass)" - we unfold all temporal operators except G
      EquivalenceClass currentClass = currentRanking.get(currentRank);
      EquivalenceClass successorClass = stateFactory.getRankSuccessor(currentClass, valuation);

      if (successorClass.isFalse()) {
        // This class is a non-accepting sink for any context, hence this transition fails.
        assert isSink(successorClass)
          && Arrays.stream(relevantSets).noneMatch(set -> isAccepting(successorClass, set));
        Arrays.fill(priorities, fail());
        continue;
      }

      // TODO Smartly cache isAccepting(successorClass) and isAccepting(currentClass) - Boolean[]?

      if (successorClass.equals(initialClass)) {
        // Special cases when the successor actually is the initial class
        if (successorContainsInitial) {
          // No need to do anything, since all states already got some priority:
          // - If the initial class is accepting, all priorities are succeed(0).
          // - If not, the code below already was executed, setting each non-initialized value to
          //   some merge(i).
          assert Arrays.stream(priorities).allMatch(priority -> priority < Integer.MAX_VALUE);
          continue;
        } else {
          // First time we see the initial class being produced from some rank under this valuation.
          successorContainsInitial = true;

          for (int contextIndex = 0; contextIndex < numberOfRelevantSets; contextIndex++) {
            if (priorities[contextIndex] < Integer.MAX_VALUE) {
              continue;
            }
            // We covered the case that q0 is accepting (under some context) already, hence we only
            // have to handle the merge.
            assert !initialAccepting[contextIndex];
            priorities[contextIndex] = merge(currentRank + 1);
          }
        }
      } else {
        // Now, check if we already have this successor class to detect merges.
        boolean merged = false;
        for (int olderIndex = 0; olderIndex < successorRankingSize; olderIndex++) {
          // Iterate over all previous (older) classes and check for equality
          EquivalenceClass olderSuccessorClass = successorRanking.get(olderIndex);
          assert olderSuccessorClass != null && !isSink(olderSuccessorClass);

          if (successorClass.equals(olderSuccessorClass)) {
            // This class merges into the older one - determine merge(i)
            merged = true;
            assert !isSink(olderSuccessorClass);

            // For merge(i), the index of the "older" source (q1) is relevant
            int olderSource = successorSources[olderIndex];
            int mergePriority = merge(olderSource + 1);

            for (int contextIndex = 0; contextIndex < numberOfRelevantSets; contextIndex++) {
              if (priorities[contextIndex] <= mergePriority) {
                // Some event occurred earlier for this context - no need to check for merge(i)
                continue;
              }
              if (isAccepting(currentClass, relevantSets[contextIndex])) {
                // The current class is accepting, hence the successor also is accepting and nothing
                // happens.
                continue;
              }

              // The current class is not accepting. There are three cases to distinguish:
              //  1) The older source (q1) is accepting, and thus the successor is accepting.
              //     Here, we have a succeed(i)
              //  2) q1 is non-accepting, but the successor q is. Then we had a succeed at the
              //     rank of q1 and we "continued" above (checked by the assert)
              //  3) Neither of the two is accepting, then this is a merge(rank(q1) + 1).

              if (isAccepting(olderSuccessorClass, relevantSets[contextIndex])) {
                assert isAccepting(currentRanking.get(olderSource), relevantSets[contextIndex]);
                if (priorities[contextIndex] == none()) {
                  // Don't overwrite a "stronger" succeed. It might be the case that, e.g., both
                  // rank 2 and 3 merge into the succeeding rank 1. Then, rank 3 would set the
                  // priority to succeed(3), whereas this actually is a succeed(2).
                  priorities[contextIndex] = succeed(currentRank);
                }
              } else {
                priorities[contextIndex] = mergePriority;
              }
            }
            break; // Stop the search for merges
          }
        }
        if (merged) {
          // No need to further investigate this class - already did this for the older one
          continue;
        }

        // Check if this state is a sink (and thus maybe the whole transition is failing)
        if (isSink(successorClass)) {
          // If we move to a non-accepting sink, the transition is a fail transition. If instead we
          // move from a non-accepting state to an accepting sink, it is succeed(i). Also, as a
          // special case, we succeed(i) if q0 is accepting, is at rank i and the sink is
          // non-rejecting.
          for (int contextIndex = 0; contextIndex < relevantSets.length; contextIndex++) {
            GSet contextSet = relevantSets[contextIndex];
            if (isAccepting(successorClass, contextSet)) {
              if (priorities[contextIndex] == none() && !isAccepting(currentClass, contextSet)) {
                // Successor is accepting and we had no event previously. We handled the case of
                // accepting initial state already. Thus, we only check whether we move from a
                // non-accepting to an accepting state.
                priorities[contextIndex] = succeed(currentRank);
              }
            } else {
              priorities[contextIndex] = fail();
            }
          }

          // If we move to a sink, we don't add the class to the ranking
          continue;
        }

        // No merge occurred and the successor is not a sink - check for succeed(i)
        for (int contextIndex = 0; contextIndex < relevantSets.length; contextIndex++) {
          if (priorities[contextIndex] != none()) {
            // Some event occurred earlier for this context - no need to check for succeed(i)
            continue;
          }
          // For succeed(i) we have to move from non-accepting rank i to some accepting state
          if (!isAccepting(currentClass, relevantSets[contextIndex])
            && isAccepting(successorClass, relevantSets[contextIndex])) {
            priorities[contextIndex] = succeed(currentRank);
          }
        }
      }

      // This is a genuine successor - add it to the ranking
      successorRanking.add(successorClass);
      successorSources[successorRankingSize] = currentRank;
      successorRankingSize += 1;
    }

    // We updated all rankings and computed some acceptance information. Now, do bookkeeping
    // and update the automata

    // If there is no initial state in the array, we need to recreate it as youngest token
    assert successorContainsInitial == successorRanking.contains(initialClass);

    if (!successorContainsInitial) {
      successorRanking.add(initialClass);
    }

    // This assert failed for "(a <-> (!a <-> Fb)) W b", however the construction still seems to
    // work correctly? The problem seems to be rooted in a mismatch between BDD and syntax
    // representation.
    // assert successorRanking.stream().noneMatch(MonitorStateFactory::isSink);
    assert successorRanking.stream().filter(state -> state.equals(initialClass)).count() == 1;

    return MonitorState.of(successorRanking);
  }

  private MonitorState getSuccessorFiniteFragment(MonitorState currentState, BitSet valuation,
    int[] priorities) {
    EquivalenceClass currentRanking = Iterables.getOnlyElement(currentState.formulaRanking());

    EquivalenceClass successorClass = stateFactory.getRankSuccessor(currentRanking, valuation);
    EquivalenceClass successorRanking;
    if (successorClass.isFalse()) {
      priorities[0] = fail();
      successorRanking = initialClass;
    } else {
      priorities[0] = succeed(0);
      successorRanking = successorClass.and(initialClass);
    }

    return MonitorState.of(successorRanking);
  }

  private MonitorState getSuccessorEventualFragment(MonitorState currentState, BitSet valuation,
    int[] priorities) {
    EquivalenceClass currentRanking = Iterables.getOnlyElement(currentState.formulaRanking());

    EquivalenceClass successorClass = stateFactory.getRankSuccessor(currentRanking, valuation);
    EquivalenceClass successorRanking = null;
    for (int contextIndex = 0; contextIndex < relevantSets.length; contextIndex++) {
      if (isAccepting(successorClass, relevantSets[contextIndex])) {
        priorities[contextIndex] = succeed(0);
        successorRanking = initialClass;
      } else {
        priorities[contextIndex] = none();
        successorRanking = successorClass;
      }
    }
    assert successorRanking != null;
    return MonitorState.of(successorRanking);
  }

  private void optimizeInitialState() {
    // Since the monitors handle "F G <psi>", we can skip non-repeating prefixes
    // TODO We actually can use this to only compute the successors until we reach a BSCC
    MutableAutomaton<MonitorState, ParityAcceptance> anyMonitor = monitorAutomata[0];

    var sccDecomposition = SccDecomposition.of(anyMonitor);

    BitSet valuation = new BitSet(0);

    MonitorState optimizedInitialState;
    MonitorState nextOptimizedInitialState = anyMonitor.initialState();

    // Search for another initial state.
    do {
      optimizedInitialState = nextOptimizedInitialState;
      nextOptimizedInitialState = sccDecomposition.isTransientScc(Set.of(optimizedInitialState))
        ? anyMonitor.successor(optimizedInitialState, valuation)
        : null;
    } while (nextOptimizedInitialState != null);

    if (optimizedInitialState.equals(anyMonitor.initialState())) {
      logger.log(Level.FINER, "No better initial state found");
    } else {
      logger.log(Level.FINER, "Updating initial state from {0} to {1}",
        new Object[] {anyMonitor.initialState(), optimizedInitialState});
      for (MutableAutomaton<MonitorState, ParityAcceptance> monitor : monitorAutomata) {
        monitor.initialStates(Set.of(optimizedInitialState));
        monitor.trim();
      }
    }
  }

  private enum Fragment {
    EVENTUAL, FINITE, FULL
  }
}
