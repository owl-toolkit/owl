package owl.automaton;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Collection;
import jhoafparser.parser.generated.ParseException;
import org.junit.Test;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.factories.ValuationSetFactory;
import owl.run.DefaultEnvironment;
import owl.util.BitSets;

public class AutomatonReaderTest {

  private static final String HOA_BUCHI = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 1\n"
    + "AP: 1 \"p0\"\n"
    + "acc-name: Buchi\n"
    + "Acceptance: 1 Inf(0)\n"
    + "--BODY--\n"
    + "State: 0 {0}\n"
    + "[t] 0\n"
    + "State: 1\n"
    + "[0] 0\n"
    + "--END--\n";

  private static final String HOA_GENERALIZED_BUCHI = "HOA: v1\n"
    + "name: \"G(p0 M Fp1)\"\n"
    + "States: 1\n"
    + "Start: 0\n"
    + "AP: 2 \"p1\" \"p0\"\n"
    + "acc-name: generalized-Buchi 2\n"
    + "Acceptance: 2 Inf(0)&Inf(1)\n"
    + "properties: trans-labels explicit-labels trans-acc complete\n"
    + "properties: deterministic stutter-invariant\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0&1] 0 {1}\n"
    + "[!0&!1] 0\n"
    + "[0&1] 0 {0 1}\n"
    + "[0&!1] 0 {0}\n"
    + "--END--";

  private static final String HOA_GENERALIZED_RABIN = "HOA: v1\n"
    + "name: \"G(Fa && XFb)\"\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name:\n"
    + "generalized-Rabin 2 1 2\n"
    + "Acceptance:\n"
    + "5 (Fin(0)&Inf(1))\n"
    + "| (Fin(2)&Inf(3)&Inf(4))\n"
    + "AP: 2 \"a\" \"b\"\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[ 0 & !1] 0 {3}\n"
    + "[ 0 & 1] 0 {1 3 4}\n"
    + "[!0 & !1] 1 {0}\n"
    + "[!0 & 1] 1 {0 4}\n"
    + "State: 1\n"
    + "[ 0 & !1] 0 {3}\n"
    + "[ 0 & 1] 0 {1 3 4}\n"
    + "[!0 & !1] 1 {0}\n"
    + "[!0 & 1] 1 {0 4}\n"
    + "--END--";

  private static final String HOA_GENERIC = "HOA: v1 \n"
    + "States: 3 \n"
    + "Start: 0 \n"
    + "acc-name: xyz 1 \n"
    + "Acceptance: 2 (Fin(0) & Inf(1)) \n"
    + "AP: 2 \"a\" \"b\" \n" + "--BODY-- \n"
    + "State: 0 \"a U b\" { 0 } \n"
    + "  2  /* !a  & !b */ \n"
    + "  0  /*  a  & !b */ \n"
    + "  1  /* !a  &  b */ \n"
    + "  1  /*  a  &  b */ \n"
    + "State: 1 { 1 } \n"
    + "  1 1 1 1       /* four transitions on one line */ \n"
    + "State: 2 \"sink state\" { 0 } \n"
    + "  2 2 2 2 \n"
    + "--END--";

  private static final String HOA_INVALID = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "acc-name: parity min odd 3\n"
    + "Acceptance: 3 Fin(0) & (Inf(1) | Fin(2))\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0 & !1] 1\n";

  private static final String HOA_MISSING_ACC_NAME = "HOA: v1\n"
    + "States: 0\n"
    + "Acceptance: f\n"
    + "--BODY--\n"
    + "--END--";

  private static final String HOA_PARITY = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "AP: 2 \"p0\" \"p1\"\n"
    + "acc-name: parity min odd 3\n"
    + "Acceptance: 3 Fin(0) & (Inf(1) | Fin(2))\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0 & !1] 1 {2}\n"
    + "State: 1\n"
    + "[0 & !1] 0 {1}\n"
    + "[!0 & 1] 1\n"
    + "--END--\n";

  private static final String HOA_RABIN = "HOA: v1 \n"
    + "States: 3 \n"
    + "Start: 0 \n"
    + "acc-name: Rabin 1 \n"
    + "Acceptance: 2 (Fin(0) & Inf(1)) \n"
    + "AP: 2 \"a\" \"b\" \n"
    + "--BODY-- \n"
    + "State: 0 \"a U b\" { 0 } \n"
    + "  2  /* !a  & !b */ \n"
    + "  0  /*  a  & !b */ \n"
    + "  1  /* !a  &  b */ \n"
    + "  1  /*  a  &  b */ \n"
    + "State: 1 { 1 } \n"
    + "  1 1 1 1       /* four transitions on one line */ \n"
    + "State: 2 \"sink state\" { 0 } \n"
    + "  2 2 2 2 \n"
    + "--END--";

  private static final String HOA_SIMPLE = "HOA: v1\n"
    + "States: 2\n"
    + "Start: 0\n"
    + "AP: 1 \"p0\"\n"
    + "acc-name: all\n"
    + "Acceptance: 0 t\n"
    + "--BODY--\n"
    + "State: 0\n"
    + "[!0] 1\n"
    + "[0] 0\n"
    + "State: 1\n"
    + "[!0] 0\n"
    + "--END--\n";

  private static Int2ObjectMap<HoaState> getStates(Automaton<HoaState, ?> automaton) {
    Int2ObjectMap<HoaState> states = new Int2ObjectLinkedOpenHashMap<>(
      automaton.getStates().size());
    for (HoaState state : automaton.getStates()) {
      int stateId = state.id;
      assertThat(states.containsKey(stateId), is(false));
      states.put(stateId, state);
    }
    assertThat(states.size(), is(automaton.getStates().size()));
    return states;
  }

  @SuppressWarnings("unchecked")
  @Test
  public void readAutomatonBuchi() throws ParseException {
    Automaton<HoaState, BuchiAcceptance> automaton = AutomatonReader.readHoa(HOA_BUCHI,
      DefaultEnvironment.annotated().factorySupplier(), BuchiAcceptance.class);
    assertThat(automaton.getStates().size(), is(2));
    Int2ObjectMap<HoaState> states = getStates(automaton);
    ValuationSetFactory valuationSetFactory = automaton.getFactory();

    assertThat(automaton.getInitialState(), is(states.get(1)));

    LabelledEdge<HoaState> successorEdge = LabelledEdge.of(states.get(0),
      valuationSetFactory.of(BitSets.createBitSet(true)));
    assertThat(automaton.getLabelledEdges(states.get(1)), containsInAnyOrder(successorEdge));

    LabelledEdge<HoaState> loopEdge = LabelledEdge.of(Edge.of(states.get(0), 0),
      valuationSetFactory.universe());
    assertThat(automaton.getLabelledEdges(states.get(0)), containsInAnyOrder(loopEdge));
  }

  @Test(expected = ParseException.class)
  public void readAutomatonInvalid() throws ParseException {
    AutomatonReader.readHoaCollection(HOA_INVALID,
      DefaultEnvironment.annotated().factorySupplier());
  }

  @Test(expected = ParseException.class)
  public void readAutomatonMissingAccName() throws ParseException {
    AutomatonReader.readHoaCollection(HOA_MISSING_ACC_NAME,
      DefaultEnvironment.annotated().factorySupplier());
  }

  @Test
  public void readAutomatonParity() throws ParseException {
    Collection<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaCollection(HOA_PARITY,
      DefaultEnvironment.annotated().factorySupplier());
    Automaton<HoaState, ?> automaton = Iterables.getOnlyElement(automata);

    assertThat(automaton.getAcceptance(), instanceOf(ParityAcceptance.class));
    ParityAcceptance acceptance = (ParityAcceptance) automaton.getAcceptance();
    assertThat(acceptance.getAcceptanceSets(), is(3));
    assertThat(acceptance.getParity(), is(Parity.MIN_ODD));

    HoaState initialState = automaton.getInitialState();
    HoaState successor = automaton.getSuccessor(initialState, BitSets.createBitSet(false, false));
    assertThat(successor, notNullValue());

    Edge<HoaState> initialToSucc = automaton.getEdge(initialState,
      BitSets.createBitSet(false, false));
    assertThat(initialToSucc, notNullValue());
    assertThat(initialToSucc.acceptanceSetIterator().nextInt(), is(2));

    Edge<HoaState> succToInitial = automaton.getEdge(successor, BitSets.createBitSet(true, false));
    assertThat(succToInitial, notNullValue());
    assertThat(succToInitial.acceptanceSetIterator().nextInt(), is(1));

    Edge<HoaState> succToSucc = automaton.getEdge(successor, BitSets.createBitSet(false, true));
    assertThat(succToSucc, notNullValue());
    assertThat(succToSucc.acceptanceSetIterator().hasNext(), is(false));
  }

  @Test
  public void readAutomatonSimple() throws ParseException {
    Collection<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaCollection(HOA_SIMPLE,
      DefaultEnvironment.annotated().factorySupplier());
    assertThat(automata.size(), is(1));
    Automaton<HoaState, ?> automaton = Iterables.getOnlyElement(automata);
    assertThat(automaton.getStates().size(), is(2));
    assertThat(automaton.getAcceptance(), instanceOf(AllAcceptance.class));

    HoaState initialState = automaton.getInitialState();
    assertThat(initialState.id, is(0));

    assertThat(automaton.getSuccessor(initialState, BitSets.createBitSet(true)), is(initialState));

    HoaState successor = automaton.getSuccessor(initialState, BitSets.createBitSet(false));
    assertThat(successor, notNullValue());
    assertThat(successor.id, is(1));
    assertThat(automaton.getSuccessor(successor, BitSets.createBitSet(false)), is(initialState));
    assertThat(automaton.getSuccessor(successor, BitSets.createBitSet(true)), nullValue());
  }

  @Test
  public void testAcceptanceGeneralizedBuchi() throws ParseException {
    Collection<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaCollection(
      HOA_GENERALIZED_BUCHI, DefaultEnvironment.annotated().factorySupplier());
    Automaton<HoaState, ?> automaton = Iterables.getOnlyElement(automata);

    assertThat(automaton.getStates().size(), is(1));
    assertThat(automaton.getAcceptance(), instanceOf(GeneralizedBuchiAcceptance.class));
    GeneralizedBuchiAcceptance acceptance = (GeneralizedBuchiAcceptance) automaton.getAcceptance();
    assertThat(acceptance.getAcceptanceSets(), is(2));
  }

  @Test
  public void testAcceptanceGeneralizedRabin() throws ParseException {
    Collection<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaCollection(
      HOA_GENERALIZED_RABIN, DefaultEnvironment.annotated().factorySupplier());
    Automaton<HoaState, ?> automaton = Iterables.getOnlyElement(automata);

    assertThat(automaton.getStates().size(), is(2));
    assertThat(automaton.getAcceptance(), instanceOf(GeneralizedRabinAcceptance.class));
    GeneralizedRabinAcceptance acceptance = (GeneralizedRabinAcceptance) automaton.getAcceptance();
    assertThat(acceptance.getAcceptanceSets(), is(5));
  }

  @Test
  public void testAcceptanceGeneric() throws ParseException {
    Collection<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaCollection(
      HOA_GENERIC, DefaultEnvironment.annotated().factorySupplier());
    Automaton<HoaState, ?> automaton = Iterables.getOnlyElement(automata);

    assertThat(automaton.getStates().size(), is(3));
    assertThat(automaton.getAcceptance(), instanceOf(EmersonLeiAcceptance.class));
    EmersonLeiAcceptance acceptance = (EmersonLeiAcceptance) automaton.getAcceptance();
    assertThat(acceptance.getAcceptanceSets(), is(2));
  }

  @Test
  public void testAcceptanceRabin() throws ParseException {
    Collection<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaCollection(
      HOA_RABIN, DefaultEnvironment.annotated().factorySupplier());
    Automaton<HoaState, ?> automaton = Iterables.getOnlyElement(automata);

    assertThat(automaton.getStates().size(), is(3));
    assertThat(automaton.getAcceptance(), instanceOf(RabinAcceptance.class));
    RabinAcceptance acceptance = (RabinAcceptance) automaton.getAcceptance();
    assertThat(acceptance.getAcceptanceSets(), is(2));
  }
}