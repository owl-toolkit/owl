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

import ltl.Formula;
import ltl.parser.Parser;
import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

public class BDDEquivalenceClassTest extends EquivalenceClassTest {

    @Override
    public EquivalenceClassFactory setUpFactory(Formula domain) {
        return new BDDEquivalenceClassFactory(domain);
    }

    @Test
    public void issue6() throws Exception {
        Formula f = Parser.formula("(p1) U (p2 & G(p2 & !p1))");
        EquivalenceClassFactory factory = new BDDEquivalenceClassFactory(f);
        EquivalenceClass clazz = factory.createEquivalenceClass(f);
        assertNotEquals(null, clazz);
    }
}