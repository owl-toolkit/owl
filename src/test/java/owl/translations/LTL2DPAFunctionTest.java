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

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;
import owl.run.Environment;
import owl.translations.ltl2dpa.LTL2DPAFunction;

class LTL2DPAFunctionTest {
  @Test
  void testComplete() {
    var environment = Environment.standard();
    var translation = new LTL2DPAFunction(environment, EnumSet.of(
      LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE,
      LTL2DPAFunction.Configuration.COMPRESS_COLOURS));

    translation.apply(LtlParser.parse("ff"));
  }
}
