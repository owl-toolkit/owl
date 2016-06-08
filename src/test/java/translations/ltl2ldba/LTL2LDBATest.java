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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import jhoafparser.consumer.*;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import translations.ldba.LimitDeterministicAutomaton;
import org.junit.Test;
import ltl.Util;
import translations.Optimisation;

import java.io.IOException;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class LTL2LDBATest {

    public static void testOutput(String ltl, EnumSet<Optimisation> opts, int size, String expectedOutput) throws HOAConsumerException, IOException {
        BiMap<String, Integer> mapping = HashBiMap.create();
        opts.remove(Optimisation.BREAKPOINT_FUSION);
        LTL2LDBA translation = new LTL2LDBA(opts);
        LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent> automaton = translation.apply(Util.createFormula(ltl, mapping));

        String hoaString = automaton.toString();
        assertEquals(hoaString, size, automaton.size());

        if (expectedOutput != null) {
            assertEquals(expectedOutput, automaton.toString(true, mapping));
        }
    }

    public static void testOutput(String ltl, int size, String expectedOutput) throws HOAConsumerException, IOException {
        testOutput(ltl, EnumSet.allOf(Optimisation.class), size, expectedOutput);
    }

    public static void testOutput(String ltl, EnumSet<Optimisation> opts, int size) throws HOAConsumerException, IOException {
        testOutput(ltl, opts, size, null);
    }

    public static void testOutput(String ltl, int size) throws HOAConsumerException, IOException {
        testOutput(ltl, EnumSet.allOf(Optimisation.class), size, null);
    }

    @Test
    public void testToHOA() throws Exception {
        String ltl = "((F G (a U c)) & G X b) | (F G X (f U d)) & X X e";
        testOutput(ltl, 9);
    }

    @Test
    public void testToHOA2() throws Exception {
        String ltl = "(G F a) | (G ((b | X ! a) & ((! b | X a))))";
        testOutput(ltl, 10);
    }

    @Test
    public void testToHOA3() throws Exception {
        String ltl = "G F a";
        testOutput(ltl, 1);
    }

    @Test
    public void testToHOA4() throws Exception {
        String ltl = "G a & F G F b";
        testOutput(ltl, 1);
    }

    @Test
    public void testToHOA5() throws Exception {
        String ltl = "G a | F G F b";
        testOutput(ltl, 3);
    }

    @Test
    public void testToHOA342() throws Exception {
        String ltl = "(F p) U (G q)";
        testOutput(ltl, 4);
    }

    @Test
    public void testDelayedJump() throws Exception {
        String ltl = "X a";
        testOutput(ltl, 3);
        testOutput(ltl, EnumSet.noneOf(Optimisation.class), 6);

        String ltl2 = "X X G b";
        testOutput(ltl2, 3);
        testOutput(ltl2, EnumSet.noneOf(Optimisation.class), 4);
    }

    @Test
    public void testOrBreakUp() throws Exception {
        String ltl = "G a | G b";
        testOutput(ltl, 3);
        testOutput(ltl, EnumSet.noneOf(Optimisation.class), 6);
    }

    @Test
    public void testToHOA6() throws Exception {
        String ltl = "G a | X G b";
        testOutput(ltl, 4);
    }

    @Test
    public void testToHOA7() throws Exception {
        String ltl = "X F (a U G b)";
        testOutput(ltl, 2);
    }

    @Test
    public void testToHOA73() throws Exception {
        String ltl = "G ((X X (a)) | (X b)) | G c";
        testOutput(ltl, 8);
    }

    @Test
    public void testOptimisations() throws Exception {
        String ltl = "((G F d | F G c) & (G F b | F G a) & (G F k | F G h))";
        testOutput(ltl, 9);
    }

    @Test
    public void testOptimisations2() throws Exception {
        String ltl = "G F (b | a)";
        testOutput(ltl, 1);
    }

    @Test
    public void testOptimisations3() throws Exception {
        String ltl = "G((a & !X a) | (X (a U (a & !b & (X(a & b & (a U (a & !b & (X(a & b))))))))))";
        testOutput(ltl, 9);
    }

    @Test
    public void testTrivial() throws Exception {
        String ltl = "a | !a";
        testOutput(ltl, 1);

        String ltl2 = "a & !a";
        testOutput(ltl2, 1);
    }

    @Test
    public void testRejectingCycle() throws Exception {
        String ltl = "!(G(a|b|c))";
        testOutput(ltl, 2);

        String ltl2 = "(G(a|b|c))";
        testOutput(ltl2, 1);
    }

    @Test
    public void testJumps() throws Exception {
        String ltl = "(G a) | X X X X b";
        testOutput(ltl, 11);
    }

    @Test
    public void testSanityCheckFailed() throws Exception {
        String ltl = "(G((F(!(a))) & (F((b) & (X(!(c))))) & (G(F((a) U (d)))))) & (G(F((X(d)) U ((b) | (G(c))))))";
        testOutput(ltl, 5);
    }

    @Test
    public void testSanityCheckFailed2() throws Exception {
        String ltl = "!(((X a) | (F b)) U (a))";
        testOutput(ltl, 7);
    }

    @Test
    public void testGR1() throws Exception {
        String ltl = "((G F b1 & G F b2) | F G !a2 | F G !a1)";
        testOutput(ltl, 4);
    }

    @Test
    public void testFOo() throws Exception {
        String ltl = "(G F c | F G b | F G a)";
        testOutput(ltl, 4);
    }

    @Test
    public void testEx() throws Exception {
        String ltl2 = "X G (a | F b)";
        testOutput(ltl2, 3);
        testOutput(ltl2, EnumSet.noneOf(Optimisation.class), 9);
    }

    @Test
    public void testSingle() throws Exception {
        String ltl = "G ((F a) & (F b))";
        testOutput(ltl, 1);
    }

    @Test
    public void testSCCPatient() throws Exception {
        final String testSCCHOA = "HOA: v1\n" +
                "tool: \"Owl\" \"* *\"\n" +
                "States: 4\n" +
                "Start: 0\n" +
                "acc-name: generalized-Buchi 1\n" +
                "Acceptance: 1 Inf(0)\n" +
                "AP: 3 \"a\" \"b\" \"c\"\n" +
                "--BODY--\n" +
                "State: 1\n" +
                "[0] 2\n" +
                "State: 2\n" +
                "[1] 3\n" +
                "State: 0\n" +
                "[t] 1\n" +
                "State: 3\n" +
                "[2] 3 {0}\n" +
                "--END--\n";

        String ltl = "X (a & (X (b & X G c)))";
        testOutput(ltl, 4, testSCCHOA);
    }

    @Test
    public void testAcceptanceSetSize() throws Exception {
        String ltl = "G ((p1 & p2 & (X(((p1)) | (p2)))) | G(! p2))";
        testOutput(ltl, 6);

        String ltl2 = "G(F(p0 & (G(F(p1))) | (G(!(p1)))))";
        testOutput(ltl2, 4);

        String ltl3 = "F(G(F(((p0) & (G(F(p1))) & (((!(p0)) & (p2)) | (F(p0)))) | ((F(G(!(p1)))) & ((!(p0)) | (((p0) | (!(p2))) & (G(!(p0)))))))))";
        testOutput(ltl3, 6);
    }

    @Test
    public void testCasePrism() throws Exception {
        String ltl = "(G F p1) & (F G ((p1) U (p3)))";
        testOutput(ltl, 2);

        ltl = "((G F p0)|(F G p1)) & ((G F (! p1))|(F G p2))";
        EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
        opts.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
        testOutput(ltl, opts, 5);

        ltl = "(G p0 |(G p1)|(G p2))&((F G p3)|(G F p4)|(G F p5))&((F G !p4)|(G F !p3)|(G F p5))";
        testOutput(ltl, opts, 34);
    }
}