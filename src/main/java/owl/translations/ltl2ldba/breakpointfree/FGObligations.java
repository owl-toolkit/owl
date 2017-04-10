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

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.factories.EquivalenceClassUtil;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;

public final class FGObligations {

  private static final EquivalenceClass[] EMPTY = new EquivalenceClass[0];

  final ImmutableSet<FOperator> foperators;
  final ImmutableSet<GOperator> goperators;
  final EquivalenceClass[] liveness;
  final EquivalenceClass safety;

  private FGObligations(ImmutableSet<FOperator> foperators,
    ImmutableSet<GOperator> goperators, EquivalenceClass safety, EquivalenceClass[] liveness) {
    this.safety = safety;
    this.liveness = liveness;
    this.goperators = goperators;
    this.foperators = foperators;
  }

  @Nullable
  static FGObligations constructRecurringObligations(Set<FOperator> fOperators1,
    Set<GOperator> gOperators1, EquivalenceClassFactory factory) {

    ImmutableSet<FOperator> fOperators = ImmutableSet.copyOf(fOperators1);
    ImmutableSet<GOperator> gOperators = ImmutableSet.copyOf(gOperators1);

    // TODO: prune gOper/fOperatos (Gset, Fset)

    EquivalenceClass safety = factory.getTrue();

    for (GOperator gOperator : gOperators) {
      Formula formula = FGObligationsEvaluator.replaceFOperators(fOperators, gOperator);
      EquivalenceClass safety2 = factory.createEquivalenceClass(formula);
      safety = safety.andWith(safety2);
      safety2.free();

      if (safety.isFalse()) {
        return null;
      }
    }

    List<EquivalenceClass> livenessList = new ArrayList<>(fOperators.size());

    for (FOperator fOperator : fOperators) {
      Formula formula = FGObligationsEvaluator.replaceGOperators(gOperators, fOperator);
      formula = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
      formula = RewriterFactory.apply(RewriterEnum.PULLUP_X, formula);

      while (formula instanceof XOperator) {
        formula = ((XOperator) formula).operand;
      }

      // Checking this doesn't make any sense...
      if (formula == BooleanConstant.FALSE) {
        EquivalenceClassUtil.free(safety);
        EquivalenceClassUtil.free(livenessList);
        return null;
      }

      if (formula == BooleanConstant.TRUE) {
        Logger.getGlobal().log(Level.FINER, "Found true obligation.");
      }

      // Wrap into F.
      formula = FOperator.create(formula);
      EquivalenceClass liveness = factory.createEquivalenceClass(formula);
      livenessList.add(liveness);
    }

    return new FGObligations(fOperators, gOperators, safety, livenessList.toArray(EMPTY));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FGObligations that = (FGObligations) o;
    return Objects.equals(foperators, that.foperators)
      && Objects.equals(goperators, that.goperators)
      && Objects.equals(safety, that.safety)
      && Arrays.equals(liveness, that.liveness);
  }

  public EquivalenceClass getObligation() {
    EquivalenceClass obligation = safety.duplicate();

    for (EquivalenceClass clazz : liveness) {
      obligation = obligation.andWith(clazz);
    }

    return obligation;
  }

  @Override
  public int hashCode() {
    return Objects.hash(foperators, goperators, safety, liveness);
  }

  boolean implies(FGObligations that) {
    EquivalenceClass thisObligation = getObligation();
    EquivalenceClass thatObligation = getObligation();
    boolean implies = thisObligation.implies(thatObligation);
    EquivalenceClassUtil.free(thisObligation, thatObligation);
    return implies;
  }

  public boolean isPureLiveness() {
    return safety.isTrue();
  }

  public boolean isPureSafety() {
    return liveness.length == 0;
  }

  @Override
  public String toString() {
    return "FGObligations{" + foperators + ", " + goperators + ", safety=" + safety + ", liveness="
      + Arrays.toString(liveness) + '}';
  }
}
