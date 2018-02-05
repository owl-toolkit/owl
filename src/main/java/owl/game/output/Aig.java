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

package owl.game.output;

import java.util.Objects;
import javax.annotation.Nullable;

public final class Aig {
  public static final Aig FALSE = new Aig();

  public final boolean leftIsNegated;
  public final boolean rightIsNegated;

  @Nullable
  public final Aig left;

  @Nullable
  public final Aig right;
  public final int variable;

  private Aig() {
    this.variable = 0;
    this.leftIsNegated = false;
    this.rightIsNegated = false;
    this.left = null;
    this.right = null;
  }

  public Aig(int variable) {
    assert variable > 0 : "Variables need to have positive indices";
    this.variable = variable;
    this.leftIsNegated = false;
    this.rightIsNegated = false;
    this.left = null;
    this.right = null;
  }

  public Aig(Aig l, Aig r, boolean lNegated, boolean rNegated) {
    this.variable = 0;
    this.left = l;
    this.right = r;
    this.leftIsNegated = lNegated;
    this.rightIsNegated = rNegated;
  }

  public Aig(Aig l, Aig r) {
    this(l, r, false, false);
  }

  public boolean isLeaf() {
    return this.left == null && this.right == null;
  }

  public boolean isConstant() {
    return this == FALSE;
  }

  public boolean isVariable() {
    return isLeaf() && !isConstant();
  }

  @Override
  public int hashCode() {
    return Objects.hash(leftIsNegated, rightIsNegated, left, right, variable);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Aig)) {
      return false;
    }
    Aig aig = (Aig) o;
    return this.leftIsNegated == aig.leftIsNegated
      && this.rightIsNegated == aig.rightIsNegated
      && Objects.equals(this.left, aig.left)
      && Objects.equals(this.right, aig.right)
      && this.variable == aig.variable;
  }
}
