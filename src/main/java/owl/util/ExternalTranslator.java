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

package owl.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Function;
import jhoafparser.parser.generated.ParseException;
import owl.automaton.Automaton;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.Registry;
import owl.ltl.Formula;

public class ExternalTranslator implements Function<Formula, Automaton<HoaState, OmegaAcceptance>> {

  private ProcessBuilder builder;

  public ExternalTranslator(String tool) {
    this(tool.split("\\s+"));
  }

  private ExternalTranslator(String[] tool) {
    this.builder = new ProcessBuilder(tool);
  }

  @Override
  public Automaton<HoaState, OmegaAcceptance> apply(Formula formula) {
    try {
      Process process = builder.start();

      BufferedInputStream inputStream = new BufferedInputStream(process.getInputStream());
      BufferedOutputStream outputStream = new BufferedOutputStream(process.getOutputStream());

      outputStream.write(formula.toString(Collections.emptyList(), true).getBytes("UTF-8"));
      outputStream.write('\n');
      outputStream.close();

      Automaton<HoaState, OmegaAcceptance> automaton = AutomatonReader.readHoa(inputStream,
        Registry.getFactories(formula).valuationSetFactory, OmegaAcceptance.class);
      process.destroy();

      return automaton;
    } catch (IOException | ParseException e) {
      e.printStackTrace();
      throw new IllegalStateException("Failed to use external translator. Reason: " + e);
    }
  }
}
