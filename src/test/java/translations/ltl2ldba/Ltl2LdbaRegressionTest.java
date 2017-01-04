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
import ltl.parser.Parser;
import omega_automaton.acceptance.BuchiAcceptance;
import org.junit.Test;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;

import java.io.IOException;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class Ltl2LdbaRegressionTest {

    static void testOutput(String ltl, int size) throws IOException {
        EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
        BiMap<String, Integer> mapping = HashBiMap.create();
        Ltl2Ldba translation = new Ltl2Ldba(opts);
        LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State>, AcceptingComponent> automaton = translation.apply(Parser.formula(ltl, mapping));

        String hoaString = automaton.toString();
        assertEquals(hoaString, size, automaton.size());
    }

    @Test
    public void testLivenessRegression() throws Exception {
        String ltl = "G (F (a & (F b)))";
        testOutput(ltl, 2);
    }

    @Test
    public void testLivenessRegression2() throws Exception {
        String ltl = "G (F a & F b)";
        testOutput(ltl, 2);
    }

    @Test
    public void testObligationSizeRegression() throws Exception {
        String ltl = "G F (b & G b)";
        testOutput(ltl, 2);
    }

    @Test
    public void testRegression7() throws Exception {
        String ltl = "(X(p1)) R (((G(p2)) R (p3)) W (p4))";
        testOutput(ltl, 22);
    }
}
