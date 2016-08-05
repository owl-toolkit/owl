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

package ltl;

import com.google.common.collect.ImmutableSet;
import ltl.parser.Parser;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class FormulaStorage {
    public static final Set<String> formulaeStrings = ImmutableSet.of("G a", "F G a", "G a | G b", "(G a) U (G b)", "X G b", "F F ((G a) & b)", "a & G b");
    public static final Set<Formula> formulae = ImmutableSet.copyOf(formulaeStrings.stream().map(Parser::formula).collect(Collectors.toSet()));

    @Test
    public void testNot() {
        for (Formula formula : formulae) {
            assertEquals(formula, formula.not().not());
            assertEquals(formula.not(), formula.not().not().not());
        }
    }
}
