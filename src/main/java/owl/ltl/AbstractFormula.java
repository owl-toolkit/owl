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

public abstract class AbstractFormula implements Formula {
  private int hashCode = 0;

  @SuppressWarnings("NonFinalFieldReferenceInEquals")
  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }

    AbstractFormula other = (AbstractFormula) o;
    return (hashCode == 0 || other.hashCode == 0 || other.hashCode == hashCode) && equals2(other);
  }

  @Override
  public final int hashCode() {
    if (hashCode == 0) {
      hashCode = hashCodeOnce();
    }

    return hashCode;
  }

  protected abstract boolean equals2(AbstractFormula o);

  protected abstract int hashCodeOnce();
}
