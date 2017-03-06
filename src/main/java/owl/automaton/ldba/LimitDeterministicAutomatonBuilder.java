/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.automaton.ldba;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.algorithms.SccAnalyser;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.transformations.AutomatonMinimization;
import owl.collections.ValuationSet;
import owl.translations.Optimisation;

public final class LimitDeterministicAutomatonBuilder<KeyS, S, KeyT, T,
  B extends GeneralizedBuchiAcceptance, C> {

  private final ExploreBuilder<KeyT, T, B> acceptingComponentBuilder;
  private final Set<C> components;
  private final Function<T, C> getComponent;
  private final ExploreBuilder<KeyS, S, NoneAcceptance> initialComponentBuilder;
  private final Set<T> initialStates;
  @Nullable
  private final Predicate<S> isProtected;
  private final Function<S, Iterable<KeyT>> jumpGenerator;
  private final EnumSet<Optimisation> optimisations;

  private LimitDeterministicAutomatonBuilder(
    ExploreBuilder<KeyS, S, NoneAcceptance> initialComponentBuilder,
    ExploreBuilder<KeyT, T, B> acceptingComponentBuilder, Function<S, Iterable<KeyT>> jumpGenerator,
    Function<T, C> annot,
    EnumSet<Optimisation> optimisations, @Nullable Predicate<S> isProtected) {
    this.initialComponentBuilder = initialComponentBuilder;
    this.acceptingComponentBuilder = acceptingComponentBuilder;
    this.optimisations = EnumSet.copyOf(optimisations);
    this.jumpGenerator = jumpGenerator;
    initialStates = new HashSet<>();
    components = new HashSet<>();
    getComponent = annot;
    this.isProtected = isProtected;
  }

  public static <S, T, Acc extends GeneralizedBuchiAcceptance, X, X2, X3>
  LimitDeterministicAutomatonBuilder<X, S, X2, T, Acc, X3> create(
    ExploreBuilder<X, S, NoneAcceptance> initialComponentBuilder,
    ExploreBuilder<X2, T, Acc> acceptingComponentBuilder, Function<S, Iterable<X2>> jumpGenerator,
    Function<T, X3> annot,
    EnumSet<Optimisation> optimisations) {
    return new LimitDeterministicAutomatonBuilder<>(initialComponentBuilder,
      acceptingComponentBuilder, jumpGenerator, annot, optimisations, null);
  }

  public static <S, T, Acc extends GeneralizedBuchiAcceptance, X, X2, X3>
  LimitDeterministicAutomatonBuilder<X, S, X2, T, Acc, X3> create(
    ExploreBuilder<X, S, NoneAcceptance> initialComponentBuilder,
    ExploreBuilder<X2, T, Acc> acceptingComponentBuilder, Function<S, Iterable<X2>> jumpGenerator,
    Function<T, X3> annot,
    EnumSet<Optimisation> optimisations,
    Predicate<S> isPr) {
    return new LimitDeterministicAutomatonBuilder<>(initialComponentBuilder,
      acceptingComponentBuilder, jumpGenerator, annot, optimisations, isPr);
  }

  @Nullable
  public T addAccepting(KeyT key) {
    T state = acceptingComponentBuilder.add(key);

    if (state == null) {
      return null;
    }

    initialStates.add(state);
    components.add(getComponent.apply(state));
    return state;
  }

  @Nullable
  public S addInitial(KeyS key) {
    return initialComponentBuilder.add(key);
  }

  public LimitDeterministicAutomaton<S, T, B, C> build() {
    MutableAutomaton<S, NoneAcceptance> initialComponent = initialComponentBuilder.build();
    SetMultimap<S, T> epsilonJumps = MultimapBuilder.hashKeys().hashSetValues().build();
    Table<S, ValuationSet, Set<T>> valuationSetJumps = HashBasedTable.create();


    // Decompose into SCCs
    List<Set<S>> sccs = optimisations.contains(Optimisation.SCC_ANALYSIS)
                        ? SccAnalyser.computeSccs(initialComponent)
                        : Collections.singletonList(initialComponent.getStates());

    for (Set<S> scc : sccs) {
      // Skip transient SCCs
      if (scc.size() == 1) {
        S state = Iterables.getOnlyElement(scc);

        Set<S> successors = initialComponent.getSuccessors(state);

        if (!successors.contains(state) && !successors.isEmpty()) {
          continue;
        }
      }

      // Generate jumps
      for (S state : scc) {
        Iterable<KeyT> jumpTargets = jumpGenerator.apply(state);

        for (KeyT targetKey : jumpTargets) {
          T target = acceptingComponentBuilder.add(targetKey);

          if (target != null) {
            components.add(getComponent.apply(target));
            epsilonJumps.put(state, target);
          }
        }
      }
    }

    MutableAutomaton<T, B> acceptingComponent = acceptingComponentBuilder.build();

    // Remove dead states in the accepting component
    AutomatonMinimization.removeDeadStates(acceptingComponent, Sets.union(initialStates,
      new HashSet<>(epsilonJumps.values())));
    epsilonJumps.values().removeIf(x -> !acceptingComponent.getStates().contains(x));

    if (optimisations.contains(Optimisation.REMOVE_EPSILON_TRANSITIONS)) {
      Set<T> reachableStates = new HashSet<>(initialStates);

      for (S state : initialComponent.getStates()) {
        Iterable<LabelledEdge<S>> successors = initialComponent.getLabelledEdges(state);
        Map<ValuationSet, Set<T>> successorJumps = valuationSetJumps.row(state);

        successors.forEach(labelledEdge -> {
          Set<T> jumpTargets = epsilonJumps.get(labelledEdge.edge.getSuccessor());

          reachableStates.addAll(jumpTargets);
          successorJumps.compute(labelledEdge.valuations, (x, existingJumpTargets) -> {
            if (existingJumpTargets != null) {
              existingJumpTargets.addAll(jumpTargets);
              return existingJumpTargets;
            }

            return new HashSet<>(jumpTargets);
          });
        });
      }

      AutomatonMinimization.removeDeadStates(initialComponent,
        Sets.union(initialComponent.getInitialStates(), valuationSetJumps.rowKeySet()));
      AutomatonMinimization.removeDeadStates(acceptingComponent, reachableStates);
      initialStates.removeIf(x -> !acceptingComponent.getStates().contains(x));
      epsilonJumps.clear();
    } else {
      Set<S> protectedStates = new HashSet<>();
      if (isProtected != null) {
        initialComponent.getStates().stream().filter(isProtected).forEach(protectedStates::add);
      }
      protectedStates.addAll(epsilonJumps.keySet());
      protectedStates.addAll(initialComponent.getInitialStates());
      AutomatonMinimization.removeDeadStates(initialComponent, protectedStates);
    }

    acceptingComponent.setInitialStates(initialStates);
    return new LimitDeterministicAutomaton<>(initialComponent, acceptingComponent, epsilonJumps,
      valuationSetJumps, initialStates, components, getComponent);
  }
}
