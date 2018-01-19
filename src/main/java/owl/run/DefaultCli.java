package owl.run;

import com.google.common.base.Strings;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import owl.run.modules.OwlModuleRegistry;
import owl.run.parser.OwlParser;
import owl.util.UncloseableWriter;

public final class DefaultCli {
  private DefaultCli() {}

  public static void main(String... args) {
    OwlParser parseResult = OwlParser.parse(args, new DefaultParser(), getOptions(),
      OwlModuleRegistry.DEFAULT_REGISTRY);
    if (parseResult == null) {
      System.exit(1);
      return;
    }

    RunUtil.execute(build(parseResult.globalSettings, parseResult.pipeline));
  }

  public static Options getOptions() {
    Option fileInput = new Option("I", "filein", true,
      "Read input from the specified file (- for stdin)");
    Option fixedInput = new Option("i", "input", true, "Use given strings as input");
    OptionGroup inputGroup = new OptionGroup().addOption(fileInput).addOption(fixedInput);
    Option workerCount = new Option("w", "worker", true,
      "Number of workers used for processing the input");
    Option fileOutput = new Option("O", "fileout", true,
      "Write output to the specified file (- for stdout)");

    return new Options()
      .addOptionGroup(inputGroup)
      .addOption(fileOutput)
      .addOption(workerCount)
      .addOption(RunUtil.getDefaultParallelOption())
      .addOption(RunUtil.getDefaultAnnotationOption());
  }

  public static Callable<Void> build(CommandLine settings, Pipeline pipeline) {
    Callable<Reader> reader;
    if (settings.hasOption("filein")) {
      String[] sources = settings.getOptionValues("filein");
      if (sources.length != 1) {
        throw RunUtil.failWithMessage("Multiple sources specified");
      }
      reader = createReaderFromPath(sources[0]);
    } else if (!Strings.isNullOrEmpty(System.getenv("OWL_INPUT"))) {
      reader = createReaderFromPath(System.getenv("OWL_INPUT"));
    } else if (settings.hasOption("input")) {
      reader = createReader(List.of(settings.getOptionValues("input")));
    } else if (!settings.getArgList().isEmpty()) {
      reader = createReader(settings.getArgList());
      settings.getArgList().clear();
    } else {
      //noinspection resource,IOResourceOpenedButNotSafelyClosed
      reader = () -> new BufferedReader(new InputStreamReader(System.in));
    }

    String destination = settings.getOptionValue("fileout");
    @SuppressWarnings("resource")
    Callable<Writer> writer = destination == null || "-".equals(destination)
      ? () -> UncloseableWriter.sysout
      : () -> Files.newBufferedWriter(Paths.get(destination), StandardOpenOption.APPEND,
        StandardOpenOption.CREATE);

    int workers;
    if (settings.hasOption("worker")) {
      Integer count = Integer.valueOf(settings.getOptionValue("worker"));
      workers = count;
      if (count < 0) {
        throw RunUtil.failWithMessage("Negative worker count");
      }
    } else {
      workers = 2;
    }

    boolean annotations = RunUtil.checkDefaultAnnotationOption(settings);
    boolean parallel = RunUtil.checkDefaultParallelOption(settings);
    DefaultEnvironment env = DefaultEnvironment.of(annotations, false, parallel);
    return () -> {
      PipelineRunner.run(pipeline, env, reader, writer, workers);
      return null;
    };
  }

  @SuppressWarnings("resource")
  private static Callable<Reader> createReader(List<String> inputs) {
    StringJoiner joiner = new StringJoiner(System.lineSeparator());
    inputs.forEach(joiner::add);
    return () -> new StringReader(joiner.toString());
  }

  @SuppressWarnings({"resource", "IOResourceOpenedButNotSafelyClosed"})
  private static Callable<Reader> createReaderFromPath(String source) {
    return () -> Files.newBufferedReader(Paths.get(source));
  }
}
