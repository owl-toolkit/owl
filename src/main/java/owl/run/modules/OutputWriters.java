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

package owl.run.modules;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.consumer.HOAIntermediateStoreAndManipulate;
import jhoafparser.storage.StoredAutomatonManipulator;
import jhoafparser.transformations.ToStateAcceptance;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonUtil;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.output.HoaPrinter;
import owl.automaton.output.HoaPrinter.HoaOption;
import owl.run.Environment;
import owl.run.modules.OwlModuleParser.WriterParser;

public final class OutputWriters {
  public static final WriterParser AUTOMATON_STATS_CLI = ImmutableWriterParser.builder()
    .key("aut-stat")
    .description("Writes several stats of a given automaton to the given format string")
    .optionsBuilder(() -> {
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
    })
    .parser(settings -> automatonStats(settings.getOptionValue("format")))
    .build();

  public static final WriterParser HOA_CLI = ImmutableWriterParser.builder()
    .key("hoa")
    .description("Writes the HOA format representation of an automaton or an game")
    .parser(settings -> {

      List<StoredAutomatonManipulator> manipulators;
      if (settings.hasOption("state-acceptance")) {
        manipulators = List.of(new ToStateAcceptance());
      } else {
        manipulators = List.of();
      }

      EnumSet<ToHoa.Setting> hoaSettings = EnumSet.noneOf(ToHoa.Setting.class);

      if (settings.hasOption("simple-trans")) {
        hoaSettings.add(ToHoa.Setting.SIMPLE_TRANSITION_LABELS);
      }

      return new ToHoa(hoaSettings, manipulators);
    }).build();

  public static final OutputWriter NULL = (writer, environment) -> object -> writer.flush();
  public static final WriterParser NULL_CLI = ImmutableWriterParser.builder()
    .key("null")
    .description("Discards the output - useful for performance testing")
    .parser(settings -> NULL)
    .build();

  public static final OutputWriter TO_STRING = (writer, environment) -> object -> {
    writer.write(object.toString());
    writer.write(System.lineSeparator());
  };
  public static final WriterParser TO_STRING_CLI = ImmutableWriterParser.builder()
    .key("string")
    .description("Prints the toString() representation of all passed objects")
    .parser(settings -> TO_STRING)
    .build();

  public static final OutputWriter HOA = ToHoa.DEFAULT;

  private OutputWriters() {
  }

  public static OutputWriter automatonStats(String format) {
    return (writer, environment) -> new AutomatonStats(format, writer)::write;
  }

  public static class AutomatonStats {
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
      automaton -> HoaPrinter.toString(automaton).replace('\n', ' '),

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
      automaton -> String.valueOf(SccDecomposition.computeSccs(automaton).size()),

      Pattern.compile("%n", Pattern.LITERAL),
      automaton -> "\n");

    private final String formatString;
    private final Writer writer;

    public AutomatonStats(String formatString, Writer writer) {
      this.formatString = formatString;
      this.writer = writer;
    }

    void write(Object object) throws IOException {
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
   * Converts any {@link HoaPrinter HOA printable} object to its corresponding <a
   * href="http://adl.github.io/hoaf/">HOA</a> representation.
   */
  public static class ToHoa implements OutputWriter {
    public static final ToHoa DEFAULT = new ToHoa(EnumSet.noneOf(Setting.class), List.of());

    private final Set<Setting> hoaSettings;
    private final List<StoredAutomatonManipulator> operations;

    public ToHoa(EnumSet<Setting> hoaSettings, List<StoredAutomatonManipulator> operations) {
      this.hoaSettings = Set.copyOf(hoaSettings);
      this.operations = List.copyOf(operations);
    }

    @Override
    public Binding bind(Writer writer, Environment environment) {
      HOAConsumer consumer = operations.isEmpty()
        ? new HOAConsumerPrint(writer)
        : new HOAIntermediateStoreAndManipulate(
          new HOAConsumerPrint(writer), operations.toArray(StoredAutomatonManipulator[]::new));

      EnumSet<HoaOption> options = EnumSet.noneOf(HoaOption.class);

      if (environment.annotations()) {
        options.add(HoaOption.ANNOTATIONS);
      }

      if (hoaSettings.contains(Setting.SIMPLE_TRANSITION_LABELS)) {
        options.add(HoaOption.SIMPLE_TRANSITION_LABELS);
      }

      return input -> HoaPrinter.feedTo(AutomatonUtil.cast(input), consumer, options);
    }

    public enum Setting {
      SIMPLE_TRANSITION_LABELS
    }
  }
}
