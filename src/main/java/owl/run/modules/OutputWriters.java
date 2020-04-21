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

package owl.run.modules;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.consumer.HOAIntermediateStoreAndManipulate;
import jhoafparser.extensions.ToStateAcceptanceFixed;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.hoa.HoaWriter;
import owl.automaton.hoa.HoaWriter.HoaOption;

public final class OutputWriters {
  public static final OwlModule<OwlModule.OutputWriter> AUTOMATON_STATS_MODULE = OwlModule.of(
    "aut-stat",
    "Writes several stats of a given automaton to the given format string",
    () -> {
      Option format = new Option("f", "format", true,
        "The format string. Uses a reduced set of the spot syntax\n"
          + "%A, %a   Number of acceptance sets\n"
          + "%C, %c   Number of SCCs\n"
          + "%D, %d   1 if the automaton is deterministic, 0 otherwise\n"
          + "%G, %g   acceptance condition (in HOA syntax)\n"
          + "%S, %s   Number of states\n"
          + "%H, %h   The automaton in HOA format on a single line\n"
          + "%M, %m   Name of the automaton\n"
          + "%n       Newline\n"
          + "%X, %x   Number of atomic propositions");
      format.setRequired(true);
      return new Options().addOption(format);
    },
    (commandLine, environment) -> new AutomatonStats(commandLine.getOptionValue("format")));

  public static final OwlModule<OwlModule.OutputWriter> HOA_OUTPUT_MODULE = OwlModule.of(
    "hoa",
    "Writes the HOA format representation of an automaton.",
    () -> {
      Option option = new Option("s", "state-acceptance", false,
        "Output an automaton with state-acceptance instead of transition acceptance.");
      return new Options().addOption(option);
    },
    (commandLine, environment) ->
      new ToHoa(environment.annotations(), commandLine.hasOption("state-acceptance")));

  public static final OwlModule<OwlModule.OutputWriter> NULL_MODULE = OwlModule.of(
    "null",
    "Discards the output - useful for performance testing",
    (commandLine, environment) -> (writer, object) -> writer.flush());

  public static final OwlModule<OwlModule.OutputWriter> TO_STRING_MODULE = OwlModule.of(
    "string",
    "Prints the toString() representation of all passed objects",
    (commandLine, environment) -> (writer, object) -> {
      writer.write(object.toString());
      writer.write(System.lineSeparator());
    });


  public static class AutomatonStats implements OwlModule.OutputWriter {
    private static final Map<Pattern, Function<Automaton<?, ?>, String>> patterns = Map.of(
      // Acceptance condition
      Pattern.compile("%G", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> automaton.acceptance().booleanExpression().toString(),

      // Acceptance set count
      Pattern.compile("%A", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(automaton.acceptance().acceptanceSets()),

      // Is deterministic
      Pattern.compile("%D", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> automaton.is(Property.DETERMINISTIC) ? "1" : "0",

      // Single line HOA
      Pattern.compile("%H", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> HoaWriter.toString(automaton).replace('\n', ' '),

      // Name
      Pattern.compile("%M", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      Automaton::name,

      // State count
      Pattern.compile("%S", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(automaton.size()),

      // Number of propositions
      Pattern.compile("%X", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(automaton.factory().alphabet().size()),

      // Number of SCCs
      Pattern.compile("%C", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(SccDecomposition.of(automaton).sccs().size()),

      Pattern.compile("%n", Pattern.LITERAL),
      automaton -> "\n");

    private final String formatString;

    public AutomatonStats(String formatString) {
      this.formatString = formatString;
    }

    @Override
    public void write(Writer writer, Object object) throws IOException {
      checkArgument(object instanceof Automaton);
      Automaton<?, ?> automaton = (Automaton<?, ?>) object;

      String result = formatString;

      for (Map.Entry<Pattern, Function<Automaton<?, ?>, String>> pattern : patterns.entrySet()) {
        Matcher matcher = pattern.getKey().matcher(result);

        if (matcher.find()) {
          String replacement = Matcher.quoteReplacement(pattern.getValue().apply(automaton));
          result = matcher.replaceAll(replacement);
        }
      }

      writer.write(result);
    }
  }

  /**
   * Converts any {@link HoaWriter HOA printable} object to its corresponding <a
   * href="http://adl.github.io/hoaf/">HOA</a> representation.
   */
  public static class ToHoa implements OwlModule.OutputWriter {
    private final boolean annotations;
    private final boolean stateAcceptance;

    public ToHoa(boolean annotations, boolean stateAcceptance) {
      this.annotations = annotations;
      this.stateAcceptance = stateAcceptance;
    }

    @Override
    public void write(Writer writer, Object object) {
      var printer = new HOAConsumerPrint(writer);
      var wrappedPrinter = stateAcceptance
        ? new HOAIntermediateStoreAndManipulate(printer, new ToStateAcceptanceFixed())
        : printer;

      EnumSet<HoaOption> options;

      if (annotations) {
        options = EnumSet.of(HoaOption.ANNOTATIONS);
      } else {
        options = EnumSet.noneOf(HoaOption.class);
      }

      HoaWriter.write((Automaton<?, ?>) object, wrappedPrinter, options);
    }
  }

  private OutputWriters() {}
}