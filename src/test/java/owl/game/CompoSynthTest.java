package owl.game;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.algorithms.ZielonkaSolver;
import owl.ltl.parser.TlsfParser;
import owl.ltl.tlsf.Tlsf;
import owl.run.DefaultEnvironment;
import owl.translations.compositionalsynthesis.Synth;
import owl.translations.ltl2dpa.LTL2DPAFunction;

@RunWith(Theories.class)
public class CompoSynthTest {

  private boolean parityGameReference(Tlsf specification) {
    var translation = new LTL2DPAFunction(
      DefaultEnvironment.annotated(), LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG);

    var automaton = AutomatonUtil.cast(
      translation.apply(specification.toFormula()), Object.class, ParityAcceptance.class);

    return ZielonkaSolver.zielonkaRealizability(
      GameViews.split(automaton, specification.inputs()));
  }

  @DataPoint
  public static final String SIMPLE_ARBITER_2_UNREAL = "INFO {\n"
    + "  TITLE:       \"Simple Arbiter, unrealizable variant 2\"\n"
    + "  DESCRIPTION: \"Parameterized Arbiter,"
    + " where each request has to be eventually granted\"\n"
    + "  SEMANTICS:   Mealy\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    r_0;\n"
    + "    r_1;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    g_0;\n"
    + "    g_1;\n"
    + "  }\n"
    + "  ASSERT {\n"
    + "    (((! (g_0)) && (true)) || ((true) && (! (g_1))));\n"
    + "    (((r_0) && (X (r_1))) -> (F ((g_0) && (g_1))));\n"
    + "  }\n"
    + "  GUARANTEE {\n"
    + "    (G ((r_0) -> (F (g_0))));\n"
    + "    (G ((r_1) -> (F (g_1))));\n"
    + "  }\n"
    + "}";
  
  @DataPoint
  public static final String ROUND_ROBIN_ARBITER_2 = "INFO {\n"
    + "  TITLE:       \"Round Robin Arbiter\"\n"
    + "  DESCRIPTION: \"Parameterized Arbiter, where requst signals have to remain HIGH until "
    + "they are granted\"\n"
    + "  SEMANTICS:   Mealy\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    r_0;\n"
    + "    r_1;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    g_0;\n"
    + "    g_1;\n"
    + "  }\n"
    + "  ASSUME {\n"
    + "    (G ((((((r_0) && (! (g_0))) ->"
    + " (X (r_0))) && (((! (r_0)) && (g_0)) -> (X (! (r_0))))) "
    + "&& (F (! ((r_0) && (g_0))))) && (((((r_1) && (! (g_1))) -> (X (r_1))) && (((! (r_1)) && "
    + "(g_1)) -> (X (! (r_1))))) && (F (! ((r_1) && (g_1)))))));\n"
    + "  }\n"
    + "  ASSERT {\n"
    + "    (((! (g_0)) && (true)) || ((true) && (! (g_1))));\n"
    + "  }\n"
    + "  GUARANTEE {\n"
    + "    (G ((r_0) -> (F (g_0))));\n"
    + "    (G ((r_1) -> (F (g_1))));\n"
    + "  }\n"
    + "}";
  
  @DataPoint
  public static final String SIMPLE_ARBITER_2 = "INFO {\n"
    + "  TITLE:       \"Simple Arbiter\"\n"
    + "  DESCRIPTION: \"Parameterized Arbiter,"
    + " where each request has to be eventually granted\"\n"
    + "  SEMANTICS:   Mealy\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    r_0;\n"
    + "    r_1;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    g_0;\n"
    + "    g_1;\n"
    + "  }\n"
    + "  ASSERT {\n"
    + "    (((! (g_0)) && (true)) || ((true) && (! (g_1))));\n"
    + "  }\n"
    + "  GUARANTEE {\n"
    + "    (G ((r_0) -> (F (g_0))));\n"
    + "    (G ((r_1) -> (F (g_1))));\n"
    + "  }\n"
    + "}";

  @DataPoint
  public static final String LOAD_COMP_2 = ""
    + "INFO {\n"
    + "  TITLE:       \"Load Balancing - Environment - 2 Clients\"\n"
    + "  DESCRIPTION: \"One of the Acacia+ Example files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    idle;\n"
    + "    request0;\n"
    + "    request1;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    grant0;\n"
    + "    grant1;\n"
    + "  }\n"
    + "  ASSUMPTIONS {\n"
    + "    G F idle;\n"
    + "    G (!(idle && !grant0 && !grant1) || X idle);\n"
    + "    G (!grant0 || X ((!request0 && !idle) U (!request0 && idle)));\n"
    + "  }\n"
    + "  INVARIANTS {\n"
    + "    !request0 || !grant1;\n"
    + "    !grant0 || !grant1;\n"
    + "    !grant1 || !grant0;\n"
    + "    !grant0 || request0;\n"
    + "    !grant1 || request1;\n"
    + "    (!grant0 && !grant1) || idle;\n"
    + "  }\n"
    + "  GUARANTEES {\n"
    + "    ! F G (request0 && !grant0);\n"
    + "    ! F G (request1 && !grant1);\n"
    + "  }\n"
    + "}";

  @DataPoint
  public static final String LILY_DEMO_01 = ""
    + "INFO {\n"
    + "  TITLE:       \"Lily Demo V1\"\n"
    + "  DESCRIPTION: \"One of the Lily demo files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    req;\n"
    + "    cancel;\n"
    + "    go;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    grant;\n"
    + "  }  \n"
    + "  INVARIANTS {\n"
    + "    req -> X (grant && X (grant && X grant));\n"
    + "    grant -> X !grant;\n"
    + "    cancel -> X (!grant U go);\n"
    + "  }\n"
    + "}";

  @DataPoint
  public static final String LILY_DEMO_02 = ""
    + "INFO {\n"
    + "  TITLE:       \"Lily Demo V2\"\n"
    + "  DESCRIPTION: \"One of the Lily demo files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    req;\n"
    + "    cancel;\n"
    + "    go;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    grant;\n"
    + "  }\n"
    + "  INVARIANTS {\n"
    + "    req -> X (grant || X (grant || X grant));\n"
    + "    grant -> X !grant;\n"
    + "    cancel -> X (!grant U go);\n"
    + "  }\n"
    + "}";

  @DataPoint
  public static final String LTL2DBA_01 = ""
    + "INFO {\n"
    + "  TITLE:       \"LTL -> DBA  -  Example 1\"\n"
    + "  DESCRIPTION: \"One of the Acacia+ example files\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    p;\n"
    + "    q;\n"
    + "    r;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    acc;\n"
    + "  }\n"
    + "  GUARANTEES {\n"
    + "    F (q && (X (p U r)))\n"
    + "      <-> G F acc;\n"
    + "  }\n"
    + "}";

  @DataPoint
  public static final String LTL2DPA_01 = ""
    + "INFO {\n"
    + "  TITLE:       \"LTL -> DPA  -  Example 1  (Source: Acacia+)\"\n"
    + "  DESCRIPTION: \"DPA A with 3 priorities: a word u is"
    + " accepted by A if the minimal priority seen infinitely often is even\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    a;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    p0;\n"
    + "    p1;\n"
    + "    p2;\n"
    + "  }\n"
    + "  INVARIANTS {\n"
    + "       ( p0 && !p1 && !p2)\n"
    + "    || (!p0 &&  p1 && !p2)\n"
    + "    || (!p0 && !p1 &&  p2);\n"
    + "  }\n"
    + "  GUARANTEES {\n"
    + "    (F G !a) <-> (G F p0 || (G F p2 && !G F p1));\n"
    + "  }\n"
    + "}";

  @DataPoint
  public static final String LTL2DPA_02 = ""
    + "INFO {\n"
    + "  TITLE:       \"LTL -> DPA  -  Example 2  (Source: Acacia+)\"\n"
    + "  DESCRIPTION: \"DPA A with 2 priorities: a word u is accepted"
    + " by A if the minimal priority seen infinitely often is even\"\n"
    + "  SEMANTICS:   Moore\n"
    + "  TARGET:      Mealy\n"
    + "}\n"
    + "MAIN {\n"
    + "  INPUTS {\n"
    + "    a;\n"
    + "    b;\n"
    + "  }\n"
    + "  OUTPUTS {\n"
    + "    p0;\n"
    + "    p1;\n"
    + "  }\n"
    + "  INVARIANTS {\n"
    + "    p0 <-> !p1;\n"
    + "  }\n"
    + "  GUARANTEES {\n"
    + "    G (a || F b) <-> G F p0;\n"
    + "  }\n"
    + "}";

  @Theory
  public void compareToReferenceSolver(String specificationString) {
    Tlsf specification = TlsfParser.parse(specificationString);
    Synth synth = new Synth(DefaultEnvironment.standard(), 10);
    assertThat(synth.apply(specification), equalTo(parityGameReference(specification)));
  }
}
