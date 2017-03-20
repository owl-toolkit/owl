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

package owl.translations.ltl2ldba;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.ROperator;
import owl.ltl.WOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.Collector;
import owl.translations.Optimisation;

public class RecurringObligationsSelector implements JumpSelector<RecurringObligations> {
  private static final Predicate<Formula> INFINITY_OPERATORS = x -> x instanceof GOperator
    || x instanceof ROperator || x instanceof WOperator;
  private final Map<Set<GOperator>, RecurringObligations> cache;
  private final JumpEvaluator<RecurringObligations> evaluator;
  private final EquivalenceClassFactory factory;
  private final EnumSet<Optimisation> optimisations;
  private final Comparator<GOperator> rankingComparator;

  RecurringObligationsSelector(EquivalenceClassFactory factory,
    EnumSet<Optimisation> optimisations) {
    this.optimisations = EnumSet.copyOf(optimisations);
    this.factory = factory;
    this.cache = new HashMap<>();
    this.rankingComparator = new RankingComparator();
    evaluator = new RecurringObligationsEvaluator(factory);
  }

  private static BitSet extractAtoms(Collector collector) {
    BitSet atoms = new BitSet();
    collector.getCollection().forEach(x -> atoms.set(((Literal) x).getAtom()));
    return atoms;
  }

  private static void free(@Nullable EquivalenceClass clazz1, EquivalenceClass clazz2,
    Iterable<EquivalenceClass> iterable1, Iterable<EquivalenceClass> iterable2) {
    EquivalenceClass.free(clazz1);
    EquivalenceClass.free(clazz2);
    EquivalenceClass.free(iterable1);
    EquivalenceClass.free(iterable2);
  }

  private static Set<GOperator> normaliseInfinityOperators(Iterable<Formula> formulas) {
    Set<GOperator> gSet = new HashSet<>();

    formulas.forEach(x -> {
      assert x instanceof GOperator || x instanceof ROperator || x instanceof WOperator;

      if (x instanceof GOperator) {
        gSet.add((GOperator) x);
      }

      if (x instanceof ROperator) {
        gSet.add(new GOperator(((ROperator) x).right));
      }

      if (x instanceof WOperator) {
        gSet.add(new GOperator(((WOperator) x).left));
      }
    });

    return gSet;
  }

  /**
   * Construct the recurring obligations for a Gset.
   *
   * @param gOperatorsSet
   *     The GOperators that have to be checked often.
   *
   * @return This methods returns null, if the Gset is inconsistent.
   */
  @Nullable
  private RecurringObligations constructRecurringObligations(Set<GOperator> gOperatorsSet) {
    // Fields for RecurringObligations
    EquivalenceClass safety = factory.getTrue();
    List<EquivalenceClass> liveness = new ArrayList<>(gOperatorsSet.size());
    List<EquivalenceClass> obligations = new ArrayList<>(gOperatorsSet.size());

    List<GOperator> gOperators = gOperatorsSet.stream().sorted(rankingComparator)
      .collect(Collectors.toList());

    for (int i = 0; i < gOperators.size(); i++) {
      GOperator gOperator = gOperators.get(i);

      // We only propagate information from already constructed G-monitors.
      RecurringObligationsEvaluator.EvaluateVisitor evaluateVisitor =
        new RecurringObligationsEvaluator.EvaluateVisitor(gOperators.subList(0, i), factory);

      Formula formula = RewriterFactory.apply(RewriterEnum.PUSHDOWN_X, RewriterFactory
          .apply(RewriterEnum.MODAL_ITERATIVE, gOperator.operand.accept(evaluateVisitor))
      );
      EquivalenceClass clazz = factory.createEquivalenceClass(formula);

      evaluateVisitor.free();

      if (clazz.isFalse()) {
        free(clazz, safety, liveness, obligations);
        return null;
      }

      if (optimisations.contains(Optimisation.OPTIMISED_CONSTRUCTION_FOR_FRAGMENTS)) {
        if (clazz.testSupport(Fragments::isX)) {
          safety = safety.andWith(clazz);
          clazz.free();
          continue;
        }

        if (clazz.testSupport(Formula::isPureEventual)) {
          liveness.add(clazz);
          continue;
        }
      }

      obligations.add(clazz);
    }

    if (safety.isTrue() && liveness.isEmpty() && obligations.isEmpty()) {
      return null;
    }

    if (safety.isFalse()) {
      free(null, safety, liveness, obligations);
      return null;
    }

    RecurringObligations recurringObligations = new RecurringObligations(safety, liveness,
      obligations);
    return cache.values().stream().filter(recurringObligations::equals).findAny()
      .orElse(recurringObligations);
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
  private boolean isSublanguage(RecurringObligations obligations,
    RecurringObligations otherObligations, EquivalenceClass master) {
    EquivalenceClass setClass = evaluator.evaluate(master, obligations);
    EquivalenceClass subsetClass = evaluator.evaluate(master, otherObligations);

    boolean equals = setClass.equals(subsetClass);

    setClass.free();
    subsetClass.free();

    return equals && obligations.implies(otherObligations);
  }

  @Override
  public Set<RecurringObligations> select(EquivalenceClass input, boolean isInitialState) {
    final Collection<Set<GOperator>> keys;
    final BiMap<Set<GOperator>, RecurringObligations> jumps = HashBiMap.create();

    EquivalenceClass state = optimisations.contains(Optimisation.EAGER_UNFOLD)
                             ? input.duplicate()
                             : input.unfold();

    // Find interesting Gs
    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      keys = selectSkeletonMonitors(state);
    } else {
      keys = selectAllMonitors(state);
    }

    // Compute resulting RecurringObligations.
    for (Set<GOperator> Gs : keys) {
      RecurringObligations obligations = cache
        .computeIfAbsent(Gs, this::constructRecurringObligations);

      if (obligations != null) {
        if (!jumps.containsValue(obligations)) {
          jumps.put(Gs, obligations);
        }

        obligations.associatedGs.addAll(Gs);
      }
    }

    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      jumps.values().removeIf(obligation -> {
        if (!isInitialState) {
          EquivalenceClass remainder = evaluator.evaluate(state, obligation);

          Collector externalLiteralCollector = new Collector(x -> x instanceof Literal);
          remainder.getSupport().forEach(x -> x.accept(externalLiteralCollector));
          BitSet externalAtoms = extractAtoms(externalLiteralCollector);

          Collector internalLiteralCollector = new Collector(x -> x instanceof Literal);
          obligation.forEach(x -> x.getSupport().forEach(y -> y.accept(internalLiteralCollector)));
          BitSet internalAtoms = extractAtoms(internalLiteralCollector);

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
        final Set<GOperator> Gs = new HashSet<>();

        state.getSupport().forEach(x -> {
          Collector collector = new Collector(INFINITY_OPERATORS);
          x.accept(collector);
          Gs.addAll(normaliseInfinityOperators(collector.getCollection()));
        });

        if (!jumps.containsKey(Gs)
          || !optimisations.contains(Optimisation.FORCE_JUMPS) && !isInitialState) {
          jumps.put(Collections.emptySet(), null);
        }
      }
    }

    state.free();
    return new HashSet<>(jumps.values());
  }

  private Set<Set<GOperator>> selectAllMonitors(EquivalenceClass state) {
    Collector collector = new Collector(INFINITY_OPERATORS);
    state.getSupport().forEach(x -> x.accept(collector));
    return Sets.powerSet(normaliseInfinityOperators(collector.getCollection()));
  }

  private List<Set<GOperator>> selectSkeletonMonitors(EquivalenceClass state) {
    final Set<Formula> support = state.getSupport(INFINITY_OPERATORS);
    final EquivalenceClass skeleton = state.exists(INFINITY_OPERATORS.negate());

    List<Set<GOperator>> sets = skeleton.satisfyingAssignments(support)
      .stream()
      .map(RecurringObligationsSelector::normaliseInfinityOperators)
      .collect(Collectors.toList());

    skeleton.free();
    return sets;
  }
}