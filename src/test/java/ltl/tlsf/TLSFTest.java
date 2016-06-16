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

package ltl.tlsf;

import ltl.parser.ParseException;
import ltl.parser.Parser;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.*;

public class TLSFTest {

    String tlsf = "INFO {\n" +
            "  TITLE:       \"LTL -> DBA  -  Example 12\"\n" +
            "  DESCRIPTION: \"One of the Acacia+ example files\"\n" +
            "  SEMANTICS:   Moore\n" +
            "  TARGET:      Mealy\n" +
            "}\n" +
            "// TEST COMMENT\n" +
            "MAIN {\n" +
            "// TEST COMMENT\n" +
            "  INPUTS {\n" +
            "    p;\n" +
            "    q;\n" +
            "  }\n" +
            "// TEST COMMENT\n" +
            "  OUTPUTS {\n" +
            "    acc;\n" +
            "  }\n" +
            "// TEST COMMENT\n" +
            "  GUARANTEE {\n" +
            "// TEST COMMENT\n" +
            "    (G p -> F q) && (G !p <-> F !q)\n" +
            "      && G F acc;\n" +
            "  }\n" +
            "// TEST COMMENT\n" +
            " }";

    @Test
    public void testTLSF() throws ParseException {
        Parser parser = new Parser(new StringReader(tlsf));
        TLSF tlsf = parser.tlsf();

        assertEquals(TLSF.Semantics.MOORE, tlsf.semantics());
        assertEquals(TLSF.Semantics.MEALY, tlsf.target());

        assertEquals(2, tlsf.inputs().cardinality());
        assertEquals(1, tlsf.outputs().cardinality());

        assertEquals(0, tlsf.mapping().get("p").intValue());
        assertEquals(1, tlsf.mapping().get("q").intValue());
        assertEquals(2, tlsf.mapping().get("acc").intValue());
    }
}