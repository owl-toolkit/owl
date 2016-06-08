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

package translations.ltl2ldba;

import org.junit.Before;
import org.junit.Test;
import omega_automaton.AutomatonState;
import translations.Optimisation;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.*;
import ltl.equivalence.BDDEquivalenceClassFactory;
import omega_automaton.collections.valuationset.BDDValuationSetFactory;
import omega_automaton.collections.valuationset.ValuationSetFactory;

import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DetLimitSlaveTest {

    private Formula formula;
    private ValuationSetFactory valuationSetFactory;
    private EquivalenceClassFactory equivalenceClassFactory;
    private DetLimitSlave automaton;
    private DetLimitSlave automatonImp;

    @Before
    public void setUp() {
        formula = new Disjunction(new FOperator(new Literal(0, false)), new XOperator(new Literal(1, false)));
        valuationSetFactory = new BDDValuationSetFactory(formula);
        equivalenceClassFactory = new BDDEquivalenceClassFactory(formula);
        automaton = new DetLimitSlave(equivalenceClassFactory.createEquivalenceClass(formula), equivalenceClassFactory, valuationSetFactory, Collections.emptySet());
        automatonImp = new DetLimitSlave(equivalenceClassFactory.createEquivalenceClass(formula), equivalenceClassFactory, valuationSetFactory,
                EnumSet.allOf(Optimisation.class));
    }

    @Test
    public void testGenerateSuccState() throws Exception {
        AutomatonState initialState = automaton.generateInitialState();

        //assertEquals(initialState, initialState.getSuccessor(new BitSet()));
        assertNotEquals(initialState, initialState.getSuccessor(new BitSet()));
    }

    @Test
    public void testGenerate() {
        automaton.generate();
        automatonImp.generate();
        assertEquals(6, automaton.size());
        assertEquals(4, automatonImp.size());
    }
}