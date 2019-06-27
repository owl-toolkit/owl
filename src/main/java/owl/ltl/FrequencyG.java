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
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.Visitor;

public final class FrequencyG extends GOperator {
  private static final double EPSILON = 1e-12;

  public final double bound;
  public final Comparison cmp;
  public final Limes limes;

  public FrequencyG(Formula operand, double bound, Comparison cmp, Limes limes) {
    super(operand);
    this.bound = bound;
    this.cmp = cmp;
    this.limes = limes;
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
  protected boolean equalsImpl(Formula o) {
    assert this.getClass() == o.getClass();
    FrequencyG that = (FrequencyG) o;
    return Objects.equals(operand, that.operand)
      && Math.abs(this.bound - that.bound) < EPSILON
      && this.cmp == that.cmp && this.limes == that.limes;
  }

  @Override
  public String operatorSymbol() {
    return "G{" + limes + ' ' + cmp + ' ' + bound + '}';
  }

  @Override
  public boolean isPureEventual() {
    throw new UnsupportedOperationException("To my best knowledge not defined");
  }

  @Override
  public boolean isPureUniversal() {
    throw new UnsupportedOperationException("To my best knowledge not defined");
  }

  @Override
  public Formula nnf() {
    return new FrequencyG(operand.nnf(), bound, cmp, limes);
  }

  @Override
  public FrequencyG not() {
    return new FrequencyG(operand.not(), 1.0 - bound, cmp.theOther(), limes.theOther());
  }

  @Override
  public String toString() {
    return "G {" + limes + cmp + bound + "} " + operand;
  }

  @Override
  public FrequencyG unfold() {
    return this;
  }

  public enum Comparison {
    GEQ, GT;

    public Comparison theOther() {
      switch (this) {
        case GEQ:
          return Comparison.GT;
        case GT:
          return Comparison.GEQ;
        default:
          throw new AssertionError();
      }
    }

    @Override
    public String toString() {
      switch (this) {
        case GEQ:
          return ">=";
        case GT:
          return ">";
        default:
          throw new AssertionError();
      }
    }
  }

  public enum Limes {
    SUP, INF;

    public Limes theOther() {
      if (this == SUP) {
        return INF;
      }

      return SUP;
    }

    @Override
    public String toString() {
      if (this == SUP) {
        return "sup";
      }
      return "inf";
    }
  }
}
