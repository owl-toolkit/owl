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
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
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

  @Nullable
  final DeterministicConstructions.GfCoSafety gfCoSafetyAutomaton;
  final DeterministicConstructions.Safety safetyAutomaton;

  private FGObligations(Set<FOperator> fOperators, Set<GOperator> gOperators,
    DeterministicConstructions.Safety safetyAutomaton,
    @Nullable DeterministicConstructions.GfCoSafety gfCoSafetyAutomaton,
    Set<UnaryModalOperator> rewrittenOperators) {
    this.gOperators = Set.copyOf(gOperators);
    this.fOperators = Set.copyOf(fOperators);
    this.rewrittenOperators = Set.copyOf(rewrittenOperators);
    this.safetyAutomaton = safetyAutomaton;
    this.gfCoSafetyAutomaton = gfCoSafetyAutomaton;
  }

  @Nullable
  static FGObligations build(Set<FOperator> fOperators1, Set<GOperator> gOperators1,
    Factories factories, boolean unfold, boolean generalized) {

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

    Set<GOperator> liveness = new HashSet<>();

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
      liveness.add(new GOperator(formula));
    }

    if (!liveness.isEmpty()) {
      var livenessFactory
       = new DeterministicConstructions.GfCoSafety(factories, unfold, liveness, generalized);
      return new FGObligations(fOperators, gOperators, safetyFactory, livenessFactory, builder);
    }

    return new FGObligations(fOperators, gOperators, safetyFactory, null, builder);
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
    return fOperators.equals(that.fOperators) && gOperators.equals(that.gOperators);
  }

  @Override
  public EquivalenceClass language() {
    var factory = safetyAutomaton.onlyInitialState().factory();
    return factory.of(
      Conjunction.of(Collections2.transform(rewrittenOperators, GOperator::of))).unfold();
  }

  @Override
  public boolean isSafety() {
    return gfCoSafetyAutomaton == null;
  }

  @Override
  public boolean isLiveness() {
    return safetyAutomaton.onlyInitialState().isTrue();
  }

  @Override
  public int hashCode() {
    int result = 31 + fOperators.hashCode();
    return 31 * result + gOperators.hashCode();
  }

  @Override
  public String toString() {
    return "<" + fOperators + ", " + gOperators + '>';
  }

  @Override
  public int compareTo(RecurringObligation o) {
    FGObligations that = (FGObligations) o;
    return Conjunction.of(modalOperators()).compareTo(Conjunction.of(that.modalOperators()));
  }

  @Override
  public Set<? extends Formula.ModalOperator> modalOperators() {
    return Sets.union(fOperators, gOperators);
  }
}
