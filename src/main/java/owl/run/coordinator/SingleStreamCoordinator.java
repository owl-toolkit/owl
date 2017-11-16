package owl.run.coordinator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.cli.ImmutableCoordinatorSettings;
import owl.cli.ModuleSettings.CoordinatorSettings;
import owl.run.PipelineExecutionException;
import owl.run.PipelineRunner;
import owl.run.PipelineSpecification;
import owl.util.CloseGuardOutputStream;

public class SingleStreamCoordinator implements Coordinator {
  public static final CoordinatorSettings settings = ImmutableCoordinatorSettings.builder()
    .key("stream")
    .options(options())
    .coordinatorSettingsParser(SingleStreamCoordinator::parseSettings)
    .build();

  private final PipelineSpecification execution;
  // Use callables here to delegate the responsibility of closing the streams to the executing code
  private final Callable<InputStream> inputStreamSupplier;
  private final Callable<OutputStream> outputStreamSupplier;
  private final int pipelineWorkerCount;

  public SingleStreamCoordinator(PipelineSpecification execution,
    Callable<InputStream> inputStreamSupplier, Callable<OutputStream> outputStreamSupplier,
    int pipelineWorkerCount) {
    this.inputStreamSupplier = inputStreamSupplier;
    this.outputStreamSupplier = outputStreamSupplier;
    this.execution = execution;
    this.pipelineWorkerCount = pipelineWorkerCount;
  }

  @SuppressWarnings("resource")
  private static Callable<InputStream> getInputFromSource(String source) {
    return "-".equals(source)
      ? () -> System.in
      : () -> Files.newInputStream(Paths.get(source), StandardOpenOption.READ);
  }

  private static InputStream getInputStream(List<String> inputs) {
    StringBuilder inputBuilder = new StringBuilder(200);
    Iterator<String> iterator = inputs.iterator();
    inputBuilder.append(iterator.next());
    while (iterator.hasNext()) {
      inputBuilder.append(System.lineSeparator()).append(iterator.next());
    }
    String string = inputBuilder.toString();
    //noinspection resource
    return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
  }

  private static Options options() {
    Option fileInput = new Option("I", "filein", true,
      "Read input from the specified file (- for stdin)");
    Option fixedInput = new Option("i", "input", true, "Use given strings as input");
    OptionGroup inputGroup = new OptionGroup().addOption(fileInput).addOption(fixedInput);
    Option workerCount = new Option("w", "worker", true,
      "Number of workers used for processing the input (0 for direct execution)");

    Option fileOutput = new Option("O", "fileout", true,
      "Write output to the specified file (- for stdout)");

    return new Options()
      .addOptionGroup(inputGroup)
      .addOption(fileOutput)
      .addOption(workerCount);
  }

  @SuppressWarnings("resource")
  private static Coordinator.Factory parseSettings(CommandLine settings) throws ParseException {
    Callable<InputStream> input;

    if (settings.hasOption("filein")) {
      String[] sources = settings.getOptionValues("filein");
      if (sources.length != 1) {
        throw new ParseException("Multiple sources specified");
      }
      input = getInputFromSource(sources[0]);
    } else if (System.getenv("OWL_INPUT") != null) {
      input = getInputFromSource(System.getenv("OWL_INPUT"));
    } else if (settings.hasOption("input")) {
      input = () -> getInputStream(Arrays.asList(settings.getOptionValues("input")));
    } else if (!settings.getArgList().isEmpty()) {
      input = () -> getInputStream(settings.getArgList());
      settings.getArgList().clear();
    } else {
      input = () -> System.in;
    }

    Callable<OutputStream> output;
    if (settings.hasOption("fileout")) {
      String destination = settings.getOptionValue("fileout");
      //noinspection resource
      output = "-".equals(destination)
        ? CloseGuardOutputStream::sysout
        : () -> Files.newOutputStream(Paths.get(destination),
          StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    } else {
      output = CloseGuardOutputStream::sysout;
    }

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

    return execution -> new SingleStreamCoordinator(execution, input, output, pipelineWorkerCount);
  }

  @Override
  public void run() {
    try (InputStream inputStream = inputStreamSupplier.call();
         OutputStream outputStream = outputStreamSupplier.call()) {
      PipelineRunner.run(inputStream, outputStream, execution, pipelineWorkerCount);
    } catch (Exception e) { // NOPMD
      throw PipelineExecutionException.wrap(e);
    }
  }
}
