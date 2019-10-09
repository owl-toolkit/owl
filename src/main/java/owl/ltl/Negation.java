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

package owl.ltl;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.IntVisitor;
import owl.ltl.visitors.Visitor;

public class Negation extends Formula.LogicalOperator {
  public final Formula operand;

  public Negation(Formula operand) {
    super(Objects.hash(Negation.class, operand));
    this.operand = operand;
  }

  @Override
  public int accept(IntVisitor visitor) {
    return visitor.visit(this);
  }

  @Override
  public <R> R accept(Visitor<R> visitor) {
    return visitor.visit(this);
  }

  @Override
  public <R, P> R accept(BinaryVisitor<P, R> visitor, P parameter) {
    return visitor.visit(this,parameter);
  }

  @Override
  public Set<Formula> children() {
    return Set.of(operand);
  }

  @Override
  protected int compareToImpl(Formula o) {
    Negation that = (Negation) o;
    return operand.compareTo(that.operand);
  }

  @Override
  protected boolean equalsImpl(Formula o) {
    Negation that = (Negation) o;
    return operand.equals(that.operand);
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
    return operand.nnf().not();
  }

  @Override
  public Formula not() {
    return operand;
  }

  @Override
  public Formula substitute(Function<? super TemporalOperator, ? extends Formula> substitution) {
    throw new UnsupportedOperationException("this is not supported");
  }
}
