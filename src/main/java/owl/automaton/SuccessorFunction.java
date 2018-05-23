package owl.automaton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import owl.automaton.edge.Edge;

@FunctionalInterface
public interface SuccessorFunction<S> extends Function<S, Collection<S>> {

  @Override
  default Collection<S> apply(S s) {
    return successors(s);
  }

  /**
   * Returns all successors of the specified {@code state}.
   *
   * @param state
   *     The starting state of the transition.
   *
   * @return The successor collection.
   *
   * @throws IllegalArgumentException
   *     If the transition function is not defined for {@code state}
   */
  Collection<S> successors(S state);

  static <S> SuccessorFunction<S> filter(Automaton<S, ?> automaton,
    Set<S> states) {
    return filter(automaton, states, e -> true);
  }

  static <S> SuccessorFunction<S> filter(Automaton<S, ?> automaton,
    Set<S> states, Predicate<Edge<S>> edgeFilter) {
    return state -> {
      if (!states.contains(state)) {
        return List.of();
      }

      List<S> successors = new ArrayList<>();

      automaton.forEachEdge(state, edge -> {
        if (edgeFilter.test(edge) && states.contains(edge.successor())) {
          successors.add(edge.successor());
        }
      });

      return successors;
    };
  }
}
