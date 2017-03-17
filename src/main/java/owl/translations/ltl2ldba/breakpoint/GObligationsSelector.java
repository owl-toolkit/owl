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

package owl.translations.ltl2ldba.breakpoint;

import com.google.common.collect.BiMap;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.ROperator;
import owl.ltl.WOperator;
import owl.ltl.visitors.Collector;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.JumpEvaluator;
import owl.translations.ltl2ldba.JumpSelector;

public class GObligationsSelector implements JumpSelector<GObligations> {
  private static final Predicate<Formula> INFINITY_OPERATORS = x -> x instanceof GOperator
    || x instanceof ROperator || x instanceof WOperator;
  private final Map<Set<GOperator>, GObligations> cache;
  private final JumpEvaluator<GObligations> evaluator;
  private final EquivalenceClassFactory factory;
  private final EnumSet<Optimisation> optimisations;

  public GObligationsSelector(Factories factories,
    EnumSet<Optimisation> optimisations) {
    this.optimisations = EnumSet.copyOf(optimisations);
    this.factory = factories.equivalenceClassFactory;
    this.cache = new HashMap<>();
    evaluator = new GObligationsEvaluator(factory);
  }

  private static Set<GOperator> normaliseInfinityOperators(Collection<Formula> formulas) {
    return new HashSet<>(Collections2.transform(formulas, x -> {
      GOperator g = Collector.transformToGOperator(x);
      if (g == null) {
        throw new AssertionError();
      }
      return g;
    }));
  }

  private static Set<Set<GOperator>> selectAllMonitors(EquivalenceClass state) {
    return Sets.powerSet(Collector.collectTransformedGOperators(state.getSupport()));
  }

  private static List<Set<GOperator>> selectSkeletonMonitors(EquivalenceClass state) {
    final Set<Formula> support = state.getSupport(INFINITY_OPERATORS);
    final EquivalenceClass skeleton = state.exists(INFINITY_OPERATORS.negate());

    List<Set<GOperator>> sets = skeleton.satisfyingAssignments(support)
      .stream()
      .map(GObligationsSelector::normaliseInfinityOperators)
      .collect(Collectors.toList());

    skeleton.free();
    return sets;
  }

  /**
   * Determines if the first language is a subset of the second language.
   *
   * @param obligations
   *     first language
   * @param otherObligations
   *     second language
   * @param master
   *     remainder
   *
   * @return true if is a sub-language
   */
  private boolean isSublanguage(GObligations obligations,
    GObligations otherObligations, EquivalenceClass master) {
    EquivalenceClass setClass = evaluator.evaluate(master, obligations);
    EquivalenceClass subsetClass = evaluator.evaluate(master, otherObligations);

    boolean equals = setClass.equals(subsetClass);

    setClass.free();
    subsetClass.free();

    return equals && obligations.implies(otherObligations);
  }

  @Override
  public Set<GObligations> select(EquivalenceClass input, boolean isInitialState) {
    final Collection<Set<GOperator>> keys;
    final BiMap<Set<GOperator>, GObligations> jumps = HashBiMap.create();

    EquivalenceClass state = optimisations.contains(Optimisation.EAGER_UNFOLD)
                             ? input.duplicate()
                             : input.unfold();

    // Find interesting Gs
    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      keys = selectSkeletonMonitors(state);
    } else {
      keys = selectAllMonitors(state);
    }

    // Compute resulting GObligations.
    for (Set<GOperator> Gs : keys) {
      GObligations obligations = cache.computeIfAbsent(Gs, x -> GObligations
        .constructRecurringObligations(x, factory, optimisations));

      if (obligations != null && !jumps.containsValue(obligations)) {
        jumps.put(Gs, obligations);
      }
    }

    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      jumps.values().removeIf(obligation -> {
        if (!isInitialState) {
          EquivalenceClass remainder = evaluator.evaluate(state, obligation);

          BitSet externalAtoms = Collector.collectAtoms(remainder.getSupport());
          BitSet internalAtoms = new BitSet();
          obligation.forEach(x -> internalAtoms.or(Collector.collectAtoms(x.getSupport())));

          // Check if external atoms are non-empty and disjoint.
          if (!externalAtoms.isEmpty()) {
            externalAtoms.and(internalAtoms);

            if (externalAtoms.isEmpty()) { // NOPMD
              return true;
            }
          }
        }

        return jumps.values().stream().anyMatch(
          otherObligation -> obligation != otherObligation && isSublanguage(obligation,
            otherObligation, state));
      });
    }

    if (!jumps.containsKey(Collections.<GOperator>emptySet())) {
      if (keys.size() > 1) {
        jumps.put(Collections.emptySet(), null);
      } else {
        if (!jumps.containsKey(Collector.collectTransformedGOperators(state.getSupport()))
          || !optimisations.contains(Optimisation.FORCE_JUMPS) && !isInitialState) {
          jumps.put(Collections.emptySet(), null);
        }
      }
    }

    state.free();
    return new HashSet<>(jumps.values());
  }
}