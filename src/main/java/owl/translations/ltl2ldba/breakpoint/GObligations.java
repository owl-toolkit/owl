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

package owl.translations.ltl2ldba.breakpoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.SyntacticFragment;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.ltl2ldba.FGSubstitution;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;
import owl.translations.ltl2ldba.RecurringObligation;

public final class GObligations implements RecurringObligation {

  final Set<GOperator> gOperators;

  final Set<GOperator> gOperatorsRewritten;

  // G(liveness[]) is a liveness language.
  final List<EquivalenceClass> liveness;

  // obligations[] are co-safety languages.
  final List<EquivalenceClass> obligations;

  // G(safety) is a safety language.
  final DeterministicConstructions.Safety safetyAutomaton;

  private GObligations(Set<GOperator> gOperators, Set<GOperator> gOperatorsRewritten,
    List<EquivalenceClass> liveness, List<EquivalenceClass> obligations,
    DeterministicConstructions.Safety safetyAutomaton) {
    this.gOperators = Set.copyOf(gOperators);
    this.gOperatorsRewritten = Set.copyOf(gOperatorsRewritten);
    this.liveness = List.copyOf(liveness);
    this.obligations = List.copyOf(obligations);
    this.safetyAutomaton = safetyAutomaton;
  }

  /**
   * Construct the recurring obligations for a G-set.
   *
   * @param gOperators
   *     The GOperators that have to be checked often.
   *
   * @return This methods returns null, if the G-set is inconsistent.
   */
  @Nullable
  static GObligations build(Set<GOperator> gOperators, Factories factories,
    Set<Configuration> optimisations) {

    // FG-Advice
    FGSubstitution evaluateVisitor = new FGSubstitution(gOperators);

    // Builders
    Set<GOperator> gOperatorsRewritten = new HashSet<>();
    Set<Formula> safety = new HashSet<>();
    List<EquivalenceClass> liveness = new ArrayList<>(gOperators.size());
    List<EquivalenceClass> obligations = new ArrayList<>(gOperators.size());

    for (GOperator gOperator : gOperators) {
      Formula formula = gOperator.operand.accept(evaluateVisitor);

      // Skip trivial formulas
      if (BooleanConstant.FALSE.equals(formula)) {
        return null;
      }

      if (BooleanConstant.TRUE.equals(formula)) {
        continue;
      }

      gOperatorsRewritten.add(new GOperator(formula));

      if (optimisations.contains(Configuration.OPTIMISED_STATE_STRUCTURE)) {
        if (SyntacticFragment.SAFETY.contains(formula)) {
          safety.add(GOperator.of(formula));
          continue;
        }

        if (formula.isPureEventual()) {
          liveness.add(factories.eqFactory.of(formula));
          continue;
        }
      }

      EquivalenceClass clazz = factories.eqFactory.of(formula);

      if (clazz.isFalse()) {
        return null;
      }

      obligations.add(clazz);
    }

    DeterministicConstructions.Safety safetyFactory = new DeterministicConstructions.Safety(
      factories, optimisations.contains(Configuration.EAGER_UNFOLD), Conjunction.of(safety));

    if ((safetyFactory.onlyInitialState().isTrue() && liveness.isEmpty() && obligations.isEmpty())
      || safetyFactory.onlyInitialState().isFalse()) {
      return null;
    }

    liveness.sort(Comparator.comparing(EquivalenceClass::representative));
    obligations.sort(Comparator.comparing(EquivalenceClass::representative));

    return new GObligations(Set.copyOf(gOperators), Set.copyOf(gOperatorsRewritten),
      liveness, obligations, safetyFactory);
  }

  @Override
  public boolean containsLanguageOf(RecurringObligation other) {
    GObligations that = (GObligations) other;
    return that.gOperatorsRewritten.containsAll(gOperatorsRewritten);
  }

  @Override
  public EquivalenceClass getLanguage() {
    EquivalenceClassFactory factory = safetyAutomaton.onlyInitialState().factory();
    return factory.of(Conjunction.of(gOperatorsRewritten));
  }

  EquivalenceClass getObligation() {
    EquivalenceClass obligation = safetyAutomaton.onlyInitialState();

    for (EquivalenceClass clazz : liveness) {
      obligation = obligation.and(clazz);
    }

    for (EquivalenceClass clazz : obligations) {
      obligation = obligation.and(clazz);
    }

    return obligation;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof GObligations)) {
      return false;
    }

    GObligations that = (GObligations) o;
    return gOperators.equals(that.gOperators);
  }

  @Override
  public int hashCode() {
    return gOperators.hashCode();
  }

  @Override
  public String toString() {
    return String.format("<%s%s%s%s>",
      safetyAutomaton.onlyInitialState().isTrue()
        ? "" : "S=" + safetyAutomaton.onlyInitialState() + ' ',
      liveness.isEmpty() ? "" : "L=" + liveness + ' ',
      obligations.isEmpty() ? "" : "O=" + obligations,
      gOperators);
  }

  @Override
  public int compareTo(RecurringObligation o) {
    GObligations that = (GObligations) o;
    return Conjunction.of(gOperators)
      .compareTo(Conjunction.of(that.gOperators));
  }
}
