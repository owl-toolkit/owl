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

package owl.util;

public abstract class ImmutableObject {
  private int cachedHashCode = 0;

  @SuppressWarnings("NonFinalFieldReferenceInEquals")
  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }

    ImmutableObject other = (ImmutableObject) o;
    return (cachedHashCode == 0 || other.cachedHashCode == 0
      || other.cachedHashCode == cachedHashCode) && equals2(other);
  }

  protected abstract boolean equals2(ImmutableObject o);

  @Override
  public final int hashCode() {
    // TODO: Hash code could potentially be 0? Not worth a boolean flag though, probably.
    if (cachedHashCode == 0) {
      cachedHashCode = hashCodeOnce();
    }

    return cachedHashCode;
  }

  protected abstract int hashCodeOnce();
}
