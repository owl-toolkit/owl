/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
 *
 * This file is part of Owl.
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

package owl.translations.ltl2ldba;

import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.EAGER_UNFOLD;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.EPSILON_TRANSITIONS;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.FORCE_JUMPS;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.SUPPRESS_JUMPS;

import java.util.EnumSet;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.run.modules.OwlModuleParser;

public abstract class AbstractLTL2LDBAModule implements OwlModuleParser.TransformerParser {

  private static final Option EPSILON = new Option("e", "epsilon", false,
    "Do not remove generated epsilon-transitions. Note: The generated output is not valid HOA, "
      + "since the format does not support epsilon transitions.");

  public static Option guessF() {
    return new Option("f", "guess-f", false,
      "Guess F-operators that are infinitely often true.");
  }

  public static Option simple() {
    return new Option("s", "simple", false,
      "Use a simpler state-space construction. This disables special optimisations and redundancy "
        + "removal.");
  }

  @Override
  public Options getOptions() {
    Options options = new Options();
    List.of(EPSILON, guessF(), simple()).forEach(options::addOption);
    return options;
  }

  static EnumSet<LTL2LDBAFunction.Configuration> configuration(CommandLine commandLine) {
    var configuration = commandLine.hasOption(simple().getOpt())
      ? EnumSet.noneOf(LTL2LDBAFunction.Configuration.class)
      : EnumSet.of(EAGER_UNFOLD, FORCE_JUMPS, OPTIMISED_STATE_STRUCTURE, SUPPRESS_JUMPS);

    if (commandLine.hasOption(EPSILON.getOpt())) {
      configuration.add(EPSILON_TRANSITIONS);
    }

    return configuration;
  }
}
