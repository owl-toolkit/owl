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

package owl.translations.ltl2ldba;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.ltl.EquivalenceClass;

public final class Jump<U extends RecurringObligation>
  implements Comparable<Jump<U>> {
  private static final Logger logger = Logger.getLogger(Jump.class.getName());

  final U obligations;
  final EquivalenceClass remainder;

  public Jump(EquivalenceClass remainder, U obligations) {
    this.remainder = remainder;
    this.obligations = obligations;
  }

  @Override
  public int compareTo(Jump<U> o) {
    int comparison = obligations.compareTo(o.obligations);

    if (comparison != 0) {
      return comparison;
    }

    return remainder.representative().compareTo(o.remainder.representative());
  }

  boolean containsLanguageOf(Jump<U> jump) {
    boolean contains = jump.remainder.implies(remainder)
      && obligations.containsLanguageOf(jump.obligations);

    if (contains) {
      logger.log(Level.FINER, () -> this + " contains the language of " + jump);
    }

    return contains;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof Jump)) {
      return false;
    }

    Jump<?> that = (Jump<?>) o;
    return remainder.equals(that.remainder)
      && obligations.equals(that.obligations);
  }

  EquivalenceClass language() {
    return remainder.and(obligations.language());
  }

  @Override
  public int hashCode() {
    return Objects.hash(remainder, obligations);
  }

  @Override
  public String toString() {
    return "Jump{" + "obligations=" + obligations + ", remainder=" + remainder + '}';
  }
}
