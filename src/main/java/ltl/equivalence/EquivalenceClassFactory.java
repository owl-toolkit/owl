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

package ltl.equivalence;

import ltl.BooleanConstant;
import ltl.Conjunction;
import ltl.Formula;

import java.util.Optional;
import java.util.function.Function;

public interface EquivalenceClassFactory {

    EquivalenceClass createEquivalenceClass(Formula formula, Function<Formula, Optional<Boolean>> environment);

    EquivalenceClass createEquivalenceClass(Formula formula);

    default EquivalenceClass createEquivalenceClass(Iterable<Formula> formulas) {
        return createEquivalenceClass(new Conjunction(formulas));
    }

    default EquivalenceClass getTrue() {
        return createEquivalenceClass(BooleanConstant.TRUE);
    }

    default EquivalenceClass getFalse() {
        return createEquivalenceClass(BooleanConstant.FALSE);
    }
}
