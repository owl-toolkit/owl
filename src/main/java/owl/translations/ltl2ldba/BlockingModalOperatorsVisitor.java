/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.ltl2ldba;

import java.util.HashSet;
import java.util.Set;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;
import owl.ltl.visitors.PropositionalVisitor;

public final class BlockingModalOperatorsVisitor
  extends PropositionalVisitor<Set<Formula.ModalOperator>> {

  public static final BlockingModalOperatorsVisitor INSTANCE
    = new BlockingModalOperatorsVisitor();

  private BlockingModalOperatorsVisitor() {}

  @Override
  protected Set<Formula.ModalOperator> visit(Formula.TemporalOperator formula) {
    if (SyntacticFragment.FINITE.contains(formula)) {
      return Set.of();
    }

    if (SyntacticFragment.CO_SAFETY.contains(formula)) {
      return Set.of((Formula.ModalOperator) formula);
    }

    return Set.of();
  }

  @Override
  public Set<Formula.ModalOperator> visit(BooleanConstant booleanConstant) {
    return Set.of();
  }

  @Override
  public Set<Formula.ModalOperator> visit(Conjunction conjunction) {
    Set<Formula.ModalOperator> blockingOperators = new HashSet<>();

    for (Formula child : conjunction.children) {
      // Only consider non-finite LTL formulas.
      if (!SyntacticFragment.FINITE.contains(child)) {
        blockingOperators.addAll(child.accept(this));
      }
    }

    return blockingOperators;
  }

  @Override
  public Set<Formula.ModalOperator> visit(Disjunction disjunction) {
    Set<Formula.ModalOperator> blockingOperators = null;

    for (Formula child : disjunction.children) {
      // Only consider non-finite LTL formulas.
      if (!SyntacticFragment.FINITE.contains(child)) {
        if (blockingOperators == null) {
          blockingOperators = new HashSet<>(child.accept(this));
        } else {
          blockingOperators.retainAll(child.accept(this));
        }
      }
    }

    return blockingOperators == null ? Set.of() : blockingOperators;
  }
}
