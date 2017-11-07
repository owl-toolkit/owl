package owl.run.meta;

import com.google.common.collect.ImmutableMap;
import java.io.OutputStream;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.output.HoaPrintable;
import owl.cli.ImmutableMetaSettings;
import owl.run.env.Environment;
import owl.run.output.OutputWriter;
import owl.run.transformer.Transformer;

public final class AutomatonStats implements Transformer.Factory, OutputWriter.Factory {
  public static final ImmutableMetaSettings<AutomatonStats> settings;

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
        automaton -> automaton.isDeterministic() ? "1" : "0")
      // Single line HOA
      .put(Pattern.compile("%H", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
        automaton -> AutomatonUtil.toHoa(automaton).replace('\n', ' '))
      // Name
      .put(Pattern.compile("%M", Pattern.CASE_INSENSITIVE | Pattern.LITERAL), HoaPrintable::getName)
      // State count
      .put(Pattern.compile("%S", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
        automaton -> String.valueOf(automaton.stateCount()))
      // Number of propositions
      .put(Pattern.compile("%X", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
        automaton -> String.valueOf(automaton.getVariables().size()))
      // Number of SCCs
      .put(Pattern.compile("%C", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
        automaton -> String.valueOf(SccDecomposition.computeSccs(automaton).size()))
      .build();

  static {
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
    settings = ImmutableMetaSettings.<AutomatonStats>builder()
      .key("aut-stat")
      .options(new Options().addOption(format))
      .metaSettingsParser(settings -> new AutomatonStats(settings.getOptionValue("format")))
      .build();
  }

  private final String formatString;

  public AutomatonStats(String formatString) {
    this.formatString = formatString;
  }

  private static String printStats(String formatString, Automaton<?, ?> automaton) {
    // TODO Tokenize + build?
    String result = formatString;

    for (Map.Entry<Pattern, Function<Automaton<?, ?>, String>> pattern : patterns.entrySet()) {
      Matcher matcher = pattern.getKey().matcher(result);
      if (matcher.find()) {
        result = matcher.replaceAll(Matcher.quoteReplacement(pattern.getValue().apply(automaton)));
      }
    }

    return result;
  }

  @Override
  public Transformer createTransformer(Environment environment) {
    return MetaUtil.asTransformer(this::print, Automaton.class);
  }

  @Override
  public OutputWriter createWriter(OutputStream stream, Environment environment) {
    return MetaUtil.asOutputWriter(stream, environment, this::print, Automaton.class);
  }

  private String print(Automaton<?, ?> automaton) {
    return printStats(formatString, automaton);
  }
}
