package owl.automaton;

import com.google.common.collect.Iterables;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import owl.automaton.edge.Edge;

public final class TransitionUtil {
  private TransitionUtil() {}

  public static <S> Function<S, Iterable<Edge<S>>> filterEdges(
    Function<S, Iterable<Edge<S>>> edges, Predicate<Edge<S>> predicate) {
    return edges.andThen(iter -> Iterables.filter(iter, predicate::test));
  }

  public static <S> Function<S, Iterable<Edge<S>>> filterEdges(
    Function<S, Iterable<Edge<S>>> edges, Set<S> set) {
    return filterEdges(edges, edge -> set.contains(edge.getSuccessor()));
  }

  public static <S> void forEachEdgeInSet(Function<S, Iterable<Edge<S>>> successorFunction,
    Set<S> states, BiConsumer<S, Edge<S>> action) {
    states.forEach(state -> successorFunction.apply(state).forEach(edge -> {
      if (states.contains(edge.getSuccessor())) {
        action.accept(state, edge);
      }
    }));
  }
}
