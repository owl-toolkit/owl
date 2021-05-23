/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.automaton.acceptance.transformer;

import static owl.automaton.acceptance.ParityAcceptance.Parity.MIN_EVEN;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Table;
import com.google.common.primitives.ImmutableIntArray;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.cli.Options;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.SuccessorFunction;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.acceptance.transformer.AcceptanceTransformation.ExtendedState;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.bdd.MtBdd;
import owl.collections.Collections3;
import owl.collections.ImmutableBitSet;
import owl.collections.Pair;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.sat.Solver;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class ZielonkaTreeTransformations {

  /**
   * Instantiation of the module. We give the name, a short description, and a function to be called
   * for construction of an instance of the module. {@link OwlModule.AutomatonTransformer} checks
   * the input passed to the instance of the module and does the necessary transformation to obtain
   * a generalized B&uuml;chi automaton if possible. If this is not possible an exception is thrown.
   *
   * <p> This object has to be imported by the {@link owl.run.modules.OwlModuleRegistry} in order to
   * be available on the Owl CLI interface. </p>
   */
  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "to-parity",
    "Convert any automaton into an equivalent parity automaton.",
    new Options()
      .addOption(null, "X-lookahead", true,
        "Only used for testing internal implementation."),
    (commandLine, environment) -> {
      OptionalInt lookahead;

      if (commandLine.hasOption("X-lookahead")) {
        String value = commandLine.getOptionValue("X-lookahead");
        int intValue = Integer.parseInt(value);

        if (intValue < -1) {
          throw new IllegalArgumentException();
        }

        lookahead = intValue == -1 ? OptionalInt.empty() : OptionalInt.of(intValue);
      } else {
        lookahead = OptionalInt.empty();
      }

      return OwlModule.AutomatonTransformer.of(a -> transform(a, lookahead));
    });

  private ZielonkaTreeTransformations() {}

  /**
   * Entry-point for standalone CLI tool. This class has be registered in build.gradle in the
   * "Startup Scripts" section in order to get the necessary scripts for CLI invocation.
   *
   * @param args
   *   the arguments
   *
   * @throws IOException
   *   the exception
   */
  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.HOA_INPUT_MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  public static <S> Automaton<ExtendedState<S, Path>, ParityAcceptance>
    transform(Automaton<S, ?> automaton) {

    return transform(automaton, OptionalInt.empty());
  }

  // TODO: this is an internal, testing only method.
  private static <S> Automaton<ExtendedState<S, Path>, ParityAcceptance>
    transform(Automaton<S, ?> automaton, OptionalInt lookahead) {

    var cachedAutomaton = AbstractMemoizingAutomaton.memoizingAutomaton(automaton);
    var sccDecomposition = SccDecomposition.of(cachedAutomaton);

    return transform(
      cachedAutomaton,
      lookahead,
      (x, y) -> sccDecomposition.index(x) != sccDecomposition.index(y),
      x -> cachedAutomaton.acceptance().booleanExpression(),
      x -> PropositionalFormula.trueConstant());
  }

  // TODO: it might be the case that depending on the iteration order the sizes are different.
  public static <S> Automaton<ExtendedState<S, Path>, ParityAcceptance> transform(
    Automaton<S, ?> uncachedAutomaton,
    OptionalInt lookahead,
    BiPredicate<S, S> sccChange,
    Function<S, ? extends PropositionalFormula<Integer>> localAlpha,
    Function<S, ? extends PropositionalFormula<Integer>> localBeta) {

    // Pass automaton through caching mechanism to guarantee fast retrieval after first computation.
    var automaton = AbstractMemoizingAutomaton.memoizingAutomaton(uncachedAutomaton);
    // Fixed configuration flag.
    boolean stutter = true;

    class ZielonkaForest {

      // The forest maps a state to an AlternatingCycleDecomposition, if a complete SCC has been
      // identified within the state exploration budget, and to an ConditionalZielonkaTree,
      // otherwise.
      private final Map<S, ZielonkaTree> zielonkaTrees = new HashMap<>();
      private final Set<S> acceptingZielonkaTreeRoots = new HashSet<>();

      // Alternating Cycle Decomposition
      private final List<AlternatingCycleDecomposition<S>>
        alternatingCycleDecompositions = new ArrayList<>();

      // Conditional Zielonka Trees
      private final Table<
        PropositionalFormula<Integer>, PropositionalFormula<Integer>, ConditionalZielonkaTree>
        cachedConditionalZielonkaTrees = HashBasedTable.create();

      private ZielonkaTree zielonkaTree(S state, Set<S> exploredStates) {
        ZielonkaTree zielonkaTree = zielonkaTrees.get(state);

        // We have seen this state before and selected a Zielonka Tree variant.
        if (zielonkaTree != null) {
          return zielonkaTree;
        }

        Set<S> boundedSccExploration = boundedSccExploration(state, exploredStates);
        assert Collections.disjoint(zielonkaTrees.keySet(), boundedSccExploration);

        // The bounded search failed to identify a complete SCC. The forest falls back to a
        // conditional Zielonka tree for state.
        if (boundedSccExploration.isEmpty()) {
          var alpha = localAlpha.apply(state);
          var beta = localBeta.apply(state);
          var conditionalZielonkaTree = cachedConditionalZielonkaTrees.get(alpha, beta);

          if (conditionalZielonkaTree == null) {
            conditionalZielonkaTree = ConditionalZielonkaTree.of(alpha, beta);
            cachedConditionalZielonkaTrees.put(alpha, beta, conditionalZielonkaTree);
          }

          zielonkaTrees.put(state, conditionalZielonkaTree);

          // Is the root of the tree accepting?
          if (alpha.evaluate(conditionalZielonkaTree.colours())) {
            acceptingZielonkaTreeRoots.add(state);
          }

          return conditionalZielonkaTree;
        }

        // A complete over-approximation of an SCC detected. The forest now uses ACD.
        alternatingCycleDecompositions.addAll(
          AlternatingCycleDecomposition.of(automaton, boundedSccExploration));

        for (S exploredState : boundedSccExploration) {
          int index = AlternatingCycleDecomposition
            .find(alternatingCycleDecompositions, exploredState);

          // The state is transient, we fake an alternating cycle decomposition.
          if (index == -1) {
            zielonkaTrees.put(exploredState, AlternatingCycleDecomposition.of(
              PropositionalFormula.trueConstant(),
              ImmutableBitSet.of(),
              Map.of(exploredState, Set.of())));
          } else {
            var alternatingCycleDecomposition
              = alternatingCycleDecompositions.get(index);
            zielonkaTrees.put(exploredState, alternatingCycleDecomposition);

            // Is the root of the tree accepting?
            if (automaton.acceptance().booleanExpression()
              .evaluate(alternatingCycleDecomposition.colours())) {
              acceptingZielonkaTreeRoots.add(exploredState);
            }
          }
        }

        return zielonkaTrees.get(state);
      }

      // Returns an empty set if budget was exceeded and the search failed.
      private Set<S> boundedSccExploration(S initialState, Set<S> exploredStates) {
        Deque<S> workList = new ArrayDeque<>();
        workList.add(initialState);
        Set<S> visitedStates = new HashSet<>();
        visitedStates.add(initialState);
        exploredStates.add(initialState);

        int budget = lookahead.orElse(Integer.MAX_VALUE);

        while (!workList.isEmpty()) {
          // We looked at too many states and exceeded our budget.
          if (exploredStates.size() > budget) {
            return Set.of();
          }

          S state = workList.remove();

          // The status of the current state is unknown.
          assert !zielonkaTrees.containsKey(state);

          for (S successor : automaton.successors(state)) {
            // We are guaranteed that the successor is in a different SCC.
            if (sccChange.test(state, successor)) {
              continue;
            }

            ZielonkaTree zielonkaTree = zielonkaTrees.get(successor);

            // We only select ACD if the SCC is fully explored and thus there can be no edge back
            // to state.
            if (zielonkaTree instanceof AlternatingCycleDecomposition) {
              continue;
            }

            // The successor is in an SCC in which the given exploration budget did not suffice.
            // thus trying again is futile.
            if (zielonkaTree instanceof ConditionalZielonkaTree) {
              return Set.of();
            }

            assert zielonkaTree == null;

            if (visitedStates.add(successor)) {
              exploredStates.add(successor);
              workList.add(successor);
            }
          }
        }

        // We fully explored an over-approximation of the SCC. We now can hand-over
        // to a fine-grained analysis.
        assert !visitedStates.isEmpty();
        return visitedStates;
      }

      private ExtendedState<S, Path> initialState(S initialState) {
        var stateTree = zielonkaTree(initialState, new HashSet<>());

        @SuppressWarnings("unchecked")
        var initialPath = stateTree instanceof AlternatingCycleDecomposition
          ? initialState(initialState, (AlternatingCycleDecomposition<S>) stateTree)
          : initialState(initialState, (ConditionalZielonkaTree) stateTree);

        return ExtendedState.of(initialState, initialPath);
      }

      private Path initialState(
        S initialState, AlternatingCycleDecomposition<S> acd) {

        assert acd.edges().containsKey(initialState);

        int acdIndex = alternatingCycleDecompositions.indexOf(acd);

        // Initial state is in a transient SCC.
        if (acdIndex == -1) {
          return Path.of();
        }

        var initialPathBuilder = ImmutableIntArray.builder(acd.height() + 2);
        initialPathBuilder.add(acdIndex);
        acd.leftMostLeaf(initialPathBuilder, initialState);
        return Path.of(initialPathBuilder.build());
      }

      @SuppressWarnings("unused") // Keep signature regular.
      private Path initialState(
        S initialState, ConditionalZielonkaTree root) {

        var initialPathBuilder = ImmutableIntArray.builder(root.height() + 1);
        root.leftMostLeaf(initialPathBuilder);
        return Path.of(initialPathBuilder.build());
      }

      private Edge<ExtendedState<S, Path>> edge(
        ExtendedState<S, Path> state, Edge<S> edge, Set<S> exploredStates) {

        var stateTree = zielonkaTree(state.state(), exploredStates);
        var successorTree = zielonkaTree(edge.successor(), exploredStates);

        var path = stateTree.equals(successorTree)
          ? state.extension()
          : Path.of();

        @SuppressWarnings("unchecked")
        var pathEdge = successorTree instanceof AlternatingCycleDecomposition
          ? edge(edge, path, (AlternatingCycleDecomposition<S>) successorTree)
          : edge(edge, path, (ConditionalZielonkaTree) successorTree);

        if (successorTree instanceof ConditionalZielonkaTree) {
          assert ((ConditionalZielonkaTree) successorTree).subtree(pathEdge.successor()) != null
            : "dangling path";
        }

        return pathEdge.mapSuccessor(x -> ExtendedState.of(edge.successor(), x));
      }

      private Edge<Path> edge(
        Edge<S> edge, Path path, AlternatingCycleDecomposition<S> acd) {

        S successor = edge.successor();
        assert acd.edges().containsKey(successor);

        int acdIndex = alternatingCycleDecompositions.indexOf(acd);

        // Successor is in a transient SCC.
        if (acdIndex == -1) {
          return Edge.of(Path.of());
        }

        var successorPathBuilder = ImmutableIntArray.builder(acd.height() + 2);
        successorPathBuilder.add(acdIndex);

        // The run switched to a new SCC.
        if (path.indices().isEmpty()) {
          acd.leftMostLeaf(successorPathBuilder, successor);
          return Edge.of(Path.of(successorPathBuilder.build()));
        }

        // The run stayed in the same SCC.
        assert path.indices().get(0) == acdIndex;

        // Find the anchor.
        AlternatingCycleDecomposition<S> anchor = acd;
        int anchorLevel = 1;

        // i = 0 is the top-level index.
        for (int i = 1, s = path.indices().length(); i < s; i++) {
          int nextAnchorIndex = path.indices().get(i);
          var nextAnchor = anchor.children().get(nextAnchorIndex);

          if (!nextAnchor.edges().containsKey(successor)
            || !nextAnchor.colours().containsAll(edge.colours())) {
            break;
          }

          anchor = nextAnchor;
          anchorLevel += 1;
          successorPathBuilder.add(nextAnchorIndex);
        }

        // Find the next-node, but only if there are states left.
        if (anchor.children().stream().anyMatch(x -> x.edges().containsKey(successor))) {
          int nextNodeId = path.indices().length() == anchorLevel
            ? -1
            : path.indices().get(anchorLevel);

          AlternatingCycleDecomposition<S> nextNode;

          do {
            nextNodeId = (nextNodeId + 1) % anchor.children().size();
            nextNode = anchor.children().get(nextNodeId);
          } while (!nextNode.edges().containsKey(successor));

          successorPathBuilder.add(nextNodeId);
          // extend successor path to left most leaf.
          nextNode.leftMostLeaf(successorPathBuilder, successor);
        }

        // min-even parity.
        return Edge.of(
          Path.of(successorPathBuilder.build()),
          anchorLevel + (acceptingZielonkaTreeRoots.contains(successor) ? -1 : 0));
      }

      private Edge<Path> edge(
        Edge<S> edge, Path path, ConditionalZielonkaTree root) {

        ImmutableBitSet colours = edge.colours().intersection(root.colours());

        int anchorLevel = 0;
        var node = root;
        var successorPathBuilder = ImmutableIntArray.builder(root.height() + 1);
        Path successorPath = null;

        var indices = path.indices();

        for (int i = 0, s = indices.length(); i < s; i++) {
          int edgeIndex = indices.get(i);
          assert !node.children().isEmpty() : String.format("root: %s, path: %s", root, path);
          var child = node.children().get(edgeIndex);

          if (child.colours().containsAll(colours)) {
            // follow current path
            node = child;
            successorPathBuilder.add(edgeIndex);
            anchorLevel += 1;
          } else {
            // found anchor node, select next child
            int nextEdge = edgeIndex;
            do {
              nextEdge = (nextEdge + 1) % node.children().size();
              child = node.children().get(nextEdge);
            } while (
              stutter && nextEdge != edgeIndex
                && !child.colours().containsAll(colours)
            );

            // We did a full turn around, reset to a default value.
            if (stutter && nextEdge == edgeIndex) {
              child = node.children().get(0);
              nextEdge = 0;
            }

            successorPathBuilder.add(nextEdge);
            // extend path to left most leaf.
            child.leftMostLeaf(successorPathBuilder);
            successorPath = Path.of(successorPathBuilder.build());

            // We did not fully cycle around.
            if (child.colours().containsAll(colours)) {
              int colour = acceptingZielonkaTreeRoots.contains(edge.successor())
                ? anchorLevel
                : anchorLevel + 1;

              // Recursively descend, but override acceptance mark.
              return edge(edge.withAcceptance(colours), successorPath, root)
                .withAcceptance(colour);
            }

            break;
          }
        }

        if (successorPath == null) {
          successorPath = Path.of(successorPathBuilder.build());
          var successorNode = root.subtree(successorPath);

          // We have not yet reached a leaf.
          if (!successorNode.children().isEmpty()) {
            successorPathBuilder = ImmutableIntArray.builder()
              .addAll(successorPath.indices());
            successorNode.leftMostLeaf(successorPathBuilder);
            successorPath = Path.of(successorPathBuilder.build());
          }
        }

        // min-even parity
        return Edge.of(
          successorPath,
          acceptingZielonkaTreeRoots.contains(edge.successor()) ? anchorLevel : anchorLevel + 1);
      }
    }

    var forest = new ZielonkaForest();

    var initialStates = automaton.initialStates()
      .stream()
      .map(forest::initialState)
      .collect(Collectors.toUnmodifiableSet());

    // Each level of a Zielonka tree removes at least one acceptance set. We add +1 to allow for
    // shifting the coulours depending on acceptance of the Zielonka root.
    var acceptance = new ParityAcceptance(automaton.acceptance().acceptanceSets() + 2, MIN_EVEN);

    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
      automaton.atomicPropositions(), automaton.factory(), initialStates, acceptance) {

      private final Set<S> exploredStates = new HashSet<>();

      @Override
      protected MtBdd<Edge<ExtendedState<S, Path>>> edgeTreeImpl(ExtendedState<S, Path> state) {
        exploredStates.add(state.state());

        Set<S> localExploredStates = new HashSet<>();

        return automaton
          .edgeTree(state.state())
          .map(edges -> Collections3.transformSet(edges,
            edge -> forest.edge(state, edge, localExploredStates)));
      }

      @Override
      public boolean edgeTreePrecomputed(ExtendedState<S, Path> state) {
        return exploredStates.contains(state.state());
      }
    };
  }

  public interface ZielonkaTree {

    ImmutableBitSet colours();

    List<? extends ZielonkaTree> children();

    int height();

  }

  @SuppressWarnings("PMD.OverrideBothEqualsAndHashcode")
  @AutoValue
  public abstract static class AlternatingCycleDecomposition<S> implements ZielonkaTree {

    @Override
    public abstract ImmutableBitSet colours();

    public abstract Map<S, Set<Edge<S>>> edges();

    @Override
    public abstract List<AlternatingCycleDecomposition<S>> children();

    @Override
    public abstract int height();

    public static <S> List<AlternatingCycleDecomposition<S>> of(
      Automaton<S, ?> automaton) {
      return of(automaton, automaton.states());
    }

    public static <S> List<AlternatingCycleDecomposition<S>> of(
      Automaton<S, ?> automaton, Set<S> restrictedStates) {

      List<AlternatingCycleDecomposition<S>> acd = new ArrayList<>();
      SccDecomposition<S> sccDecomposition = SccDecomposition.of(
        restrictedStates, SuccessorFunction.filter(automaton, restrictedStates));

      for (Set<S> scc : sccDecomposition.sccsWithoutTransient()) {
        Map<S, Set<Edge<S>>> sccEdges = new HashMap<>(scc.size());

        for (S state : scc) {
          sccEdges.put(state, automaton.edges(state).stream()
            .filter(edge -> scc.contains(edge.successor()))
            .collect(Collectors.toUnmodifiableSet()));
        }

        sccEdges = Map.copyOf(sccEdges);

        var coloursOfChildScc = sccEdges.values().stream()
          .flatMap(x -> x.stream().map(Edge::colours))
          .reduce(ImmutableBitSet::union)
          .orElse(ImmutableBitSet.of());

        acd.add(of(automaton.acceptance().booleanExpression(), coloursOfChildScc, sccEdges));
      }

      return acd;
    }

    public static <S> AlternatingCycleDecomposition<S> of(
      PropositionalFormula<Integer> alpha,
      ImmutableBitSet colours,
      Map<S, Set<Edge<S>>> edges) {

      List<AlternatingCycleDecomposition<S>> childrenBuilder = new ArrayList<>();

      for (Pair<ImmutableBitSet, Map<S, Set<Edge<S>>>> x : childrenOf(alpha, colours, edges)) {
        childrenBuilder.add(of(alpha, x.fst(), x.snd()));
      }

      var children = List.copyOf(childrenBuilder);

      return new AutoValue_ZielonkaTreeTransformations_AlternatingCycleDecomposition<>(
        colours, edges, children, height(children));
    }

    private static <S> int height(List<AlternatingCycleDecomposition<S>> children) {
      int height = 0;

      for (var child : children) {
        height = Math.max(height, child.height() + 1);
      }

      return height;
    }

    private static <S> List<Pair<ImmutableBitSet, Map<S, Set<Edge<S>>>>> childrenOf(
      PropositionalFormula<Integer> alpha,
      ImmutableBitSet colours,
      Map<S, Set<Edge<S>>> edges) {

      assert isClosed(edges);

      // Invert acceptance condition (alpha) in order to obtain alternation in tree.
      // Sort colour sets lexicographically. This ensures that we always compute
      // the same Zielonka tree for a given acceptance condition.
      var maximalModels = Solver.maximalModels(
        PropositionalFormula.Conjunction
          .of(alpha.evaluate(colours) ? PropositionalFormula.Negation.of(alpha) : alpha), colours)
        .stream()
        .map(ImmutableBitSet::copyOf)
        .sorted()
        .toArray(ImmutableBitSet[]::new);

      var children = new ArrayList<Pair<ImmutableBitSet, Map<S, Set<Edge<S>>>>>();

      for (ImmutableBitSet childColours : maximalModels) {
        SuccessorFunction<S> successorFunction = state -> edges.get(state).stream()
          .filter(edge -> childColours.containsAll(edge.colours()))
          .map(Edge::successor)
          .collect(Collectors.toSet());

        for (Set<S> childScc
          : SccDecomposition.of(edges.keySet(), successorFunction).sccsWithoutTransient()) {
          Map<S, Set<Edge<S>>> childEdges = new HashMap<>(edges.size());

          for (S childSccState : childScc) {
            var newFilteredEdges = new ArrayList<>(edges.get(childSccState).size());

            for (Edge<S> edge : edges.get(childSccState)) {
              if (childScc.contains(edge.successor()) && childColours.containsAll(edge.colours())) {
                newFilteredEdges.add(edge);
              }
            }

            assert !newFilteredEdges.isEmpty();
            @SuppressWarnings({"unchecked", "unused"})
            var unused
              = childEdges.put(childSccState, Set.of(newFilteredEdges.toArray(Edge[]::new)));
          }

          childEdges = Map.copyOf(childEdges);
          assert isClosed(childEdges);

          var coloursOfChildScc = childEdges.values().stream()
            .flatMap(x -> x.stream().map(Edge::colours))
            .reduce(ImmutableBitSet::union)
            .orElse(ImmutableBitSet.of());

          if (alpha.evaluate(coloursOfChildScc) == alpha.evaluate(childColours)) {
            children.add(Pair.of(coloursOfChildScc, Map.copyOf(childEdges)));
          } else {
            children.addAll(childrenOf(alpha, coloursOfChildScc, childEdges));
          }
        }
      }

      return Collections3.maximalElements(children,
        (pair1, pair2) -> isSubsetEq(pair1.snd(), pair2.snd()));
    }

    private static <S> boolean isSubsetEq(
      Map<S, Set<Edge<S>>> edge1, Map<S, Set<Edge<S>>> edge2) {

      for (Map.Entry<S, Set<Edge<S>>> entry : edge1.entrySet()) {
        var value2 = edge2.get(entry.getKey());

        if (value2 == null || !value2.containsAll(entry.getValue())) {
          return false;
        }
      }

      return true;
    }

    public AlternatingCycleDecomposition<S> restriction(S state) {
      var qChildren = new ArrayList<>(children());
      qChildren.removeIf(x -> !x.edges().containsKey(state));
      qChildren.replaceAll(x -> x.restriction(state));
      return new AutoValue_ZielonkaTreeTransformations_AlternatingCycleDecomposition<>(
        colours(), edges(), List.copyOf(qChildren), height(qChildren));
    }

    private static <S> boolean isClosed(Map<S, ? extends Collection<Edge<S>>> edges) {
      return edges.values().stream()
        .flatMap(x -> x.stream().map(Edge::successor))
        .allMatch(edges::containsKey);
    }

    private static <S> int find(List<AlternatingCycleDecomposition<S>> acd, S state) {
      int index = -1;

      for (int i = 0, s = acd.size(); i < s; i++) {
        if (acd.get(i).edges().containsKey(state)) {
          assert index == -1;
          index = i;
          // keep on searching for assert.
        }
      }

      return index;
    }

    private void leftMostLeaf(ImmutableIntArray.Builder builder, S state) {
      for (int i = 0, s = children().size(); i < s; i++) {
        var child = children().get(i);

        if (child.edges().containsKey(state)) {
          builder.add(i);
          child.leftMostLeaf(builder, state);
          break;
        }
      }
    }

    @Memoized
    @Override
    public abstract int hashCode();
  }

  @SuppressWarnings("PMD.OverrideBothEqualsAndHashcode")
  @AutoValue
  public abstract static class ConditionalZielonkaTree implements ZielonkaTree {

    private static final Interner<ConditionalZielonkaTree> INTERNER = Interners.newWeakInterner();

    @Override
    public abstract ImmutableBitSet colours();

    @Override
    public abstract List<ConditionalZielonkaTree> children();

    @Override
    public abstract int height();

    public static ConditionalZielonkaTree of(
      PropositionalFormula<Integer> alpha,
      PropositionalFormula<Integer> beta) {

      return of(ImmutableBitSet.copyOf(alpha.variables()), alpha, beta, new HashMap<>());
    }

    private static ConditionalZielonkaTree of(
      ImmutableBitSet colours,
      PropositionalFormula<Integer> alpha,
      PropositionalFormula<Integer> beta,
      Map<ImmutableBitSet, ConditionalZielonkaTree> cache) {

      var zielonkaTree = cache.get(colours);

      if (zielonkaTree != null) {
        return zielonkaTree;
      }

      // Invert acceptance condition (alpha) in order to obtain alternation in tree.
      // Sort colour sets lexicographically. This ensures that we always compute
      // the same Zielonka tree for a given acceptance condition.
      var maximalModels = Solver.maximalModels(
        PropositionalFormula.Conjunction
          .of(alpha.evaluate(colours) ? PropositionalFormula.Negation.of(alpha) : alpha, beta),
        colours)
        .stream()
        .map(ImmutableBitSet::copyOf)
        .sorted()
        .toArray(ImmutableBitSet[]::new);

      var children = new ArrayList<ConditionalZielonkaTree>();
      int height = 0;

      for (ImmutableBitSet childColours : maximalModels) {
        var child = of(childColours, alpha, beta, cache);
        height = Math.max(height, child.height() + 1);
        children.add(child);
      }

      zielonkaTree = INTERNER.intern(
        new AutoValue_ZielonkaTreeTransformations_ConditionalZielonkaTree(
          colours, List.copyOf(children), height));

      cache.put(colours, zielonkaTree);
      return zielonkaTree;
    }

    public ConditionalZielonkaTree subtree(Path path) {
      return subtree(path.indices().asList());
    }

    public ConditionalZielonkaTree subtree(List<Integer> path) {
      var subtree = this;

      for (Integer i : path) {
        subtree = subtree.children().get(i);
      }

      return subtree;
    }

    private void leftMostLeaf(ImmutableIntArray.Builder builder) {
      if (!children().isEmpty()) {
        builder.add(0);
        children().get(0).leftMostLeaf(builder);
      }
    }

    @Memoized
    @Override
    public abstract int hashCode();
  }

  @AutoValue
  public abstract static class Path {

    private static final Interner<Path> INTERNER = Interners.newWeakInterner();

    public abstract ImmutableIntArray indices();

    public static Path of() {
      return of(ImmutableIntArray.of());
    }

    public static Path of(ImmutableIntArray indices) {
      return INTERNER.intern(new AutoValue_ZielonkaTreeTransformations_Path(indices.trimmed()));
    }
  }
}
