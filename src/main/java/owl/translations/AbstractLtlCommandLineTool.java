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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

public abstract class AbstractLtlCommandLineTool extends AbstractCommandLineTool<LabelledFormula> {
  @Override
  protected Collection<LabelledFormula> parseInput(InputStream stream)
    throws IOException {
    try (BufferedReader reader =
           new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader.lines()
        .filter(line -> !line.isEmpty())
        .map(LtlParser::parse)
        .collect(Collectors.toList());
    }
  }
}
