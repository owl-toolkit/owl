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

import ltl.parser.Parser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestFrequencyG {

    @Test
    public void testNegation() {
        String test = "G { >= 0.4} a";
        String testNegated = "G {sup > 0.6} (!a)";
        Formula f = Parser.formula(test);
        Formula notF = Parser.formula(testNegated);
        assertEquals(f.not(), notF);
    }

    @Test
    public void testUnfoldingWorks() {
        String test = "G { >= 0.4} a";
        Formula f = Parser.formula(test);
        assertEquals(f, f.unfold());
    }
}
