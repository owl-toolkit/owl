package owl.translations.safra;

import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.util.annotation.Tuple;

public class SafraBuilder<S> {
  private static final Logger logger = Logger.getLogger(SafraBuilder.class.getName());
  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("safra")
    .description("Translates NBA to DRA using Safra's construction")
    .parser(settings -> environment -> (input, context) ->
      new SafraBuilder<>(AutomatonUtil.cast(input, Object.class, BuchiAcceptance.class)).build())
    .build();
  private final Automaton<S, BuchiAcceptance> nba;

  SafraBuilder(Automaton<S, BuchiAcceptance> nba) {
    this.nba = nba;
  }

  Automaton<Tree<Label<S>>, RabinAcceptance> build() {
    int nbaSize = nba.size();
    int pairCount = nbaSize * 2;
    RabinAcceptance acceptance = RabinAcceptance.of(pairCount);
    Tree<Label<S>> initialState = Tree.of(Label.of(Set.copyOf(nba.initialStates()), 0));

    BiFunction<Tree<Label<S>>, BitSet, Edge<Tree<Label<S>>>> successor = (tree, valuation) -> {
      BitSet usedIndices = new BitSet(nbaSize);
      tree.forEach(node -> usedIndices.set(node.index()));
      BitSet edgeAcceptance = new BitSet(nbaSize);

      Tree<Label<S>> successorTree = tree.map((father, children) -> {
        // Successor
        Set<Edge<S>> fatherEdges = father.states().stream().flatMap(state ->
          nba.edges(state, valuation).stream()).collect(Collectors.toSet());

        if (fatherEdges.isEmpty()) {
          return Tree.of(father.with(Set.of()));
        }

        Label<S> newFather = father.with(Collections2.transform(fatherEdges, Edge::successor));
        Set<S> newChildStates = fatherEdges.stream().filter(edge -> edge.inSet(0))
          .map(Edge::successor).collect(Collectors.toUnmodifiableSet());

        int index = usedIndices.nextClearBit(0);
        usedIndices.set(index);
        return Tree.of(newFather, Collections3.concat(children,
          List.of(Tree.of(Label.of(newChildStates, index)))));
      }).map((father, children) -> {
        // Horizontal merge
        Set<S> olderStates = new HashSet<>();
        List<Tree<Label<S>>> prunedChildren = new ArrayList<>(children.size());

        for (Tree<Label<S>> child : children) {
          Label<S> prunedLabel = child.label().without(olderStates);

          if (prunedLabel.states().isEmpty()) {
            edgeAcceptance.set(acceptance.pairs().get(prunedLabel.index()).finSet());
            usedIndices.clear(prunedLabel.index());
          } else {
            // Recursive pruning of the child
            prunedChildren.add(child.map((subNode, subChildren) ->
              Tree.of(subNode.without(olderStates), subChildren)));
            olderStates.addAll(prunedLabel.states());
          }
        }

        return Tree.of(father, prunedChildren);
      }).map((father, children) -> {
        List<Tree<Label<S>>> nonEmptyChildren = children.stream().filter(
          child -> !child.label().states().isEmpty()).collect(Collectors.toUnmodifiableList());

        if (nonEmptyChildren.isEmpty()) {
          return Tree.of(father);
        }

        Set<S> childStates = nonEmptyChildren.stream().map(Tree::label).map(Label::states)
          .flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());

        // Vertical merge
        if (childStates.equals(father.states())) {
          edgeAcceptance.set(acceptance.pairs().get(father.index()).infSet());
          children.forEach(child -> child.forEach(node -> usedIndices.clear(node.index())));
          return Tree.of(father);
        }
        return Tree.of(father, nonEmptyChildren);
      });

      usedIndices.flip(0, nbaSize);
      BitSets.forEach(usedIndices, index ->
        edgeAcceptance.set(acceptance.pairs().get(index).finSet()));

      logger.log(Level.FINEST, "{0} + {1} -> {2} @ {3}",
        new Object[] {tree, valuation, successorTree, edgeAcceptance});

      return Edge.of(successorTree, edgeAcceptance);
    };

    return AutomatonFactory.create(initialState, nba.factory(), successor, acceptance);
  }

  @Tuple
  @Value.Immutable
  public abstract static class Label<S> {
    abstract Set<S> states();

    abstract int index();


    static <S> Label<S> of(Collection<S> states, int index) {
      assert index >= 0;
      return LabelTuple.create(states, index);
    }


    Label<S> without(Set<S> states) {
      return of(Sets.difference(states(), states), index());
    }

    Label<S> with(Collection<S> states) {
      return of(states, index());
    }

    @Override
    public String toString() {
      return states().stream().map(Object::toString).sorted().collect(Collectors.toSet())
        + ":" + index();
    }
  }
}