/*
 * Copyright (C) 2016 - 2022  (See AUTHORS)
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

package owl.automaton.determinization;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.edge.Edge;
import owl.automaton.minimization.GfgNcwMinimization;
import owl.automaton.minimization.GfgNcwMinimization.CanonicalGfgNcw;
import owl.collections.BitSet2;
import owl.collections.Collections3;
import owl.collections.ImmutableBitSet;

public final class Determinization {

  private Determinization() {
  }

  public static <S> Automaton<Set<S>, AllAcceptance>
  determinizeAllAcceptance(Automaton<S, ? extends AllAcceptance> automaton) {

    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
        automaton.atomicPropositions(),
        automaton.factory(),
        Set.of(automaton.initialStates()),
        AllAcceptance.INSTANCE) {

      @Override
      public Edge<Set<S>> edgeImpl(Set<S> state, BitSet valuation) {
        Set<S> successors = state.stream()
            .flatMap(x -> automaton.successors(x, valuation).stream())
            .collect(Collectors.toUnmodifiableSet());
        return successors.isEmpty() ? null : Edge.of(successors);
      }
    };
  }

  public static <S> Automaton<BreakpointState<S>, CoBuchiAcceptance>
  determinizeCoBuchiAcceptance(Automaton<S, ? extends CoBuchiAcceptance> ncw) {

    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
        ncw.atomicPropositions(),
        ncw.factory(),
        Set.of(new BreakpointState<>(ncw.initialStates(), ncw.initialStates())),
        CoBuchiAcceptance.INSTANCE) {

      @Override
      public Edge<BreakpointState<S>> edgeImpl(
          BreakpointState<S> breakpointState, BitSet valuation) {

        Set<S> successors = new HashSet<>(breakpointState.allRuns.size());
        Set<S> acceptingSuccessors = new HashSet<>(breakpointState.acceptingRuns.size());

        for (S run : breakpointState.allRuns) {
          successors.addAll(ncw.successors(run, valuation));
        }

        for (S acceptingRun : breakpointState.acceptingRuns) {
          for (Edge<S> edge : ncw.edges(acceptingRun, valuation)) {
            if (edge.colours().isEmpty()) {
              acceptingSuccessors.add(edge.successor());
            }
          }
        }

        if (successors.isEmpty()) {
          return Edge.of(new BreakpointState<>(Set.of(), Set.of()), 0);
        }

        if (acceptingSuccessors.isEmpty()) {
          // Make only one immutable copy.
          successors = Set.copyOf(successors);
          return Edge.of(new BreakpointState<>(successors, successors), 0);
        }

        return Edge.of(new BreakpointState<>(successors, acceptingSuccessors));
      }
    };
  }

  public static Automaton<ImmutableBitSet, CoBuchiAcceptance>
  determinizeCanonicalGfgNcw(GfgNcwMinimization.CanonicalGfgNcw canonicalGfgNcw) {

    var alphaMaximalUpToHomogenityGfgNcw = canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw;

    var dcw = new AbstractMemoizingAutomaton.EdgeImplementation<>(
        alphaMaximalUpToHomogenityGfgNcw.atomicPropositions(),
        alphaMaximalUpToHomogenityGfgNcw.factory(),
        Set.of(ImmutableBitSet.copyOf(alphaMaximalUpToHomogenityGfgNcw.initialStates())),
        CoBuchiAcceptance.INSTANCE) {

      @Override
      protected Edge<ImmutableBitSet> edgeImpl(ImmutableBitSet states, BitSet valuation) {

        BitSet successors = new BitSet();
        BitSet acceptingSuccessors = new BitSet();

        for (var state = states.first();
            state.isPresent();
            state = states.higher(state.getAsInt())) {

          for (var edge : alphaMaximalUpToHomogenityGfgNcw.edges(state.getAsInt(), valuation)) {
            int successor = edge.successor();
            successors.set(successor);

            if (edge.colours().isEmpty()) {
              acceptingSuccessors.set(successor);
            }
          }
        }

        // Select only maximal elements using the subsafe-equivalance relation.
        var maximalSuccessors = maximalElements(canonicalGfgNcw, successors);
        var maximalAcceptingSuccessors = maximalElements(canonicalGfgNcw, acceptingSuccessors);

        if (maximalAcceptingSuccessors.isEmpty()) {
          // We reset.
          BitSet currentSafeComponents = findSafeComponents(states);
          assert currentSafeComponents.cardinality() == 1;
          int currentSafeComponent = currentSafeComponents.nextSetBit(0);
          assert currentSafeComponent >= 0;

          BitSet nextSafeComponents = findSafeComponents(maximalSuccessors);
          assert nextSafeComponents.cardinality() >= 1;
          int nextSafeComponent = nextSafeComponents.nextSetBit(currentSafeComponent + 1);

          if (nextSafeComponent < 0) {
            nextSafeComponent = nextSafeComponents.nextSetBit(0);
          }

          return Edge.of(
              canonicalGfgNcw.safeComponents.get(nextSafeComponent).intersection(maximalSuccessors),
              0);
        } else {
          return maximalAcceptingSuccessors.size() < states.size() ? Edge.of(
              maximalAcceptingSuccessors, 0) : Edge.of(maximalAcceptingSuccessors);
        }
      }

      private BitSet findSafeComponents(ImmutableBitSet states) {
        List<ImmutableBitSet> safeComponents = canonicalGfgNcw.safeComponents;
        BitSet indices = new BitSet();

        for (int i = 0, s = safeComponents.size(); i < s; i++) {
          if (safeComponents.get(i).intersects(states)) {
            indices.set(i);
          }
        }

        return indices;
      }
    };

    Verify.verify(
        LanguageContainment.equalsCoBuchi(canonicalGfgNcw.alphaMaximalUpToHomogenityGfgNcw, dcw));
    return dcw;
  }

  private static ImmutableBitSet maximalElements(
      CanonicalGfgNcw canonicalGfgNcw, BitSet statesBitSet) {

    Set<Integer> states = BitSet2.asSet(statesBitSet);

    Preconditions.checkArgument(canonicalGfgNcw.languageEquivalenceClasses.stream()
        .allMatch(clazz -> Collections.disjoint(clazz, states) || clazz.containsAll(states)));
    return ImmutableBitSet.copyOf(
        Collections3.maximalElements(states, canonicalGfgNcw::subsafeEquivalent));
  }

  public record BreakpointState<S>(Set<S> allRuns, Set<S> acceptingRuns) {

    public BreakpointState {
      allRuns = Set.copyOf(allRuns);
      acceptingRuns = Set.copyOf(acceptingRuns);
    }
  }
}
