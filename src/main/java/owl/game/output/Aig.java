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

package owl.game.output;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class Aig {
  public static final Aig FALSE = new AutoValue_Aig(0, null, false, null, false);

  public abstract int variable();

  @Nullable
  public abstract Aig left();

  public abstract boolean leftIsNegated();

  @Nullable
  public abstract Aig right();

  public abstract boolean rightIsNegated();


  public static Aig leaf(int variable) {
    checkArgument(variable > 0, "Variables need to have positive indices");
    return new AutoValue_Aig(variable, null, false, null, false);
  }

  public static Aig node(Aig left, Aig right) {
    return new AutoValue_Aig(0, left, false, right, false);
  }

  public static Aig node(Aig left, boolean leftNegated, Aig right, boolean rightNegated) {
    return new AutoValue_Aig(0, left, leftNegated, right, rightNegated);
  }

  public boolean isLeaf() {
    return left() == null && right() == null;
  }

  @SuppressWarnings({"ReferenceEquality", "ObjectEquality"})
  public boolean isConstant() {
    return this == FALSE;
  }

  public boolean isVariable() {
    return isLeaf() && !isConstant();
  }
}
