package owl.run;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonUtil;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.output.HoaPrintable;
import owl.automaton.output.HoaPrintable.HoaOption;
import owl.run.ModuleSettings.WriterSettings;
import owl.run.env.Environment;

public final class OutputWriters {

  public static final WriterSettings AUTOMATON_STATS = new WriterSettings() {
    @Override
    public OutputWriter create(CommandLine settings, Environment environment) {
      return new AutomatonStats(settings.getOptionValue("format"))::writeChecked;
    }

    @Override
    public String getKey() {
      return "aut-stat";
    }

    @Override
    public Options getOptions() {
      Option format = new Option("f", "format", true,
        "The format string. Uses a reduced set of the spot syntax\n"
          + "%A, %a   Number of acceptance sets\n"
          + "%C, %c   Number of SCCs\n"
          + "%D, %d   1 if the automaton is deterministic, 0 otherwise\n"
          + "%G, %g   acceptance condition (in HOA syntax)\n"
          + "%S, %s   Number of states\n"
          + "%H, %h   The automaton in HOA format on a single line\n"
          + "%M, %m   Name of the automaton\n"
          + "%X, %x   Number of atomic propositions");
      format.setRequired(true);

      return new Options().addOption(format);
    }
  };

  public static final WriterSettings HOA = new WriterSettings() {
    @Override
    public OutputWriter create(CommandLine settings, Environment environment) {
      return (object, writer) -> {
        checkArgument(object instanceof HoaPrintable);
        HOAConsumer consumer = new HOAConsumerPrint(writer);

        if (!ToHoa.parseSettings(settings).manipulations.isEmpty()) {
          StoredAutomatonManipulator[] manipulators =
            ToHoa.parseSettings(settings).manipulations.toArray(
              new StoredAutomatonManipulator[ToHoa.parseSettings(settings).manipulations.size()]);
          consumer = new HOAIntermediateStoreAndManipulate(consumer, manipulators);
        }

        ((HoaPrintable) object).toHoa(consumer, ToHoa.parseSettings(settings)
          .getOptions(environment.annotations()));
      };
    }

    @Override
    public String getDescription() {
      return "Writes the HOA format representation of an automaton or an arena";
    }

    @Override
    public String getKey() {
      return "hoa";
    }

    @Override
    public Options getOptions() {
      return new Options()
        .addOption("s", "state-acceptance", false, "Convert to state-acceptance")
        .addOption(null, "simple-trans", false,
          "Force use of simple transition labels, resulting "
            + "in 2^AP edges per state)");
    }
  };

  public static final WriterSettings NULL = ImmutableWriterSettings.builder()
    .key("null")
    .description("Discards the output - useful for performance testing")
    .constructor((x, y) -> (object, stream) -> stream.flush())
    .build();

  public static final WriterSettings STRING = ImmutableWriterSettings.builder()
    .key("string")
    .description("Prints the toString() representation of all passed objects")
    .constructor((x, y) -> (object, stream) -> stream.write(object + System.lineSeparator()))
    .build();

  private OutputWriters() {
  }

  static final class AutomatonStats {
    private static final Map<Pattern, Function<Automaton<?, ?>, String>> patterns =
      ImmutableMap.<Pattern, Function<Automaton<?, ?>, String>>builder()
        // Acceptance condition
        .put(Pattern.compile("%G", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
          automaton -> automaton.getAcceptance().getBooleanExpression().toString())
        // Acceptance set count
        .put(Pattern.compile("%A", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
          automaton -> String.valueOf(automaton.getAcceptance().getAcceptanceSets()))
        // Is deterministic
        .put(Pattern.compile("%D", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
          automaton -> automaton.is(Property.DETERMINISTIC) ? "1" : "0")
        // Single line HOA
        .put(Pattern.compile("%H", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
          automaton -> AutomatonUtil.toHoa(automaton).replace('\n', ' '))
        // Name
        .put(Pattern.compile("%M", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
          HoaPrintable::getName)
        // State count
        .put(Pattern.compile("%S", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
          automaton -> String.valueOf(automaton.size()))
        // Number of propositions
        .put(Pattern.compile("%X", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
          automaton -> String.valueOf(automaton.getVariables().size()))
        // Number of SCCs
        .put(Pattern.compile("%C", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
          automaton -> String.valueOf(SccDecomposition.computeSccs(automaton).size()))
        .build();

    private final String formatString;

    private AutomatonStats(String formatString) {
      this.formatString = formatString;
    }

    private void writeChecked(Object object, Writer writer) throws IOException {
      checkArgument(object instanceof Automaton);
      Automaton<?, ?> automaton = (Automaton<?, ?>) object;

      String result = formatString;

      for (Map.Entry<Pattern, Function<Automaton<?, ?>, String>> pattern : patterns.entrySet()) {
        Matcher matcher = pattern.getKey().matcher(result);

        if (matcher.find()) {
          result = matcher
            .replaceAll(Matcher.quoteReplacement(pattern.getValue().apply(automaton)));
        }
      }

      writer.write(result);
    }
  }

  /**
   * Converts any {@link HoaPrintable HOA printable} object to its corresponding <a
   * href="http://adl.github.io/hoaf/">HOA</a> representation.
   */
  static class ToHoa {
    private final Set<Setting> hoaSettings;
    private final List<StoredAutomatonManipulator> manipulations;

    ToHoa(EnumSet<Setting> hoaSettings, List<StoredAutomatonManipulator> manipulations) {
      this.hoaSettings = ImmutableSet.copyOf(hoaSettings);
      this.manipulations = ImmutableList.copyOf(manipulations);
    }

    static ToHoa parseSettings(CommandLine settings) {
      List<StoredAutomatonManipulator> manipulators;
      if (settings.hasOption("state-acceptance")) {
        manipulators = List.of(new ToStateAcceptance());
      } else {
        manipulators = List.of();
      }

      EnumSet<Setting> hoaSettings = EnumSet.noneOf(Setting.class);

      if (settings.hasOption("simple-trans")) {
        hoaSettings.add(Setting.SIMPLE_TRANSITION_LABELS);
      }

      return new ToHoa(hoaSettings, manipulators);
    }

    EnumSet<HoaOption> getOptions(boolean annotations) {
      EnumSet<HoaOption> options = EnumSet.noneOf(HoaOption.class);
      if (annotations) {
        options.add(HoaOption.ANNOTATIONS);
      }
      if (hoaSettings.contains(Setting.SIMPLE_TRANSITION_LABELS)) {
        options.add(HoaOption.SIMPLE_TRANSITION_LABELS);
      }
      return options;
    }

    public enum Setting {
      SIMPLE_TRANSITION_LABELS
    }
  }
}
