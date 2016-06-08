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

package translations.parity2game;

import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.Formula;
import ltl.parser.LTLParser;
import translations.Optimisation;
import translations.ldba2parity.LDBA2Parity;
import translations.ldba2parity.ParityAutomaton;
import translations.ltl2ldba.LTL2LDBA;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.function.Function;

public class Parity2Arena implements Function<ParityAutomaton, Arena> {

    private final BitSet environmentAlphabet;

    public Parity2Arena(BitSet environmentAlphabet) {
        this.environmentAlphabet = environmentAlphabet;
    }

    @Override
    public Arena apply(ParityAutomaton automaton) {
        Arena arena = new Arena(automaton, environmentAlphabet, Arena.Player.Environment);
        arena.generate();
        return arena;
    }

    public static void main(String... args) throws ltl.parser.ParseException, HOAConsumerException, FileNotFoundException {
        if (args.length == 0) {
            // args = new String[]{"G (!a | (F b)) & G (!c | F d)"};
            args = new String[]{"G(!r1 | X((w1)U(g1)))" + " & " + "G(!r2 | X((w2)U(g2)))" + " & " + "G(!r3 | X((w3)U(g3)))" + " & " + "G(!r4 | X((w4)U(g4)))" + " & " + "G(((!g2 & !g3 & !g4) | (!g1 & !g3 & !g4) | (!g1 & !g2 & !g4) | (!g1 & !g2 & !g3)))"};
        }

        BitSet bs = new BitSet();
        bs.set(0);
        bs.set(3);
        bs.set(6);
        bs.set(9);

        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);

        Function<Formula, Arena> translation = (new LTL2LDBA(optimisations))
                .andThen(new LDBA2Parity<>())
                .andThen(new Parity2Arena(bs));

        LTLParser parser = new LTLParser(new StringReader(args[0]));
        Formula formula = parser.parse();

        Arena arena = translation.apply(formula);
        arena.toHOA(new HOAConsumerPrint(new FileOutputStream(new File("temp.hoa"))), parser.map);
    }
}
