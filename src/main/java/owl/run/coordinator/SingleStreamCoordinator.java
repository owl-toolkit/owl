package owl.run.coordinator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.run.ImmutableCoordinatorSettings;
import owl.run.ModuleSettings.CoordinatorSettings;
import owl.run.PipelineExecutionException;
import owl.run.PipelineRunner;
import owl.run.PipelineSpecification;
import owl.util.UncloseableWriter;

public class SingleStreamCoordinator implements Coordinator {
  public static final CoordinatorSettings settings = ImmutableCoordinatorSettings.builder()
    .key("stream")
    .options(options())
    .coordinatorSettingsParser((settings, env) -> parseSettings(settings))
    .build();

  private final int pipelineWorkerCount;
  // Use callables to delay exception handling.
  private final Callable<Reader> readerSupplier;
  private final PipelineSpecification specification;
  private final Callable<Writer> writerSupplier;

  private SingleStreamCoordinator(PipelineSpecification specification,
    Callable<Reader> readerSupplier, Callable<Writer> writerSupplier, int pipelineWorkerCount) {
    this.readerSupplier = readerSupplier;
    this.writerSupplier = writerSupplier;
    this.specification = specification;
    this.pipelineWorkerCount = pipelineWorkerCount;
  }

  private static Callable<Reader> createReader(List<String> inputs) {
    StringJoiner joiner = new StringJoiner(System.lineSeparator());
    inputs.forEach(joiner::add);
    return () -> new StringReader(joiner.toString());
  }

  private static Callable<Reader> createReaderFromSource(String source) {
    return "-".equals(source)
           ? () -> new BufferedReader(new InputStreamReader(System.in))
           : () -> Files.newBufferedReader(Paths.get(source));
  }

  private static Options options() {
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
      .addOption(workerCount);
  }

  @SuppressWarnings("resource")
  private static Coordinator.Factory parseSettings(CommandLine settings) throws ParseException {
    Callable<Reader> reader;
    if (settings.hasOption("filein")) {
      String[] sources = settings.getOptionValues("filein");
      if (sources.length != 1) {
        throw new ParseException("Multiple sources specified");
      }
      reader = createReaderFromSource(sources[0]);
    } else if (System.getenv("OWL_INPUT") != null) {
      reader = createReaderFromSource(System.getenv("OWL_INPUT"));
    } else if (settings.hasOption("input")) {
      reader = createReader(Arrays.asList(settings.getOptionValues("input")));
    } else if (!settings.getArgList().isEmpty()) {
      reader = createReader(settings.getArgList());
      settings.getArgList().clear();
    } else {
      reader = createReaderFromSource("-");
    }

    String destination = settings.getOptionValue("fileout");
    Callable<Writer> writer = destination == null || "-".equals(destination)
             ? UncloseableWriter::sysout
             : () -> Files.newBufferedWriter(Paths.get(destination), StandardOpenOption.APPEND,
                      StandardOpenOption.CREATE);

    int pipelineWorkerCount;
    if (settings.hasOption("worker")) {
      Integer count = Integer.valueOf(settings.getOptionValue("worker"));
      pipelineWorkerCount = count;
      if (count < 0) {
        throw new ParseException("Negative worker count");
      }
    } else {
      pipelineWorkerCount = 2;
    }

    return execution -> new SingleStreamCoordinator(execution, reader, writer,
      pipelineWorkerCount);
  }

  @Override
  public void run() {
    try (Reader reader = readerSupplier.call();
         Writer writer = writerSupplier.call()) {
      new PipelineRunner(specification, reader, writer, pipelineWorkerCount).run();
    } catch (Exception e) { // NOPMD
      throw PipelineExecutionException.wrap(e);
    }
  }
}
