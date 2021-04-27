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

import static picocli.CommandLine.Command;
import static picocli.CommandLine.IExecutionExceptionHandler;
import static picocli.CommandLine.ParameterException;
import static picocli.CommandLine.ParseResult;
import static picocli.CommandLine.Spec;

import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "owl",
         description =
           "A tool collection and library for ω-words, ω-automata and linear temporal logic.",
         synopsisSubcommandLabel = "COMMAND",
         usageHelpAutoWidth = true,
         subcommands = {
           // LTL Translation Commands
           LtlTranslationCommands.Ltl2NbaCommand.class,
           LtlTranslationCommands.Ltl2NgbaCommand.class,
           LtlTranslationCommands.Ltl2LdbaCommand.class,
           LtlTranslationCommands.Ltl2LdgbaCommand.class,
           LtlTranslationCommands.Ltl2DpaCommand.class,
           LtlTranslationCommands.Ltl2DraCommand.class,
           LtlTranslationCommands.Ltl2DgraCommand.class,
           LtlTranslationCommands.Ltl2DelaCommand.class,
           LtlTranslationCommands.Ltl2AigCommand.class,

           // LTL Conversion Commands
           LtlConversionCommands.Delta2Normalisation.class,

           // Automaton Conversion Commands
           AutomatonConversionCommands.Ngba2LdbaCommand.class,
           AutomatonConversionCommands.Nba2DpaCommand.class,
           AutomatonConversionCommands.NbaSimCommand.class,
           AutomatonConversionCommands.Aut2ParityCommand.class,
           AutomatonConversionCommands.GfgMinimisation.class,

           // Miscellaneous commands
           MiscCommands.BibliographyCommand.class,
           MiscCommands.DelagMigrationCommand.class,

           MiscCommands.LtlInspectionCommand.class,
           LtlConversionCommands.LtlUtilities.class,
           LtlConversionCommands.RLtlReader.class,

           MiscCommands.AutInspectionCommand.class,
           AutomatonConversionCommands.AutUtilities.class,
           MiscCommands.Automaton2GameCommand.class,
         })
@SuppressWarnings("PMD.SystemPrintln")
public final class OwlCommand extends AbstractOwlCommand {

  @Spec
  CommandSpec spec = null;

  private final List<String> args;

  private OwlCommand() {
    this.args = null;
  }

  public OwlCommand(String[] args) {
    this.args = Arrays.stream(args)
      .filter(Predicate.not(Objects::isNull))
      .collect(Collectors.toUnmodifiableList());
  }

  public static void main(String[] args) {
    System.exit(new CommandLine(new OwlCommand(args))
      .setExecutionExceptionHandler(new ExecutionExceptionHandler())
      .registerConverter(LabelledFormula.class, LtlParser::parse)
      .execute(args));
  }

  @Override
  public Integer call() {
    throw new ParameterException(spec.commandLine(), "Missing required subcommand.");
  }

  @Override
  protected List<String> rawArgs() {
    return args;
  }

  private static class ExecutionExceptionHandler implements IExecutionExceptionHandler {
    @Override
    public int handleExecutionException(
      Exception ex, CommandLine commandLine, ParseResult parseResult) throws Exception {

      if (ex instanceof NoSuchFileException) {
        var exception = (NoSuchFileException) ex;

        var file = exception.getFile();
        var reason = exception.getReason();

        if (reason == null) {
          System.err.printf("Could not access file \"%s\".", file);
        } else {
          System.err.printf(
            "Could not access file \"%s\", because of the following reason: %s", file, reason);
        }
      } else if (ex instanceof IllegalArgumentException) {
        if (ex.getCause() instanceof RecognitionException
          || ex.getCause() instanceof ParseCancellationException) {
          System.err.printf("Could not parse linear temporal logic formula: %s", ex.getMessage());
        } else {
          ex.printStackTrace(System.err);
        }
      } else {
        ex.printStackTrace(System.err);
      }

      return -1;
    }
  }
}


