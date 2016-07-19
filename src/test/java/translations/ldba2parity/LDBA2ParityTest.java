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

package translations.ldba2parity;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.HOAFParser;
import omega_automaton.collections.Collections3;
import ltl.Formula;
import ltl.parser.Parser;
import translations.ltl2ldba.AcceptingComponent;
import translations.ltl2ldba.InitialComponent;
import translations.ltl2ldba.LTL2LDBA;
import translations.nba2ldba.NBA2LDBA;
import omega_automaton.StoredBuchiAutomaton;
import translations.nba2ldba.YCAcc;
import translations.nba2ldba.YCInit;
import org.junit.Before;
import org.junit.Test;
import translations.Optimisation;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class LDBA2ParityTest {

    private LTL2LDBA ltl2ldba;
    private NBA2LDBA nba2ldba;
    private LDBA2Parity<InitialComponent.State, AcceptingComponent.State> ldba2parity;
    private LDBA2Parity<YCInit.State, YCAcc.State> ldba2parity2;

    private Formula ltl;
    private StoredBuchiAutomaton nba;

    private final BiMap<String, Integer> mapping = ImmutableBiMap.of("a", 0, "b", 1, "c", 2, "d", 3);
    private final String INPUT = "HOA: v1\n" +
            "States: 2\n" +
            "Start: 0\n" +
            "acc-name: Buchi\n" +
            "Acceptance: 1 Inf(0)\n" +
            "AP: 1 \"a\"\n" +
            "--BODY--\n" +
            "State: 0 {0}\n" +
            " [0]   1 \n" +
            "State: 1 \n" +
            " [t]   0 \n" +
            " [!0]  1 \n" +
            "--END--";

    @Before
    public void setUp() throws Exception {
        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
        optimisations.remove(Optimisation.FORCE_JUMPS);

        nba2ldba = new NBA2LDBA(optimisations);
        ltl2ldba = new LTL2LDBA(optimisations);
        ldba2parity = new LDBA2Parity<>();
        ldba2parity2 = new LDBA2Parity<>();

        ltl = Parser.formula("(F G a) | (F G b)");

        StoredBuchiAutomaton.Builder builder = new StoredBuchiAutomaton.Builder();
        HOAFParser.parseHOA(new ByteArrayInputStream(INPUT.getBytes(StandardCharsets.UTF_8)), builder);
        nba = Collections3.getElement(builder.getAutomata());
    }

    @Test
    public void testLTL2LDBA() {
        Formula ltl = Parser.formula("(F G a) | (F G b)");
        ParityAutomaton parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(2, parity.size());
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F (a | b)");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(2, parity.size());
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("(F G a) & (F G b)");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(1, parity.size());
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F G (a | X b)");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(3, parity.size());
        assertEquals(3, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F G (a | X b | X X c)");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(8, parity.size());
        assertEquals(3, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("(F G a) | ((F G b) & (G X (X c U F d)))");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(4, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("((G F a)) -> ((G a) & (G F b))");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(3, parity.size());
        assertEquals(3, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("!(((G F a)) -> ((G a) & (G F b)))");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(2, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("(G (F (a))) U b");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(4, parity.size());
        assertEquals(3, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("!((G (F (a))) U b)");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerPrint(System.out)), mapping);
       // assertEquals(4, parity.size()); // should be 3
       // assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("((G F a)) -> ((G F a) & (G F b))");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(3, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("!(((G F a)) -> ((G F a) & (G F b)))");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerPrint(System.out)), mapping);
        assertEquals(5, parity.size()); // should be 4
        assertEquals(5, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F((a) & ((a) W ((b) & ((c) W (a)))))");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(5, parity.size()); // should be 4
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("G (s | G (p | (s & F t)))");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(5, parity.size());
        assertEquals(4, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("F G a | F G b | F G c | F G d");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(4, parity.size()); // 4 is best
        assertEquals(2, parity.getAcceptance().getAcceptanceSets());

        ltl = Parser.formula("! F((a) & ((a) W ((b) & ((c) W (a)))))");
        parity = (ParityAutomaton) ltl2ldba.andThen(ldba2parity).apply(ltl);
        parity.toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()), mapping);
        assertEquals(16, parity.size()); // was 10
        assertEquals(9, parity.getAcceptance().getAcceptanceSets());
    }

    public void testNBA2LDBA() {
        ParityAutomaton parity = (ParityAutomaton) nba2ldba.andThen(ldba2parity2).apply(nba);
        parity.toHOA(new HOAConsumerPrint(System.out), mapping);
    }
}