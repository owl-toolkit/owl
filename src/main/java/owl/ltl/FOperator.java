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

package owl.ltl;

import java.util.BitSet;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

/**
 * Finally.
 */
public class FOperator extends UnaryModalOperator {

  public FOperator(Formula f) {
    super(f);
  }

  /**
   * Construct a LTL-equivalent formula for F(operand). The method examines the operand and might
   * choose to construct a simpler formula. However, the size of the syntax tree is not increased.
   * In order to syntactically construct F(operand) use the constructor.
   *
   * @param operand the operand of the F-operator
   *
   * @return a formula equivalent to F(operand)
   */
  public static Formula of(Formula operand) {
    if (operand instanceof BooleanConstant) {
      return operand;
    }

    if (operand instanceof Disjunction) {
      return Disjunction.of(((Disjunction) operand).map(FOperator::of));
    }

    if (operand instanceof Biconditional) {
      Biconditional biconditional = (Biconditional) operand;
      return Disjunction.of(FOperator.of(Conjunction.of(biconditional.left, biconditional.right)),
        FOperator.of(Conjunction.of(biconditional.left.not(), biconditional.right.not())));
    }

    if (operand instanceof FOperator) {
      return operand;
    }

    if (operand instanceof MOperator) {
      MOperator mOperator = (MOperator) operand;
      return FOperator.of(Conjunction.of(mOperator.left, mOperator.right));
    }

    if (operand instanceof UOperator) {
      return of(((UOperator) operand).right);
    }

    if (operand instanceof WOperator) {
      WOperator wOperator = (WOperator) operand;
      return Disjunction.of(of(GOperator.of(wOperator.left)), of(wOperator.right));
    }

    return new FOperator(operand);
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
  public String getOperator() {
    return "F";
  }

  @Override
  public boolean isPureEventual() {
    return true;
  }

  @Override
  public boolean isPureUniversal() {
    return operand.isPureUniversal();
  }

  @Override
  public boolean isSuspendable() {
    return operand.isPureUniversal() || operand.isSuspendable();
  }

  @Override
  public Formula nnf() {
    return FOperator.of(operand.nnf());
  }

  @Override
  public Formula not() {
    return GOperator.of(operand.not());
  }

  @Override
  public Formula unfold() {
    return Disjunction.of(operand.unfold(), this);
  }

  @Override
  public Formula unfoldTemporalStep(BitSet valuation) {
    return Disjunction.of(operand.unfoldTemporalStep(valuation), this);
  }
}
