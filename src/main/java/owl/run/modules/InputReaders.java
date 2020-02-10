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

package owl.run.modules;

import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;
import java.util.logging.Level;
import java.util.logging.Logger;
import jhoafparser.parser.generated.ParseException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.automaton.hoa.HoaReader;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.run.PipelineException;

public final class InputReaders {
  private static final Logger logger = Logger.getLogger(InputReaders.class.getName());

  public static final OwlModule<OwlModule.InputReader> HOA_INPUT_MODULE = OwlModule.of(
    "hoa",
    "Parses automata given in HOA format, converting them to transition based acceptance if "
      + "necessary",
    (commandline, environment) -> (reader, callback, stopSignal) -> {
      try {
        HoaReader.readStream(
          reader, environment.factorySupplier()::getValuationSetFactory, callback::accept);
      } catch (ParseException e) {
        throw new PipelineException("Failed to parse input automaton", e);
      }
    });

  public static final OwlModule<OwlModule.InputReader> LTL_INPUT_MODULE = OwlModule.of(
    "ltl",
    "Parses LTL formulas and converts them into NNF",
    (commandLine, environment) -> (reader, callback, stopSignal) ->
      CharStreams.readLines(reader, new LineProcessor<>() {
        @Override
        public boolean processLine(String line) {
          if (stopSignal.get()) {
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
      }));

  private InputReaders() {}
}
