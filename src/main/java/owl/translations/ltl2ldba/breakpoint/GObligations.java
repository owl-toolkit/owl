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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.factories.EquivalenceClassUtil;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;
import owl.translations.ltl2ldba.RankingComparator;
import owl.translations.ltl2ldba.RecurringObligation;
import owl.translations.ltl2ldba.breakpoint.GObligationsJumpManager.EvaluateVisitor;
import owl.util.ImmutableObject;

public final class GObligations extends ImmutableObject implements RecurringObligation {
  private static final Comparator<GOperator> rankingComparator = new RankingComparator();

  final ImmutableSet<GOperator> gOperators;
  // G(liveness[]) is a liveness language.
  final EquivalenceClass[] liveness;
  // obligations[] are co-safety languages.
  final EquivalenceClass[] obligations;
  final ImmutableSet<GOperator> rewrittenGOperators;
  // G(safety) is a safety language.
  final EquivalenceClass safety;

  private GObligations(EquivalenceClass safety, List<EquivalenceClass> liveness,
    List<EquivalenceClass> obligations, ImmutableSet<GOperator> es,
    ImmutableSet<GOperator> build) {
    this.safety = safety;
    this.obligations = obligations.toArray(new EquivalenceClass[obligations.size()]);
    this.liveness = liveness.toArray(new EquivalenceClass[liveness.size()]);
    this.gOperators = es;
    rewrittenGOperators = build;
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

      Formula formula = RewriterFactory.apply(
        RewriterEnum.MODAL_ITERATIVE, gOperator.operand.accept(evaluateVisitor));

      if (!(formula instanceof BooleanConstant) && !(formula instanceof GOperator)) {
        builder.add(new GOperator(formula));
      }

      EquivalenceClass clazz = factory.createEquivalenceClass(formula);

      evaluateVisitor.free();

      if (clazz.isFalse()) {
        EquivalenceClassUtil.free(clazz, safety, liveness, obligations);
        return null;
      }

      if (optimisations.contains(Configuration.OPTIMISED_STATE_STRUCTURE)) {
        if (clazz.testSupport(Fragments::isFinite)) {
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
      EquivalenceClassUtil.free(null, safety, liveness, obligations);
      return null;
    }

    return new GObligations(safety, liveness, obligations, ImmutableSet.copyOf(gOperators),
      builder.build());
  }

  @Override
  public boolean containsLanguageOf(RecurringObligation other) {
    checkArgument(other instanceof GObligations);

    if (((GObligations) other).rewrittenGOperators.containsAll(rewrittenGOperators)) {
      return true;
    }

    // TODO: fix memory leak.
    return ((GObligations) other).getObligation().implies(getObligation());
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    GObligations that = (GObligations) o;
    // TODO: fix memory leak.
    return getObligation().equals(that.getObligation());
  }

  void forEach(Consumer<EquivalenceClass> consumer) {
    consumer.accept(safety);

    for (EquivalenceClass clazz : liveness) {
      consumer.accept(clazz);
    }

    for (EquivalenceClass clazz : obligations) {
      consumer.accept(clazz);
    }
  }

  @Override
  public EquivalenceClass getLanguage() {
    return safety.getFactory().createEquivalenceClass(rewrittenGOperators);
  }

  EquivalenceClass getObligation() {
    EquivalenceClass obligation = safety.duplicate();

    for (EquivalenceClass clazz : liveness) {
      obligation = obligation.andWith(clazz);
    }

    for (EquivalenceClass clazz : obligations) {
      obligation = obligation.andWith(clazz);
    }

    return obligation;
  }

  @Override
  protected int hashCodeOnce() {
    // TODO: fix memory leak.
    return getObligation().hashCode();
  }

  @Override
  public String toString() {
    return '<' + (safety.isTrue() ? "" : "S=" + safety + ' ')
      + (liveness.length <= 0 ? "" : "L=" + Arrays.toString(liveness) + ' ')
      + (obligations.length <= 0 ? "" : "O=" + Arrays.toString(obligations)) + '>';
  }
}
