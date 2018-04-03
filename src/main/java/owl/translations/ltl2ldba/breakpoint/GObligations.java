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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.translations.ltl2ldba.FGSubstitution;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;
import owl.translations.ltl2ldba.RecurringObligation;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class GObligations implements RecurringObligation {

  abstract Set<GOperator> gOperators();

  abstract Set<GOperator> gOperatorsRewritten();

  // G(liveness[]) is a liveness language.
  abstract List<EquivalenceClass> liveness();

  // obligations[] are co-safety languages.
  abstract List<EquivalenceClass> obligations();

  // G(safety) is a safety language.
  abstract EquivalenceClass safety();

  /**
   * Construct the recurring obligations for a Gset.
   *
   * @param gOperators
   *     The GOperators that have to be checked often.
   *
   * @return This methods returns null, if the Gset is inconsistent.
   */
  @Nullable
  static GObligations build(Set<GOperator> gOperators, EquivalenceClassFactory factory,
    Set<Configuration> optimisations) {

    // FG-Advice
    FGSubstitution evaluateVisitor = new FGSubstitution(gOperators);

    // Builders
    Set<GOperator> gOperatorsRewritten = new HashSet<>();
    EquivalenceClass safety = factory.getTrue();
    List<EquivalenceClass> liveness = new ArrayList<>(gOperators.size());
    List<EquivalenceClass> obligations = new ArrayList<>(gOperators.size());

    for (GOperator gOperator : gOperators) {
      Formula formula = gOperator.operand.accept(evaluateVisitor);

      // Skip trivial formulas
      if (!(formula instanceof BooleanConstant) && !(formula instanceof GOperator)) {
        gOperatorsRewritten.add(new GOperator(formula));
      }

      EquivalenceClass clazz = factory.of(formula);

      if (clazz.isFalse()) {
        return null;
      }

      if (optimisations.contains(Configuration.OPTIMISED_STATE_STRUCTURE)) {
        Set<Formula> modalOperators = clazz.modalOperators();

        if (modalOperators.stream().allMatch(Fragments::isSafety)) {
          safety = safety.and(clazz);

          if (safety.isFalse()) {
            return null;
          }

          continue;
        }

        if (clazz.atomicPropositions().isEmpty()
          && modalOperators.stream().allMatch(Formula::isPureEventual)) {
          liveness.add(clazz);
          continue;
        }
      }

      obligations.add(clazz);
    }

    if (safety.isTrue() && liveness.isEmpty() && obligations.isEmpty()) {
      return null;
    }

    return GObligationsTuple.create(Set.copyOf(gOperators), Set.copyOf(gOperatorsRewritten),
      liveness, obligations, safety);
  }

  @Override
  public boolean containsLanguageOf(RecurringObligation other) {
    checkArgument(other instanceof GObligations);

    return ((GObligations) other).gOperatorsRewritten().containsAll(gOperatorsRewritten())
      || ((GObligations) other).getObligation().implies(getObligation());
  }

  void forEach(Consumer<EquivalenceClass> consumer) {
    consumer.accept(safety());

    for (EquivalenceClass clazz : liveness()) {
      consumer.accept(clazz);
    }

    for (EquivalenceClass clazz : obligations()) {
      consumer.accept(clazz);
    }
  }

  @Override
  public EquivalenceClass getLanguage() {
    return safety().factory().of(Conjunction.of(gOperatorsRewritten()));
  }

  EquivalenceClass getObligation() {
    EquivalenceClass obligation = safety();

    for (EquivalenceClass clazz : liveness()) {
      obligation = obligation.and(clazz);
    }

    for (EquivalenceClass clazz : obligations()) {
      obligation = obligation.and(clazz);
    }

    return obligation;
  }

  @Override
  public String toString() {
    return '<' + (safety().isTrue() ? "" : "S=" + safety() + ' ')
      + (liveness().isEmpty() ? "" : "L=" + liveness() + ' ')
      + (obligations().isEmpty() ? "" : "O=" + obligations()) + '>';
  }
}
