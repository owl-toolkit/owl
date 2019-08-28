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

package owl.translations.dpa2safety;

import org.junit.jupiter.api.Test;
import owl.ltl.parser.LtlParser;
import owl.run.Environment;
import owl.translations.ltl2dpa.LTL2DPAFunction;

class Dpa2SafetyTest {

  @Test
  void test() {
    LTL2DPAFunction dpaConstructor = new LTL2DPAFunction(Environment.standard(),
      LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG);
    DPA2Safety safetyConstructor = new DPA2Safety<>();
    safetyConstructor.apply(dpaConstructor.apply(LtlParser.parse("F G a | G F b | X c")), 1);
    safetyConstructor.apply(dpaConstructor.apply(LtlParser.parse("F G a | G F b | X c")), 9);
  }
}