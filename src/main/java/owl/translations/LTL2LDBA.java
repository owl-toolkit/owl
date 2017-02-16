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
import owl.automaton.output.HoaPrintable;
import owl.ltl.Formula;
import owl.translations.ltl2ldba.LTL2LDGBA;
import owl.translations.ltl2ldba.ng.LTL2LDBAng;
import owl.translations.ltl2ldba.ng.LTL2LDGBAng;

public final class LTL2LDBA extends AbstractLtlCommandLineTool {
  private final Function<EnumSet<Optimisation>, Function<Formula, ? extends HoaPrintable>>
    constructor;
  private final boolean nondet;

  private LTL2LDBA(EnumSet<Configuration> configuration) {
    if (configuration.contains(Configuration.GENERALISED)) {
      if (configuration.contains(Configuration.GUESS_F)) {
        constructor = LTL2LDGBAng::new;
      } else {
        constructor = LTL2LDGBA::new;
      }
    } else {
      if (configuration.contains(Configuration.GUESS_F)) {
        constructor = LTL2LDBAng::new;
      } else {
        constructor = owl.translations.ltl2ldba.LTL2LDBA::new;
      }
    }

    nondet = configuration.contains(Configuration.NONDET_INITIAL_COMPONENT);
  }

  @SuppressWarnings("ProhibitedExceptionDeclared")
  public static void main(String... argsArray) throws Exception { // NOPMD
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));

    EnumSet<Configuration> configuration = EnumSet.of(Configuration.GENERALISED);

    if (args.remove("--Büchi") || args.remove("--Buchi")) {
      configuration.remove(Configuration.GENERALISED);
    }

    if ((args.remove("--generalised-Büchi") || args.remove("--generalised-Buchi")) && !configuration
      .contains(Configuration.GENERALISED)) {
      throw new IllegalArgumentException("Invalid arguments: " + Arrays.toString(argsArray));
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
  protected Function<Formula, ? extends HoaPrintable> getTranslation(
    EnumSet<Optimisation> optimisations) {
    if (nondet) {
      optimisations.remove(Optimisation.DETERMINISTIC_INITIAL_COMPONENT);
    }

    return constructor.apply(optimisations);
  }

  enum Configuration {
    GENERALISED, GUESS_F, NONDET_INITIAL_COMPONENT
  }
}
