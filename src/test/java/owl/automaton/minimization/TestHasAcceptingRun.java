package owl.automaton.minimization;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.EnumSet;
import java.util.Set;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.generated.ParseException;
import org.hamcrest.Matchers;
import org.junit.Test;
import owl.automaton.Automaton;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithms.EmptinessCheck;
import owl.automaton.transformations.ParityUtil;
import owl.translations.Optimisation;
import owl.translations.ldba2dpa.RankingState;
import owl.translations.nba2dpa.NBA2DPAFunction;
import owl.translations.nba2ldba.BreakpointState;
import owl.util.TestEnvironment;

public class TestHasAcceptingRun {

  private static final String INPUT1 = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 2 \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 0 \n"
    + "[t] 0\n"
    + "[!0] 1 {0}"
    + "State: 1\n"
    + "[!1] 0 \n"
    + "[!0 & !1] 1 {0}"
    + "--END--";

  private static final String INPUT2 = "HOA: v1\n"
    + "States: 4\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 2 \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 0 \n"
    + "[t] 1 {0}\n"
    + "[0] 2 {0}\n"
    + "State: 2 \n"
    + "[0] 2 {0}\n"
    + "State: 3 \n"
    + "[t] 3 {0}\n"
    + "State: 1 \n"
    + "[1] 3 {0}\n"
    + "--END--";

  private static final String INPUT3 = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "properties: trans-acc trans-label \n"
    + "AP: 1 \"a\" \n"
    + "--BODY--\n"
    + "State: 0 \n"
    + "[t] 1 \n"
    + "State: 1 \n"
    + "[t] 0 \n"
    + "--END--";

  @Test
  public void testHasAcceptingRun() throws ParseException {
    testHasAcceptingRun(INPUT1, true, true);
  }

  @Test
  public void testHasAcceptingRun2() throws ParseException {
    testHasAcceptingRun(INPUT2, true, true);
  }

  @Test
  public void testHasAcceptingRun3() throws ParseException {
    testHasAcceptingRun(INPUT3, false, true);
  }

  private void testHasAcceptingRun(String input, boolean hasAcceptingRun,
    boolean complementHasAcceptingRun) throws ParseException {
    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    NBA2DPAFunction<HoaState> translation = new NBA2DPAFunction<>();

    Automaton<HoaState, GeneralizedBuchiAcceptance> automaton = AutomatonReader.readHoa(input,
      TestEnvironment.get().factorySupplier(), GeneralizedBuchiAcceptance.class);

    automaton.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    MutableAutomaton<RankingState<Set<HoaState>, BreakpointState<HoaState>>, ParityAcceptance>
      result = translation.apply(automaton);
    result.toHoa(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    assertThat(EmptinessCheck.isEmpty(result), Matchers.is(!hasAcceptingRun));
    ParityUtil.complement(result, RankingState::createSink);
    assertThat(EmptinessCheck.isEmpty(result), Matchers.is(!complementHasAcceptingRun));
  }
}
