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

package translations.ltl2parity;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import ltl.Formula;
import ltl.parser.Parser;
import org.junit.Before;
import org.junit.Test;
import translations.Optimisation;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class LDBA2ParityTest {

    private LTL2Parity ltl2parity;
    private static final BiMap<String, Integer> MAPPING = ImmutableBiMap.of("a", 0, "b", 1, "c", 2, "d", 3);

    @Before
    public void setUp() throws Exception {
        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
        optimisations.remove(Optimisation.FORCE_JUMPS);

        ltl2parity = new LTL2Parity();
    }

    // @Test
    public void testLTL2LDBA() {
        Formula ltl = Parser.formula("(F G a) | (F G b)");
        ParityAutomaton parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(2, parity.size());
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F (a | b)");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(2, parity.size());
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("(F G a) & (F G b)");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(1, parity.size());
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F G (a | X b)");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(3, parity.size());
        assertEquals(3, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F G (a | X b | X X c)");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(8, parity.size());
        assertEquals(3, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("(F G a) | ((F G b) & (G X (X c U F d)))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(4, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("((G F a)) -> ((G a) & (G F b))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(3, parity.size());
        assertEquals(3, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("!(((G F a)) -> ((G a) & (G F b)))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(2, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("(G (F (a))) U b");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(4, parity.size());
        assertEquals(3, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("!((G (F (a))) U b)");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerPrint(System.out)), MAPPING);
        // assertEquals(4, parity.size()); // should be 3
        // assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("((G F a)) -> ((G F a) & (G F b))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(3, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("!(((G F a)) -> ((G F a) & (G F b)))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerPrint(System.out)), MAPPING);
        // assertEquals(4, parity.size()); // should be 4
        // assertEquals(5, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F((a) & ((a) W ((b) & ((c) W (a)))))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(5, parity.size()); // should be 4
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("G (s | G (p | (s & F t)))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(5, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F G a | F G b | F G c | F G d");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(4, parity.size()); // 4 is best
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("! F((a) & ((a) W ((b) & ((c) W (a)))))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(4, parity.size()); // was 10
        // assertEquals(5, parity.getAcceptance().getAcceptanceSets());
    }
}
