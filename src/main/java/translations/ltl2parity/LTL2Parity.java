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
import translations.Optimisation;
import translations.ltl2ldba.*;
import translations.ldba.LimitDeterministicAutomaton;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class LTL2Parity implements Function<Formula, ParityAutomaton<?>> {

    // Polling time in ms.
    private static final int MILLIS = 33;
    private final LTL2LDBA translator;

    public LTL2Parity() {
        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
        optimisations.remove(Optimisation.FORCE_JUMPS);
        translator = new LTL2LDBA(optimisations);
    }

    @Override
    public ParityAutomaton<?> apply(Formula formula) {
        return apply(formula, new AtomicInteger());
    }


    private ParityAutomaton<?> apply(Formula formula, AtomicInteger size) {
        LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State>, AcceptingComponent> ldba = translator.apply(formula);

        if (ldba.isDeterministic()) {
            return new WrappedParityAutomaton(ldba.getAcceptingComponent());
        }

        RankingParityAutomaton parity = new RankingParityAutomaton(ldba, ldba.getAcceptingComponent().getFactory(), size);
        parity.generate();

        return parity;
    }

    public static void main(String... args) throws Exception {
        Deque<String> argsDeque = new ArrayDeque<>(Arrays.asList(args));

        boolean parallelMode = argsDeque.remove("--parallel");

        LTL2Parity translation = new LTL2Parity();

        if (argsDeque.isEmpty()) {
            argsDeque.add("(F((G(b)) | (G(F(a))))) & (F((G(F(c))) | (G(d)))) & (F((G(F(b))) | (G(c)))) & (F((G(F(d))) | (G(h))))");
        }

        Parser parser = new Parser(new StringReader(argsDeque.getFirst()));
        Formula formula = parser.formula();
        Automaton<?, ?> automaton = null;

        if (parallelMode) {
            ExecutorService executor = Executors.newFixedThreadPool(2);

            AtomicInteger automatonCounter = new AtomicInteger();
            AtomicInteger complementCounter = new AtomicInteger();

            Future<ParityAutomaton<?>> automatonFuture = executor.submit(() -> translation.apply(formula, automatonCounter));
            Future<ParityAutomaton<?>> complementFuture = executor.submit(() -> translation.apply(formula.not(), complementCounter));

            ParityAutomaton<?> complement = null;

            while (true) {
                // Get new results.
                if (automaton == null && automatonFuture.isDone()) {
                    automaton = automatonFuture.get();
                }

                if (complement == null && complementFuture.isDone()) {
                    complement = complementFuture.get();
                }

                int size = automatonCounter.get();
                int complementSize = complementCounter.get();

                if (automaton != null && size <= complementSize) {
                    complementFuture.cancel(true);
                    break;
                }

                if (complement != null && complementSize < size) {
                    automatonFuture.cancel(true);
                    complement.complement();
                    automaton = complement;
                    break;
                }

                try {
                    Thread.sleep(MILLIS);
                } catch (InterruptedException ex) {
                    // Let's continue checking stuff...
                }
            }

            executor.shutdown();
        } else {
            automaton = translation.apply(formula);
        }

        automaton.toHOA(new HOAConsumerPrint(System.out), parser.map);
    }
}
