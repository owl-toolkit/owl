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

package owl.ltl.robust;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import javax.annotation.Nullable;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;

public enum Robustness {
  NEVER {
    @Override
    Robustness stronger() {
      return EVENTUALLY;
    }

    @Override
    Robustness weaker() {
      throw new IllegalStateException(this + " is the bot element");
    }
  },
  EVENTUALLY {
    @Override
    Robustness stronger() {
      return INFINITELY_OFTEN;
    }

    @Override
    Robustness weaker() {
      return NEVER;
    }
  },
  INFINITELY_OFTEN {
    @Override
    Robustness stronger() {
      return EVENTUALLY_ALWAYS;
    }

    @Override
    Robustness weaker() {
      return EVENTUALLY;
    }
  },
  EVENTUALLY_ALWAYS {
    @Override
    Robustness stronger() {
      return ALWAYS;
    }

    @Override
    Robustness weaker() {
      return INFINITELY_OFTEN;
    }
  },
  ALWAYS {
    @Override
    Robustness stronger() {
      throw new IllegalStateException(this + " is the top element");
    }

    @Override
    Robustness weaker() {
      return EVENTUALLY_ALWAYS;
    }
  };

  public static Formula buildFormula(Split split, EnumSet<Robustness> robustness) {
    // One of the five cases is always true, hence short circuit for trivial cases.
    if (robustness.isEmpty()) {
      return BooleanConstant.FALSE;
    }
    if (EnumSet.complementOf(robustness).isEmpty()) {
      return BooleanConstant.TRUE;
    }

    // In this iteration we exploit two properties:
    // First, robustness levels form a lattice, and second the robustness levels are iterated over
    // in ascending fashion, allowing us to build "intervals".
    //
    // For example if a formula should satisfy NEVER, EVENTUALLY, or ALWAYS, it is sufficient to
    // check that we have "ALWAYS | !INFINITELY_OFTEN". If we instead want EVENTUALLY or
    // EVENTUALLY_ALWAYS, we can check for "EVENTUALLY & !INFINITELY_OFTEN | EVENTUALLY_ALWAYS &
    // !ALWAYS".
    Collection<Formula> requirements = new ArrayList<>();
    @Nullable
    Robustness lowerBound = null;
    Robustness[] levels = Robustness.values();
    for (Robustness level : levels) {
      if (lowerBound == null && robustness.contains(level)) {
        lowerBound = level;
      } else if (lowerBound != null && !robustness.contains(level)) {
        // Interval is closed now
        Formula upperBoundExpression = split.get(level).not();
        if (lowerBound == NEVER) {
          requirements.add(upperBoundExpression);
        } else {
          requirements.add(Conjunction.of(split.get(lowerBound), upperBoundExpression));
        }
        lowerBound = null;
      }
    }
    if (lowerBound != null) {
      requirements.add(split.get(lowerBound));
    }

    return Disjunction.of(requirements);
  }

  abstract Robustness stronger();

  abstract Robustness weaker();
}
