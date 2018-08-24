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

package owl.ltl.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;
import owl.ltl.LabelledFormula;
import owl.ltl.tlsf.Tlsf;

class TlsfParserTest {

  private static final String TLSF1 = "INFO {\n"
    + "  TITLE:       \"LTL -> DBA  -  Example 12\"\n"
    + "  DESCRIPTION: \"One of the Acacia+ example files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "// TEST COMMENT\n"
    + "MAIN {\n"
    + "// TEST COMMENT\n"
    + "  INPUTS {\n"
    + "    p;\n"
    + "    q;\n"
    + "  }\n"
    + "// TEST COMMENT\n"
    + "  OUTPUTS {\n"
    + "    acc;\n"
    + "  }\n"
    + "// TEST COMMENT\n"
    + "  GUARANTEE {\n"
    + "// TEST COMMENT\n"
    + "    (G p -> F q) && (G !p <-> F !q)\n"
    + "      && G F acc;\n"
    + "  }\n"
    + "// TEST COMMENT\n"
    + " }";

  private static final String TLSF2 = "INFO {\n"
    + "  TITLE:       \"Load Balancing - Environment - 2 Clients\"\n"
    + "  DESCRIPTION: \"One of the Acacia+ Example files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + '\n'
    + "MAIN {\n"
    + '\n'
    + "  INPUTS {\n"
    + "    idle;\n"
    + "    request0;\n"
    + "    request1;\n"
    + "  }\n"
    + '\n'
    + "  OUTPUTS {\n"
    + "    grant0;\n"
    + "    grant1;\n"
    + "  }\n"
    + '\n'
    + "  ASSUMPTIONS {\n"
    + "    G F idle;\n"
    + "    G (!(idle && !grant0 && !grant1) || X idle);    \n"
    + "    G (!grant0 || X ((!request0 && !idle) U (!request0 && idle)));\n"
    + "  }\n"
    + '\n'
    + "  INVARIANTS {\n"
    + "    !request0 || !grant1;\n"
    + "    !grant0 || !grant1;\n"
    + "    !grant1 || !grant0;\n"
    + "    !grant0 || request0;\n"
    + "    !grant1 || request1;\n"
    + "    (!grant0 && !grant1) || idle;\n"
    + "  }\n"
    + '\n'
    + "  GUARANTEES {\n"
    + "    ! F G (request0 && !grant0);\n"
    + "    ! F G (request1 && !grant1);\n"
    + "  }\n"
    + '\n'
    + "}\n";

  private static final String LILY = "INFO {\n"
    + "  TITLE:       \"Lily Demo V1\"\n"
    + "  DESCRIPTION: \"One of the Lily demo files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + '\n'
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    go;\n"
    + "    cancel;\n"
    + "    req;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    grant;\n"
    + "  }\n"
    + "  ASSERT {\n"
    + "    ((req) -> (X ((grant) && (X ((grant) && (X (grant)))))));\n"
    + "    ((grant) -> (X (! (grant))));\n"
    + "    ((cancel) -> (X ((! (grant)) U (go))));\n"
    + "  }\n"
    + '}';

  private static final String LILY_LTL = "(G ((((X req) -> (X (grant && (X ((grant) && (X (gran"
    + "t))))))) && (grant -> (X (! (grant))))) && ((X (cancel)) -> (X ((! (grant)) U (X (go)))))))";

  private static final String UPPER_CASE = "INFO {\n"
    + "  TITLE:       \"Lily Demo V1\"\n"
    + "  DESCRIPTION: \"One of the Lily demo files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + '\n'
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    GO;\n"
    + "    CANCEL;\n"
    + "    REQ;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    GRANT;\n"
    + "  }\n"
    + "  ASSERT {\n"
    + "    ((REQ) -> (X ((GRANT) && (X ((GRANT) && (X (GRANT)))))));\n"
    + "    ((GRANT) -> (X (! (GRANT))));\n"
    + "    ((CANCEL) -> (X ((! (GRANT)) U (GO))));\n"
    + "  }\n"
    + '}';

  private static final String UPPER_CASE_DIFFICULT = "INFO {\n"
    + "  TITLE:       \"Lily Demo V1\"\n"
    + "  DESCRIPTION: \"One of the Lily demo files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + '\n'
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    BARFOO;\n"
    + "    FOO;\n"
    + "    BAR;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    FOOBAR;\n"
    + "  }\n"
    + "  ASSERT {\n"
    + "    ((BARFOO) -> (X ((FOO) && (X ((BAR) && (X (FOOBAR)))))));\n"
    + "  }\n"
    + '}';

  private static final String UPPER_CASE_FAULTY = "INFO {\n"
    + "  TITLE:       \"Lily Demo V1\"\n"
    + "  DESCRIPTION: \"One of the Lily demo files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + '\n'
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    Foo;\n"
    + "    fOO;\n"
    + "    foo;\n"
    + "  }\n"
    + '}';

  private static final String TLSF_COMPLETE = "INFO {\n"
    + "  TITLE:       \"TLSF - Test Specification\"\n"
    + "  DESCRIPTION: \"Test Test Test\"\n"
    + "  SEMANTICS:   Mealy\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + '\n'
    + "MAIN {\n"
    + '\n'
    + "  INPUTS {\n"
    + "    a1;\n"
    + "    b2;\n"
    + "    c3;\n"
    + "    d4;\n"
    + "    e5;\n"
    + "  } \n"
    + '\n'
    + "  OUTPUTS {\n"
    + "    f6;\n"
    + "    g7;\n"
    + "    h8;\n"
    + "    i9;\n"
    + "  }\n"
    + '\n'
    + "  INITIALLY {\n"
    + "    a1;\n"
    + "  }\n"
    + '\n'
    + "  PRESET {\n"
    + "    b2;\n"
    + "  }\n"
    + '\n'
    + "  REQUIRE {\n"
    + "    c3;\n"
    + "  }\n"
    + '\n'
    + "  ASSERT {\n"
    + "    d4;\n"
    + "  }\n"
    + '\n'
    + "  INVARIANTS {\n"
    + "    e5;\n"
    + "  }\n"
    + '\n'
    + "  ASSUME {\n"
    + "    f6;\n"
    + "  }\n"
    + '\n'
    + "  ASSUMPTIONS {\n"
    + "    g7;\n"
    + "  }\n"
    + '\n'
    + "  GUARANTEE {\n"
    + "    h8;\n"
    + "  }\n"
    + '\n'
    + "  GUARANTEES {\n"
    + "    i9;\n"
    + "  }  \n"
    + '}';

  private static final String LTL_COMPLETE =
    "((a1) -> ((b2) && ((((G (c3)) && (f6)) && (g7)) -> (((G ((d4) && (e5))) && (h8)) && (i9)))))";

  @Test
  void testParse1() {
    Tlsf tlsf = TlsfParser.parse(TLSF1);

    assertEquals(Tlsf.Semantics.MOORE, tlsf.semantics());
    assertEquals(Tlsf.Semantics.MEALY, tlsf.target());

    assertEquals(2, tlsf.inputs().cardinality());
    assertEquals(1, tlsf.outputs().cardinality());

    assertEquals(0, tlsf.variables().indexOf("p"));
    assertEquals(1, tlsf.variables().indexOf("q"));
    assertEquals(2, tlsf.variables().indexOf("acc"));
  }

  @Test
  void testParse2() {
    Tlsf tlsf = TlsfParser.parse(TLSF2);

    assertEquals(Tlsf.Semantics.MOORE, tlsf.semantics());
    assertEquals(Tlsf.Semantics.MEALY, tlsf.target());

    assertEquals(3, tlsf.inputs().cardinality());
    assertEquals(2, tlsf.outputs().cardinality());
  }

  @Test
  void testParseLily() {
    Tlsf lily = TlsfParser.parse(LILY);
    LabelledFormula expectedFormula = LtlParser.parse(LILY_LTL, lily.toFormula().variables());
    assertEquals(expectedFormula.split(Set.of("go", "cancel", "req")), lily.toFormula());
  }

  @Test
  void testCompParseLily() {
    Tlsf lily = TlsfParser.parse(LILY);
    assertEquals(3, lily.assert_().size());
  }

  @Test
  void testParseUpperCase() {
    Tlsf lily = TlsfParser.parse(LILY);
    Tlsf upperCase = TlsfParser.parse(UPPER_CASE);
    assertEquals(lily.toFormula().formula(), upperCase.toFormula().formula());
  }

  @Test
  void testParseUpperCaseDifficult() {
    Tlsf upperCaseDifficult = TlsfParser.parse(UPPER_CASE_DIFFICULT);
    assertEquals(List.of("BARFOO", "FOO", "BAR", "FOOBAR"), upperCaseDifficult.variables());
  }

  @Test
  void testParseUpperCaseFaulty() {
    assertThrows(ParseCancellationException.class, () -> TlsfParser.parse(UPPER_CASE_FAULTY));
  }

  @Test
  void testTlsfComplete() {
    Tlsf tlsf = TlsfParser.parse(TLSF_COMPLETE);
    assertEquals(LtlParser.syntax(LTL_COMPLETE, tlsf.variables()), tlsf.toFormula().formula());
  }
}