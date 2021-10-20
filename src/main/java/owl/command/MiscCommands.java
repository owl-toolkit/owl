/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import static com.google.common.base.Preconditions.checkArgument;
import static picocli.CommandLine.ArgGroup;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Mixin;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import owl.Bibliography;
import owl.automaton.Automaton;
import owl.automaton.ParityUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.hoa.HoaWriter;
import owl.game.GameViews;
import owl.game.PgSolverFormat;
import owl.ltl.SyntacticFragments;

@SuppressWarnings({"PMD.ImmutableField", "PMD.SystemPrintln"})
final class MiscCommands {

  private MiscCommands() {}

  @Command(
    name = "bibliography",
    description = "Print the bibliography of all implemented algorithms and constructions. "
      + "Single references can be looked up by listing them, e.g. 'owl bibliography SE20'. "
      + "If you want to cite Owl as a whole, it is recommended to use reference [KMS18]."
  )
  static final class BibliographyCommand extends AbstractOwlSubcommand {

    static final String HOW_TO_USE = "To look up a reference, e.g. [SE20], used in this help "
      + "message please use 'owl bibliography'.";

    @Parameters(
      description = "Print the bibliography only for the specified references."
    )
    @Nullable
    private String[] references;

    @Override
    protected int run() throws Exception {
      if (references == null) {
        // Sort entries by their citeKey.
        new TreeMap<>(Bibliography.INDEX).forEach(BibliographyCommand::printEntry);
      } else {
        for (var citeKey : references) {
          printEntry(citeKey, Bibliography.INDEX.get(citeKey));
        }
      }

      return 0;
    }

    private static void printEntry(String citKey, Bibliography.Publication publication) {
      if (publication == null) {
        System.err.printf("[%s]:%nNo entry found.%n%n", citKey);
        return;
      }

      System.out.printf("[%s]:%n%s%n", citKey, publication);
    }
  }

  @Command(
    name = "ltl-inspect",
    hidden = true // Command is hidden, since it is not finished yet.
  )
  static final class LtlInspectionCommand extends AbstractOwlSubcommand {

    @Mixin
    private Mixins.FormulaReader formulaReader = null;

    @Override
    protected int run() throws IOException {

      try (var source = formulaReader.source()) {
        var formulaIterator = source.iterator();

        while (formulaIterator.hasNext()) {
          var formula = formulaIterator.next();

          System.out.printf("Formula: %s,%nSyntactic class: %s%n",
            formula, SyntacticFragments.FormulaClass.classify(formula.formula()));
        }
      }

      return 0;
    }
  }

  @Command(
    name = "aut-inspect",
    hidden = true // Command is hidden, since it is not finished yet.
  )
  static final class AutInspectionCommand extends AbstractOwlSubcommand {

    @Nullable
    @Option(
      names = { "-f", "--format"},
      required = true,
      description = {
          "The format string. Uses a reduced set of the spot syntax\n"
            + "%A, %a   Number of acceptance sets\n"
            + "%C, %c   Number of SCCs\n"
            + "%D, %d   1 if the automaton is deterministic, 0 otherwise\n"
            + "%G, %g   acceptance condition (in HOA syntax)\n"
            + "%S, %s   Number of states\n"
            + "%H, %h   The automaton in HOA format on a single line\n"
            + "%M, %m   Name of the automaton\n"
            + "%n       Newline\n"
            + "%X, %x   Number of atomic propositions" }
    )
    private String format;

    private static final Map<Pattern, Function<Automaton<?, ?>, String>> PATTERNS = Map.of(
      // Acceptance condition
      Pattern.compile("%G", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> automaton.acceptance().booleanExpression().toString(),

      // Acceptance set count
      Pattern.compile("%A", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(automaton.acceptance().acceptanceSets()),

      // Is deterministic
      Pattern.compile("%D", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> automaton.is(Automaton.Property.DETERMINISTIC) ? "1" : "0",

      // Single line HOA
      Pattern.compile("%H", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> HoaWriter.toString(automaton).replace('\n', ' '),

      // State count
      Pattern.compile("%S", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(automaton.states().size()),

      // Number of propositions
      Pattern.compile("%X", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(automaton.atomicPropositions().size()),

      // Number of SCCs
      Pattern.compile("%C", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(SccDecomposition.of(automaton).sccs().size()),

      Pattern.compile("%n", Pattern.LITERAL),
      automaton -> "\n");

    @Mixin
    private Mixins.AutomatonReader automatonReader = null;

    @Override
    protected int run() {

      try (var source = automatonReader.source(EmersonLeiAcceptance.class)) {
        var automatonIterator = source.iterator();

        while (automatonIterator.hasNext()) {
          String result = Objects.requireNonNull(format);

          for (var pattern : PATTERNS.entrySet()) {
            Matcher matcher = pattern.getKey().matcher(result);

            if (matcher.find()) {
              String replacement = Matcher.quoteReplacement(pattern.getValue().apply(
                automatonIterator.next()));
              result = matcher.replaceAll(replacement);
            }
          }

          System.out.printf("%s%n", result);
        }
      }

      return 0;
    }
  }

  @Command(
    name = "delag",
    description = DelagMigrationCommand.DELAG_DESCRIPTION
  )
  static final class DelagMigrationCommand extends AbstractOwlSubcommand {

    private static final String DELAG_DESCRIPTION = "The functionality of the 'delag' subcommand "
      + "has been moved to the 'ltl2dela' subcommand. You can use 'owl ltl2dela -t=MS17' to access "
      + "the original 'delag' construction.";

    @Override
    protected int run() throws Exception {
      return 0;
    }
  }

  @Command(
    name = "dpa2pg",
    description =
      "Converts a deterministic parity automaton into a parity game by splitting the transitions. "
        + "This subcommand outputs the game in the PG-solver format and this command is only "
        + "expected to be used for prototyping, since in practice the resulting files are too "
        + "large.",
    hidden = true
  )
  static final class Automaton2GameCommand extends AbstractOwlSubcommand {

    @Mixin
    private Mixins.AutomatonReader automatonReader = null;

    @Option(
      names = { "-o", "--output-file" },
      description = "Output file (default: write to stdout)."
    )
    private Path gameFile = null;

    @ArgGroup(multiplicity = "1")
    private InputsOutputs inputsOutputs = null;

    private static class InputsOutputs {
      @Option(
        names = {"-e", "--environment"},
        description = "List of atomic propositions controlled by the environment."
      )
      private String[] environment;

      @Option(
        names = {"-s", "--system"},
        description = "List of atomic propositions controlled by the system."
      )
      private String[] system;

      @Option(
        names = {"--environment-prefix"},
        description = "Prefix of atomic propositions controlled by the environment."
      )
      private String environmentPrefix;

      @Option(
        names = {"--system-prefix"},
        description = "Prefix of atomic propositions controlled by the system."
      )
      private String systemPrefix;
    }

    @Override
    protected int run() throws IOException {

      Predicate<String> environmentAtomicProposition;

      if (inputsOutputs.environment != null) {
        environmentAtomicProposition
          = Arrays.asList(inputsOutputs.environment)::contains;
      } else if (inputsOutputs.system != null) {
        environmentAtomicProposition
          = Predicate.not(Arrays.asList(inputsOutputs.system)::contains);
      } else if (inputsOutputs.systemPrefix != null) {
        environmentAtomicProposition
          = Predicate.not(inputsOutputs.systemPrefix::startsWith);
      } else {
        environmentAtomicProposition
          = inputsOutputs.environmentPrefix::startsWith;
      }

      try (var source = automatonReader.source(ParityAcceptance.class);
           var sink = new PrintWriter(gameFile == null
             ? new BufferedWriter(new OutputStreamWriter(System.out))
             : Files.newBufferedWriter(gameFile))) {

        var automatonIterator = source.iterator();

        while (automatonIterator.hasNext()) {

          var automaton = ParityUtil.convert(
            OmegaAcceptanceCast.cast(
              Views.complete(automatonIterator.next()), ParityAcceptance.class),
            ParityAcceptance.Parity.MAX_EVEN);

          checkArgument(automaton.initialStates().size() <= 1,
            "Multiple initial states are not supported");
          checkArgument(automaton.is(Automaton.Property.DETERMINISTIC),
            "Input automaton needs to be deterministic");

          PgSolverFormat.write(
            GameViews.split(automaton, environmentAtomicProposition), sink, false);
        }
      }

      return 0;
    }
  }
}
