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

import ltl.BooleanConstant;
import ltl.Formula;
import ltl.equivalence.BDDEquivalenceClassFactory;
import ltl.equivalence.SylvanEquivalenceClassFactory;
import ltl.visitors.AlphabetVisitor;
import omega_automaton.collections.valuationset.BDDValuationSetFactory;
import omega_automaton.collections.valuationset.SylvanValuationSetFactory;

public final class Registry {

  public enum Backend {
    JDD, SYLVAN
  }

  private static final Backend DEFAULT_BACKEND;

  static {
    boolean loadSuccessful = false;

    try {
      NativeLibraryLoader.loadLibrary("sylvan");
      loadSuccessful = true;
    } catch (UnsatisfiedLinkError error) {
      System.err.print(error);
    }

    DEFAULT_BACKEND = loadSuccessful ? Backend.SYLVAN : Backend.JDD;
  }

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
    switch (backend) {
      case SYLVAN:
        return new Factories(new SylvanEquivalenceClassFactory(formula, alphabetSize, null), new SylvanValuationSetFactory(alphabetSize));

      case JDD:
      default:
        return new Factories(new BDDEquivalenceClassFactory(formula), new BDDValuationSetFactory(alphabetSize));
    }
  }
}
