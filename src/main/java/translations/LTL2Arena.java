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

package translations;

import com.google.common.collect.BiMap;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.HashBiMap;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.*;
import ltl.parser.LTLParser;
import translations.ldba2parity.LDBA2Parity;
import translations.ldba2parity.ParityAutomaton;
import translations.ltl2ldba.LTL2LDBA;
import translations.parity2arena.Arena;
import translations.parity2arena.Parity2Arena;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.function.Function;

public class LTL2Arena {

    public static void main(String... args) throws ltl.parser.ParseException, HOAConsumerException, FileNotFoundException {
        Formula ltl;
        BiMap<String, Integer> mapping;
        Parity2Arena parity2Arena;

        if (args.length == 0) {
            ltl = getSharedResourceArbiterLTL(5);
            mapping = getSharedResourceArbiterMapping(5);
            parity2Arena = getSharedResourceArbiterTranslator(5);
            System.out.print(ltl.toString());
        } else {
            LTLParser parser = new LTLParser(new StringReader(args[0]));
            ltl = parser.parse();
            mapping = parser.map;

            BitSet bs = new BitSet();

            // All variables starting with i or e are implicitly marked as controlled by the environment
            for (BiMap.Entry<String, Integer> entry : mapping.entrySet()) {
                if (entry.getKey().startsWith("i") || entry.getKey().startsWith("e")) {
                    bs.set(entry.getValue());
                }
            }

            parity2Arena = new Parity2Arena(bs);
        }

        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);

        Function<Formula, ParityAutomaton> translation = (new LTL2LDBA(optimisations))
                .andThen(new LDBA2Parity<>());
                //.andThen(parity2Arena);

        translation.apply(ltl).toHOA(new HOAConsumerPrint(new FileOutputStream(new File("big2.hoa"))), mapping);
    }

    private static Parity2Arena getSharedResourceArbiterTranslator(int clients) {
        BitSet environmentAlphabet = new BitSet();
        environmentAlphabet.set(0, clients);
        return new Parity2Arena(environmentAlphabet, Arena.Player.System);
    }

    private static Formula getSharedResourceArbiterLTL(int clients) {
        Formula clientBehaviour = BooleanConstant.TRUE;
        Formula exclusiveAccess = BooleanConstant.FALSE;

        for (int i = 0; i < clients; i++) {
            Formula client = GOperator.create(Disjunction.create(new Literal(i, true), XOperator.create(UOperator.create(new Literal(clients + i), new Literal(2 * clients + i)))));
            clientBehaviour = Conjunction.create(client, clientBehaviour);

            Formula exAc = BooleanConstant.TRUE;

            for (int j = 0; j < clients; j++) {
                if (i == j) {
                    continue;
                }

                exAc = Conjunction.create(new Literal(2 * clients + j, true), exAc);
            }

            exclusiveAccess = Disjunction.create(exclusiveAccess, exAc);
        }

        return Conjunction.create(clientBehaviour, GOperator.create(exclusiveAccess));
    }

    private static BiMap<String, Integer> getSharedResourceArbiterMapping(int clients) {
        BiMap<String, Integer> mapping = HashBiMap.create();

        for (int i = 0; i < clients; i++) {
            mapping.put("r" + i, i);
            mapping.put("w" + i, clients + i);
            mapping.put("g" + i, 2 * clients + i);
        }

        return mapping;
    }
}
