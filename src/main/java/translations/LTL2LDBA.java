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

import ltl.Formula;
import omega_automaton.output.HOAPrintable;
import translations.ltl2ldba.Ltl2Ldba;
import translations.ltl2ldba.Ltl2Ldgba;
import translations.ltl2ldba.ng.Ltl2LdbaNg;
import translations.ltl2ldba.ng.Ltl2LdgbaNg;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.function.Function;

public class LTL2LDBA extends AbstractLTLCommandLineTool {
    enum Configuration {
        GENERALISED, GUESS_F, // NONDET_INITIAL_COMPONENT
    }

    private final Function<EnumSet<Optimisation>, Function<Formula, ? extends HOAPrintable>> constructor;

    private LTL2LDBA(EnumSet<Configuration> configuration) {
        if (configuration.contains(Configuration.GENERALISED)) {
            if (configuration.contains(Configuration.GUESS_F)) {
                constructor = Ltl2LdgbaNg::new;
            } else {
                constructor = Ltl2Ldgba::new;
            }
        } else {
            if (configuration.contains(Configuration.GUESS_F)) {
                constructor = Ltl2LdbaNg::new;
            } else {
                constructor = Ltl2Ldba::new;
            }
        }
    }

    @Override
    protected Function<Formula, ? extends HOAPrintable> getTranslation(EnumSet<Optimisation> optimisations) {
        return constructor.apply(optimisations);
    }

    public static void main(String... argsArray) throws Exception {
        Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));

        EnumSet<Configuration> configuration = EnumSet.of(Configuration.GENERALISED);

        if (args.remove("--Büchi") || args.remove("--Buchi")) {
            configuration.remove(Configuration.GENERALISED);
        }

        if ((args.remove("--generalised-Büchi") || args.remove("--generalised-Buchi")) && !configuration.contains(Configuration.GENERALISED)) {
            throw new RuntimeException("Invalid arguments: " + Arrays.toString(argsArray));
        }

        if (args.remove("--guess-F")) {
            configuration.add(Configuration.GUESS_F);
        }

        new LTL2LDBA(configuration).execute(args);
    }
}
