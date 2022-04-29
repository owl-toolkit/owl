/*
 * Copyright (C) 2021, 2022  (Salomon Sickert)
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
import static owl.logic.propositional.PropositionalFormula.trueConstant;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Table;
import com.google.common.primitives.ImmutableIntArray;
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
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.SuccessorFunction;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;
import owl.collections.Collections3;
import owl.collections.ImmutableBitSet;
import owl.collections.Pair;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Conjunction;
import owl.logic.propositional.sat.Solver;

public final class ZielonkaTreeTransformations {

  private ZielonkaTreeTransformations() {
  }

  public static <S> Automaton<ZielonkaState<S>, ParityAcceptance>
  transform(Automaton<S, ?> automaton) {

    var cachedAutomaton = AbstractMemoizingAutomaton.memoizingAutomaton(automaton);
    var sccDecomposition = SccDecomposition.of(cachedAutomaton);

    return transform(
        cachedAutomaton,
        OptionalInt.empty(),
        (x, y) -> sccDecomposition.index(x) != sccDecomposition.index(y),
        x -> cachedAutomaton.acceptance().booleanExpression()
    );
  }

  // TODO: it might be the case that depending on the iteration order the sizes are different.
  public static <S> AutomatonWithZielonkaTreeLookup<ZielonkaState<S>, ParityAcceptance>
  transform(
      AbstractMemoizingAutomaton<S, ?> automaton,
      OptionalInt lookahead,
      BiPredicate<S, S> sccChange,
      Function<S, ? extends PropositionalFormula<Integer>> localAlpha) {

    // Fixed configuration flag.
    boolean stutter = true;

    class ZielonkaForest {

      // The forest maps a state to an AlternatingCycleDecomposition, if a complete SCC has been
      // identified within the state exploration budget, and to an ConditionalZielonkaTree,
      // otherwise.
      private final Map<S, ZielonkaTree> zielonkaTrees = new HashMap<>();
      private final Set<S> acceptingZielonkaTreeRoots = new HashSet<>();
      private final AcdCache<S> acdCache = new AcdCache<>(
          automaton.acceptance());

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
          var conditionalZielonkaTree = cachedConditionalZielonkaTrees.get(alpha, trueConstant());

          if (conditionalZielonkaTree == null) {
            conditionalZielonkaTree = ConditionalZielonkaTree.of(alpha, trueConstant());
            cachedConditionalZielonkaTrees.put(alpha, trueConstant(), conditionalZielonkaTree);
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
            AlternatingCycleDecomposition.of(automaton, boundedSccExploration, acdCache));

        for (S exploredState : boundedSccExploration) {
          int index = AlternatingCycleDecomposition
              .find(alternatingCycleDecompositions, exploredState);

          // The state is transient, we fake an alternating cycle decomposition.
          if (index == -1) {
            zielonkaTrees.put(exploredState, AlternatingCycleDecomposition.of(
                AllAcceptance.INSTANCE,
                ImmutableBitSet.of(),
                Map.of(exploredState, Set.of()),
                acdCache));
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

      private ZielonkaState<S> initialState(S initialState) {
        var stateTree = zielonkaTree(initialState, new HashSet<>());

        @SuppressWarnings("unchecked")
        var initialPath = stateTree instanceof AlternatingCycleDecomposition
            ? ((AlternatingCycleDecomposition<S>) stateTree).leftMostLeaf(initialState)
            : ((ConditionalZielonkaTree) stateTree).leftMostLeaf();

        return ZielonkaState.of(initialState, initialPath);
      }

      private Edge<ZielonkaState<S>> edge(
          ZielonkaState<S> state, Edge<S> edge, Set<S> exploredStates) {

        var stateTree = zielonkaTree(state.state(), exploredStates);
        var successorTree = zielonkaTree(edge.successor(), exploredStates);

        // We switched SCC, re-initialise path.
        if (!stateTree.equals(successorTree)) {
          return Edge.of(initialState(edge.successor()));
        }

        @SuppressWarnings("unchecked")
        var pathEdge = successorTree instanceof AlternatingCycleDecomposition
            ? edge(edge, state.path(), (AlternatingCycleDecomposition<S>) successorTree)
            : edge(edge, state.path(), (ConditionalZielonkaTree) successorTree);

        assert successorTree instanceof AlternatingCycleDecomposition
            || ((ConditionalZielonkaTree) successorTree).subtree(pathEdge.successor()) != null
            : "dangling path";

        return pathEdge.mapSuccessor(x -> ZielonkaState.of(edge.successor(), x));
      }

      private Edge<ImmutableIntArray> edge(
          Edge<S> edge, ImmutableIntArray path, AlternatingCycleDecomposition<S> acd) {

        S successor = edge.successor();
        assert acd.edges().containsKey(successor);

        // State and successor are in the same SCC, let's find the anchor.
        var anchor = acd;
        int anchorLevel = 0;
        var successorPathBuilder = ImmutableIntArray.builder(acd.height() + 2);

        for (int i = 0, s = path.length(); i < s; i++) {
          int nextAnchorIndex = path.get(i);
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
        boolean stateInChildPresent = false;
        var children = anchor.children();

        for (int i = 0, s = children.size(); i < s; i++) {
          AlternatingCycleDecomposition<S> x = children.get(i);
          if (x.edges().containsKey(successor)) {
            stateInChildPresent = true;
            break;
          }
        }

        if (stateInChildPresent) {
          int nextNodeId = path.length() == anchorLevel
              ? -1
              : path.get(anchorLevel);

          AlternatingCycleDecomposition<S> nextNode;

          do {
            nextNodeId = (nextNodeId + 1) % children.size();
            nextNode = children.get(nextNodeId);
          } while (!nextNode.edges().containsKey(successor));

          successorPathBuilder.add(nextNodeId);
          // extend successor path to left most leaf.
          nextNode.leftMostLeaf(successorPathBuilder, successor);
        }

        // min-even parity.
        return Edge.of(
            successorPathBuilder.build().trimmed(),
            anchorLevel + (acceptingZielonkaTreeRoots.contains(successor) ? 0 : 1));
      }

      private Edge<ImmutableIntArray> edge(
          Edge<S> edge, ImmutableIntArray path, ConditionalZielonkaTree root) {

        ImmutableBitSet colours = edge.colours().intersection(root.colours());

        int anchorLevel = 0;
        var node = root;
        var successorPathBuilder = ImmutableIntArray.builder(root.height() + 1);
        ImmutableIntArray successorPath = null;

        for (int i = 0, s = path.length(); i < s; i++) {
          int edgeIndex = path.get(i);
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
            successorPath = successorPathBuilder.build().trimmed();

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
          successorPath = successorPathBuilder.build().trimmed();
          var successorNode = root.subtree(successorPath);

          // We have not yet reached a leaf.
          if (!successorNode.children().isEmpty()) {
            successorPathBuilder = ImmutableIntArray.builder().addAll(successorPath);
            successorNode.leftMostLeaf(successorPathBuilder);
            successorPath = successorPathBuilder.build().trimmed();
          }
        }

        // min-even parity
        return Edge.of(
            successorPath,
            acceptingZielonkaTreeRoots.contains(edge.successor()) ? anchorLevel : anchorLevel + 1);
      }
    }

    var forest = new ZielonkaForest();

    var initialStates = Collections3.transformSet(automaton.initialStates(), forest::initialState);

    // Each level of a Zielonka tree removes at least one acceptance set. We add +1 to allow for
    // shifting the colours depending on acceptance of the Zielonka root.
    var acceptance = new ParityAcceptance(automaton.acceptance().acceptanceSets() + 2, MIN_EVEN);

    class AutomatonWithZielonkaTreeLookupImpl
        extends
        AbstractMemoizingAutomaton.EdgeTreeImplementation<ZielonkaState<S>, ParityAcceptance>
        implements AutomatonWithZielonkaTreeLookup<ZielonkaState<S>, ParityAcceptance> {

      private AutomatonWithZielonkaTreeLookupImpl(
          List<String> atomicPropositions,
          BddSetFactory factory,
          Set<ZielonkaState<S>> initialStates,
          ParityAcceptance acceptance) {

        super(atomicPropositions, factory, initialStates, acceptance);
      }

      @Override
      protected MtBdd<Edge<ZielonkaState<S>>> edgeTreeImpl(ZielonkaState<S> state) {
        Set<S> exploredStates = new HashSet<>();

        return automaton
            .edgeTree(state.state())
            .map(edges -> Collections3.transformSet(edges,
                edge -> forest.edge(state, edge, exploredStates)));
      }

      @Override
      public ZielonkaTree lookup(ZielonkaState<S> state) {
        return forest.zielonkaTrees.get(state.state());
      }
    }

    return new AutomatonWithZielonkaTreeLookupImpl(
        automaton.atomicPropositions(), automaton.factory(), initialStates, acceptance);
  }

  public interface ZielonkaTreeLookup<S> {

    ZielonkaTree lookup(S state);

  }

  public interface AutomatonWithZielonkaTreeLookup<S, A extends EmersonLeiAcceptance>
      extends Automaton<S, A>, ZielonkaTreeLookup<S> {

  }

  public interface ZielonkaTree {

    ImmutableBitSet colours();

    List<? extends ZielonkaTree> children();

    int height();

  }

  @AutoValue
  abstract static class AcdCacheEntry<S> {

    abstract ImmutableBitSet colours();

    abstract Map<S, Set<Edge<S>>> edges();

    static <S> AcdCacheEntry<S> of(
        ImmutableBitSet colours,
        Map<S, Set<Edge<S>>> edges) {

      return new AutoValue_ZielonkaTreeTransformations_AcdCacheEntry<>(colours, Map.copyOf(edges));
    }
  }

  private static class AcdCache<S> {

    private final PropositionalFormula<Integer> alpha;

    private final ZielonkaDag dag;

    private final Map<AcdCacheEntry<S>, AlternatingCycleDecomposition<S>> cache = new HashMap<>();

    public AcdCache(EmersonLeiAcceptance acceptance) {
      this.alpha = acceptance.booleanExpression();
      this.dag = new ZielonkaDag(this.alpha);
    }

    List<ImmutableBitSet> children(ImmutableBitSet node) {
      return dag.children(node);
    }

    @Nullable
    AlternatingCycleDecomposition<S> get(
        ImmutableBitSet colours,
        Map<S, Set<Edge<S>>> edges) {

      return cache.get(AcdCacheEntry.of(colours, edges));
    }

    @Nullable
    AlternatingCycleDecomposition<S> put(
        ImmutableBitSet colours,
        Map<S, Set<Edge<S>>> edges,
        AlternatingCycleDecomposition<S> acd) {

      return cache.put(AcdCacheEntry.of(colours, edges), acd);
    }
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
      return of(automaton, automaton.states(), new AcdCache<>(automaton.acceptance()));
    }

    public static <S> List<AlternatingCycleDecomposition<S>> of(
        Automaton<S, ?> automaton, Set<S> restrictedStates, AcdCache<S> cache) {

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

        acd.add(of(automaton.acceptance(), coloursOfChildScc, sccEdges, cache));
      }

      return acd;
    }

    public static <S> AlternatingCycleDecomposition<S> of(
        EmersonLeiAcceptance alpha,
        ImmutableBitSet colours,
        Map<S, Set<Edge<S>>> edges,
        AcdCache<S> cache) {

      if (!alpha.booleanExpression().isTrue()) {
        assert cache.alpha.equals(alpha.booleanExpression());
        var acd = cache.get(colours, edges);

        if (acd != null) {
          return acd;
        }
      }

      List<AlternatingCycleDecomposition<S>> childrenBuilder = new ArrayList<>();

      for (Pair<ImmutableBitSet, Map<S, Set<Edge<S>>>> x
          : childrenOf(alpha, colours, edges, cache)) {

        childrenBuilder.add(of(alpha, x.fst(), x.snd(), cache));
      }

      var children = List.copyOf(childrenBuilder);

      AlternatingCycleDecomposition<S> acd
          = new AutoValue_ZielonkaTreeTransformations_AlternatingCycleDecomposition<>(
          colours, edges, children, height(children));

      if (!alpha.booleanExpression().isTrue()) {
        assert cache.alpha.equals(alpha.booleanExpression());
        cache.put(colours, edges, acd);
      }

      return acd;
    }

    private static <S> int height(List<AlternatingCycleDecomposition<S>> children) {
      int height = 0;

      for (var child : children) {
        height = Math.max(height, child.height() + 1);
      }

      return height;
    }

    private static <S> List<Pair<ImmutableBitSet, Map<S, Set<Edge<S>>>>> childrenOf(
        EmersonLeiAcceptance alpha,
        ImmutableBitSet colours,
        Map<S, Set<Edge<S>>> edges,
        AcdCache<S> cache) {

      assert isClosed(edges);

      List<ImmutableBitSet> maximalModels;

      if (alpha.booleanExpression().isTrue()) {
        maximalModels = List.of();
      } else {
        assert alpha.booleanExpression().equals(cache.alpha);
        maximalModels = cache.children(colours);
      }

      var children = new ArrayList<Pair<ImmutableBitSet, Map<S, Set<Edge<S>>>>>();

      for (ImmutableBitSet childColours : maximalModels) {
        SuccessorFunction<S> successorFunction = state -> {
          Set<S> successors = new HashSet<>();
          for (var edge : edges.get(state)) {
            if (childColours.containsAll(edge.colours())) {
              successors.add(edge.successor());
            }
          }
          return successors;
        };

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

          var coloursOfChildScc = ImmutableBitSet.of();

          for (Set<Edge<S>> sccEdges : childEdges.values()) {
            for (Edge<S> sccEdge : sccEdges) {
              coloursOfChildScc = coloursOfChildScc.union(sccEdge.colours());
            }
          }

          if (alpha.booleanExpression().evaluate(coloursOfChildScc)
              == alpha.booleanExpression().evaluate(childColours)) {
            children.add(Pair.of(coloursOfChildScc, Map.copyOf(childEdges)));
          } else {
            children.addAll(childrenOf(alpha, coloursOfChildScc, childEdges, cache));
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

    public ImmutableIntArray restrictPathToSubtree(S state, ImmutableIntArray unrestrictedPath) {
      var builder = ImmutableIntArray.builder(unrestrictedPath.length());
      restrictPathToSubtree(state, unrestrictedPath, 0, builder);
      return builder.build().trimmed();
    }

    private void restrictPathToSubtree(
        S state, ImmutableIntArray unrestrictedPath, int index, ImmutableIntArray.Builder builder) {

      if (index == unrestrictedPath.length()) {
        return;
      }

      int childIndex = unrestrictedPath.get(index);
      var children = children();
      assert children.get(childIndex).edges().containsKey(state) : children;

      int newChildIndex = 0;

      for (int i = 0; i < childIndex; i++) {
        if (children.get(i).edges().containsKey(state)) {
          newChildIndex++;
        }
      }

      builder.add(newChildIndex);
      children.get(childIndex).restrictPathToSubtree(state, unrestrictedPath, index + 1, builder);
    }

    private static <S> boolean isClosed(Map<S, ? extends Collection<Edge<S>>> edges) {
      return edges.values().stream()
          .flatMap(x -> x.stream().map(Edge::successor))
          .allMatch(edges::containsKey);
    }

    private static <S> int find(List<AlternatingCycleDecomposition<S>> acd, S state) {
      for (int i = 0, s = acd.size(); i < s; i++) {
        if (acd.get(i).edges().containsKey(state)) {
          return i;
        }
      }

      return -1;
    }

    public ImmutableIntArray leftMostLeaf(S state) {
      assert edges().containsKey(state);
      var pathBuilder = ImmutableIntArray.builder(height());
      leftMostLeaf(pathBuilder, state);
      return pathBuilder.build().trimmed();
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
      var maximalModels = Solver.DEFAULT_MAXIMAL_MODELS.maximalModels(
              Conjunction.of(
                  alpha.evaluate(colours) ? PropositionalFormula.Negation.of(alpha) : alpha, beta),
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

    public ConditionalZielonkaTree subtree(ImmutableIntArray path) {
      return subtree(path.asList());
    }

    public ConditionalZielonkaTree subtree(List<Integer> path) {
      var subtree = this;

      for (Integer i : path) {
        subtree = subtree.children().get(i);
      }

      return subtree;
    }

    private ImmutableIntArray leftMostLeaf() {
      var pathBuilder = ImmutableIntArray.builder(height());
      leftMostLeaf(pathBuilder);
      return pathBuilder.build().trimmed();
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
  public abstract static class ZielonkaState<S> implements AnnotatedState<S> {

    @Override
    public abstract S state();

    public abstract ImmutableIntArray path();

    public static <S> ZielonkaState<S> of(S state, ImmutableIntArray path) {
      return new AutoValue_ZielonkaTreeTransformations_ZielonkaState<>(state, path);
    }
  }
}
