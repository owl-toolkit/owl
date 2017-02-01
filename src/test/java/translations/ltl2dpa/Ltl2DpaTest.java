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

package translations.ltl2dpa;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.parser.Parser;
import omega_automaton.output.HOAPrintable;
import org.junit.Test;
import translations.Optimisation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class Ltl2DpaTest {

    static void testOutput(String ltl, int size, int accSize) throws IOException {
        EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
        opts.remove(Optimisation.PARALLEL);
        BiMap<String, Integer> mapping = HashBiMap.create();
        Ltl2Dpa translation = new Ltl2Dpa(opts);
        ParityAutomaton<?> automaton = translation.apply(Parser.formula(ltl, mapping));

        try (OutputStream stream = new ByteArrayOutputStream()) {
            HOAConsumer consumer = new HOAConsumerPrint(stream);
            automaton.setAtomMapping(mapping.inverse());
            automaton.toHOA(consumer, EnumSet.allOf(HOAPrintable.Option.class));
            assertEquals(stream.toString(), size, automaton.size());
            assertEquals(stream.toString(), accSize, automaton.getAcceptance().getAcceptanceSets());
        } catch (IOException ex) {
            throw new IllegalStateException(ex.toString(), ex);
        }
    }

    @Test
    public void testRegression1() throws Exception {
        String ltl = "G (F (a & (a U b)))";
        testOutput(ltl, 2, 1);
        testOutput("! " + ltl, 2, 4);
    }

    @Test
    public void testRegression2() throws Exception {
        String ltl = "G (F (a & X (F b)))";
        testOutput(ltl, 2, 1);
        testOutput("! " + ltl, 3,2);
    }

    @Test
    public void testRegression3() throws Exception {
        String ltl = "F ((a | (G b)) & (c | (G d)) & (e | (G f)))";
        testOutput(ltl, 32, 2);
    }
}