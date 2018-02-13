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

public final class LabelledAig {
  public final Aig aig;
  public final boolean isNegated;

  LabelledAig(Aig aig, boolean isNegated) {
    this.aig = aig;
    this.isNegated = isNegated;
  }

  LabelledAig(Aig aig) {
    this.aig = aig;
    this.isNegated = false;
  }

  public LabelledAig flip() {
    return new LabelledAig(this.aig, !this.isNegated);
  }

  @Override
  public int hashCode() {
    return aig.hashCode() ^ Boolean.hashCode(isNegated);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof LabelledAig)) {
      return false;
    }
    LabelledAig other = (LabelledAig) o;
    return this.isNegated == other.isNegated
      && Objects.equals(this.aig, other.aig);
  }
}
