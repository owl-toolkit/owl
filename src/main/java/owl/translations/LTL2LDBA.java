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

package owl.translations;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.function.Function;
import ltl.Formula;
import omega_automaton.output.HOAPrintable;
import owl.translations.ltl2ldba.Ltl2Ldba;
import owl.translations.ltl2ldba.Ltl2Ldgba;
import owl.translations.ltl2ldba.ng.Ltl2LdbaNg;
import owl.translations.ltl2ldba.ng.Ltl2LdgbaNg;

public class LTL2LDBA extends AbstractLTLCommandLineTool {
  private final Function<EnumSet<Optimisation>, Function<Formula, ? extends HOAPrintable>> constructor;
  private final boolean nondet;

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

    nondet = configuration.contains(Configuration.NONDET_INITIAL_COMPONENT);
  }

  public static void main(String... argsArray) throws Exception {
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));

    EnumSet<Configuration> configuration = EnumSet.of(Configuration.GENERALISED);

    if (args.remove("--Büchi") || args.remove("--Buchi")) {
      configuration.remove(Configuration.GENERALISED);
    }

    if ((args.remove("--generalised-Büchi") || args.remove("--generalised-Buchi")) && !configuration
      .contains(Configuration.GENERALISED)) {
      throw new RuntimeException("Invalid arguments: " + Arrays.toString(argsArray));
    }

    if (args.remove("--guess-F")) {
      configuration.add(Configuration.GUESS_F);
    }

    if (args.remove("-n") || args.remove("--nondeterministic-initial-component")) {
      configuration.add(Configuration.NONDET_INITIAL_COMPONENT);
    }

    new LTL2LDBA(configuration).execute(args);
  }

  @Override
  protected Function<Formula, ? extends HOAPrintable> getTranslation(
    EnumSet<Optimisation> optimisations) {
    if (nondet) {
      optimisations.remove(Optimisation.DETERMINISTIC_INITIAL_COMPONENT);
    }

    return constructor.apply(optimisations);
  }

  enum Configuration {
    GENERALISED, GUESS_F, NONDET_INITIAL_COMPONENT;
  }
}
