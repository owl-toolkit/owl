package owl.automaton;

import com.google.common.collect.Collections2;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.collections.Collections3;

/**
 * Mixin interface for implementing an automaton by {@link Automaton#labelledEdges(Object)}.
 *
 * <p>It is impossible to implement the incompatible sister interface {@link EdgesAutomatonMixin}
 * and the compiler will reject the code, since there are conflicting defaults for
 * {@link Automaton#prefersLabelled()}.</p>
 *
 * @param <S> the type of the states of the automaton
 * @param <A> the type of the omega-acceptance condition of the automaton
 */
public interface LabelledEdgesAutomatonMixin<S, A extends OmegaAcceptance> extends Automaton<S, A> {

  @Override
  default Set<S> successors(S state) {
    return new HashSet<>(Collections2.transform(labelledEdges(state), LabelledEdge::successor));
  }

  @Override
  default Collection<Edge<S>> edges(S state) {
    return new ArrayList<>(Collections2.transform(labelledEdges(state), LabelledEdge::edge));
  }

  @Override
  default Collection<Edge<S>> edges(S state, BitSet valuation) {
    return Collections3.transformUnique(labelledEdges(state),
      x -> x.valuations.contains(valuation) ? x.edge : null);
  }

  @Override
  default boolean prefersLabelled() {
    return true;
  }
}
