/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jhoafparser.parser.generated.ParseException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.cli.ImmutableTransformerSettings;
import owl.cli.ModuleSettings.TransformerSettings;
import owl.factories.ValuationSetFactory;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.PrintVisitor;
import owl.run.env.Environment;
import owl.run.transformer.Transformers;

public class ExternalTranslator
  implements Function<LabelledFormula, Automaton<HoaState, OmegaAcceptance>> {
  private static final Logger logger = Logger.getLogger(ExternalTranslator.class.getName());
  private static final Pattern splitPattern = Pattern.compile("\\s+");
  public static final TransformerSettings settings = ImmutableTransformerSettings.builder()
    .key("ltl2aut-ext")
    .description("Runs an external tool for ltl to automaton translation")
    .options(options())
    .transformerSettingsParser(settings -> {
      InputMode inputMode;
      String inputType = settings.getOptionValue("inputType");
      if (inputType == null) {
        inputMode = InputMode.STDIN;
      } else {
        switch (inputType) {
          case "stdin":
            inputMode = InputMode.STDIN;
            break;
          case "replace":
            inputMode = InputMode.REPLACE;
            break;
          default:
            throw new org.apache.commons.cli.ParseException("Unknown input mode " + inputType);
        }
      }
      String toolPath = settings.getOptionValue("tool");
      String[] tool = splitPattern.split(toolPath);
      return environment -> {
        ExternalTranslator translator = new ExternalTranslator(environment, inputMode, tool);
        return Transformers.fromFunction(LabelledFormula.class, translator);
      };
    }).build();

  private final Environment env;
  private final InputMode inputMode;
  private final String[] tool;

  public ExternalTranslator(Environment env, String tool) {
    this(env, InputMode.STDIN, splitPattern.split(tool));
  }

  @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
  ExternalTranslator(Environment env, InputMode inputMode, String[] tool) {
    this.env = env;
    this.inputMode = inputMode;

    this.tool = tool;
    if (inputMode == InputMode.REPLACE) {
      checkArgument(Arrays.stream(tool).anyMatch("%f"::equals));
    }
  }

  private static Options options() {
    Option tool = new Option("t", "tool", true, "The tool invocation");
    tool.setRequired(true);

    Option inputType = new Option("i", "input", true, "How to pass the formula to the tool. "
      + "Available modes are stdin or replace (add %f to the invocation)");

    return new Options()
      .addOption(tool)
      .addOption(inputType);
  }

  @Override
  public Automaton<HoaState, OmegaAcceptance> apply(LabelledFormula formula) {
    ProcessBuilder processBuilder;
    String formulaString = PrintVisitor.toString(formula, true);
    if (inputMode == InputMode.REPLACE) {
      String[] invocation = tool.clone();
      for (int i = 0; i < invocation.length; i++) {
        // TODO Replace all %f even if they are only part of an argument, add %% to denote literal %
        if ("%f".equals(invocation[i])) {
          invocation[i] = formulaString;
        }
      }
      processBuilder = new ProcessBuilder(invocation);
    } else {
      processBuilder = new ProcessBuilder(tool);
    }
    Process process = null;
    try {
      process = processBuilder.start();
      logger.log(Level.FINER, "Running process {0}", processBuilder.command());

      if (inputMode == InputMode.STDIN) {
        try (OutputStreamWriter outputStream =
               new OutputStreamWriter(process.getOutputStream(), env.charset())) {
          logger.log(Level.FINER, "Passing {0} to process", formulaString);
          outputStream.write(formulaString);
          outputStream.write('\n');
        }
      }

      try (BufferedInputStream inputStream = new BufferedInputStream(process.getInputStream())) {
        ValuationSetFactory vsFactory = env.factorySupplier()
          .getValuationSetFactory(formula.variables);
        Automaton<HoaState, OmegaAcceptance> automaton = AutomatonReader
          .readHoa(inputStream, vsFactory, OmegaAcceptance.class);
        logger.log(Level.FINEST, () -> String.format("Read automaton for %s:%n%s", formula,
          AutomatonUtil.toHoa(automaton)));
        return automaton;
      }
    } catch (IOException | ParseException e) {
      throw new IllegalStateException("Failed to use external translator.", e);
    } finally {
      if (process != null && process.isAlive()) {
        process.destroy();
        try {
          process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { // NOPMD
        }
        process.destroyForcibly();
      }
    }
  }

  enum InputMode {
    STDIN, REPLACE
  }
}
