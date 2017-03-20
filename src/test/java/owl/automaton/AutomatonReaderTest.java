package owl.automaton;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static owl.collections.BitSets.toSet;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Collection;
import jhoafparser.parser.generated.ParseException;
import org.junit.Test;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.factories.ValuationSetFactory;

public class AutomatonReaderTest {
  private Int2ObjectMap<HoaState> getStates(Automaton<HoaState, ?> automaton) {
    Int2ObjectMap<HoaState> states = new Int2ObjectLinkedOpenHashMap<>(automaton.stateCount());
    for (HoaState state : automaton.getStates()) {
      int stateId = state.getId();
      assertThat(states.containsKey(stateId), is(false));
      states.put(stateId, state);
    }
    assertThat(states.size(), is(automaton.stateCount()));
    return states;
  }

  @SuppressWarnings("unchecked")
  @Test
  public void readAutomatonBuchi() throws ParseException {
    String hoaString = "HOA: v1\n"
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
    Automaton<HoaState, BuchiAcceptance> automaton =
      AutomatonReader.readHoaInput(hoaString, BuchiAcceptance.class);
    assertThat(automaton.getStates().size(), is(2));
    Int2ObjectMap<HoaState> states = getStates(automaton);
    ValuationSetFactory valuationSetFactory = automaton.getFactory();

    assertThat(automaton.getInitialState(), is(states.get(1)));

    LabelledEdge<HoaState> successorEdge = new LabelledEdge<>(Edges.create(states.get(0)),
      valuationSetFactory.createValuationSet(toSet(true)));
    assertThat(automaton.getLabelledEdges(states.get(1)), containsInAnyOrder(successorEdge));

    LabelledEdge<HoaState> loopEdge = new LabelledEdge<>(Edges.create(states.get(0), 0),
      valuationSetFactory.createUniverseValuationSet());
    assertThat(automaton.getLabelledEdges(states.get(0)), containsInAnyOrder(loopEdge));
  }

  @Test(expected = ParseException.class)
  public void readAutomatonInvalid() throws ParseException {
    String hoaString = "HOA: v1\n"
      + "States: 2\n"
      + "Start: 0\n"
      + "acc-name: parity min odd 3\n"
      + "Acceptance: 3 Fin(0) & (Inf(1) | Fin(2))\n"
      + "--BODY--\n"
      + "State: 0\n"
      + "[!0 & !1] 1\n";
    AutomatonReader.readHoaInput(hoaString);
  }

  @Test(expected = ParseException.class)
  public void readAutomatonMissingAccName() throws ParseException {
    String hoaString = "HOA: v1\n"
      + "States: 0\n"
      + "Acceptance: f\n"
      + "--BODY--\n"
      + "--END--";
    AutomatonReader.readHoaInput(hoaString);
  }

  @Test
  public void readAutomatonParity() throws ParseException {
    String hoaString = "HOA: v1\n"
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
    Collection<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaInput(hoaString);
    Automaton<HoaState, ?> automaton = automata.iterator().next();

    assertThat(automaton.getAcceptance(), instanceOf(ParityAcceptance.class));
    ParityAcceptance acceptance = (ParityAcceptance) automaton.getAcceptance();
    assertThat(acceptance.getAcceptanceSets(), is(3));
    assertThat(acceptance.getPriority(), is(Priority.ODD));

    HoaState initialState = automaton.getInitialState();
    HoaState successor = automaton.getSuccessor(initialState, toSet(false, false));
    assertThat(successor, notNullValue());

    Edge<HoaState> initialToSucc = automaton.getEdge(initialState, toSet(false, false));
    assertThat(initialToSucc, notNullValue());
    assertThat(initialToSucc.acceptanceSetIterator().nextInt(), is(2));

    Edge<HoaState> succToInitial = automaton.getEdge(successor, toSet(true, false));
    assertThat(succToInitial, notNullValue());
    assertThat(succToInitial.acceptanceSetIterator().nextInt(), is(1));

    Edge<HoaState> succToSucc = automaton.getEdge(successor, toSet(false, true));
    assertThat(succToSucc, notNullValue());
    assertThat(succToSucc.acceptanceSetIterator().hasNext(), is(false));
  }

  @Test
  public void readAutomatonSimple() throws ParseException {
    String hoaString = "HOA: v1\n"
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
    Collection<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaInput(hoaString);
    assertThat(automata.size(), is(1));
    Automaton<HoaState, ?> automaton = automata.iterator().next();
    assertThat(automaton.getStates().size(), is(2));
    assertThat(automaton.getAcceptance(), instanceOf(AllAcceptance.class));

    HoaState initialState = automaton.getInitialState();
    assertThat(initialState.getId(), is(0));

    assertThat(automaton.getSuccessor(initialState, toSet(true)), is(initialState));

    HoaState successor = automaton.getSuccessor(initialState, toSet(false));
    assertThat(successor, notNullValue());
    assertThat(successor.getId(), is(1));
    assertThat(automaton.getSuccessor(successor, toSet(false)), is(initialState));
    assertThat(automaton.getSuccessor(successor, toSet(true)), nullValue());
  }
}