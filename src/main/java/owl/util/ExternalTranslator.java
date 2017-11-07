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
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.regex.Pattern;
import jhoafparser.parser.generated.ParseException;
import owl.automaton.Automaton;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.factories.ValuationSetFactory;
import owl.factories.jbdd.JBddSupplier;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.PrintVisitor;

public class ExternalTranslator
  implements Function<LabelledFormula, Automaton<HoaState, OmegaAcceptance>> {
  private static final Pattern splitPattern = Pattern.compile("\\s+");
  private final ProcessBuilder builder;

  public ExternalTranslator(String tool) {
    this(splitPattern.split(tool));
  }

  private ExternalTranslator(String[] tool) {
    this.builder = new ProcessBuilder(tool);
  }

  @Override
  public Automaton<HoaState, OmegaAcceptance> apply(LabelledFormula formula) {
    Process process = null;
    try {
      process = builder.start();

      try (OutputStreamWriter outputStream =
             new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
        outputStream.write(PrintVisitor.toString(formula, true));
        outputStream.write('\n');
      }

      ValuationSetFactory vsFactory = JBddSupplier.async()
        .getFactories(formula).valuationSetFactory;
      try (BufferedInputStream inputStream = new BufferedInputStream(process.getInputStream())) {
        return AutomatonReader.readHoa(inputStream, vsFactory, OmegaAcceptance.class);
      }
    } catch (IOException | ParseException e) {
      throw new IllegalStateException("Failed to use external translator.", e);
    } finally {
      if (process != null && process.isAlive()) {
        process.destroy();
      }
    }
  }
}
