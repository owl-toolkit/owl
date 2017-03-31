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

package owl.translations.ltl2ldba.breakpointfree;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.factories.EquivalenceClassFactory;
import owl.factories.EquivalenceClassUtil;
import owl.factories.Factories;
import owl.ltl.BinaryModalOperator;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Collector;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.EquivalenceClassStateFactory;
import owl.translations.ltl2ldba.JumpEvaluator;
import owl.translations.ltl2ldba.JumpSelector;

public final class FGObligationsSelector implements JumpSelector<FGObligations> {

  private final Table<ImmutableSet<FOperator>, ImmutableSet<GOperator>, FGObligations> cache;
  private final JumpEvaluator<FGObligations> evaluator;
  private final EquivalenceClassFactory factory;
  private final EnumSet<Optimisation> optimisations;
  private final EquivalenceClassStateFactory stateFactory;

  public FGObligationsSelector(Factories factories, EnumSet<Optimisation> optimisations) {
    this.optimisations = EnumSet.copyOf(optimisations);
    this.factory = factories.equivalenceClassFactory;
    evaluator = new FGObligationsEvaluator(factory);
    cache = HashBasedTable.create();
    stateFactory = new EquivalenceClassStateFactory(factory, optimisations);
  }

  private static <E> boolean isSubset(Collection<E> subset, Collection<E> set) {
    return set.containsAll(subset);
  }

  private static Set<UnaryModalOperator> selectAllMonitors(EquivalenceClass state) {
    Formula formula = state.getRepresentative();
    Set<GOperator> gOperators = Collector.collectTransformedGOperators(formula);
    Set<FOperator> fOperators = Collector.collectTransformedFOperators(gOperators);
    return Sets.union(gOperators, fOperators);
  }

  private static Set<UnaryModalOperator> selectReducedMonitors(GOperator gOperator) {
    Set<FOperator> fOperators = Collector.collectTransformedFOperators(gOperator.operand, true);
    Set<UnaryModalOperator> operators = new HashSet<>();
    fOperators.forEach(x -> operators.addAll(selectReducedMonitors(x)));

    if (!(gOperator.operand instanceof FOperator)) {
      operators.add(gOperator);
    }

    return Sets.union(operators, fOperators);
  }

  private static Set<UnaryModalOperator> selectReducedMonitors(EquivalenceClass state) {
    // Scan only content of topmost Gs.
    Set<UnaryModalOperator> operators = new HashSet<>();

    for (Formula support : state.getSupport()) {
      // No decomposition needed.
      if (Fragments.isSafety(support) || Fragments.isCoSafety(support)) {
        continue;
      }

      assert support instanceof UnaryModalOperator || support instanceof BinaryModalOperator;

      Formula formula = support;

      while (formula instanceof XOperator) {
        formula = ((XOperator) formula).operand;
      }

      GOperator gOperator = Collector.transformToGOperator(formula);


      if (gOperator != null) {
        operators.addAll(selectReducedMonitors(gOperator));
      } else {
        FOperator fOperator = Collector.transformToFOperator(formula);
        assert fOperator != null;
        operators.addAll(selectReducedMonitors(fOperator));
      }
    }

    return operators;
  }

  private static Set<UnaryModalOperator> selectReducedMonitors(FOperator fOperator) {
    Set<GOperator> gOperators = Collector.collectTransformedGOperators(fOperator.operand, true);
    Set<UnaryModalOperator> operators = new HashSet<>();
    gOperators.forEach(x -> operators.addAll(selectReducedMonitors(x)));

    if (!(fOperator.operand instanceof GOperator)) {
      operators.add(fOperator);
    }

    return Sets.union(operators, gOperators);
  }

  private boolean containsSuperLanguage(FGObligations set, Iterable<FGObligations> sets,
    EquivalenceClass master) {
    for (FGObligations superSet : sets) {
      if (set.equals(superSet)) {
        continue;
      }

      if (isLanguageContained(set, superSet, master)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Determines if the first language is a subset of the second language.
   *
   * @param subset
   *     first language
   * @param set
   *     second language
   * @param master
   *     remainder
   *
   * @return true if is a sub-language
   */
  private boolean isLanguageContained(FGObligations subset, FGObligations set,
    EquivalenceClass master) {
    if (!isSubset(set.foperators, subset.foperators)) {
      return false;
    }

    EquivalenceClass subsetSafety = evaluator.evaluate(master, subset)
      .andWith(subset.getObligation());
    EquivalenceClass setSafety = evaluator.evaluate(master, set)
      .andWith(set.getObligation());

    boolean isLanguageContained = subsetSafety.implies(setSafety);

    if (isLanguageContained) {
      Logger.getGlobal().log(Level.FINER, () -> subset + " is included in  " + set);
      Logger.getGlobal().log(Level.FINER, () -> subsetSafety + " is included in  " + setSafety);
    }

    EquivalenceClassUtil.free(setSafety, subsetSafety);
    return isLanguageContained;
  }

  @Override
  public Set<FGObligations> select(EquivalenceClass state, boolean isInitialState) {
    if (state.testSupport(Fragments::isX)) {
      Logger.getGlobal().log(Level.FINER, () -> state + " is finite. Suppressing jumps.");
      return Collections.singleton(null);
    }

    if (optimisations.contains(Optimisation.FORCE_JUMPS)
      && state.testSupport(Fragments::isSafety)) {
      Logger.getGlobal().log(Level.FINER, () -> state + " is pure safety. Forcing jump.");
      return Collections.singleton(FGObligations.constructRecurringObligations(ImmutableSet.of(),
        ImmutableSet.of(), factory));
    }

    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      // State is a boolean function over co-safety properties.
      if (state.testSupport(Fragments::isCoSafety)) {
        Logger.getGlobal().log(Level.FINER, () -> state + " is pure co-safety. Suppressing jumps.");
        return Collections.singleton(null);
      }

      // Check for dependency on co-safety properties.
      EquivalenceClass coSafety = state.exists(x -> !Fragments.isCoSafety(x));
      boolean isDependent = !coSafety.isTrue();
      coSafety.free();

      if (isDependent) {
        Logger.getGlobal().log(Level.FINER,
          () -> state + " is dependent on co-safety. Suppressing jumps.");
        // return Collections.singleton(null);
      }
    }

    Set<UnaryModalOperator> keys;

    // Find interesting Fs and Gs
    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      keys = selectReducedMonitors(state);
    } else {
      keys = selectAllMonitors(state);
    }

    Logger.getGlobal().log(Level.FINER,
      () -> state + " can jump to the following components: " + keys);

    // Compute resulting FGObligations.
    Map<Set<UnaryModalOperator>, FGObligations> jumps = new HashMap<>();

    for (Set<UnaryModalOperator> operators : Sets.powerSet(keys)) {
      ImmutableSet<FOperator> fOperators = operators.stream().filter(FOperator.class::isInstance)
        .map(x -> (FOperator) x).collect(ImmutableSet.toImmutableSet());
      ImmutableSet<GOperator> gOperators = operators.stream().filter(GOperator.class::isInstance)
        .map(x -> (GOperator) x).collect(ImmutableSet.toImmutableSet());

      FGObligations obligations = cache.get(fOperators, gOperators);

      if (obligations == null) {
        obligations = FGObligations.constructRecurringObligations(fOperators, gOperators, factory);

        if (obligations != null) {
          cache.put(fOperators, gOperators, obligations);
        }
      }

      if (obligations != null) {
        EquivalenceClass remainder = evaluator.evaluate(state, obligations);

        if (!remainder.isFalse()) {
          jumps.put(operators, obligations);
        }
      }
    }

    Logger.getGlobal().log(Level.FINER, () -> state + " has the following jumps: " + jumps);

    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      jumps.values().removeIf(set -> containsSuperLanguage(set, jumps.values(), state));
    }

    boolean patientState = true;

    if (jumps.size() == 1 && optimisations.contains(Optimisation.FORCE_JUMPS)) {
      Set<Formula> infinitelyOften = state.getSupport(GOperator.class::isInstance);

      if (state.equals(stateFactory.getInitial(Conjunction.create(infinitelyOften)))) {
        patientState = false;
      }
    }

    if (patientState) {
      jumps.put(null, null);
    }

    return new HashSet<>(jumps.values());
  }
}
