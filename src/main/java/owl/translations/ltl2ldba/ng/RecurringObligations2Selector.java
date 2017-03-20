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

package owl.translations.ltl2ldba.ng;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.PropositionalFormula;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Collector;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.JumpEvaluator;
import owl.translations.ltl2ldba.JumpSelector;

public final class RecurringObligations2Selector implements JumpSelector<RecurringObligations2> {
  private static final Predicate<Formula> F_OPERATORS = x -> x instanceof FOperator
    || x instanceof UOperator; // x instanceof MOperator, ROp, WOp, ...
  private static final Predicate<Formula> G_OPERATORS = x -> x instanceof GOperator;
  // || x instanceof ROperator || x instanceof WOperator;
  private final Map<Set<UnaryModalOperator>, RecurringObligations2> cache;
  private final JumpEvaluator<RecurringObligations2> evaluator;
  private final EquivalenceClassFactory factory;
  private final EnumSet<Optimisation> optimisations;

  public RecurringObligations2Selector(EquivalenceClassFactory factory,
    EnumSet<Optimisation> optimisations
  ) {
    this.optimisations = EnumSet.copyOf(optimisations);
    this.factory = factory;
    this.cache = new HashMap<>();
    evaluator = new RecurringObligations2Evaluator(factory);
  }

  private static BitSet extractAtoms(Collector collector) {
    BitSet atoms = new BitSet();
    collector.getCollection().forEach(x -> atoms.set(((Literal) x).getAtom()));
    return atoms;
  }

  private static Set<UnaryModalOperator> normalise(Collection<Formula> formulas) {
    Set<UnaryModalOperator> set = new HashSet<>();
    set.addAll(normaliseToFOperators(formulas.stream().filter(F_OPERATORS)));
    set.addAll(normaliseToGOperators(formulas.stream().filter(G_OPERATORS)));
    return set;
  }

  private static Set<FOperator> normaliseToFOperators(Collection<Formula> formulas) {
    return normaliseToFOperators(formulas.stream());
  }

  private static Set<FOperator> normaliseToFOperators(Stream<Formula> formulas) {
    Set<FOperator> fSet = new HashSet<>();

    formulas.forEach(x -> {
      if (x instanceof FOperator) {
        fSet.add((FOperator) x);
        return;
      }

      if (x instanceof UOperator) {
        fSet.add(new FOperator(((UOperator) x).right));
        return;
      }

      assert false;

      if (x instanceof ROperator) {
        fSet.add(new FOperator(((ROperator) x).left));
      }

      if (x instanceof WOperator) {
        fSet.add(new FOperator(((WOperator) x).right));
      }
    });

    return fSet;
  }

  private static Set<GOperator> normaliseToGOperators(Collection<Formula> formulas) {
    return normaliseToGOperators(formulas.stream());
  }

  private static Set<GOperator> normaliseToGOperators(Stream<Formula> formulas) {
    Set<GOperator> gSet = new HashSet<>();

    formulas.forEach(x -> {
      if (x instanceof GOperator) {
        gSet.add((GOperator) x);
        return;
      }

      assert false;

      if (x instanceof ROperator) {
        gSet.add(new GOperator(((ROperator) x).right));
      }

      if (x instanceof WOperator) {
        gSet.add(new GOperator(((WOperator) x).left));
      }
    });

    return gSet;
  }

  // TODO: Move to optional?
  @Nullable
  private RecurringObligations2 constructRecurringObligations(Set<FOperator> fOperators,
    Set<GOperator> gOperators) {
    // Fields for RecurringObligations
    EquivalenceClass safety = factory.getTrue();

    RecurringObligations2Evaluator.SubstitutionVisitor substitutionVisitor =
      new RecurringObligations2Evaluator.SubstitutionVisitor(fOperators, gOperators);

    for (GOperator gOperator : gOperators) {
      Formula formula = gOperator.operand.accept(substitutionVisitor);
      EquivalenceClass safety2 = factory.createEquivalenceClass(formula);
      safety = safety.andWith(safety2);
      safety2.free();

      if (safety.isFalse()) {
        return null;
      }
    }

    List<EquivalenceClass> livenessList = new ArrayList<>(fOperators.size());
    for (FOperator fOperator : fOperators) {
      Formula formula = fOperator.operand.accept(substitutionVisitor).accept(substitutionVisitor);

      while (formula instanceof XOperator) {
        //noinspection OverlyStrongTypeCast
        formula = ((XOperator) formula).operand;
      }

      if (formula == BooleanConstant.FALSE) {
        EquivalenceClass.free(safety);
        EquivalenceClass.free(livenessList);
        return null;
      }

      EquivalenceClass liveness = factory.createEquivalenceClass(new FOperator(formula));
      livenessList.add(liveness);
    }

    if (safety.isTrue() && livenessList.isEmpty()) {
      return null;
    }

    RecurringObligations2 recurringObligations = new RecurringObligations2(safety, livenessList);
    return cache.values().stream().filter(recurringObligations::equals).findAny()
      .orElse(recurringObligations);
  }

  // Compute support from Gs and scoped Fs.
  private Set<Formula> extractRelevantOperators(EquivalenceClass state) {
    Collector scopedFOperators = new Collector(F_OPERATORS);
    Set<Formula> support = state.getSupport(G_OPERATORS);
    support.forEach(x -> x.accept(scopedFOperators));
    support.addAll(scopedFOperators.getCollection());
    return support;
  }

  /**
   * Determines if the first language is a subset of the second language.
   *
   * @param entry
   *     first language
   * @param otherEntry
   *     second language
   * @param master
   *     remainder
   *
   * @return true if is a sub-language
   */
  private boolean isSubLanguage(Map.Entry<?, RecurringObligations2> entry,
    Map.Entry<?, RecurringObligations2> otherEntry, EquivalenceClass master) {
    EquivalenceClass setClass = evaluator.evaluate(master, entry.getValue());
    EquivalenceClass subsetClass = evaluator.evaluate(master, otherEntry.getValue());

    boolean equals = Objects.equals(setClass, subsetClass);

    setClass.free();
    subsetClass.free();

    return equals && entry.getValue().implies(otherEntry.getValue());
  }

  @Override
  public Set<RecurringObligations2> select(EquivalenceClass clazz, boolean isInitialState) {
    Collection<Set<UnaryModalOperator>> keys;

    // Find interesting Fs and Gs
    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      keys = selectReducedMonitors(clazz);
    } else {
      keys = selectAllMonitors(clazz);
    }

    // Compute resulting RecurringObligations.
    Map<Set<UnaryModalOperator>, RecurringObligations2> jumps = new HashMap<>();
    for (Set<UnaryModalOperator> operators : keys) {
      Set<FOperator> fOperators = operators.stream().filter(F_OPERATORS).map(x -> (FOperator) x)
        .collect(Collectors.toSet());
      Set<GOperator> gOperators = operators.stream().filter(G_OPERATORS).map(x -> (GOperator) x)
        .collect(Collectors.toSet());
      RecurringObligations2 obligations = cache.computeIfAbsent(operators,
        (x) -> this.constructRecurringObligations(fOperators, gOperators));

      if (obligations != null) {
        jumps.put(operators, obligations);
        obligations.associatedFs.addAll(fOperators);
        obligations.associatedGs.addAll(gOperators);
      }
    }

    boolean removedCoveredLanguage = false;

    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      removedCoveredLanguage = jumps.entrySet().removeIf(entry -> {
        if (!isInitialState) {
          EquivalenceClass remainder = evaluator.evaluate(clazz, entry.getValue());

          Collector externalLiteralCollector = new Collector(x -> x instanceof Literal);
          remainder.getSupport().forEach(x -> x.accept(externalLiteralCollector));
          BitSet externalAtoms = extractAtoms(externalLiteralCollector);

          Collector internalLiteralCollector = new Collector(x -> x instanceof Literal);
          entry.getKey().forEach(x -> x.accept(internalLiteralCollector));
          BitSet internalAtoms = extractAtoms(internalLiteralCollector);

          // Check if external atoms are non-empty and disjoint.
          if (!externalAtoms.isEmpty()) {
            externalAtoms.and(internalAtoms);

            if (externalAtoms.isEmpty()) { // NOPMD
              return true;
            }
          }
        }

        return jumps.entrySet().stream()
          .anyMatch(otherEntry -> otherEntry != entry && isSubLanguage(entry, otherEntry, clazz));
      });
    }

    // Compute if jump is urgent.
    boolean isUrgent = false;

    if ((isInitialState || optimisations.contains(Optimisation.FORCE_JUMPS))
      && jumps.entrySet().size() == 1) {
      Set<Formula> support = clazz.getSupport(G_OPERATORS);
      EquivalenceClass skeleton = clazz.exists(x -> !support.contains(x));

      Collector collector = new Collector(G_OPERATORS);
      clazz.getSupport().forEach(x -> x.accept(collector));

      EquivalenceClass gConjunction = factory.createEquivalenceClass(collector.getCollection());

      Collector externCollector = new Collector(x -> !(x instanceof PropositionalFormula));
      Collector internCollector = new Collector(x -> !(x instanceof PropositionalFormula));

      clazz.getSupport().forEach(x -> x.accept(externCollector));
      skeleton.getSupport().forEach(x -> x.accept(internCollector));

      if (Objects.equals(gConjunction, skeleton) && Objects
        .equals(normaliseToGOperators(collector.getCollection()),
          Iterables.getOnlyElement(jumps.values()).associatedGs) && Objects
        .equals(externCollector
          .getCollection(), internCollector.getCollection())) {
        isUrgent = true;
      }
    }

    // Force jump, if subst with not G is false...

    if (!isUrgent && !jumps.containsKey(Collections.<UnaryModalOperator>emptySet())) {
      if (keys.size() > 1 || removedCoveredLanguage) {
        jumps.put(Collections.emptySet(), null);
      } else {
        Collector collector = new Collector(G_OPERATORS.or(F_OPERATORS));
        clazz.getSupport().forEach(x -> x.accept(collector));

        if (!jumps.containsKey(normalise(collector.getCollection()))
          || !optimisations.contains(Optimisation.FORCE_JUMPS) && !isInitialState) {
          jumps.put(Collections.emptySet(), null);
        }
      }
    }

    return new HashSet<>(jumps.values());
  }

  private Set<Set<UnaryModalOperator>> selectAllMonitors(EquivalenceClass state) {
    return Sets.powerSet(normalise(extractRelevantOperators(state)));
  }

  private List<Set<UnaryModalOperator>> selectReducedMonitors(EquivalenceClass state) {
    Set<Formula> support = state.getSupport(G_OPERATORS);
    EquivalenceClass skeleton = state.exists(x -> !support.contains(x));

    List<Set<GOperator>> sets = skeleton.satisfyingAssignments(support)
      .stream().map(RecurringObligations2Selector::normaliseToGOperators)
      .collect(Collectors.toList());

    skeleton.free();

    // Enhance with FOperators:

    return sets.stream().map(x -> {
      Collector scopedFOperators = new Collector(F_OPERATORS);
      x.forEach(y -> y.accept(scopedFOperators));
      return Sets.powerSet(scopedFOperators.getCollection()).stream()
        .map(z -> Sets.union(x, normaliseToFOperators(z)));
    }).flatMap(x -> x).collect(Collectors.toList());
  }
}