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

package owl.factories;

import java.util.ArrayList;
import java.util.List;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.visitors.PropositionalVisitor;

/**
 * For the propositional view on LTL modal operators (F, G, U, X) and literals (a, !a) are treated
 * as propositions.
 */
public final class PropositionVisitor extends PropositionalVisitor<Void> {
  private final List<Formula.ModalOperator> mapping;

  private PropositionVisitor() {
    mapping = new ArrayList<>();
  }

  public static List<Formula.ModalOperator> extractPropositions(Formula formula) {
    PropositionVisitor visitor = new PropositionVisitor();
    formula.accept(visitor);
    return visitor.mapping;
  }

  @Override
  public Void visit(BooleanConstant booleanConstant) {
    return null;
  }

  @Override
  public Void visit(Conjunction conjunction) {
    conjunction.children().forEach(c -> c.accept(this));
    return null;
  }

  @Override
  public Void visit(Disjunction disjunction) {
    disjunction.children().forEach(c -> c.accept(this));
    return null;
  }

  @Override
  protected Void visit(Formula.TemporalOperator formula) {
    if (formula instanceof Literal) {
      return null;
    }

    formula.children().forEach(c -> c.accept(this));
    mapping.add((Formula.ModalOperator) formula);
    return null;
  }
}
