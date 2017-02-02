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

import java.io.IOException;
import java.util.logging.Logger;
import owl.factories.sylvan.EQFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.factories.jdd.VSFactory;
import owl.util.NativeLibraryLoader;

public final class Registry {

  private static final Backend DEFAULT_BACKEND = Backend.JDD;

  static {
    try {
      NativeLibraryLoader.loadLibrary("sylvan");
    } catch (UnsatisfiedLinkError | IOException error) {
      Logger.getGlobal().info("Failed to load the jSylvan native BDD library.\n"
        + "The following exception occurred: " + error.toString());
    }
  }

  private Registry() {
  }

  public static Factories getFactories(int alphabetSize) {
    return getFactories(alphabetSize, DEFAULT_BACKEND);
  }

  public static Factories getFactories(int alphabetSize, Backend backend) {
    return getFactories(BooleanConstant.TRUE, alphabetSize, backend);
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
        return new Factories(new EQFactory(formula, alphabetSize, null),
          new owl.factories.sylvan.VSFactory(alphabetSize));

      case JDD:
      default:
        return new Factories(new owl.factories.jdd.EQFactory(formula, alphabetSize, null), new VSFactory(alphabetSize));
    }
  }

  public enum Backend {
    JDD, SYLVAN
  }
}
