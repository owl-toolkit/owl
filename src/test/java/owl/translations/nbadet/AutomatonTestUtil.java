package owl.translations.nbadet;

import java.io.StringReader;
import java.util.HashMap;
import jhoafparser.parser.generated.ParseException;

import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.hoa.HoaReader;
import owl.run.Environment;

public final class AutomatonTestUtil {

  // make PMD silent.
  private AutomatonTestUtil() {}

  /** Read from HOA string and transform such that
   * state object corresponds to HOA state number in string. */
  public static <A extends OmegaAcceptance> Automaton<Integer, A> autFromString(
      String hoa, Class<A> acc) throws ParseException {
    final var supplier = Environment.annotated().factorySupplier();
    final var parsed = HoaReader.read(new StringReader(hoa), supplier::getValuationSetFactory);
    final var aut = OmegaAcceptanceCast.cast(parsed, acc);

    var stateMap = new HashMap<HoaReader.HoaState, Integer>();
    aut.states().forEach(st -> stateMap.put(st, Integer.valueOf(st.toString())));
    return Views.quotientAutomaton(aut, stateMap::get);
  }

}
