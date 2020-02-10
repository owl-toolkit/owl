/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.ltl.rewriter;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierFactory.Mode;
import owl.run.modules.OwlModule;

public final class SimplifierTransformer implements OwlModule.LabelledFormulaTransformer {
  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "simplify-ltl",
    "Rewrites / simplifies LTL formulas",
    () -> {
      Option modeOption = new Option("m", "mode", true, "Specify the rewrites to be applied by a "
        + "comma separated list. Possible values are: simple, fairness, fixpoint. By default, "
        + "the \"fixpoint\" mode is chosen.");
      modeOption.setRequired(false);
      modeOption.setArgs(Option.UNLIMITED_VALUES);
      modeOption.setValueSeparator(',');
      return new Options().addOption(modeOption);
    },
    (commandLine, environment) -> {
      if (!commandLine.hasOption("mode")) {
        return new SimplifierTransformer(List.of(Mode.SYNTACTIC_FIXPOINT));
      }

      String[] modes = commandLine.getOptionValues("mode");
      List<Mode> rewrites = new ArrayList<>(modes.length);

      for (String mode : modes) {
        rewrites.add(parseMode(mode));
      }

      return new SimplifierTransformer(rewrites);
    });

  private final List<Mode> rewrites;

  private SimplifierTransformer(List<Mode> rewrites) {
    this.rewrites = List.copyOf(rewrites);
  }

  private static Mode parseMode(String mode) throws ParseException {
    switch (mode) {
      case "simple":
        return Mode.SYNTACTIC;
      case "fixpoint":
        return Mode.SYNTACTIC_FIXPOINT;
      case "fairness":
        return Mode.SYNTACTIC_FAIRNESS;
      default:
        throw new ParseException("Unknown mode " + mode);
    }
  }

  @Override
  public Object transform(LabelledFormula object) {
    LabelledFormula result = object;

    for (Mode rewrite : rewrites) {
      result = SimplifierFactory.apply(result, rewrite);
    }

    return result;
  }
}
