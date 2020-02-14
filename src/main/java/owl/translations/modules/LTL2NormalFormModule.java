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

package owl.translations.modules;

import java.io.IOException;
import java.util.List;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.Transformer;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.mastertheorem.Normalisation;

public final class LTL2NormalFormModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2normalform",
    "Translate LTL to the Delta_2 normal-form.",
    options(),
    (commandLine, environment) -> OwlModule.LabelledFormulaTransformer.of(
      Normalisation.of(commandLine.hasOption("s"), commandLine.hasOption("d"), false)));

  private static Options options() {
    var options = new Options();
    options.addOption(
      new Option("s", "only-stable", false, "Compute "
        + "the Delta_2 normal-form that is only equivalent for stable words."));
    options.addOption(
      new Option("d", "dual", false, "Use dual construction."));
    options.addOption("l", "local", false, "Only apply the "
      + "normalisation procedure to formulas outside of Delta_2.");
    return options;
  }

  private LTL2NormalFormModule() {}

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(SimplifierTransformer.MODULE),
      OutputWriters.TO_STRING_MODULE));
  }
}
