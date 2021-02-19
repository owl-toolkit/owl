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

import static owl.logic.propositional.PropositionalFormula.Negation;
import static owl.logic.propositional.PropositionalFormula.trueConstant;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.cli.Options;
import owl.automaton.AbstractAutomaton;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.SuccessorFunction;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.acceptance.transformer.AcceptanceTransformation.AcceptanceTransformer;
import owl.automaton.acceptance.transformer.AcceptanceTransformation.ExtendedState;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Colours;
import owl.automaton.edge.Edge;
import owl.bdd.ValuationSet;
import owl.collections.Collections3;
import owl.collections.ValuationTree;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Conjunction;
import owl.logic.propositional.sat.Solver;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class ToParityTransformer {

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
      .addOption(null, "stutter", false,
        "Repeat acceptance marks in order to move as far as possible in the Zielonka split-tree.")
      .addOption(null, "scc-analysis", false,
        "Compute SCC-decomposition of automaton, heuristically simplify acceptance condition "
          + "for each SCC, and construct a separate Zielonka split-tree for each SCC.")
      .addOption(null, "scc-analysis-jump-to-bottom", false,
        "If scc-analysis is enabled, select an initial leaf reducing the size of the "
          + "resulting SCC."),
    (commandLine, environment) -> {
      boolean stutter = commandLine.hasOption("stutter");
      boolean sccAnalysis = commandLine.hasOption("scc-analysis");
      boolean sccAnalysisJumpToBottom = commandLine.hasOption("scc-analysis-jump-to-bottom");
      return OwlModule.AutomatonTransformer.of(
        a -> transform(a, stutter, sccAnalysis, sccAnalysisJumpToBottom));
    });

  private ToParityTransformer() {}

  /**
   * Entry-point for standalone CLI tool. This class has be registered in build.gradle in the
   * "Startup Scripts" section in order to get the necessary scripts for CLI invocation.
   *
   * @param args the arguments
   * @throws IOException the exception
   */
  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.HOA_INPUT_MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  /**
   * Translation procedure. This function returns an on-the-fly generated automaton and assumes the
   * the argument automaton is not changed after calling this method.
   *
   * @param automaton the automaton
   * @param <S> the state type
   *
   * @return an on-the-fly generated Parity automaton
   */
  public static <S> Automaton<ExtendedState<S, Path>, ParityAcceptance> transform(
    Automaton<S, ?> automaton,
    boolean stutter,
    boolean sccAnalysis,
    boolean sccAnalysisJumpToBottom) {

    if (!sccAnalysis) {
      return AcceptanceTransformation.transform(
        automaton, acceptance -> ZielonkaTreeRoot.of(acceptance, stutter));
    }

    // Make a copy for faster access.
    // TODO: Replace by Automaton.copyOf().
    var automatonCopy = HashMapAutomaton.copyOf(automaton);
    var sccDecomposition = SccDecomposition.of(automatonCopy);
    var parityAutomaton = transformSccBased(automatonCopy, sccDecomposition, stutter);

    if (!sccAnalysisJumpToBottom) {
      return parityAutomaton;
    }

    // Make a mutable copy.
    var parityAutomatonCopy = HashMapAutomaton.copyOf(parityAutomaton);

    for (Set<S> scc : sccDecomposition.sccsWithoutTransient()) {
      var extendedStatesOfScc = parityAutomatonCopy.states().stream()
        .filter(state -> scc.contains(state.state()))
        .collect(Collectors.toSet());

      // The annotations are not increasing the size of the SCC, nothing to-do.
      if (extendedStatesOfScc.size() == scc.size()) {
        continue;
      }

      var filteredSuccessorFunction
        = SuccessorFunction.filter(parityAutomatonCopy, extendedStatesOfScc);

      var refinedSccDecomposition
        = SccDecomposition.of(extendedStatesOfScc, filteredSuccessorFunction);

      // There is only a single bottom SCC.
      if (refinedSccDecomposition.sccs().size() == 1) {
        continue;
      }

      int currentSelectedBottomScc = Collections.min(refinedSccDecomposition.bottomSccs());

      for (int bottomScc : refinedSccDecomposition.bottomSccs()) {
        int newBottomSccSize = refinedSccDecomposition.sccs().get(bottomScc).size();
        int oldBottomSccSize = refinedSccDecomposition.sccs().get(currentSelectedBottomScc)
          .size();

        if (newBottomSccSize < oldBottomSccSize
          || (newBottomSccSize == oldBottomSccSize && bottomScc < currentSelectedBottomScc)) {
          currentSelectedBottomScc = bottomScc;
        }
      }

      Set<ExtendedState<S, Path>> selectedScc = refinedSccDecomposition.sccs()
        .get(currentSelectedBottomScc);

      Function<ExtendedState<S, Path>, ExtendedState<S, Path>> updateState = state -> {
        Objects.requireNonNull(state);

        if (!extendedStatesOfScc.contains(state) || selectedScc.contains(state)) {
          return state;
        }

        return selectedScc.stream().filter(x -> x.state().equals(state.state()))
          .findFirst().orElseThrow();
      };

      // Redirect transitions
      parityAutomatonCopy.initialStates(parityAutomatonCopy.initialStates().stream()
        .map(updateState).collect(Collectors.toList()));
      parityAutomatonCopy.updateEdges((state, edge) -> edge.mapSuccessor(updateState));
      parityAutomatonCopy.trim();
    }

    return parityAutomatonCopy;

  }

  private static <S> boolean implies(
    Automaton<S, ?> automaton,
    Set<S> scc,
    Predicate<? super Edge<S>> edgePredicate1,
    Predicate<? super Edge<S>> edgePredicate2) {

    // We exclude all transitions that satisfy predicate 2.
    var filteredSuccessorFunction
      = SuccessorFunction.filter(automaton, scc, edgePredicate2.negate());

    assert !scc.isEmpty();
    return SccDecomposition.of(scc, filteredSuccessorFunction).allMatch(subScc -> {
      for (S state : subScc) {
        for (Edge<S> edge : automaton.edges(state)) {

          // This edge does no belong to the subScc.
          if (!subScc.contains(edge.successor())) {
            continue;
          }

          // This edge is excluded.
          if (edgePredicate2.test(edge)) {
            continue;
          }

          // This subSCC contains an edge that satisfies predicate 1. Thus there exists an infinite
          // run satisfying predicate 1, but not predicate 2. Implication wrong.
          if (edgePredicate1.test(edge)) {
            return false;
          }
        }
      }

      return true;
    });
  }

  private static <S> boolean equivalent(
    Automaton<S, ?> automaton,
    Set<S> scc,
    Predicate<Edge<S>> edgePredicate1,
    Predicate<Edge<S>> edgePredicate2) {

    return implies(automaton, scc, edgePredicate1, edgePredicate2)
      && implies(automaton, scc, edgePredicate2, edgePredicate1);
  }

  private static <S, A extends OmegaAcceptance>
    Automaton<ExtendedState<S, Path>, ParityAcceptance> transformSccBased(
    Automaton<S, A> automaton, SccDecomposition<S> sccDecomposition, boolean stutter) {

    A acceptance = automaton.acceptance();

    Map<S, Integer> indexMap = sccDecomposition.indexMap();
    Map<Integer, ZielonkaTreeRoot> sccTransformer = new HashMap<>();
    Map<Integer, Map<Integer, Integer>> colourRemapping = new HashMap<>();

    List<ParityAcceptance> localAcceptances = new ArrayList<>();

    for (Set<S> scc : sccDecomposition.sccsWithoutTransient()) {
      Integer index = indexMap.get(scc.iterator().next());

      BitSet allEdges = new BitSet();
      allEdges.set(0, acceptance.acceptanceSets());
      BitSet someEdges = new BitSet();

      for (S state : scc) {
        for (Edge<S> edges : automaton.edges(state)) {
          BitSet accepanteMarks = edges.colours().copyInto(new BitSet());
          allEdges.and(accepanteMarks);
          someEdges.or(accepanteMarks);
        }
      }

      List<Set<Integer>> equivalentMarks = Collections3.quotient(IntStream
        .range(0, acceptance.acceptanceSets())
        // .filter(x -> !allEdges.get(x) && someEdges.get(x)) // skip non trivial
        .boxed()
        .collect(Collectors.toList()),
        (x, y) -> equivalent(automaton, scc, e1 -> e1.colours().contains((int) x),
          e2 -> e2.colours().contains((int) y)));

      Map<Integer, Integer> mapping = new HashMap<>();

      for (Set<Integer> clazz : equivalentMarks) {
        int representative = Collections.min(clazz);

        for (Integer element : clazz) {
          mapping.put(element, representative);
        }
      }

      OmegaAcceptance simplifiedAcceptance = new EmersonLeiAcceptance(
      acceptance.acceptanceSets(),
      acceptance.booleanExpression().substitute((Integer acceptanceMark) -> {
        if (allEdges.get(acceptanceMark)) {
          assert someEdges.get(acceptanceMark);
          return Optional.of(trueConstant());
        }

        if (!someEdges.get(acceptanceMark)) {
          assert !allEdges.get(acceptanceMark);
          return Optional.of(PropositionalFormula.falseConstant());
        }

        return Optional.of(PropositionalFormula.Variable.of(mapping.get(acceptanceMark)));
      }));

      Set<Integer> variables = simplifiedAcceptance.booleanExpression().variables();
      List<PropositionalFormula<Integer>> facts = new ArrayList<>();

      Set<Integer> singletons = new HashSet<>();

      for (Integer i : variables) {
        // On every path in the scc i set to true inf.
        if (implies(automaton, scc, e1 -> true, e2 -> e2.colours().contains((int) i))) {
          facts.add(PropositionalFormula.Variable.of(i));
          singletons.add(i);
        }
      }

      for (Integer i : variables) {
        for (Integer j : variables) {
          if (i.equals(j) || singletons.contains(j)) {
            continue;
          }

          if (implies(automaton, scc, e1 -> e1.colours().contains((int) i),
            e2 -> e2.colours().contains((int) j))) {
            facts.add(PropositionalFormula.Disjunction.of(
              Negation.of(PropositionalFormula.Variable.of(i)),
              PropositionalFormula.Variable.of(j)));
          }

          if (singletons.contains(i)) {
            continue;
          }

          if (implies(automaton, scc, e1 -> true,
            e2 -> e2.colours().contains((int) i) || e2.colours().contains((int) j))) {
            facts.add(PropositionalFormula.Disjunction.of(
              PropositionalFormula.Variable.of(i),
              PropositionalFormula.Variable.of(j)));
          }
        }
      }

      PropositionalFormula<Integer> context = Conjunction.of(facts);
      ZielonkaTreeRoot localTransformer
        = ZielonkaTreeRoot.of(simplifiedAcceptance, context, stutter);
      localAcceptances.add(localTransformer.transformedAcceptance());
      sccTransformer.put(index, localTransformer);
      colourRemapping.put(index, mapping);
    }

    ZielonkaTreeRoot defaultTransformer =
      ZielonkaTreeRoot.of(AllAcceptance.INSTANCE, stutter);
    localAcceptances.add(defaultTransformer.transformedAcceptance());
    Preconditions.checkState(
      localAcceptances.stream().allMatch(x -> x.parity() == Parity.MIN_EVEN));
    ParityAcceptance globalAcceptance = Collections.max(localAcceptances,
      Comparator.comparingInt(ParityAcceptance::acceptanceSets));

    var initialStates = Collections3.transformSet(
      automaton.initialStates(), s -> {
        var transformer =
          sccTransformer.getOrDefault(indexMap.get(s), defaultTransformer);
        return ExtendedState.of(s, transformer.initialExtension());
      });

    return new AbstractAutomaton<>(
      automaton.factory(), initialStates, globalAcceptance) {

      @Override
      public Set<Edge<ExtendedState<S, Path>>>
        edges(ExtendedState<S, Path> state, BitSet valuation) {

        return Collections3.transformSet(
          automaton.edges(state.state(), valuation),
          x -> transformEdge(state, x));
      }

      @Override
      public Map<Edge<ExtendedState<S, Path>>, ValuationSet> edgeMap(ExtendedState<S, Path> state) {
        return Collections3.transformMap(
          automaton.edgeMap(state.state()),
          x -> transformEdge(state, x));
      }

      @Override
      public ValuationTree<Edge<ExtendedState<S, Path>>> edgeTree(ExtendedState<S, Path> state) {
        return automaton.edgeTree(state.state())
          .map(x -> Collections3.transformSet(x, y -> transformEdge(state, y)));
      }

      @Override
      public List<PreferredEdgeAccess> preferredEdgeAccess() {
        return automaton.preferredEdgeAccess();
      }

      private Edge<ExtendedState<S, Path>> transformEdge(
        ExtendedState<S, Path> state, Edge<? extends S> edge) {

        boolean sameScc = indexMap.get(state.state()).equals(indexMap.get(edge.successor()));
        var successorTransformer
          = sccTransformer.getOrDefault(indexMap.get(edge.successor()), defaultTransformer);

        if (sameScc) {
          Map<Integer, Integer> mapping = colourRemapping.get(indexMap.get(edge.successor()));

          Edge<? extends S> mappedEdge = mapping == null
            ? edge.withoutAcceptance()
            : edge.mapAcceptance(mapping::get);

          Edge<Path> extensionEdge
            = successorTransformer.transformColours(mappedEdge.colours(), state.extension());
          return extensionEdge.mapSuccessor(x -> ExtendedState.of(mappedEdge.successor(), x));
        } else {
          return Edge.of(
            ExtendedState.of(edge.successor(), successorTransformer.initialExtension()));
        }
      }
    };
  }

  @AutoValue
  public abstract static class ZielonkaTreeRoot
    implements AcceptanceTransformer<ParityAcceptance, Path> {

    public abstract ZielonkaTree root();

    public abstract boolean rootAccepting();

    public abstract boolean stutter();

    public static ZielonkaTreeRoot of(
      OmegaAcceptance alpha, boolean stutter) {

      return of(alpha, trueConstant(), stutter);
    }

    public static ZielonkaTreeRoot of(
      OmegaAcceptance alpha, PropositionalFormula<Integer> beta, boolean stutter) {

      return of(alpha.booleanExpression(), beta, stutter);
    }

    public static ZielonkaTreeRoot of(
      PropositionalFormula<Integer> alpha, PropositionalFormula<Integer> beta, boolean stutter) {

      Set<Integer> colours = alpha.variables();
      ZielonkaTree root = ZielonkaTree.of(Colours.copyOf(colours), alpha, beta);
      boolean accepting = alpha.evaluate(root.colours());
      return new AutoValue_ToParityTransformer_ZielonkaTreeRoot(root, accepting, stutter);
    }

    @Override
    public ParityAcceptance transformedAcceptance() {
      return new ParityAcceptance(root().height() + 2, Parity.MIN_EVEN);
    }

    @Override
    public Path initialExtension() {
      return extendPathToLeaf(new ArrayList<>());
    }

    @Override
    public Edge<Path> transformColours(Colours unfilteredColours, Path currentPath) {
      Colours colours = unfilteredColours.intersection(root().colours());

      int anchorLevel = 0;
      var node = this.root();
      List<Integer> successorPathBuilder = new ArrayList<>();
      Path successorPath = null;

      for (Integer edgeIndex : currentPath.indices()) {
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
            stutter() && nextEdge != edgeIndex
              && !child.colours().containsAll(colours)
          );

          // We did a full turn around, reset to a default value.
          if (stutter() && nextEdge == edgeIndex) {
            nextEdge = 0;
          }

          successorPathBuilder.add(nextEdge);
          // extend path to any leaf
          successorPath = extendPathToLeaf(successorPathBuilder);

          // We did not fully cycle around.
          if (child.colours().containsAll(colours)) {
            // Recursively descend, but override acceptance mark.
            return transformColours(colours, successorPath)
              .withAcceptance(rootAccepting() ? anchorLevel : anchorLevel + 1);
          }

          break;
        }
      }

      if (successorPath == null) {
        successorPath = Path.of(successorPathBuilder);
      }

      return Edge.of(successorPath, rootAccepting() ? anchorLevel : anchorLevel + 1);
    }

    private Path extendPathToLeaf(List<Integer> path) {
      var localNode = root().subtree(path);

      while (!localNode.children().isEmpty()) {
        localNode = localNode.children().get(0);
        path.add(0);
      }

      return Path.of(path);
    }
  }

  @AutoValue
  public abstract static class ZielonkaTree {

    public abstract Colours colours();

    public abstract List<ZielonkaTree> children();

    private static ZielonkaTree of(
      Colours colours,
      PropositionalFormula<Integer> alpha,
      PropositionalFormula<Integer> beta) {

      return of(colours, alpha, beta, new HashMap<>());
    }

    private static ZielonkaTree of(
      Colours colours,
      PropositionalFormula<Integer> alpha,
      PropositionalFormula<Integer> beta,
      Map<Colours, ZielonkaTree> cache) {

      var zielonkaTree = cache.get(colours);

      if (zielonkaTree != null) {
        return zielonkaTree;
      }

      // Invert acceptance condition (alpha) in order to obtain alternation in tree.
      // Sort colour sets lexicographically. This ensures that we always compute
      // the same Zielonka tree for a given acceptance condition.
      var maximalModels = Solver.maximalModels(
        Conjunction.of(alpha.evaluate(colours) ? Negation.of(alpha) : alpha, beta), colours)
        .stream()
        .map(Colours::copyOf)
        .sorted()
        .toArray(Colours[]::new);

      var children = new ArrayList<ZielonkaTree>();

      for (Colours childColours : maximalModels) {
        children.add(of(childColours, alpha, beta, cache));
      }

      zielonkaTree = new AutoValue_ToParityTransformer_ZielonkaTree(colours, List.copyOf(children));

      cache.put(colours, zielonkaTree);
      return zielonkaTree;
    }

    public int height() {
      int max = 0;

      for (ZielonkaTree child : children()) {
        max = Math.max(max, child.height() + 1);
      }

      return max;
    }

    public ZielonkaTree subtree(Path path) {
      return subtree(path.indices());
    }

    public ZielonkaTree subtree(List<Integer> path) {
      var subtree = this;

      for (Integer i : path) {
        subtree = subtree.children().get(i);
      }

      return subtree;
    }
  }

  @AutoValue
  public abstract static class Path {

    public abstract List<Integer> indices();

    public static Path of(List<Integer> indices) {
      return new AutoValue_ToParityTransformer_Path(List.copyOf(indices));
    }
  }
}