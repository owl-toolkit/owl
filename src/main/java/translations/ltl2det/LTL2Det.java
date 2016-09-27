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

package translations.ltl2det;

import com.google.common.collect.BiMap;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.Formula;
import ltl.parser.ParseException;
import ltl.parser.Parser;
import ltl.simplifier.Simplifier;
import omega_automaton.Automaton;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.output.HOAPrintable;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;
import translations.ltl2ldba.*;
import translations.ltl2parity.LTL2Parity;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;

public class LTL2Det {

    public static void main(String... args) throws ParseException, ExecutionException, FileNotFoundException {
        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        Deque<String> argsDeque = new ArrayDeque<>(Arrays.asList(args));

        if (!argsDeque.remove("--parallel")) {
            optimisations.remove(Optimisation.PARALLEL);
        }

        EnumSet<HOAPrintable.Option>  options = argsDeque.remove("--debug") ? EnumSet.of(HOAPrintable.Option.COMMENTS) : EnumSet.noneOf(HOAPrintable.Option.class);
        boolean readStdin = argsDeque.isEmpty();

        Formula formula;
        BiMap<String, Integer> mapping;


        Parser parser = readStdin ? new Parser(System.in) : new Parser(new StringReader(argsDeque.getFirst()));
        formula = Simplifier.simplify(parser.formula(), Simplifier.Strategy.MODAL_EXT);
        mapping = parser.map;

        LTL2LDGBA translationLDGBA = new LTL2LDGBA(optimisations);
        LTL2Parity translationParity = new LTL2Parity(optimisations);

        LimitDeterministicAutomaton<InitialComponent.State, GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent<GeneralisedAcceptingComponent.State>, GeneralisedAcceptingComponent>
                ldba = translationLDGBA.apply(formula);

        Automaton<?, ?> automaton = translationParity.apply(formula);

        if (ldba.isDeterministic() && ldba.getAcceptingComponent().size() <= automaton.size()) {
            automaton = ldba.getAcceptingComponent();
        }

        automaton.toHOA(new HOAConsumerPrint(System.out), mapping, options);
    }
}
