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

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class LabelledAig {
  public abstract Aig aig();

  public abstract boolean isNegated();


  public static LabelledAig of(Aig aig) {
    return of(aig, false);
  }

  public static LabelledAig of(Aig aig, boolean negated) {
    return new AutoValue_LabelledAig(aig, negated);
  }


  public LabelledAig flip() {
    return of(aig(), !this.isNegated());
  }
}
