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
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.minimizations.MinimizationUtil;
import owl.collections.ValuationSet;

public final class LimitDeterministicAutomatonBuilder<KeyS, S, KeyT, T,
  B extends GeneralizedBuchiAcceptance, C> {

  private static final Logger logger = Logger.getLogger(MinimizationUtil.class.getName());

  private final MutableAutomatonBuilder<KeyT, T, B> acceptingComponentBuilder;
  private final Set<C> components;
  private final Function<S, Iterable<KeyT>> jumpGenerator;
  private final Function<T, C> getComponent;
  private final MutableAutomatonBuilder<KeyS, S, NoneAcceptance> initialComponentBuilder;
  private final Set<T> initialStates;
  private final Predicate<S> isProtected;
  private final EnumSet<Configuration> optimisations;

  private LimitDeterministicAutomatonBuilder(
    MutableAutomatonBuilder<KeyS, S, NoneAcceptance> initialComponentBuilder,
    MutableAutomatonBuilder<KeyT, T, B> acceptingComponentBuilder,
    Function<S, Iterable<KeyT>> jumpGenerator,
    Function<T, C> annotations,
    EnumSet<Configuration> optimisations, Predicate<S> isProtected) {
    this.initialComponentBuilder = initialComponentBuilder;
    this.acceptingComponentBuilder = acceptingComponentBuilder;
    this.optimisations = EnumSet.copyOf(optimisations);
    this.jumpGenerator = jumpGenerator;
    initialStates = new HashSet<>();
    components = new HashSet<>();
    getComponent = annotations;
    this.isProtected = isProtected;
  }

  public static <S, T, Acc extends GeneralizedBuchiAcceptance, X, X2, X3>
  LimitDeterministicAutomatonBuilder<X, S, X2, T, Acc, X3> create(
    MutableAutomatonBuilder<X, S, NoneAcceptance> initialComponentBuilder,
    MutableAutomatonBuilder<X2, T, Acc> acceptingComponentBuilder,
    Function<S, Iterable<X2>> jumpGenerator,
    Function<T, X3> annotations,
    EnumSet<Configuration> optimisations) {
    return new LimitDeterministicAutomatonBuilder<>(initialComponentBuilder,
      acceptingComponentBuilder, jumpGenerator, annotations, optimisations, x -> false);
  }

  public static <S, T, Acc extends GeneralizedBuchiAcceptance, X, X2, X3>
  LimitDeterministicAutomatonBuilder<X, S, X2, T, Acc, X3> create(
    MutableAutomatonBuilder<X, S, NoneAcceptance> initialComponentBuilder,
    MutableAutomatonBuilder<X2, T, Acc> acceptingComponentBuilder,
    Function<S, Iterable<X2>> jumpGenerator,
    Function<T, X3> annotations,
    EnumSet<Configuration> optimisations,
    Predicate<S> isProtected) {
    return new LimitDeterministicAutomatonBuilder<>(initialComponentBuilder,
      acceptingComponentBuilder, jumpGenerator, annotations, optimisations, isProtected);
  }

  @Nullable
  public T addAccepting(KeyT key) {
    T state = acceptingComponentBuilder.add(key);

    if (state == null) {
      return null;
    }

    initialStates.add(state);
    C component = getComponent.apply(state);

    if (component != null) {
      components.add(component);
    }

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
    generateJumps(initialComponent, epsilonJumps);
    MutableAutomaton<T, B> acceptingComponent = acceptingComponentBuilder.build();

    // Preprocess:
    // Remove dead states in the accepting component. Note that the .values() collection is backed
    // by the internal map of the epsilonJumps, hence removal is forwarded.
    MinimizationUtil.removeDeadStates(acceptingComponent);
    Set<T> acceptingComponentStates = acceptingComponent.states();
    epsilonJumps.values().retainAll(acceptingComponentStates);
    initialStates.retainAll(acceptingComponentStates);

    Predicate<S> initialComponentProtectedStates = x -> isProtected.test(x)
      || epsilonJumps.keySet().contains(x) || valuationSetJumps.rowKeySet().contains(x);

    if (optimisations.contains(Configuration.REMOVE_EPSILON_TRANSITIONS)) {
      Set<T> extendedInitialStates = new HashSet<>(initialStates);

      for (S state : initialComponent.states()) {
        Map<ValuationSet, Set<T>> successorJumps = valuationSetJumps.row(state);

        initialComponent.forEachLabelledEdge(state, (edge, valuations) -> {
          Collection<T> targets = epsilonJumps.get(edge.successor());
          extendedInitialStates.addAll(targets);
          successorJumps.compute(valuations, (x, existingJumpTargets) -> {
            if (existingJumpTargets == null) {
              return new HashSet<>(targets);
            }

            existingJumpTargets.addAll(targets);
            return existingJumpTargets;
          });
        });
      }

      epsilonJumps.clear();
      removeUnprotectedStates(initialComponent, initialComponentProtectedStates);
      acceptingComponent.initialStates(extendedInitialStates);
      acceptingComponent.trim();
      MinimizationUtil.removeDeadStates(acceptingComponent);
      initialStates.retainAll(acceptingComponent.states());
    } else {
      removeUnprotectedStates(initialComponent, initialComponentProtectedStates);
    }

    return new LimitDeterministicAutomatonImpl<>(initialComponent, acceptingComponent,
      epsilonJumps, valuationSetJumps, components, getComponent, initialStates);
  }

  private void removeUnprotectedStates(MutableAutomaton<S, ?> automaton, Predicate<S> isProtected) {
    for (Set<S> scc : SccDecomposition.computeSccs(automaton)) {
      if (scc.stream().noneMatch(isProtected) && SccDecomposition.isTrap(automaton, scc)) {
        // The is a BSCC without protected states. Safe to remove.
        logger.log(Level.FINER, "Removing scc {0}", scc);
        automaton.removeStateIf(scc::contains);
        automaton.trim();
      }
    }
  }

  private void generateJumps(Automaton<S, NoneAcceptance> initialComponent,
    Multimap<S, T> epsilonJumps) {
    // Decompose into SCCs
    List<Set<S>> sccs = optimisations.contains(Configuration.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES)
      ? SccDecomposition.computeSccs(initialComponent, true)
      : List.of(initialComponent.states());

    for (Set<S> scc : sccs) {
      // Skip transient SCCs
      if (scc.size() == 1) {
        S state = Iterables.getOnlyElement(scc);

        Set<S> successors = initialComponent.successors(state);

        if (!successors.contains(state) && !successors.isEmpty()) {
          continue;
        }
      }

      // Generate jumps
      for (S state : scc) {
        Iterable<KeyT> jumps = jumpGenerator.apply(state);

        if (jumps == null) {
          continue;
        }

        for (KeyT key : jumps) {
          T target = acceptingComponentBuilder.add(key);

          if (target != null) {
            C component = getComponent.apply(target);

            if (component != null) {
              components.add(component);
            }

            epsilonJumps.put(state, target);
          }
        }
      }
    }
  }

  public enum Configuration {
    SUPPRESS_JUMPS_FOR_TRANSIENT_STATES, REMOVE_EPSILON_TRANSITIONS
  }
}
