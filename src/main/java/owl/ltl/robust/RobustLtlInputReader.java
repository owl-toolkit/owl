package owl.ltl.robust;

import java.io.BufferedReader;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.modules.InputReader;
import owl.run.modules.OwlModuleParser;

public final class RobustLtlInputReader implements OwlModuleParser.ReaderParser {
  public static final RobustLtlInputReader INSTANCE = new RobustLtlInputReader();
  private static final Logger logger = Logger.getLogger(RobustLtlInputReader.class.getName());

  private RobustLtlInputReader() {}

  @Override
  public String getKey() {
    return "rltl";
  }

  @Override
  public Options getOptions() {
    Option truthOption = new Option("t", "truth", true, "Specify the truth values which shall be "
      + "satisfied by the rLTL formula. Possible values: 0 - never, 1 - eventually, 2 - infinitely "
      + "often, 3 - eventually always, 4 - always.");
    truthOption.setRequired(true);
    return new Options().addOption(truthOption);
  }

  @Override
  public String getDescription() {
    return "Parses a given rLTL formula and converts it into an LTL specification based on the "
      + "given truth values";
  }

  @Override
  public InputReader parse(CommandLine commandLine) throws ParseException {
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
    return (reader, env, callback) -> new BufferedReader(reader).lines().forEach(line -> {
      if (env.isShutdown() || line.isEmpty()) {
        return;
      }
      logger.log(Level.INFO, "Parsing {0} with robustness {1}", new Object[] {line, robustness});
      callback.accept(RobustLtlParser.parse(line).toLtl(robustness));
    });
  }
}
