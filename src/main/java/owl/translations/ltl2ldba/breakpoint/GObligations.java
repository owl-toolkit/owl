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

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.factories.EquivalenceClassUtil;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.RankingComparator;
import owl.util.ImmutableObject;

public final class GObligations extends ImmutableObject {

  private static final EquivalenceClass[] EMPTY = new EquivalenceClass[0];
  private static final Comparator<GOperator> rankingComparator = new RankingComparator();

  final Set<GOperator> associatedGs;
  // G(liveness[]) is a liveness language.
  final EquivalenceClass[] liveness;

  // obligations[] are co-safety languages.
  final EquivalenceClass[] obligations;
  // G(safety) is a safety language.
  final EquivalenceClass safety;

  private GObligations(EquivalenceClass safety, List<EquivalenceClass> liveness,
    List<EquivalenceClass> obligations, ImmutableSet<GOperator> es) {
    this.safety = safety;
    this.obligations = obligations.toArray(EMPTY);
    this.liveness = liveness.toArray(EMPTY);
    this.associatedGs = es;
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
  static GObligations constructRecurringObligations(Set<GOperator> gOperatorsSet,
    EquivalenceClassFactory factory, EnumSet<Optimisation> optimisations) {
    // Fields for GObligations
    EquivalenceClass safety = factory.getTrue();
    List<EquivalenceClass> liveness = new ArrayList<>(gOperatorsSet.size());
    List<EquivalenceClass> obligations = new ArrayList<>(gOperatorsSet.size());

    List<GOperator> gOperators = gOperatorsSet.stream().sorted(rankingComparator)
      .collect(Collectors.toList());

    for (int i = 0; i < gOperators.size(); i++) {
      GOperator gOperator = gOperators.get(i);

      // We only propagate information from already constructed G-monitors.
      GObligationsEvaluator.EvaluateVisitor evaluateVisitor =
        new GObligationsEvaluator.EvaluateVisitor(gOperators.subList(0, i), factory);

      Formula formula = RewriterFactory.apply(RewriterEnum.PUSHDOWN_X, RewriterFactory
        .apply(RewriterEnum.MODAL_ITERATIVE, gOperator.operand.accept(evaluateVisitor))
      );
      EquivalenceClass clazz = factory.createEquivalenceClass(formula);

      evaluateVisitor.free();

      if (clazz.isFalse()) {
        EquivalenceClassUtil.free(clazz, safety, liveness, obligations);
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
      EquivalenceClassUtil.free(null, safety, liveness, obligations);
      return null;
    }

    return new GObligations(safety, liveness, obligations, ImmutableSet.copyOf(gOperators));
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    GObligations that = (GObligations) o;
    return safety.equals(that.safety) && Arrays.equals(liveness, that.liveness) && Arrays
      .equals(obligations, that.obligations);
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
    return Objects.hash(safety, Arrays.hashCode(liveness), Arrays.hashCode(obligations));
  }

  boolean implies(GObligations other) {
    // TODO: fix memory leak.
    return getObligation().implies(other.getObligation());
  }

  public boolean isEmpty() {
    return safety.isTrue() && obligations.length == 0 && liveness.length == 0;
  }

  public boolean isPureLiveness() {
    return obligations.length == 0 && safety.isTrue();
  }

  public boolean isPureSafety() {
    return obligations.length == 0 && liveness.length == 0;
  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder(50);

    stringBuilder.append('<');
    if (!safety.isTrue()) {
      stringBuilder.append("safety=").append(safety);
    }

    if (liveness.length > 0) {
      if (!safety.isTrue()) {
        stringBuilder.append(", ");
      }

      stringBuilder.append("liveness=").append(Arrays.toString(liveness));
    }

    if (obligations.length > 0) {
      if (!safety.isTrue() || liveness.length > 0) {
        stringBuilder.append(", ");
      }

      stringBuilder.append("obligations=").append(Arrays.toString(obligations));
    }

    stringBuilder.append('>');
    return stringBuilder.toString();
  }
}