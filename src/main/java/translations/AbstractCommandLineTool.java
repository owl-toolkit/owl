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

import jhoafparser.consumer.HOAConsumerPrint;
import ltl.Formula;
import ltl.parser.Parser;
import omega_automaton.output.HOAPrintable;

import java.io.StringReader;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.function.Function;

public abstract class AbstractCommandLineTool {

    private static final String ANNOTATIONS = "--annotations";
    private static final String OPTIMISATIONS = "--optimisations=";

    protected abstract Function<Formula, ? extends HOAPrintable> getTranslation(EnumSet<Optimisation> optimisations);

    private EnumSet<HOAPrintable.Option> parseHOAOutputOptions(Deque<String> args) {
        return args.remove(ANNOTATIONS)
                ? EnumSet.of(HOAPrintable.Option.ANNOTATIONS)
                : EnumSet.noneOf(HOAPrintable.Option.class);
    }

    private EnumSet<Optimisation> parseOptimisationOptions(Deque<String> args) {
        for (Iterator<String> iterator = args.iterator(); iterator.hasNext(); ) {
            String argument = iterator.next();

            if (argument.startsWith(OPTIMISATIONS)) {
                iterator.remove();
                argument = argument.substring(OPTIMISATIONS.length());

                switch (argument) {
                    case "all":
                        return EnumSet.allOf(Optimisation.class);

                    case "none":
                        return EnumSet.noneOf(Optimisation.class);

                    default:
                        StringTokenizer tokenizer = new StringTokenizer(argument, "([{,;])");
                        EnumSet<Optimisation> set = EnumSet.noneOf(Optimisation.class);

                        while (tokenizer.hasMoreTokens()) {
                            set.add(Optimisation.valueOf(tokenizer.nextToken()));
                        }

                        return set;
                }
            }
        }

        return EnumSet.allOf(Optimisation.class);
    }

    void execute(Deque<String> args) throws Exception {
        EnumSet<HOAPrintable.Option> options = parseHOAOutputOptions(args);
        EnumSet<Optimisation> optimisations = parseOptimisationOptions(args);

        Function<Formula, ? extends HOAPrintable> translation = getTranslation(optimisations);

        Parser parser = new Parser(new StringReader(args.removeLast()));
        Formula formula = parser.formula();

        HOAPrintable result = translation.apply(formula);
        result.setAtomMapping(parser.map.inverse());
        result.toHOA(new HOAConsumerPrint(System.out), options);
    }
}
