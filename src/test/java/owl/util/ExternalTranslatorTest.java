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

package owl.util;

import static org.hamcrest.CoreMatchers.is;

import org.junit.Assume;
import org.junit.Test;
import owl.ltl.parser.LtlParser;
import owl.run.DefaultEnvironment;
import owl.translations.ExternalTranslator;

public class ExternalTranslatorTest {

  private static final String[] FORMULA = {
    "(G F a) | (b U c)",
    "F(X((b) W (a)))",
    "G (F (a & X F (b & X F c)))"
  };

  private static final String[] TOOL = {
    "ltl2tgba -H",
    "ltl2tgba -H --deterministic --generic",
    "ltl2tgba -H --deterministic --generic"
  };

  @Test
  public void testApply() {
    // Check if the tool is installed and available.
    try {
      ExternalTranslator tool = new ExternalTranslator(DefaultEnvironment.annotated(), TOOL[0]);
      tool.apply(LtlParser.parse(FORMULA[0]));
    } catch (IllegalStateException ex) {
      // Assumption is always violated now.
      Assume.assumeThat(true, is(false));
    }

    for (int i = 1; i < FORMULA.length; i++) {
      ExternalTranslator tool = new ExternalTranslator(DefaultEnvironment.annotated(), TOOL[i]);
      tool.apply(LtlParser.parse(FORMULA[i]));
    }
  }
}