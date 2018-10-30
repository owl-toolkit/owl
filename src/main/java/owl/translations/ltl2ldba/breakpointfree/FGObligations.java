/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.translations.ltl2ldba.breakpointfree;

import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.rewriter.SimplifierFactory.Mode;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.ltl2ldba.RecurringObligation;

public final class FGObligations implements RecurringObligation {

  final Set<FOperator> fOperators;
  final Set<GOperator> gOperators;
  final Set<UnaryModalOperator> rewrittenOperators;
  final DeterministicConstructions.Safety safetyAutomaton;
  final List<DeterministicConstructions.GfCoSafety> gfCoSafetyAutomata;

  private FGObligations(Set<FOperator> fOperators, Set<GOperator> gOperators,
    DeterministicConstructions.Safety safetyAutomaton,
    List<DeterministicConstructions.GfCoSafety> gfCoSafetyAutomata,
    Set<UnaryModalOperator> rewrittenOperators) {
    this.gOperators = Set.copyOf(gOperators);
    this.fOperators = Set.copyOf(fOperators);
    this.rewrittenOperators = Set.copyOf(rewrittenOperators);
    this.safetyAutomaton = safetyAutomaton;
    this.gfCoSafetyAutomata = List.copyOf(gfCoSafetyAutomata);
  }

  @Nullable
  @SuppressWarnings({"PMD.CompareObjectsWithEquals", "ReferenceEquality", "ObjectEquality"})
  static FGObligations build(Set<FOperator> fOperators1, Set<GOperator> gOperators1,
    Factories factories, boolean unfold) {

    Set<FOperator> fOperators = Set.copyOf(fOperators1);
    Set<GOperator> gOperators = Set.copyOf(gOperators1);
    Set<UnaryModalOperator> builder = new HashSet<>();

    // TODO: prune gOper/fOperatos (Gset, Fset)

    Formula safety = BooleanConstant.TRUE;

    for (GOperator gOperator : gOperators) {
      Formula formula = FGObligationsJumpManager
        .replaceFOperators(fOperators, gOperators, gOperator);

      safety = Conjunction.of(safety, formula);

      // Wrap into G.
      formula = GOperator.of(formula);

      if (formula instanceof GOperator) {
        builder.add((GOperator) formula);
      } else {
        builder.add(new GOperator(formula));
      }
    }

    var safetyFactory = new DeterministicConstructions.Safety(factories, unfold, safety);

    if (safetyFactory.onlyInitialState().isFalse()) {
      return null;
    }

    var livenessFactories = new ArrayList<DeterministicConstructions.GfCoSafety>(
      fOperators.size());

    for (FOperator fOperator : fOperators) {
      Formula formula = FGObligationsJumpManager
        .replaceGOperators(gOperators, fOperators, fOperator);
      formula = SimplifierFactory.apply(formula, Mode.SYNTACTIC_FIXPOINT);
      formula = SimplifierFactory.apply(formula, Mode.PULL_UP_X);

      while (formula instanceof XOperator) {
        formula = ((UnaryModalOperator) formula).operand;
      }

      // Checking this doesn't make any sense...
      if (formula == BooleanConstant.FALSE) {
        return null;
      }

      if (formula == BooleanConstant.TRUE) {
        Logger.getGlobal().log(Level.FINER, "Found true obligation.");
        continue;
      }

      // Wrap into F.
      formula = FOperator.of(formula);

      if (!(formula instanceof FOperator)) {
        formula = new FOperator(formula);
      }

      builder.add((FOperator) formula);
      livenessFactories.add(
        new DeterministicConstructions.GfCoSafety(factories, unfold, new GOperator(formula)));
    }

    livenessFactories.sort(
      Comparator.comparing(x -> x.onlyInitialState().representative()));

    return new FGObligations(fOperators, gOperators, safetyFactory, livenessFactories,
      builder);
  }

  @Override
  public boolean containsLanguageOf(RecurringObligation o) {
    FGObligations that = (FGObligations) o;
    return that.rewrittenOperators.containsAll(rewrittenOperators);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof FGObligations)) {
      return false;
    }

    FGObligations that = (FGObligations) o;
    return fOperators.equals(that.fOperators)
      && gOperators.equals(that.gOperators);
  }

  @Override
  public EquivalenceClass getLanguage() {
    EquivalenceClassFactory factory =  safetyAutomaton.onlyInitialState().factory();
    return factory.of(Conjunction.of(Collections2.transform(rewrittenOperators, GOperator::of)));
  }

  @Override
  public int hashCode() {
    return Objects.hash(fOperators, gOperators);
  }

  boolean isPureLiveness() {
    return safetyAutomaton.onlyInitialState().isTrue();
  }

  boolean isPureSafety() {
    return gfCoSafetyAutomata.isEmpty();
  }

  @Override
  public String toString() {
    return "<" + fOperators + ", " + gOperators + '>';
  }

  @Override
  public int compareTo(RecurringObligation o) {
    FGObligations that = (FGObligations) o;
    return Conjunction.of(Sets.union(fOperators, gOperators))
      .compareTo(Conjunction.of(Sets.union(that.fOperators, that.gOperators)));
  }
}
