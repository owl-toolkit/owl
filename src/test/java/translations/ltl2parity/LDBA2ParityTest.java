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
import omega_automaton.Automaton;
import org.junit.Before;
import org.junit.Test;
import translations.Optimisation;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class LDBA2ParityTest {

    private LTL2Parity ltl2parity;
    private static final BiMap<String, Integer> MAPPING = ImmutableBiMap.of("a", 0, "b", 1, "c", 2, "d", 3, "e", 4);

    @Before
    public void setUp() throws Exception {
        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
        optimisations.remove(Optimisation.FORCE_JUMPS);

        ltl2parity = new LTL2Parity();
    }

    private void assertSizes(String ltl, int states, int acceptanceSets) {
        Automaton<?, ?> automaton = ltl2parity.apply(Parser.formula(ltl));
        automaton.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerPrint(System.out)), MAPPING);
        assertEquals("States for " + ltl, states, automaton.size());
        assertEquals("Acceptance Sets for " + ltl, acceptanceSets, automaton.getAcceptance().getAcceptanceSets());
    }

    @Test
    public void testSizeReach() {
        assertSizes("F (a | b)", 2, 2);
        assertSizes("! (F a)", 1, 2);
    }

    @Test
    public void testSizeDisjunction() {
        assertSizes("(F G a) | (F G b)", 2, 2);
    }

    @Test
    public void testSizeConjunction() {
        assertSizes("(F G a) & (F G b)", 1, 2);
    }

    @Test
    public void testSizeNextVolatile() {
        assertSizes("F G (a | X b)", 2, 2);
        assertSizes("F G (a & X b)", 2, 2);
        assertSizes("F G (a | X b | X X c)", 4, 2);
        assertSizes("F G (a & X b & X X c)", 3, 2);
    }

    // @Test
    // TODO: FIXME: Size depends on set ordering!
    public void testSizeFGFragment() {
        assertSizes("(G F a) -> (G F b)", 1, 4);
        assertSizes("(G F a) -> ((G F b) & (G F c))", 2, 4);
        assertSizes("((G F a) & (G F b)) -> (G F c)", 2, 4);
        assertSizes("((G F a) & (G F b)) -> ((G F c) & (G F d))", 4, 4);

        assertSizes("(G F a) -> (G a) & (G F b)", 2, 3);
        assertSizes("!((G F a) -> (G a) & (G F b))", 2, 4);
    }

    @Test
    public void testSizeRoundRobin() {
        assertSizes("F G a | F G b | F G c", 3, 2);
        assertSizes("F G a | F G b | F G c | F G d", 4, 2);
        assertSizes("F G a | F G b | F G c | F G d | F G e", 5, 2);
    }

    // @Test
    public void testLTL2LDBA() {
        Formula ltl;
        ParityAutomaton parity;

        ltl = Parser.formula("(F G a) | ((F G b) & (G X (X c U F d)))");
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
        assertEquals(4, parity.size()); // should be 3
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("((G F a)) -> ((G F a) & (G F b))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(2, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("!(((G F a)) -> ((G F a) & (G F b)))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerPrint(System.out)), MAPPING);
        assertEquals(2, parity.size());
        assertEquals(5, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F((a) & ((a) W ((b) & ((c) W (a)))))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(5, parity.size()); // should be 4
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("G (s | G (p | (s & F t)))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(5, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("! F((a) & ((a) W ((b) & ((c) W (a)))))");
        parity = (ParityAutomaton) ltl2parity.apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), MAPPING);
        assertEquals(5, parity.size()); // was 4
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());
    }
}
