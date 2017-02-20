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

package owl.translations.ltl2dpa;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumSet;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import org.junit.Test;
import owl.automaton.output.HoaPrintable;
import owl.ltl.parser.LtlParseResult;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.ParseException;
import owl.translations.Optimisation;

public class LTL2DPATest {

  static void testOutput(String ltl, int size, int accSize) throws ParseException {
    EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
    opts.remove(Optimisation.PARALLEL);
    LtlParseResult parseResult = LtlParser.parse(ltl);
    LTL2DPA translation = new LTL2DPA(opts);
    ParityAutomaton<?> automaton = translation.apply(parseResult.getFormula());
    automaton.setVariables(parseResult.getVariableMapping());

    try (OutputStream stream = new ByteArrayOutputStream()) {
      HOAConsumer consumer = new HOAConsumerPrint(stream);
      automaton.toHoa(consumer, EnumSet.allOf(HoaPrintable.Option.class));
      assertEquals(stream.toString(), size, automaton.size());
      assertEquals(stream.toString(), accSize, automaton.getAcceptance().getAcceptanceSets());
    } catch (IOException ex) {
      throw new IllegalStateException(ex.toString(), ex);
    }
  }

  @Test
  public void testRegression1() throws ParseException {
    String ltl = "G (F (a & (a U b)))";
    testOutput(ltl, 2, 1);
    testOutput("! " + ltl, 2, 4);
  }

  @Test
  public void testRegression2() throws ParseException {
    String ltl = "G (F (a & X (F b)))";
    testOutput(ltl, 2, 1);
    testOutput("! " + ltl, 3, 2);
  }

  @Test
  public void testRegression3() throws ParseException {
    String ltl = "F ((a | (G b)) & (c | (G d)) & (e | (G f)))";
    testOutput(ltl, 32, 2);
  }
}
