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

package owl.bdd;

import java.util.Collection;
import java.util.List;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;

/**
 * A factory for creating propositional equivalence classes for LTL formulas.
 *
 * @implNote Since at creation time the set of atomic propositions is fixed only formulas using
 *     these atomic propositions might be used.
 */
public interface EquivalenceClassFactory {

  /**
   * The atomic propositions associated with this factory.
   *
   * @return the list of atomic propositions for all formulas.
   */
  List<String> atomicPropositions();

  /**
   * Create or retrieve a (propositional) equivalence class for a LTL formula.
   *
   * @param formula The LTL formula. It is expected to be negation normal form.
   *
   * @return the corresponding equivalence class.
   */
  EquivalenceClass of(Formula formula);

  default EquivalenceClass of(boolean value) {
    return of(BooleanConstant.of(value));
  }

  EquivalenceClass and(Collection<? extends EquivalenceClass> classes);

  EquivalenceClass or(Collection<? extends EquivalenceClass> classes);

  Encoding defaultEncoding();

  EquivalenceClassFactory withDefaultEncoding(Encoding encoding);

  enum Encoding {
    /**
     * Encode only temporal operators separate from their negation.
     */
    AP_COMBINED,

    /**
     * Encode literals and temporal operators separate from their negation.
     */
    AP_SEPARATE
  }

  void clearCaches();
}
