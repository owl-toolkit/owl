package owl.run;

import static owl.run.RunUtil.checkDefaultAnnotationOption;
import static owl.run.RunUtil.checkDefaultParallelOption;
import static owl.run.RunUtil.failWithMessage;
import static owl.run.RunUtil.getDefaultAnnotationOption;
import static owl.run.RunUtil.getDefaultParallelOption;

import com.google.common.base.Strings;
import java.util.concurrent.Callable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.run.modules.OwlModuleRegistry;
import owl.run.parser.OwlParser;

public final class ServerCli {
  private ServerCli() {}

  public static Options getOptions() {
    Option portOption = new Option(null, "port", true, "Port to listen on (default 5050)");

    return new Options()
      .addOption(portOption)
      .addOption(getDefaultAnnotationOption())
      .addOption(getDefaultParallelOption());
  }

  public static Callable<Void> build(CommandLine settings, Pipeline pipeline) {
    int port;

    if (Strings.isNullOrEmpty(settings.getOptionValue("port"))) {
      port = 5050;
    } else {
      try {
        port = Integer.parseInt(settings.getOptionValue("port"));
      } catch (NumberFormatException e) {
        throw failWithMessage("Invalid value for port", e);
      }
    }

    boolean annotations = checkDefaultAnnotationOption(settings);
    boolean parallel = checkDefaultParallelOption(settings);
    return new ServerRunner(pipeline, () -> DefaultEnvironment.of(annotations, parallel), port);
  }

  public static void main(String... args) {
    OwlParser parseResult =
      OwlParser.parse(args, new DefaultParser(), getOptions(), OwlModuleRegistry.DEFAULT_REGISTRY);
    if (parseResult == null) {
      System.exit(1);
      return;
    }

    RunUtil.execute(build(parseResult.globalSettings, parseResult.pipeline));
  }
}
