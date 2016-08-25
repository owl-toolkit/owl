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

package translations.tlsf2arena;

import jhoafparser.consumer.HOAConsumerException;
import ltl.parser.Parser;
import ltl.tlsf.TLSF;
import translations.Optimisation;
import translations.ltl2parity.LTL2Parity;
import translations.ltl2parity.ParityAutomaton;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;

public class TLSF2Arena {

    public static void main(String... args) throws ltl.parser.ParseException, HOAConsumerException, IOException {
        if (args.length == 0) {
            args = new String[]{"/Users/sickert/Documents/workspace/syntcomp/Benchmarks2016/TLSF/acaciaplus/easy.tlsf"};
        }

        Parser parser = new Parser(new FileInputStream(args[0]));
        TLSF tlsf = parser.tlsf();

        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);

        Any2BitArena bit = new Any2BitArena();
        Any2BitArena.Player fstPlayer = tlsf.target().isMealy() ? Any2BitArena.Player.Environment : Any2BitArena.Player.System;

        File nodeFile = new File(args[0] + ".arena.nodes");
        File edgeFile = new File(args[0] + ".arena.edges");

        LTL2Parity translation = new LTL2Parity();
        ParityAutomaton<?> parity = translation.apply(tlsf.toFormula());
        System.out.print(parity);
        bit.writeBinary(parity, fstPlayer, tlsf.inputs(), nodeFile, edgeFile);
        bit.readBinary(nodeFile, edgeFile);
    }
}
