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

package owl.cinterface;

// For the tree annotation "non-existing" or generic types are needed: PARITY, WEAK, BOTTOM
enum Acceptance {
  BUCHI, CO_BUCHI, CO_SAFETY, PARITY, PARITY_MAX_EVEN, PARITY_MAX_ODD, PARITY_MIN_EVEN,
  PARITY_MIN_ODD, SAFETY, WEAK, BOTTOM;

  Acceptance lub(Acceptance other) {
    if (this == BOTTOM || this == other) {
      return other;
    }

    switch (this) {
      case CO_SAFETY:
        return other == SAFETY ? WEAK : other;

      case SAFETY:
        return other == CO_SAFETY ? WEAK : other;

      case WEAK:
        return (other == SAFETY || other == CO_SAFETY) ? this : other;

      case BUCHI:
      case CO_BUCHI:
        return (other == CO_SAFETY || other == SAFETY || other == WEAK) ? this : PARITY;

      default:
        return PARITY;
    }
  }

  boolean isLessThanParity() {
    return this == BUCHI || this == CO_BUCHI || this == CO_SAFETY || this == SAFETY
      || this == WEAK || this == BOTTOM;
  }

  boolean isLessOrEqualWeak() {
    return this == CO_SAFETY || this == SAFETY || this == WEAK || this == BOTTOM;
  }
}
