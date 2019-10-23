/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
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
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.output.HoaPrinter;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.PrintVisitor;
import owl.run.Environment;
import owl.run.modules.OwlModule;

public class ExternalTranslator
  implements Function<LabelledFormula, Automaton<HoaState, OmegaAcceptance>> {
  private static final Logger logger = Logger.getLogger(ExternalTranslator.class.getName());
  private static final Pattern splitPattern = Pattern.compile("\\s+");

  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "ltl2aut-ext",
    "Runs an external tool for LTL to automaton translation",
    () -> {
      Option toolOption = new Option("t", "tool", true, "The tool invocation");
      toolOption.setRequired(true);

      Option inputType = new Option("i", "input", true, "How to pass the formula to the tool. "
        + "Available modes are stdin or replace (add %f to the invocation)");

      return new Options()
        .addOption(toolOption)
        .addOption(inputType);
    },
    (commandLine, environment) -> {
      String inputType = commandLine.getOptionValue("inputType");
      InputMode inputMode;
      if (inputType == null || "stdin".equals(inputType)) {
        inputMode = InputMode.STDIN;
      } else if ("replace".equals(inputType)) {
        inputMode = InputMode.REPLACE;
      } else {
        throw new org.apache.commons.cli.ParseException("Unknown input mode " + inputType);
      }

      String toolPath = commandLine.getOptionValue("tool");
      String[] tool = splitPattern.split(toolPath);

      ExternalTranslator translator = new ExternalTranslator(environment, inputMode, tool);
      return OwlModule.Transformer.of(LabelledFormula.class, translator);
    });

  private final Environment env;
  private final InputMode inputMode;
  private final String[] tool;

  public ExternalTranslator(Environment env, String tool) {
    this(env, InputMode.STDIN, splitPattern.split(tool));
  }

  @SuppressWarnings({"PMD.ArrayIsStoredDirectly",
                      "AssignmentToCollectionOrArrayFieldFromParameter"})
  private ExternalTranslator(Environment env, InputMode inputMode, String[] tool) {
    this.env = env;
    this.inputMode = inputMode;

    this.tool = tool;
    if (inputMode == InputMode.REPLACE) {
      checkArgument(Arrays.asList(tool).contains("%f"));
    }
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
        //noinspection NestedTryStatement
        try (Writer outputStream = new OutputStreamWriter(process.getOutputStream(),
          Charset.defaultCharset())) {
          logger.log(Level.FINER, "Passing {0} to process", formulaString);
          outputStream.write(formulaString);
          outputStream.write('\n');
        }
      }

      //noinspection NestedTryStatement
      try (Reader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
        FactorySupplier factorySupplier = env.factorySupplier();
        ValuationSetFactory vsFactory = factorySupplier.getValuationSetFactory(formula.variables());
        Automaton<HoaState, OmegaAcceptance> automaton =
          AutomatonReader.readHoa(reader, x -> {
            checkArgument(vsFactory.alphabet().containsAll(x));
            return vsFactory;
          });
        logger.log(Level.FINEST, () -> String.format("Read automaton for %s:%n%s", formula,
          HoaPrinter.toString(automaton)));
        return automaton;
      }
    } catch (IOException | ParseException e) {
      throw new IllegalStateException("Failed to use external translator.", e);
    } finally {
      if (process != null && process.isAlive()) {
        process.destroy();
        try {
          process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
          // NOPMD
        }
        process.destroyForcibly();
      }
    }
  }

  enum InputMode {
    STDIN, REPLACE
  }
}
