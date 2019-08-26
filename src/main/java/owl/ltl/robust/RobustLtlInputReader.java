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

package owl.ltl.robust;

import java.io.BufferedReader;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.modules.OwlModule;

public final class RobustLtlInputReader {
  public static final OwlModule<OwlModule.InputReader> RLTL_INPUT_MODULE = OwlModule.of(
    "rltl",
    "Parses a given rLTL formula and converts it into an LTL specification based on the "
      + "given truth values",
    RobustLtlInputReader::options,
    (commandLine, environment) -> RobustLtlInputReader.createInstance(commandLine));

  private static final Logger logger = Logger.getLogger(RobustLtlInputReader.class.getName());

  private RobustLtlInputReader() {}

  private static Options options() {
    Option truthOption = new Option("t", "truth", true,
      "Specify the truth values which shall be satisfied by the rLTL formula. "
        + "Possible values: "
        + "0 - never, "
        + "1 - eventually, "
        + "2 - infinitely often, "
        + "3 - eventually always, "
        + "4 - always.");
    truthOption.setRequired(true);
    return new Options().addOption(truthOption);
  }

  private static OwlModule.InputReader createInstance(CommandLine commandLine)
    throws ParseException {
    String string = commandLine.getOptionValue("truth");

    EnumSet<Robustness> robustness = EnumSet.noneOf(Robustness.class);

    for (int i = 0; i < string.length(); ++i) {
      char chr = string.charAt(i);
      if (chr == '0') {
        robustness.add(Robustness.NEVER);
      } else if (chr == '1') {
        robustness.add(Robustness.EVENTUALLY);
      } else if (chr == '2') {
        robustness.add(Robustness.INFINITELY_OFTEN);
      } else if (chr == '3') {
        robustness.add(Robustness.EVENTUALLY_ALWAYS);
      } else if (chr == '4') {
        robustness.add(Robustness.ALWAYS);
      } else {
        throw new ParseException("Invalid truth value " + chr);
      }
    }

    //noinspection resource,IOResourceOpenedButNotSafelyClosed
    return (reader, callback, shutdownSignal) -> new BufferedReader(reader).lines()
      .forEach(line -> {
        if (shutdownSignal.get() || line.isEmpty()) {
          return;
        }
        logger.log(Level.INFO, "Parsing {0} with robustness {1}", new Object[] {line, robustness});
        callback.accept(RobustLtlParser.parse(line).toLtl(robustness));
      });
  }
}
