package owl.ltl.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import owl.ltl.LabelledFormula;
import owl.ltl.tlsf.Tlsf;

public class TlsfParserTest {

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
    + "\n"
    + "MAIN {\n"
    + "\n"
    + "  INPUTS {\n"
    + "    idle;\n"
    + "    request0;\n"
    + "    request1;\n"
    + "  }\n"
    + "\n"
    + "  OUTPUTS {\n"
    + "    grant0;\n"
    + "    grant1;\n"
    + "  }\n"
    + "\n"
    + "  ASSUMPTIONS {\n"
    + "    G F idle;\n"
    + "    G (!(idle && !grant0 && !grant1) || X idle);    \n"
    + "    G (!grant0 || X ((!request0 && !idle) U (!request0 && idle)));\n"
    + "  }\n"
    + "\n"
    + "  INVARIANTS {\n"
    + "    !request0 || !grant1;\n"
    + "    !grant0 || !grant1;\n"
    + "    !grant1 || !grant0;\n"
    + "    !grant0 || request0;\n"
    + "    !grant1 || request1;\n"
    + "    (!grant0 && !grant1) || idle;\n"
    + "  }\n"
    + "\n"
    + "  GUARANTEES {\n"
    + "    ! F G (request0 && !grant0);\n"
    + "    ! F G (request1 && !grant1);\n"
    + "  }\n"
    + "\n"
    + "}\n";

  private static final String LILY = "INFO {\n"
    + "  TITLE:       \"Lily Demo V1\"\n"
    + "  DESCRIPTION: \"One of the Lily demo files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "\n"
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
    + "}";

  private static final String LILY_LTL = "(G ((((X req) -> (X (grant && (X ((grant) && (X (gran"
    + "t))))))) && (grant -> (X (! (grant))))) && ((X (cancel)) -> (X ((! (grant)) U (X (go)))))))";

  @Test
  public void testParse1() {
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
  public void testParse2() {
    Tlsf tlsf = TlsfParser.parse(TLSF2);

    assertEquals(Tlsf.Semantics.MOORE, tlsf.semantics());
    assertEquals(Tlsf.Semantics.MEALY, tlsf.target());

    assertEquals(3, tlsf.inputs().cardinality());
    assertEquals(2, tlsf.outputs().cardinality());
  }

  @Test
  public void testParseLily() {
    Tlsf lily = TlsfParser.parse(LILY);
    LabelledFormula expectedFormula = LtlParser.parse(LILY_LTL, lily.toFormula().variables);
    assertEquals(expectedFormula, lily.toFormula());
  }
}