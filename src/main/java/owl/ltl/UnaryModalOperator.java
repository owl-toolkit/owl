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

public abstract class UnaryModalOperator extends Formula.ModalOperator {
  public final Formula operand;

  public UnaryModalOperator(Class<? extends UnaryModalOperator> clazz, Formula operand) {
    super(Objects.hash(clazz, operand));
    this.operand = operand;
  }

  @Override
  public Set<Formula> children() {
    return Set.of(operand);
  }

  public abstract String operatorSymbol();

  @Override
  public String toString() {
    return operatorSymbol() + operand;
  }

  @Override
  protected int compareToImpl(Formula o) {
    assert this.getClass() == o.getClass();
    UnaryModalOperator that = (UnaryModalOperator) o;
    return operand.compareTo(that.operand);
  }

  @Override
  protected boolean equalsImpl(Formula o) {
    assert this.getClass() == o.getClass();
    UnaryModalOperator that = (UnaryModalOperator) o;
    return operand.equals(that.operand);
  }
}
