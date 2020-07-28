/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.ltl.visitors;

import com.google.common.collect.Comparators;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.Negation;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

public final class PrintVisitor implements Visitor<String> {
  private final boolean parenthesize;
  @Nullable
  private final List<String> atomicPropositions;

  private PrintVisitor(boolean parenthesize, @Nullable List<String> atomicPropositions) {
    this.atomicPropositions = atomicPropositions == null
      ? atomicPropositions
      : List.copyOf(atomicPropositions);
    this.parenthesize = parenthesize;
  }

  public static String toString(LabelledFormula formula, boolean parenthesize) {
    PrintVisitor visitor = new PrintVisitor(parenthesize, formula.atomicPropositions());
    return formula.formula().accept(visitor);
  }

  @Override
  public String visit(Biconditional biconditional) {
    return '(' + visitParenthesized(biconditional.leftOperand())
      + " <-> " + visitParenthesized(biconditional.rightOperand()) + ')';
  }

  @Override
  public String visit(BooleanConstant booleanConstant) {
    return booleanConstant.toString();
  }

  @Override
  public String visit(Conjunction conjunction) {
    assert Comparators.isInStrictOrder(conjunction.operands, Comparator.naturalOrder());
    return '(' + conjunction.operands.stream()
      .map(this::visitParenthesized)
      .collect(Collectors.joining(" & ")) + ')';
  }

  @Override
  public String visit(Disjunction disjunction) {
    assert Comparators.isInStrictOrder(disjunction.operands, Comparator.naturalOrder());
    return '(' + disjunction.operands.stream()
      .map(this::visitParenthesized)
      .collect(Collectors.joining(" | ")) + ')';
  }

  @Override
  public String visit(FOperator fOperator) {
    return visit((Formula.UnaryTemporalOperator) fOperator);
  }

  @Override
  public String visit(GOperator gOperator) {
    return visit((Formula.UnaryTemporalOperator) gOperator);
  }

  @Override
  public String visit(Negation negation) {
    return "! " + visitParenthesized(negation.operand());
  }

  @Override
  public String visit(Literal literal) {
    String name = atomicPropositions == null
      ? "p" + literal.getAtom()
      : atomicPropositions.get(literal.getAtom());
    return literal.isNegated() ? '!' + name : name;
  }

  @Override
  public String visit(MOperator mOperator) {
    return visit((Formula.BinaryTemporalOperator) mOperator);
  }

  @Override
  public String visit(ROperator rOperator) {
    return visit((Formula.BinaryTemporalOperator) rOperator);
  }

  @Override
  public String visit(UOperator uOperator) {
    return visit((Formula.BinaryTemporalOperator) uOperator);
  }

  @Override
  public String visit(WOperator wOperator) {
    return visit((Formula.BinaryTemporalOperator) wOperator);
  }

  @Override
  public String visit(XOperator xOperator) {
    return visit((Formula.UnaryTemporalOperator) xOperator);
  }

  private String visit(Formula.UnaryTemporalOperator operator) {
    return operator.operatorSymbol() + visitParenthesized(operator.operand());
  }

  private String visit(Formula.BinaryTemporalOperator operator) {
    return "((" + operator.leftOperand().accept(this) + ") "
      + operator.operatorSymbol()
      + " (" + operator.rightOperand().accept(this) + "))";
  }

  private String visitParenthesized(Formula formula) {
    return parenthesize ? '(' + formula.accept(this) + ')' : formula.accept(this);
  }
}