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

import com.google.common.collect.Iterables;
import jhoafparser.parser.HOAFParser;
import omega_automaton.StoredBuchiAutomaton;
import omega_automaton.output.HOAPrintable;
import translations.nba2ldba.Nba2Ldba;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;

public class NBA2LDBA extends AbstractCommandLineTool<StoredBuchiAutomaton> {
    private Map<Integer, String> mapping;

    @Override
    protected Function<StoredBuchiAutomaton, ? extends HOAPrintable> getTranslation(EnumSet<Optimisation> optimisations) {
        return new Nba2Ldba(optimisations);
    }

    @Override
    protected StoredBuchiAutomaton parseInput(InputStream stream) throws Exception {
        StoredBuchiAutomaton.Builder builder = new StoredBuchiAutomaton.Builder();
        HOAFParser.parseHOA(stream, builder);
        StoredBuchiAutomaton nba = Iterables.getOnlyElement(builder.getAutomata());
        mapping = nba.getAtomMapping();
        return nba;
    }

    @Override
    protected Map<Integer, String> getAtomMapping() {
        return mapping;
    }

    public static void main(String... args) throws Exception {
        new NBA2LDBA().execute(new ArrayDeque<>(Arrays.asList(args)));
    }
}
