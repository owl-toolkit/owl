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

package owl.util;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;
import owl.translations.ExternalTranslator;

class ExternalTranslatorTest {

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
  void testApply() {
    // Check if the tool is installed and available.
    try {
      var tool = new ExternalTranslator(TOOL[0], ExternalTranslator.InputMode.STDIN);
      tool.apply(LtlParser.parse(FORMULA[0]));
    } catch (IllegalStateException | CompletionException ex) {
      // Assumption is always violated now.
      assumeTrue(false);
    }

    for (int i = 1; i < FORMULA.length; i++) {
      var tool = new ExternalTranslator(TOOL[i], ExternalTranslator.InputMode.STDIN);
      tool.apply(LtlParser.parse(FORMULA[i]));
    }
  }
}