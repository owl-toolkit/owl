package ltl.parser;

import ltl.*;
import ltl.tlsf.TLSF;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;

public class ParserTest {

    private static final String[] INPUT = {
            "!a",
            "G a",
            "F a & X b",
            "(a -> b) U c",
            "1 U b",
            "a M b",
            "G {sup < 0.5} F a",
            "G { >= 0.5} F a",
            "a R b",
            "!(a R b)"
    };

    private static final Formula[] FORMULAS = {
            new Literal(0, true),
            new GOperator(new Literal(0)),
            new Conjunction(new FOperator(new Literal(0)), new XOperator(new Literal(1))),
            new UOperator(new Disjunction(new Literal(0, true), new Literal(1)), new Literal(2)),
            new FOperator(new Literal(0)),
            new UOperator(new Literal(1), new Conjunction(new Literal(0), new Literal(1))),
            new FrequencyG(new GOperator(new Literal(0, true)), 0.5, FrequencyG.Comparison.GEQ, FrequencyG.Limes.SUP),
            new FrequencyG(new FOperator(new Literal(0)), 0.5, FrequencyG.Comparison.GEQ, FrequencyG.Limes.INF),
            new ROperator(new Literal(0), new Literal(1)),
            new UOperator(new Literal(0, true), new Literal(1, true))
    };

    @Test
    public void formula() throws Exception {
        for (int i = 0; i < INPUT.length; i++) {
            Parser parser = new Parser(new StringReader(INPUT[i]));
            assertEquals(FORMULAS[i], parser.formula());
        }
    }

    private static final String TLSF1 = "INFO {\n" +
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

    private static final String TLSF2 = "INFO {\n" +
            "  TITLE:       \"Load Balancing - Environment - 2 Clients\"\n" +
            "  DESCRIPTION: \"One of the Acacia+ Example files\"\n" +
            "  SEMANTICS:   Moore\n" +
            "  TARGET:      Mealy\n" +
            "}\n" +
            "\n" +
            "MAIN {\n" +
            "\n" +
            "  INPUTS {\n" +
            "    idle;\n" +
            "    request0;\n" +
            "    request1;\n" +
            "  }\n" +
            "\n" +
            "  OUTPUTS {\n" +
            "    grant0;\n" +
            "    grant1;\n" +
            "  }\n" +
            "\n" +
            "  ASSUMPTIONS {\n" +
            "    G F idle;\n" +
            "    G (!(idle && !grant0 && !grant1) || X idle);    \n" +
            "    G (!grant0 || X ((!request0 && !idle) U (!request0 && idle)));\n" +
            "  }\n" +
            "\n" +
            "  INVARIANTS {\n" +
            "    !request0 || !grant1;\n" +
            "    !grant0 || !grant1;\n" +
            "    !grant1 || !grant0;\n" +
            "    !grant0 || request0;\n" +
            "    !grant1 || request1;\n" +
            "    (!grant0 && !grant1) || idle;\n" +
            "  }\n" +
            "\n" +
            "  GUARANTEES {\n" +
            "    ! F G (request0 && !grant0);\n" +
            "    ! F G (request1 && !grant1);\n" +
            "  }\n" +
            "\n" +
            "}\n";

    @Test
    public void testTLSF1() throws ParseException {
        Parser parser = new Parser(new StringReader(TLSF1));
        TLSF tlsf = parser.tlsf();

        assertEquals(TLSF.Semantics.MOORE, tlsf.semantics());
        assertEquals(TLSF.Semantics.MEALY, tlsf.target());

        assertEquals(2, tlsf.inputs().cardinality());
        assertEquals(1, tlsf.outputs().cardinality());

        assertEquals(0, tlsf.mapping().get("p").intValue());
        assertEquals(1, tlsf.mapping().get("q").intValue());
        assertEquals(2, tlsf.mapping().get("acc").intValue());
    }


    @Test
    public void testTLSF2() throws ParseException {
        Parser parser = new Parser(new StringReader(TLSF2));
        TLSF tlsf = parser.tlsf();

        assertEquals(TLSF.Semantics.MOORE, tlsf.semantics());
        assertEquals(TLSF.Semantics.MEALY, tlsf.target());

        assertEquals(3, tlsf.inputs().cardinality());
        assertEquals(2, tlsf.outputs().cardinality());
    }
}