package owl.run;

import com.google.common.base.Strings;
import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
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
import owl.util.GuardedStream;

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
    Callable<ReadableByteChannel> reader;
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
      reader = createSystemReader();
    }

    int workers = settings.hasOption("worker")
      ? Integer.parseInt(settings.getOptionValue("worker"))
      : 0;
    if (workers < 0) {
      throw RunUtil.failWithMessage("Negative worker count");
    }

    String destination = settings.getOptionValue("fileout");
    @SuppressWarnings("resource")
    Callable<WritableByteChannel> writer = destination == null || "-".equals(destination)
      ? () -> Channels.newChannel(GuardedStream.sysout)
      : () -> Files.newByteChannel(Paths.get(destination),
        StandardOpenOption.APPEND, StandardOpenOption.CREATE);

    boolean annotations = RunUtil.checkDefaultAnnotationOption(settings);
    boolean parallel = RunUtil.checkDefaultParallelOption(settings);
    DefaultEnvironment env = DefaultEnvironment.of(annotations, parallel);
    return () -> {
      PipelineRunner.run(pipeline, env, reader.call(), writer.call(), workers);
      return null;
    };
  }

  @SuppressWarnings("resource")
  private static Callable<ReadableByteChannel> createReader(List<String> inputs) {
    // Build the byte array now, since the inputs list may get cleared after this call.
    StringJoiner joiner = new StringJoiner(System.lineSeparator());
    inputs.forEach(joiner::add);
    byte[] bytes = joiner.toString().getBytes(StandardCharsets.UTF_8);
    return () -> Channels.newChannel(new ByteArrayInputStream(bytes));
  }

  @SuppressWarnings("resource")
  private static Callable<ReadableByteChannel> createReaderFromPath(String source) {
    if (source.trim().equals("-")) {
      return createSystemReader();
    }
    return () -> Files.newByteChannel(Paths.get(source), StandardOpenOption.READ);
  }

  @SuppressWarnings("resource")
  private static Callable<ReadableByteChannel> createSystemReader() {
    return () -> Channels.newChannel(System.in);
  }
}
