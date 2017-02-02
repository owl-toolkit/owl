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

package owl.translations;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParseResult;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.ParseException;

public abstract class AbstractLtlCommandLineTool extends AbstractCommandLineTool<Formula> {
  private List<String> variables;

  @Override
  protected List<String> getVariables() {
    return variables;
  }

  @Override
  protected Formula parseInput(InputStream stream) throws ParseException, IOException {
    LtlParseResult parser = LtlParser.parse(stream);
    Formula formula = parser.getFormula();
    variables = parser.getVariableMapping();
    return formula;
  }
}
