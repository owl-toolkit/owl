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

package owl.automaton.symbolic;

import com.google.auto.value.AutoValue;
import owl.automaton.acceptance.ParityAcceptance;
import owl.bdd.BddSet;
import owl.collections.ImmutableBitSet;

@FunctionalInterface
public interface SymbolicDPASolver {
  Solution solve(SymbolicAutomaton<? extends ParityAcceptance> dpa, ImmutableBitSet controlledAps);

  @AutoValue
  abstract class Solution {
    public abstract Winner winner();

    public abstract BddSet winningRegion();

    public abstract BddSet strategy();

    static Solution of(Winner winner, BddSet winningRegion, BddSet strategy) {
      return new AutoValue_SymbolicDPASolver_Solution(winner, winningRegion, strategy);
    }

    public enum Winner {
      CONTROLLER,
      ENVIRONMENT
    }
  }

}
