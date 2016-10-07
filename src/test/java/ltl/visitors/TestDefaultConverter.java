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

package ltl.visitors;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ltl.Formula;
import ltl.FrequencyG;
import ltl.parser.Parser;

public class TestDefaultConverter {

    @Test
    public void testNoUnneccessaryChanges_frequencyG() {
        Formula freq = Parser.formula("G {>= 0.6} a");
        // DefaultConverter is abstract, ergo we have to use a subclass for
        // testing
        freq = freq.accept(new RestrictToFGXU());
        assertTrue(freq instanceof FrequencyG);

    }
}
