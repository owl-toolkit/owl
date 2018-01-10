package owl.translations.dpa2safety;

import com.google.common.primitives.ImmutableIntArray;
import java.util.BitSet;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.IntPredicate;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.translations.dpa2safety.DPA2Safety.Counters;

public class DPA2Safety<S> implements BiFunction<Automaton<S, ParityAcceptance>, Integer,
  Automaton<Counters<S>, AllAcceptance>> {

  @Override
  public Automaton<Counters<S>, AllAcceptance> apply(Automaton<S, ParityAcceptance> automaton,
    Integer bound) {
    int d;

    if (automaton.getAcceptance().getAcceptanceSets() % 2 == 0) {
      d = automaton.getAcceptance().getAcceptanceSets() + 1;
    } else {
      d = automaton.getAcceptance().getAcceptanceSets();
    }

    Counters<S> initialState = new Counters<>(automaton.getInitialState(), d / 2 + 1);

    IntPredicate isAcceptingColour = x -> automaton.getAcceptance().isAccepting(x);

    BiFunction<Counters<S>, BitSet, Edge<Counters<S>>> successor = (x, y) -> {
      Edge<S> edge = automaton.getEdge(x.state, y);

      if (edge == null) {
        return null;
      }

      int[] counters = x.counters.toArray();
      int colour = edge.smallestAcceptanceSet();
      int i = (colour != Integer.MAX_VALUE ? colour : d) / 2;

      if (isAcceptingColour.test(colour)) {
        // Reset
        for (i++; i < counters.length; i++) {
          counters[i] = 0;
        }
      } else {
        // Increment
        counters[i]++;

        if (x.counters.get(i) == bound) {
          return null;
        }
      }

      return Edge.of(new Counters<>(edge.getSuccessor(), counters));
    };

    return AutomatonFactory.createStreamingAutomaton(AllAcceptance.INSTANCE, initialState,
      automaton.getFactory(), successor);
  }

  static final class Counters<X> {
    final X state;
    final ImmutableIntArray counters;

    Counters(X state, int length) {
      this(state, new int[length]);
    }

    Counters(X state, int[] counters) {
      this.state = state;
      this.counters = ImmutableIntArray.copyOf(counters);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Counters<?> counters1 = (Counters<?>) o;
      return Objects.equals(state, counters1.state) && Objects.equals(counters, counters1.counters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(state, counters);
    }

    @Override
    public String toString() {
      return "Counters{" + "state=" + state + ", counters=" + counters + '}';
    }
  }
}