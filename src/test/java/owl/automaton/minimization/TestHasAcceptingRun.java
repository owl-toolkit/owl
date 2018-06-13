package owl.automaton.minimization;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.generated.ParseException;
import org.junit.Test;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.algorithms.EmptinessCheck;
import owl.automaton.output.HoaPrinter;
import owl.run.DefaultEnvironment;
import owl.translations.nba2dpa.NBA2DPA;

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

  private static void testHasAcceptingRun(String input, boolean hasAcceptingRun,
    boolean complementHasAcceptingRun) throws ParseException {
    NBA2DPA translation = new NBA2DPA();

    var automaton = translation.apply(AutomatonReader.readHoa(input,
      DefaultEnvironment.annotated().factorySupplier(), GeneralizedBuchiAcceptance.class));
    HoaPrinter.feedTo(automaton, new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    assertThat(EmptinessCheck.isEmpty(automaton), is(!hasAcceptingRun));

    var complement = Views.complement(AutomatonUtil.cast(automaton), new Object());
    HoaPrinter.feedTo(complement, new HOAIntermediateCheckValidity(new HOAConsumerNull()));
    assertThat(EmptinessCheck.isEmpty(complement), is(!complementHasAcceptingRun));
  }
}
