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
import owl.translations.dpa2safety.DPA2Safety.Counter;

public class DPA2Safety<S> implements BiFunction<Automaton<S, ParityAcceptance>, Integer,
  Automaton<Counter<S>, AllAcceptance>> {

  @Override
  public Automaton<Counter<S>, AllAcceptance> apply(Automaton<S, ParityAcceptance> automaton,
    Integer bound) {
    int d;

    if (automaton.getAcceptance().getAcceptanceSets() % 2 == 0) {
      d = automaton.getAcceptance().getAcceptanceSets() + 1;
    } else {
      d = automaton.getAcceptance().getAcceptanceSets();
    }

    Counter<S> initialState = new Counter<>(automaton.getInitialState(), d / 2 + 1);

    IntPredicate isAcceptingColour = x -> automaton.getAcceptance().isAccepting(x);

    BiFunction<Counter<S>, BitSet, Edge<Counter<S>>> successor = (x, y) -> {
      Edge<S> edge = automaton.getEdge(x.state, y);

      if (edge == null) {
        return null;
      }

      int[] counters = x.counters.toArray();
      int colour = edge.smallestAcceptanceSet();
      int i = (colour == Integer.MAX_VALUE ? d : colour) / 2;

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

      return Edge.of(new Counter<>(edge.getSuccessor(), counters));
    };

    return AutomatonFactory.create(initialState, automaton.getFactory(), successor,
      AllAcceptance.INSTANCE);
  }

  static final class Counter<X> {
    // TODO Tuple style
    final X state;
    final ImmutableIntArray counters;

    Counter(X state, int length) {
      this(state, new int[length]);
    }

    Counter(X state, int[] counters) {
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
      final Counter<?> counters1 = (Counter<?>) o;
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