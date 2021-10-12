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

import static picocli.CommandLine.ArgGroup;
import static picocli.CommandLine.Option;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAIntermediateStoreAndManipulate;
import jhoafparser.extensions.HOAConsumerPrintFixed;
import jhoafparser.extensions.ToStateAcceptanceFixed;
import jhoafparser.parser.generated.ParseException;
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

@SuppressWarnings("PMD.ImmutableField")
final class Mixins {

  private Mixins() {}

  static final class AutomatonReader {

    @Option(
      names = { "-i", "--input-file" },
      description = "Input file (default: read from stdin)."
    )
    private Path automatonFile = null;

    <A extends EmersonLeiAcceptance> Stream<Automaton<Integer, ? extends A>>
      source(Class<A> acceptanceClass) throws ParseException, IOException {

      try (var reader = automatonFile == null
        ? new BufferedReader(new InputStreamReader(System.in))
        : Files.newBufferedReader(automatonFile)) {

        List<Automaton<Integer, ? extends A>> automata = new ArrayList<>();

        // Warning: the 'readStream'-method reads until the reader is exhausted and thus this
        // methods blocks in while reading from stdin.
        HoaReader.readStream(reader,
          FactorySupplier.defaultSupplier()::getBddSetFactory,
          null,
          automaton -> {
            Preconditions.checkArgument(
              OmegaAcceptanceCast.isInstanceOf(automaton.acceptance().getClass(), acceptanceClass),
              String.format("Expected %s, but got %s.", acceptanceClass, automaton.acceptance()));
            automata.add(OmegaAcceptanceCast.cast(automaton, acceptanceClass));
          });

        return automata.stream();
      }
    }
  }

  static final class AutomatonWriter {

    @Option(
      names = { "-o", "--output-file" },
      description = "Output file (default: write to stdout)."
    )
    private Path automatonFile = null;

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
        if (automatonFile == null) {
          writer = new BufferedWriter(new OutputStreamWriter(System.out));
        } else {
          writer = Files.newBufferedWriter(automatonFile);
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

    static final class Source {

      @Option(
        names = { "-f", "--formula" },
        description = "Use the argument of the option as the input formula."
      )
      String formula = null;

      @Option(
        names = { "-i", "--input-file" },
        description = "Input file (default: read from stdin). The file is read line-by-line and "
          + "it is assumed that each line contains a formula. Empty lines are skipped."
      )
      Path formulaFile = null;

    }

    Stream<LabelledFormula> source() throws IOException {
      BufferedReader reader;

      if (source == null) {
        reader = new BufferedReader(new InputStreamReader(System.in));
      } else if (source.formula != null) {
        reader = new BufferedReader(new StringReader(source.formula));
      } else {
        reader = Files.newBufferedReader(source.formulaFile);
      }

      return reader.lines().filter(Predicate.not(String::isBlank)).map((String line) -> {
        try {
          return LtlParser.parse(line);
        } catch (RecognitionException | ParseCancellationException ex) {
          throw new IllegalArgumentException(line, ex);
        }
      }).onClose(() -> {
        try {
          reader.close();
        } catch (IOException ex) {
          throw new UncheckedIOException(ex);
        }
      });
    }
  }

  static final class FormulaWriter {

    @Option(
      names = { "-o", "--output-file" },
      description = "Output file (default: write to stdout)."
    )
    private Path formulaFile = null;

    final class Sink implements AutoCloseable {

      private final BufferedWriter writer;

      private Sink() throws IOException {
        if (formulaFile == null) {
          writer = new BufferedWriter(new OutputStreamWriter(System.out));
        } else {
          writer = Files.newBufferedWriter(formulaFile);
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
        System.err.printf("%s:\n"
            + "  Input Automaton (after preprocessing):\n"
            + "    States: %d\n"
            + "    Acceptance Name: %s\n"
            + "    Acceptance Sets: %d\n",
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
        System.err.printf("  Output Automaton (before postprocessing):\n"
            + "    States: %d\n"
            + "    Acceptance Name: %s\n"
            + "    Acceptance Sets: %d\n"
            + "  Runtime (without pre- and postprocessing): %d %s\n",
          automaton.states().size(),
          automaton.acceptance().name(),
          automaton.acceptance().acceptanceSets(),
          stopwatch.elapsed(timeUnit),
          timeUnit);
      }
    }
  }
}
