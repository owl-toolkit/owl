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

package owl.jni;

import java.util.HashMap;
import java.util.Map;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.PropositionalFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.visitors.PropositionalVisitor;

class JniAcceptanceAnnotator extends PropositionalVisitor<Map<Formula, JniAcceptance>> {
  static final JniAcceptanceAnnotator INSTANCE = new JniAcceptanceAnnotator();

  @Override
  protected Map<Formula, JniAcceptance> visit(Formula.TemporalOperator formula) {
    if (SyntacticFragment.SAFETY.contains(formula) || SyntacticFragments.isGPast(formula)) {
      return Map.of(formula, JniAcceptance.SAFETY);
    }

    if (SyntacticFragment.CO_SAFETY.contains(formula) || SyntacticFragments.isFPast(formula)) {
      return Map.of(formula, JniAcceptance.CO_SAFETY);
    }

    if (SyntacticFragments.isDetBuchiRecognisable(formula)) {
      return Map.of(formula, JniAcceptance.BUCHI);
    }

    if (SyntacticFragments.isDetCoBuchiRecognisable(formula)) {
      return Map.of(formula, JniAcceptance.CO_BUCHI);
    }

    return Map.of(formula, JniAcceptance.PARITY);
  }

  @Override
  public Map<Formula, JniAcceptance> visit(Biconditional biconditional) {
    Map<Formula, JniAcceptance> acceptanceMap = new HashMap<>();

    acceptanceMap.putAll(biconditional.left.accept(this));
    acceptanceMap.putAll(biconditional.right.accept(this));

    JniAcceptance leftAcceptance = acceptanceMap.get(biconditional.left);
    JniAcceptance rightAcceptance = acceptanceMap.get(biconditional.right);

    if (leftAcceptance.lub(rightAcceptance).isLessOrEqualWeak()) {
      acceptanceMap.put(biconditional, JniAcceptance.WEAK);
    } else {
      acceptanceMap.put(biconditional, JniAcceptance.PARITY);
    }

    return acceptanceMap;
  }

  @Override
  public Map<Formula, JniAcceptance> visit(BooleanConstant booleanConstant) {
    return Map.of(booleanConstant, JniAcceptance.BOTTOM);
  }

  @Override
  public Map<Formula, JniAcceptance> visit(Conjunction conjunction) {
    return visitPropositional(conjunction);
  }

  @Override
  public Map<Formula, JniAcceptance> visit(Disjunction disjunction) {
    return visitPropositional(disjunction);
  }

  private Map<Formula, JniAcceptance> visitPropositional(PropositionalFormula formula) {
    JniAcceptance acceptance = JniAcceptance.BOTTOM;
    Map<Formula, JniAcceptance> acceptanceMap = new HashMap<>();

    for (Formula child : formula.children) {
      Map<Formula, JniAcceptance> childDecisions = child.accept(this);
      acceptanceMap.putAll(childDecisions);
      acceptance = acceptance.lub(acceptanceMap.get(child));
    }

    acceptanceMap.put(formula, acceptance);
    return acceptanceMap;
  }
}
