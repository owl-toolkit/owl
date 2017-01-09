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

import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.consumer.HOAIntermediateStoreAndManipulate;
import jhoafparser.transformations.ToStateAcceptance;
import omega_automaton.output.HOAPrintable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractCommandLineTool<T> {

    private static final String ANNOTATIONS = "--annotations";
    private static final String OPTIMISATIONS = "--optimisations=";

    protected abstract Function<T, ? extends HOAPrintable> getTranslation(EnumSet<Optimisation> optimisations);

    protected abstract T parseInput(InputStream stream) throws Exception;

    protected abstract Map<Integer, String> getAtomMapping();

    private EnumSet<HOAPrintable.Option> parseHOAOutputOptions(Deque<String> args) {
        return args.remove(ANNOTATIONS)
                ? EnumSet.of(HOAPrintable.Option.ANNOTATIONS)
                : EnumSet.noneOf(HOAPrintable.Option.class);
    }

    private EnumSet<Optimisation> parseOptimisationOptions(Deque<String> args) {
        EnumSet<Optimisation> set = EnumSet.noneOf(Optimisation.class);
        Iterator<String> iterator = args.iterator();

        while (iterator.hasNext()) {
            String argument = iterator.next();

            if (!argument.startsWith(OPTIMISATIONS)) {
                continue;
            }

            // Remove element from collection and remove prefix from string.
            argument = argument.substring(OPTIMISATIONS.length());
            iterator.remove();

            switch (argument) {
                case "all":
                    return EnumSet.complementOf(set);

                case "none":
                    return set;

                default:
                    for (String optimisation : argument.split(",")) {
                        set.add(Optimisation.valueOf(optimisation));
                    }

                    return set;
            }
        }

        return EnumSet.complementOf(set);
    }

    void execute(Deque<String> args) throws Exception {
        // Read input.
        EnumSet<HOAPrintable.Option> options = parseHOAOutputOptions(args);
        EnumSet<Optimisation> optimisations = parseOptimisationOptions(args);
        Function<T, ? extends HOAPrintable> translation = getTranslation(optimisations);
        boolean stateAcceptance = args.remove("--state-acceptance");
        boolean readStdin = args.isEmpty();
        T input = parseInput(readStdin ? System.in : new ByteArrayInputStream(args.getFirst().getBytes("UTF-8")));

        // Apply translation.
        HOAPrintable result = translation.apply(input);

        // Write output.
        result.setAtomMapping(getAtomMapping());
        HOAConsumer consumer = new HOAConsumerPrint(System.out);
        if (stateAcceptance) {
            consumer = new HOAIntermediateStoreAndManipulate(consumer, new ToStateAcceptance());
        }
        result.toHOA(consumer, options);
    }
}
