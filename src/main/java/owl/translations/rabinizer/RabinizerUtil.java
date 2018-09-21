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

package owl.translations.rabinizer;

import java.util.HashSet;
import java.util.Set;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BinaryModalOperator;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.UnaryModalOperator;

final class RabinizerUtil {

  private RabinizerUtil() {}

  private static void findSupportingSubFormulas(EquivalenceClass equivalenceClass,
    Set<GOperator> gOperators) {
    // Due to the BDD representation, we have to do a somewhat weird construction. The problem is
    // that we can't simply do a class.getSupport(G) to determine the relevant G operators in the
    // formula. For example, to the BDD "X G a" and "G a" have no relation, hence the G-support
    // of "X G a" is empty, although "G a" certainly is important for the formula. So, instead,
    // we determine all relevant temporal operators in the support and for all of those collect the
    // G operators.

    // TODO Can we optimize for eager?

    for (Formula temporalOperator : equivalenceClass.modalOperators()) {
      if (temporalOperator instanceof GOperator) {
        gOperators.add((GOperator) temporalOperator);
      } else {
        Formula unwrapped = temporalOperator;

        while (unwrapped instanceof UnaryModalOperator) {
          unwrapped = ((UnaryModalOperator) unwrapped).operand;

          if (unwrapped instanceof GOperator) {
            break;
          }
        }

        EquivalenceClassFactory factory = equivalenceClass.factory();

        if (unwrapped instanceof GOperator) {
          gOperators.add((GOperator) unwrapped);
        } else if (unwrapped instanceof BinaryModalOperator) {
          BinaryModalOperator binaryOperator = (BinaryModalOperator) unwrapped;
          findSupportingSubFormulas(factory.of(binaryOperator.left), gOperators);
          findSupportingSubFormulas(factory.of(binaryOperator.right), gOperators);
        } else {
          findSupportingSubFormulas(factory.of(unwrapped), gOperators);
        }
      }
    }
  }

  static Set<GOperator> getRelevantSubFormulas(EquivalenceClass equivalenceClass) {
    Formula representative = equivalenceClass.representative();

    if (representative != null) {
      return representative.subformulas(GOperator.class);
    }

    Set<GOperator> operators = new HashSet<>();
    equivalenceClass.modalOperators().forEach(
      formula -> operators.addAll(formula.subformulas(GOperator.class)));

    return operators;
  }

  static Set<GOperator> getSupportSubFormulas(EquivalenceClass equivalenceClass) {
    if (equivalenceClass.isTrue() || equivalenceClass.isFalse()) {
      return Set.of();
    }

    Set<GOperator> operators = new HashSet<>();
    findSupportingSubFormulas(equivalenceClass, operators);
    return operators;
  }

  static String printRanking(int[] ranking) {
    if (ranking.length == 0) {
      return "[]";
    }
    StringBuilder builder = new StringBuilder(ranking.length * 3 + 2);
    builder.append('[').append(ranking[0]);
    for (int i = 1; i < ranking.length; i++) {
      builder.append(',').append(ranking[i]);
    }
    builder.append(']');
    return builder.toString();
  }
}