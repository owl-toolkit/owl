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

package owl.translations.ltl2ldba;

import static owl.translations.ltl2ldba.LTL2LDBAFunction.logger;

import java.util.Objects;
import java.util.logging.Level;
import owl.ltl.EquivalenceClass;

public class Jump<U extends RecurringObligation> {
  final U obligations;
  final EquivalenceClass remainder;
  private final EquivalenceClass language;

  Jump(EquivalenceClass remainder, U obligations) {
    this.remainder = remainder;
    this.obligations = obligations;
    this.language = remainder.and(obligations.getLanguage());
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

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Jump<?> jump = (Jump<?>) o;
    return Objects.equals(remainder, jump.remainder)
      && Objects.equals(obligations, jump.obligations);
  }

  EquivalenceClass getLanguage() {
    return language.duplicate();
  }

  @Override
  public int hashCode() {
    return Objects.hash(remainder, obligations);
  }

  @Override
  public String toString() {
    return "Jump{" + "obligations=" + obligations + ", remainder=" + remainder + ", language="
      + language + '}';
  }
}
