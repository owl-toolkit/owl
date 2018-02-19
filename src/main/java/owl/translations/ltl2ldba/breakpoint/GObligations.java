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

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;
import owl.translations.ltl2ldba.RankingComparator;
import owl.translations.ltl2ldba.RecurringObligation;
import owl.translations.ltl2ldba.breakpoint.GObligationsJumpManager.EvaluateVisitor;

@Value.Style(visibility = ImplementationVisibility.PACKAGE)
@Value.Immutable(builder = false, copy = false, prehash = true)
public abstract class GObligations implements RecurringObligation {
  private static final Comparator<GOperator> rankingComparator = new RankingComparator();

  @Value.Parameter
  abstract Set<GOperator> goperators();

  // G(liveness[]) is a liveness language.
  @Value.Parameter
  abstract List<EquivalenceClass> liveness();

  // obligations[] are co-safety languages.
  @Value.Parameter
  abstract List<EquivalenceClass> obligations();

  @Value.Parameter
  abstract Set<GOperator> rewrittenGOperators();

  // G(safety) is a safety language.
  @Value.Parameter
  abstract EquivalenceClass safety();

  /**
   * Construct the recurring obligations for a Gset.
   *
   * @param gOperatorsSet
   *     The GOperators that have to be checked often.
   *
   * @return This methods returns null, if the Gset is inconsistent.
   */
  @Nullable
  static GObligations build(Set<GOperator> gOperatorsSet,
    EquivalenceClassFactory factory, ImmutableSet<Configuration> optimisations) {
    // Fields for GObligations
    EquivalenceClass safety = factory.getTrue();
    List<EquivalenceClass> liveness = new ArrayList<>(gOperatorsSet.size());
    List<EquivalenceClass> obligations = new ArrayList<>(gOperatorsSet.size());

    List<GOperator> gOperators = gOperatorsSet.stream().sorted(rankingComparator)
      .collect(Collectors.toList());
    ImmutableSet.Builder<GOperator> builder = ImmutableSet.builder();

    for (int i = 0; i < gOperators.size(); i++) {
      GOperator gOperator = gOperators.get(i);

      // We only propagate information from already constructed G-monitors.
      EvaluateVisitor evaluateVisitor = new EvaluateVisitor(gOperators.subList(0, i),
        factory.getTrue());

      Formula formula = gOperator.operand.accept(evaluateVisitor);

      if (!(formula instanceof BooleanConstant) && !(formula instanceof GOperator)) {
        builder.add(new GOperator(formula));
      }

      EquivalenceClass clazz = factory.of(formula);

      if (clazz.isFalse()) {
        return null;
      }

      if (optimisations.contains(Configuration.OPTIMISED_STATE_STRUCTURE)) {
        if (clazz.testSupport(Fragments::isFinite)) {
          safety = safety.and(clazz);
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
      return null;
    }

    return ImmutableGObligations.of(ImmutableSet.copyOf(gOperators), liveness, obligations,
      builder.build(), safety);
  }

  @Override
  public boolean containsLanguageOf(RecurringObligation other) {
    checkArgument(other instanceof GObligations);

    return ((GObligations) other).rewrittenGOperators().containsAll(rewrittenGOperators())
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
    return safety().getFactory().of(Conjunction.of(rewrittenGOperators()));
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || hashCode() != o.hashCode() || getClass() != o.getClass()) {
      return false;
    }

    GObligations that = (GObligations) o;
    return getObligation().equals(that.getObligation());
  }

  @Override
  public int hashCode() {
    return getObligation().hashCode();
  }

  @Override
  public String toString() {
    return '<' + (safety().isTrue() ? "" : "S=" + safety() + ' ')
      + (liveness().isEmpty() ? "" : "L=" + liveness() + ' ')
      + (obligations().isEmpty() ? "" : "O=" + obligations()) + '>';
  }
}
