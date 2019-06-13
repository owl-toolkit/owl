/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.run.modules;

import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jhoafparser.parser.generated.ParseException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.automaton.AutomatonReader;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.PipelineException;
import owl.run.modules.OwlModuleParser.ReaderParser;

public final class InputReaders {
  static final Logger logger = Logger.getLogger(InputReaders.class.getName());

  public static final InputReader HOA = (reader, env, callback) -> {
    try {
      AutomatonReader
        .readHoaStream(reader, env.factorySupplier()::getValuationSetFactory, callback::accept);
    } catch (ParseException e) {
      throw new PipelineException("Failed to parse input automaton", e);
    }
  };
  public static final ReaderParser HOA_CLI = ImmutableReaderParser.builder()
    .key("hoa")
    .description("Parses automata given in HOA format, converting them to transition based "
      + "acceptance if necessary")
    .parser(settings -> HOA).build();

  public static final InputReader LTL = (reader, env, callback) ->
    CharStreams.readLines(reader, new LineProcessor<Void>() {
      @Override
      public boolean processLine(String line) {
        if (env.isShutdown()) {
          return false;
        }

        if (line.isEmpty()) {
          return true;
        }

        logger.log(Level.FINEST, "Parsing formula {0}", line);

        LabelledFormula formula;
        try {
          formula = LtlParser.parse(line);
        } catch (RecognitionException | ParseCancellationException e) {
          throw new PipelineException("Failed to parse LTL formula " + line, e);
        }
        logger.log(Level.FINE, "Read formula {0} from line {1}", new Object[] {formula, line});
        callback.accept(formula);
        return true;
      }

      @Override
      public Void getResult() {
        return null;
      }
    });
  public static final ReaderParser LTL_CLI = ImmutableReaderParser.builder()
    .key("ltl")
    .description("Parses LTL formulas and converts them into NNF")
    .parser(settings -> LTL).build();


  @SuppressWarnings({"ProhibitedExceptionThrown", "PMD.AvoidCatchingGenericException",
                      "PMD.AvoidThrowingRawExceptionTypes"})
  public static Consumer<Object> checkedCallback(CheckedCallback consumer) {
    return input -> {
      try {
        consumer.accept(input);
      } catch (Exception e) {
        Throwables.throwIfUnchecked(e);
        throw new RuntimeException(e);
      }
    };
  }

  private InputReaders() {}

  @SuppressWarnings({"ProhibitedExceptionDeclared", "PMD.SignatureDeclareThrowsException"})
  @FunctionalInterface
  public interface CheckedCallback {
    void accept(Object input) throws Exception;
  }
}
