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

package owl.factories;

import owl.factories.jdd.ValuationFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;

public final class Registry {
  private static final Backend DEFAULT_BACKEND = Backend.JDD;

  private Registry() {
  }

  public static Factories getFactories(int alphabetSize) {
    return getFactories(BooleanConstant.TRUE, alphabetSize, DEFAULT_BACKEND);
  }

  public static Factories getFactories(Formula formula) {
    return getFactories(formula, AlphabetVisitor.extractAlphabet(formula), DEFAULT_BACKEND);
  }

  public static Factories getFactories(Formula formula, Backend backend) {
    return getFactories(formula, AlphabetVisitor.extractAlphabet(formula), backend);
  }

  public static Factories getFactories(Formula formula, int alphabetSize, Backend backend) {
    return new Factories(owl.factories.jdd.EquivalenceFactory.create(formula, alphabetSize),
      new ValuationFactory(alphabetSize));
  }

  public enum Backend {
    JDD
  }
}
