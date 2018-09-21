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

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;

public final class SafetyDetector {
  private SafetyDetector() {}

  public static boolean hasSafetyCore(EquivalenceClass state, boolean substitutionAnalysis) {
    Set<Formula.ModalOperator> modalOperators = state.modalOperators();

    if (modalOperators.stream().allMatch(SyntacticFragment.SAFETY::contains)) {
      return true;
    }

    // Check if the state has an independent safety core.
    if (substitutionAnalysis) {

      Set<Formula.ModalOperator> coreComplement = new HashSet<>();
      BitSet coreComplementAtomicPropositions = new BitSet();

      modalOperators.forEach(x -> {
        if (!SyntacticFragment.SAFETY.contains(x)) {
          coreComplement.add(x);
          coreComplementAtomicPropositions.or(x.atomicPropositions(true));
        }
      });

      EquivalenceClass core = state.substitute(x ->
        coreComplement.stream().anyMatch(y -> y.anyMatch(x::equals)) ? BooleanConstant.FALSE : x);

      return !core.isFalse()
        && !core.atomicPropositions().intersects(coreComplementAtomicPropositions);
    }

    return false;
  }
}
