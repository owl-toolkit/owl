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

package owl.translations;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import owl.automaton.Automaton;
import owl.automaton.hoa.HoaReader;
import owl.bdd.FactorySupplier;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.PrintVisitor;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;

public class ExternalTranslator implements Function<LabelledFormula, Automaton<Integer, ?>> {
  private static final Logger logger = Logger.getLogger(ExternalTranslator.class.getName());
  private static final Pattern splitPattern = Pattern.compile("\\s+");

  private final List<String> command;
  private final InputMode inputMode;

  public ExternalTranslator(String command, InputMode inputMode) {
    this(List.of(splitPattern.split(command)), inputMode);
  }

  public ExternalTranslator(List<String> command, InputMode inputMode) {
    this.inputMode = inputMode;
    this.command = List.copyOf(command);

    if (inputMode == InputMode.REPLACE) {
      checkArgument(this.command.contains("%f"));
    }
  }

  @Override
  public Automaton<Integer, ?> apply(LabelledFormula formula) {
    ProcessBuilder processBuilder;
    String formulaString = PrintVisitor.toString(formula, true);

    if (inputMode == InputMode.REPLACE) {
      var adjustedCommand = new ArrayList<>(command);
      adjustedCommand.replaceAll(x -> "%f".equals(x) ? formulaString : x);
      processBuilder = new ProcessBuilder(adjustedCommand);
    } else {
      processBuilder = new ProcessBuilder(command);
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
        return HoaReader.read(reader,
          FactorySupplier.defaultSupplier()::getBddSetFactory,
          formula.atomicPropositions());
      }
    } catch (IOException | ParseException | NoSuchElementException ex) {
      throw new CompletionException("Exception occurred while using external translator.", ex);
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

  public enum InputMode {
    STDIN, REPLACE
  }
}
