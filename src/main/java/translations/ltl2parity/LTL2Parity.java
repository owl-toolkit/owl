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

import com.google.common.collect.BiMap;
import jhoafparser.consumer.HOAConsumerPrint;
import ltl.Formula;
import ltl.parser.ParseException;
import ltl.parser.Parser;
import ltl.simplifier.Simplifier;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.output.HOAPrintable;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;
import translations.ltl2ldba.AcceptingComponent;
import translations.ltl2ldba.InitialComponent;
import translations.ltl2ldba.Ltl2Ldba;

import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class LTL2Parity implements Function<Formula, ParityAutomaton<?>> {

    // Polling time in ms.
    private static final int SLEEP_MS = 50;
    private final Ltl2Ldba translator;
    private final EnumSet<Optimisation> optimisations;

    public LTL2Parity() {
        this(EnumSet.complementOf(EnumSet.of(Optimisation.PARALLEL)));
    }

    public LTL2Parity(EnumSet<Optimisation> optimisations) {
        this.optimisations = EnumSet.copyOf(optimisations);
        this.optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
        this.optimisations.remove(Optimisation.FORCE_JUMPS);
        translator = new Ltl2Ldba(this.optimisations);
    }

    @Override
    public ParityAutomaton<?> apply(Formula formula) {
        if (!optimisations.contains(Optimisation.PARALLEL)) {
            return apply(formula, new AtomicInteger());
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);

        AtomicInteger automatonCounter = new AtomicInteger(-1);
        AtomicInteger complementCounter = new AtomicInteger(-1);

        Future<ParityAutomaton<?>> automatonFuture = executor.submit(() -> apply(formula, automatonCounter));
        Future<ParityAutomaton<?>> complementFuture = executor.submit(() -> apply(formula.not(), complementCounter));

        ParityAutomaton<?> automaton = null;
        ParityAutomaton<?> complement = null;

        try {
            while (true) {
                try {
                    // Get new results.
                    if (automaton == null && automatonFuture.isDone()) {
                        automaton = automatonFuture.get();
                    }

                    if (complement == null && complementFuture.isDone()) {
                        complement = complementFuture.get();
                    }

                    int size = automaton == null ? automatonCounter.get() : automaton.size();
                    int complementSize = complement == null ? complementCounter.get() : complement.size();

                    if (automaton != null && size <= complementSize) {
                        complementFuture.cancel(true);
                        return automaton;
                    }

                    if (complement != null && complementSize < size) {
                        automatonFuture.cancel(true);
                        complement.complement();
                        return complement;
                    }

                    Thread.sleep(SLEEP_MS);
                } catch (InterruptedException ex) {
                    // Let's continue checking stuff...
                }
            }
        } catch (ExecutionException ex) {
            // The translation broke down, it is unsafe to continue...
            // In order to immediately shutdown the JVM without using System.exit(), we cancel all running Futures.

            automatonFuture.cancel(true);
            complementFuture.cancel(true);
            executor.shutdown();
            throw new RuntimeException(ex);
        } finally {
            executor.shutdown();
        }
    }


    private ParityAutomaton<?> apply(Formula formula, AtomicInteger size) {
        LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State>, AcceptingComponent> ldba = translator.apply(formula);

        if (ldba.isDeterministic()) {
            return new WrappedParityAutomaton(ldba.getAcceptingComponent());
        }

        RankingParityAutomaton parity = new RankingParityAutomaton(ldba, ldba.getAcceptingComponent().getFactory(), size, optimisations);
        parity.generate();

        return parity;
    }

    public static void main(String... args) throws ParseException, ExecutionException, FileNotFoundException {
        EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
        Deque<String> argsDeque = new ArrayDeque<>(Arrays.asList(args));

        if (!argsDeque.remove("--parallel")) {
            optimisations.remove(Optimisation.PARALLEL);
        }

        boolean tlsfInput = argsDeque.remove("--tlsf");
        boolean decompose = argsDeque.remove("--decompose");
        EnumSet<HOAPrintable.Option>  options = argsDeque.remove("--debug") ? EnumSet.of(HOAPrintable.Option.ANNOTATIONS) : EnumSet.noneOf(HOAPrintable.Option.class);
        boolean readStdin = argsDeque.isEmpty();

        LTL2Parity translation = new LTL2Parity(optimisations);

        Formula formula;
        BiMap<String, Integer> mapping;

        if (tlsfInput) {
            Parser parser = new Parser(System.in);
            formula = parser.tlsf().toFormula();
            mapping = parser.map;
        } else {
            Parser parser = readStdin ? new Parser(System.in) : new Parser(new StringReader(argsDeque.getFirst()));
            formula = parser.formula();
            mapping = parser.map;
        }

        formula = Simplifier.simplify(formula, Simplifier.Strategy.MODAL_EXT);

        if (decompose) {
            throw new UnsupportedOperationException();
        } else {
            ParityAutomaton<?> parityAutomaton = translation.apply(formula);
            parityAutomaton.setAtomMapping(mapping.inverse());
            parityAutomaton.toHOA(new HOAConsumerPrint(System.out), options);
        }
    }
}
