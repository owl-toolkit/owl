/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.command;

import static owl.thirdparty.picocli.CommandLine.ArgGroup;
import static owl.thirdparty.picocli.CommandLine.Option;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.hoa.HoaReader;
import owl.automaton.hoa.HoaWriter;
import owl.bdd.FactorySupplier;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.ltl.visitors.PrintVisitor;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerException;
import owl.thirdparty.jhoafparser.consumer.HOAIntermediateStoreAndManipulate;
import owl.thirdparty.jhoafparser.owl.extensions.HOAConsumerPrintFixed;
import owl.thirdparty.jhoafparser.owl.extensions.ToStateAcceptanceFixed;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

@SuppressWarnings("PMD.ImmutableField")
final class Mixins {

  private Mixins() {}

  static final class AutomatonReader {

    @Option(
      names = { "-i", "--input-file" },
      description = "Input file (default: read from stdin). If '-' is specified, then the tool "
        + "reads from stdin. This option is repeatable."
    )
    private String[] automatonFile = { "-" };

    <A extends EmersonLeiAcceptance> Stream<Automaton<Integer, ? extends A>>
      source(Class<A> acceptanceClass) {

      return Stream.of(automatonFile).flatMap(file -> {
        try (var reader = "-".equals(file)
          ? new BufferedReader(new InputStreamReader(System.in))
          : Files.newBufferedReader(Path.of(file))) {

          List<Automaton<Integer, ? extends A>> automata = new ArrayList<>();

          // Warning: the 'readStream'-method reads until the reader is exhausted and thus this
          // method blocks in while reading from stdin.
          HoaReader.readStream(reader,
            FactorySupplier.defaultSupplier()::getBddSetFactory,
            null,
            automaton -> {
              Preconditions.checkArgument(
                OmegaAcceptanceCast.isInstanceOf(automaton.acceptance().getClass(),
                  acceptanceClass),
                String.format("Expected %s, but got %s.", acceptanceClass, automaton.acceptance()));
              automata.add(OmegaAcceptanceCast.cast(automaton, acceptanceClass));
            });

          return automata.stream();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        } catch (ParseException e) {
          throw new UncheckedExecutionException(e);
        }
      });
    }
  }

  static final class AutomatonWriter {

    @Option(
      names = { "-o", "--output-file" },
      description = "Output file (default: write to stdout). If '-' is specified, then the tool "
        + "writes to stdout."
    )
    private String automatonFile = null;

    @Option(
      names = {"--complete"},
      description = "Output an automaton with a complete transition relation."
    )
    boolean complete = false;

    @Option(
      names = {"--dry-run"},
      description = "Do not output resulting automaton."
    )
    private boolean dryRun = false;

    @Option(
      names = {"--state-acceptance"},
      description = "Output an automaton with a state-based acceptance condition instead of one "
        + "with a transition-based acceptance condition. For this the acceptance marks of edges "
        + "are pushed onto the successor states. However, this simple procedure might yield "
        + "suboptimal results."
    )
    private boolean stateAcceptance = false;

    @Option(
      names = {"--state-labels"},
      description = "Annotate each state of the automaton with the 'toString()' method."
    )
    private boolean stateLabels = false;

    class Sink implements AutoCloseable {

      private final BufferedWriter writer;
      private final String subcommand;
      private final List<String> subcommandArgs;

      private Sink(String subcommand, List<String> subcommandArgs) throws IOException {
        // Normalise for '-' representing output to stdout.
        if ("-".equals(automatonFile)) {
          automatonFile = null;
        }

        if (automatonFile == null) {
          writer = new BufferedWriter(new OutputStreamWriter(System.out));
        } else {
          writer = Files.newBufferedWriter(Path.of(automatonFile));
        }

        this.subcommand = subcommand;
        this.subcommandArgs = List.copyOf(subcommandArgs);
      }

      @SuppressWarnings("PMD.AvoidReassigningParameters")
      void accept(Automaton<?, ?> automaton, String automatonName)
        throws HOAConsumerException, IOException {

        if (dryRun) {
          return;
        }

        if (complete && !automaton.is(Automaton.Property.COMPLETE)) {
          automaton = Views.complete(automaton);
        }

        var printer = new HOAConsumerPrintFixed(writer);

        // Replace this by a fixed version to preserve owl header extension in case of state
        // acceptance.
        var wrappedPrinter = stateAcceptance
          ? new HOAIntermediateStoreAndManipulate(printer, new ToStateAcceptanceFixed())
          : printer;

        HoaWriter.write(
          automaton,
          wrappedPrinter,
          stateLabels,
          subcommand,
          subcommandArgs,
          automatonName);

        writer.flush();
      }

      @Override
      public void close() throws IOException {
        writer.close();
      }
    }

    Sink sink(String subcommand, List<String> subcommandArgs) throws IOException {
      return new Sink(subcommand, subcommandArgs);
    }
  }

  static final class FormulaReader {

    @ArgGroup
    private Source source = null;

    private static final class Source {

      @Option(
        names = {"-f", "--formula"},
        description = "Use the argument of the option as the input formula. This option is "
          + "repeatable, but cannot be combined with '-i'."
      )
      String[] formula = null;

      @Option(
        names = {"-i", "--input-file"},
        description = "Input file (default: read from stdin). The file is read line-by-line and "
          + "it is assumed that each line contains a formula. Empty lines are skipped. If '-' is "
          + "specified, then the tool reads from stdin. This option is repeatable, but cannot be "
          + "combined with '-f'."
      )
      String[] formulaFile = null;

    }

    Stream<String> stringSource() throws IOException {
      // Default to stdin.
      if (source == null) {
        source = new Source();
        source.formulaFile = new String[]{ "-" };
      }

      Stream<String> stringStream;

      if (source.formulaFile == null) {
        assert source.formula != null;
        stringStream = Stream.of(source.formula);
      } else {
        List<Stream<String>> readerStreams = new ArrayList<>(source.formulaFile.length);

        for (String file : source.formulaFile) {
          BufferedReader reader = "-".equals(file)
            ? new BufferedReader(new InputStreamReader(System.in))
            : Files.newBufferedReader(Path.of(file));

          readerStreams.add(reader.lines().onClose(() -> {
            try {
              reader.close();
            } catch (IOException ex) {
              throw new UncheckedIOException(ex);
            }
          }));
        }

        // This workaround helps against getting stuck while reading from stdin.
        stringStream = readerStreams.size() == 1
          ? readerStreams.get(0)
          : readerStreams.stream().flatMap(Function.identity());
      }

      return stringStream.filter(Predicate.not(String::isBlank));
    }

    Stream<LabelledFormula> source() throws IOException {
      return stringSource().map((String line) -> {
        try {
          return LtlParser.parse(line);
        } catch (RecognitionException | ParseCancellationException ex) {
          throw new IllegalArgumentException(line, ex);
        }
      });
    }
  }

  static final class FormulaWriter {

    @Option(
      names = { "-o", "--output-file" },
      description = "Output file (default: write to stdout). If '-' is specified, then the tool "
        + "writes to stdout."
    )
    private String formulaFile = null;

    final class Sink implements AutoCloseable {

      private final BufferedWriter writer;

      private Sink() throws IOException {
        // Normalise for '-' representing output to stdout.
        if ("-".equals(formulaFile)) {
          formulaFile = null;
        }

        if (formulaFile == null) {
          writer = new BufferedWriter(new OutputStreamWriter(System.out));
        } else {
          writer = Files.newBufferedWriter(Path.of(formulaFile));
        }
      }

      void accept(LabelledFormula labelledFormula) throws IOException {
        writer.write(PrintVisitor.toString(labelledFormula, true));
        writer.write(System.lineSeparator());
        writer.flush();
      }

      @Override
      public void close() throws IOException {
        writer.close();
      }
    }

    FormulaWriter.Sink sink() throws IOException {
      return new FormulaWriter.Sink();
    }
  }

  static final class AcceptanceSimplifier {

    @Option(
      names = {"--skip-acceptance-simplifier"},
      description = "Bypass the automatic simplification of automata acceptance conditions."
    )
    boolean skipAcceptanceSimplifier = false;

  }

  static final class FormulaSimplifier {

    @Option(
      names = {"--skip-formula-simplifier"},
      description = "Bypass the automatic simplification of formulas."
    )
    boolean skipSimplifier = false;

  }

  static final class Verifier {

    @Option(
      names = "--verify",
      description = "Verify the computed result. If the verification fails the tool aborts with an "
        + "error. This flag is intended only for testing.",
      hidden = true
    )
    boolean verify = false;

  }

  @SuppressWarnings("PMD.SystemPrintln")
  static final class Diagnostics {

    private final Stopwatch stopwatch = Stopwatch.createUnstarted();

    @Option(
      names = "--diagnostics",
      description = "Print diagnostic information to stderr."
    )
    private boolean printDiagnostics = false;

    @Option(
      names = "--diagnostics-time-unit",
      description = "Select the time unit (${COMPLETION-CANDIDATES}) for reporting runtimes. The "
        + "default value is ${DEFAULT-VALUE}. Be aware that for NANOSECONDS the reporting might "
        + "not be accurate.",
      defaultValue = "MILLISECONDS"
    )
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    void start(String subcommand, Automaton<?, ?> automaton) {
      if (printDiagnostics) {
        System.err.printf("""
            %s:
              Input Automaton (after preprocessing):
                States: %d
                Acceptance Name: %s
                Acceptance Sets: %d
            """,
          subcommand,
          automaton.states().size(),
          automaton.acceptance().name(),
          automaton.acceptance().acceptanceSets());
        stopwatch.start();
      }
    }

    void finish(Automaton<?, ?> automaton) {
      if (printDiagnostics) {
        stopwatch.stop();
        System.err.printf("""
              Output Automaton (before postprocessing):
                States: %d
                Acceptance Name: %s
                Acceptance Sets: %d
              Runtime (without pre- and postprocessing): %d %s
            """,
          automaton.states().size(),
          automaton.acceptance().name(),
          automaton.acceptance().acceptanceSets(),
          stopwatch.elapsed(timeUnit),
          timeUnit);
      }
    }
  }
}
