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

package omega_automaton.collections.valuationset;

import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public class BddValuationSetFactoryTest {
    private BDDValuationSetFactory factory;
    private Set<String> alphabet;

    @Before
    public void setUp() throws Exception {
        alphabet = ImmutableSet.of("a", "b");
        factory = new BDDValuationSetFactory(2);
    }

    @Test
    public void testGetAlphabet() throws Exception {
        assertEquals(alphabet.size(), factory.getSize());
    }

    @Test
    public void testCreateEmptyValuationSet() throws Exception {
        assertEquals(0, factory.createEmptyValuationSet().size());
    }
}
