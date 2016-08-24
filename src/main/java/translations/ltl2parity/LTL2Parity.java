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

import jhoafparser.consumer.HOAConsumerPrint;
import ltl.Formula;
import ltl.parser.Parser;
import omega_automaton.Automaton;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.acceptance.ParityAcceptance;
import translations.Optimisation;
import translations.ltl2ldba.*;
import translations.ldba.LimitDeterministicAutomaton;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

public class LTL2Parity implements Function<Formula, Automaton<?, ?>> {

    private final LTL2LDBA translator;

    public LTL2Parity() {
        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
        optimisations.remove(Optimisation.FORCE_JUMPS);
        translator = new LTL2LDBA(optimisations);
    }

    @Override
    public Automaton<?, ?> apply(Formula formula) {
        LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State>, AcceptingComponent> ldba = translator.apply(formula);

        if (ldba.isDeterministic()) {
            return ldba.getAcceptingComponent();
        }

        ParityAutomaton parity = new ParityAutomaton(ldba, ldba.getAcceptingComponent().getFactory());
        parity.generate();

        return parity;
    }

    public static void main(String... args) throws Exception {
        Deque<String> argsDeque = new ArrayDeque<>(Arrays.asList(args));

        boolean parallelMode = argsDeque.remove("--parallel");

        Function<Formula, Automaton<?, ?>> translation = new LTL2Parity();

        if (argsDeque.isEmpty()) {
            argsDeque.add("G F a");
        }

        Parser parser = new Parser(new StringReader(argsDeque.getFirst()));
        Formula formula = parser.formula();
        Automaton<?, ?> automaton;

        if (parallelMode) {
            ExecutorService executor = Executors.newFixedThreadPool(2);

            Future<Automaton<?, ?>> automatonFuture = executor.submit(() -> translation.apply(formula));
            Future<Automaton<?, ?>> complementFuture = executor.submit(() -> translation.apply(formula.not()));

            automaton = automatonFuture.get();
            Automaton<?, ?> complement = complementFuture.get();

            executor.shutdownNow();

            // The complement is smaller, lets take that one.
            if (complement instanceof ParityAutomaton && complement.size() < automaton.size()) {
                ParityAutomaton complementParity = ((ParityAutomaton) complement);
                complementParity.complement();
                automaton = complementParity;
            }
        } else {
            automaton = translation.apply(formula);
        }

        automaton.toHOA(new HOAConsumerPrint(System.out), parser.map);
    }
}
