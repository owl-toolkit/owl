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

package owl.ltl;

import java.util.BitSet;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;
import owl.util.annotation.CEntryPoint;

/**
 * Historically.
 */
public class HOperator extends UnaryModalOperator {

  public HOperator(Formula operand) {
    super(HOperator.class, operand);
  }

  /**
   * Construct a LTL-equivalent formula for H(operand). The method examines the operand and might
   * choose to construct a simpler formula. However, the size of the syntax tree is not increased.
   * In order to syntactically construct H(operand) use the constructor.
   *
   * @param operand The operand of the H-operator
   *
   * @return a formula equivalent to H(operand)
   */
  @CEntryPoint
  public static Formula of(Formula operand) {
    if (operand instanceof BooleanConstant) {
      return operand;
    }

    if (operand instanceof Conjunction) {
      return Conjunction.of(((Conjunction) operand).map(HOperator::of));
    }

    if (operand instanceof Biconditional) {
      Biconditional biconditional = (Biconditional) operand;
      return Conjunction.of(
        HOperator.of(Disjunction.of(biconditional.left.not(), biconditional.right)),
        HOperator.of(Disjunction.of(biconditional.left, biconditional.right.not())));
    }

    if (operand instanceof OOperator && ((OOperator) operand).operand instanceof HOperator) {
      return operand;
    }

    if (operand instanceof HOperator) {
      return operand;
    }

    if (operand instanceof TOperator) {
      return of(((TOperator) operand).right);
    }

    return new HOperator(operand);
  }

  @Override
  public int accept(IntVisitor v) {
    return v.visit(this);
  }

  @Override
  public <R> R accept(Visitor<R> v) {
    return v.visit(this);
  }

  @Override
  public <A, B> A accept(BinaryVisitor<B, A> v, B parameter) {
    return v.visit(this, parameter);
  }

  @Override
  public String operatorSymbol() {
    return "H";
  }

  @Override
  public boolean isPureEventual() {
    return false;
  }

  @Override
  public boolean isPureUniversal() {
    return false;
  }

  @Override
  public Formula nnf() {
    return HOperator.of(operand.nnf());
  }

  @Override
  public Formula not() {
    return OOperator.of(operand.not());
  }

  @Override
  public Formula unfold() {
    return Conjunction.of(operand.unfold(), this);
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return Conjunction.of(operand.unfoldTemporalStep(valuation), this);
  }
}
