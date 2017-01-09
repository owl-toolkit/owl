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

package translations;

import ltl.Formula;
import ltl.parser.ParseException;
import ltl.parser.Parser;

import java.io.InputStream;
import java.util.Map;

public abstract class AbstractLTLCommandLineTool extends AbstractCommandLineTool<Formula> {
    private Map<Integer, String> mapping;

    @Override
    protected Formula parseInput(InputStream stream) throws ParseException {
        Parser parser = new Parser(stream);
        Formula formula = parser.formula();
        mapping = parser.map.inverse();
        return formula;
    }

    @Override
    protected Map<Integer, String> getAtomMapping() {
        return mapping;
    }
}
