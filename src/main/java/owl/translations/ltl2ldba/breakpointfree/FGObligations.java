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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.collections.Collections3;
import owl.factories.EquivalenceClassFactory;
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
import owl.translations.ltl2ldba.RecurringObligation;

public final class FGObligations implements RecurringObligation {
  final ImmutableSet<FOperator> fOperators;
  final ImmutableSet<GOperator> gOperators;
  final EquivalenceClass[] liveness;
  final ImmutableSet<UnaryModalOperator> rewrittenOperators;
  final EquivalenceClass safety;

  @SuppressWarnings("PMD.ArrayIsStoredDirectly")
  private FGObligations(ImmutableSet<FOperator> fOperators, ImmutableSet<GOperator> gOperators,
    EquivalenceClass safety, EquivalenceClass[] liveness,
    ImmutableSet<UnaryModalOperator> rewrittenOperators) {
    this.safety = safety;
    this.liveness = liveness;
    this.gOperators = gOperators;
    this.fOperators = fOperators;
    this.rewrittenOperators = rewrittenOperators;
  }

  @Nullable
  static FGObligations build(Set<FOperator> fOperators1, Set<GOperator> gOperators1,
    EquivalenceClassFactory factory) {

    ImmutableSet<FOperator> fOperators = ImmutableSet.copyOf(fOperators1);
    ImmutableSet<GOperator> gOperators = ImmutableSet.copyOf(gOperators1);
    ImmutableSet.Builder<UnaryModalOperator> builder = ImmutableSet.builder();

    // TODO: prune gOper/fOperatos (Gset, Fset)

    EquivalenceClass safety = factory.getTrue();

    for (GOperator gOperator : gOperators) {
      Formula formula = FGObligationsJumpManager
        .replaceFOperators(fOperators, gOperators, gOperator);
      EquivalenceClass safety2 = factory.of(formula);
      safety = safety.and(safety2);

      if (safety.unfold().isFalse()) {
        return null;
      }

      // Wrap into G.
      formula = GOperator.of(formula);

      if (formula instanceof GOperator) {
        builder.add((GOperator) formula);
      } else {
        builder.add(new GOperator(formula));
      }
    }

    List<EquivalenceClass> livenessList = new ArrayList<>(fOperators.size());

    for (FOperator fOperator : fOperators) {
      Formula formula = FGObligationsJumpManager
        .replaceGOperators(gOperators, fOperators, fOperator);
      formula = SimplifierFactory.apply(formula, Mode.SYNTACTIC_FIXPOINT);
      formula = SimplifierFactory.apply(formula, Mode.PULLUP_X);

      while (formula instanceof XOperator) {
        formula = ((UnaryModalOperator) formula).operand;
      }

      // Checking this doesn't make any sense...
      //noinspection ObjectEquality
      if (formula == BooleanConstant.FALSE) {
        return null;
      }

      //noinspection ObjectEquality
      if (formula == BooleanConstant.TRUE) {
        Logger.getGlobal().log(Level.FINER, "Found true obligation.");
        continue;
      }

      // Wrap into F.
      formula = FOperator.of(formula);

      if (formula instanceof FOperator) {
        builder.add((FOperator) formula);
      } else {
        builder.add(new FOperator(formula));
      }

      EquivalenceClass liveness = factory.of(formula);
      livenessList.add(liveness);
    }

    return new FGObligations(fOperators, gOperators, safety,
      livenessList.toArray(EquivalenceClass.EMPTY_ARRAY),
      builder.build());
  }

  @Override
  public boolean containsLanguageOf(RecurringObligation other) {
    checkArgument(other instanceof FGObligations);
    return ((FGObligations) other).rewrittenOperators.containsAll(rewrittenOperators);

    // TODO: Fix this? -> Might Depend on EquivalenceClassFactory Literal Encoding.
    //EquivalenceClass thisObligation = getObligation();
    //EquivalenceClass thatObligation = ((FGObligations) other).getObligation();
    //boolean implies = thatObligation.implies(thisObligation);
    //EquivalenceClassUtil.free(thisObligation, thatObligation);
    //return implies;
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
    return Objects.equals(fOperators, that.fOperators)
      && Objects.equals(gOperators, that.gOperators)
      && Objects.equals(safety, that.safety)
      && Arrays.equals(liveness, that.liveness);
  }

  @Override
  public EquivalenceClass getLanguage() {
    return safety.getFactory()
      .of(Conjunction.of(Collections3.transform(rewrittenOperators, GOperator::of)));
  }

  EquivalenceClass getObligation() {
    EquivalenceClass obligation = safety;

    for (EquivalenceClass clazz : liveness) {
      obligation = obligation.and(clazz);
    }

    return obligation;
  }

  @Override
  public int hashCode() {
    return Objects.hash(fOperators, gOperators, safety, Arrays.hashCode(liveness));
  }

  boolean isPureLiveness() {
    return safety.isTrue();
  }

  boolean isPureSafety() {
    return liveness.length == 0;
  }

  @Override
  public String toString() {
    return "<" + fOperators + ", " + gOperators + '>';
  }
}
