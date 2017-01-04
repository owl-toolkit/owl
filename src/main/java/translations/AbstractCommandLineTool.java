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
import java.util.function.Function;

public abstract class AbstractCommandLineTool {

    protected abstract Function<Formula, ? extends HOAPrintable> getTranslation(EnumSet<Optimisation> optimisations);

    void execute(Deque<String> args) throws Exception {
        // TODO: Parse Optimisations
        Function<Formula, ? extends HOAPrintable> translation = getTranslation(EnumSet.allOf(Optimisation.class));

        Parser parser = new Parser(new StringReader(args.removeLast()));
        Formula formula = parser.formula();

        HOAPrintable result = translation.apply(formula);
        result.setAtomMapping(parser.map.inverse());
        result.toHOA(new HOAConsumerPrint(System.out));
    }
}
