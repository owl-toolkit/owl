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

package owl.translations;

import java.util.function.Predicate;
import java.util.stream.Collectors;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;

public final class SafetyCoreDetector {
  private SafetyCoreDetector() {
  }

  public static boolean safetyCoreExists(EquivalenceClass state) {
    var modalOperators = state.modalOperators();

    if (SyntacticFragments.isSafety(modalOperators)) {
      return true;
    }

    var nonSafetyModalOperators = modalOperators.stream()
      .filter(Predicate.not(SyntacticFragment.SAFETY::contains))
      .collect(Collectors.toSet());

    var safetyCore = state.substitute(x -> nonSafetyModalOperators.stream()
      .anyMatch(y -> y.anyMatch(x::equals)) ? BooleanConstant.FALSE : x);

    return !safetyCore.isFalse()
      && !safetyCore.atomicPropositions().intersects(
        Conjunction.of(nonSafetyModalOperators).atomicPropositions(true));
  }
}
