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

package owl.cinterface;

import static owl.cinterface.CDecomposedDPA.RealizabilityStatus.REALIZABLE;
import static owl.cinterface.CDecomposedDPA.RealizabilityStatus.UNKNOWN;
import static owl.cinterface.CDecomposedDPA.RealizabilityStatus.UNREALIZABLE;
import static owl.ltl.SyntacticFragment.SINGLE_STEP;

import com.google.common.collect.Iterables;
import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.graalvm.nativeimage.c.type.CIntPointer;
import owl.cinterface.CAutomaton.Acceptance;
import owl.cinterface.CDecomposedDPA.RealizabilityStatus;
import owl.cinterface.CDecomposedDPA.Structure.NodeType;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.ltl.rewriter.LiteralMapper;
import owl.ltl.rewriter.PullUpXVisitor;
import owl.ltl.util.FormulaIsomorphism;
import owl.ltl.visitors.PropositionalVisitor;

public final class DecomposedDPA {

  final Tree structure;
  final List<CAutomaton.DeterministicAutomatonWrapper<?, ?>> automata;

  private final ImmutableIntArray leafIndices;
  private final Map<ImmutableIntArray, RealizabilityStatus> profiles = new HashMap<>();

  private DecomposedDPA(Tree structure,
    List<CAutomaton.DeterministicAutomatonWrapper<?, ?>> automata) {
    this.automata = automata;
    this.leafIndices = ImmutableIntArray.copyOf(structure.leafIndices());
    this.structure = structure;
  }

  static DecomposedDPA of(LabelledFormula labelledFormula) {
    var atomicPropositions = labelledFormula.atomicPropositions();
    var builder = new TreeBuilder(atomicPropositions);
    var tree = labelledFormula.formula().accept(builder);
    var globalFactory = CInterface.ENVIRONMENT.factorySupplier()
      .getValuationSetFactory(atomicPropositions);
    tree.initializeFilter(builder.automata, builder.sharedAutomata, globalFactory.universe());
    return new DecomposedDPA(tree, List.copyOf(builder.automata));
  }

  boolean declare(RealizabilityStatus newStatus, CIntPointer profile, int length) {
    assert newStatus == REALIZABLE || newStatus == UNREALIZABLE;
    var oldStatus = profiles.put(normalise(profile, length), newStatus);
    assert oldStatus == null || oldStatus == newStatus;
    return oldStatus == null;
  }

  RealizabilityStatus query(CIntPointer profile, int length) {
    return profiles.getOrDefault(normalise(profile, length), UNKNOWN);
  }

  ImmutableIntArray normalise(CIntPointer profile, int length) {
    var builder = ImmutableIntArray.builder(length);
    int i = 0;

    for (int reference : leafIndices.asList()) {
      builder.add(automata.get(reference).normalise(profile.read(i)));
      i++;
    }

    assert length == i : "Length mismatch.";
    return builder.build();
  }

  private static boolean isSingleStep(Formula formula) {
    if (formula instanceof Conjunction) {
      return formula.operands.stream().allMatch(DecomposedDPA::isSingleStep);
    }

    return formula instanceof GOperator && SINGLE_STEP.contains(((GOperator) formula).operand());
  }

  private static final class TreeBuilder extends PropositionalVisitor<Tree> {

    private final List<String> atomicPropositions;
    private final Map<Formula, Acceptance> annotations = new HashMap<>();
    private final List<CAutomaton.DeterministicAutomatonWrapper<?, ?>> automata = new ArrayList<>();
    private final Map<Formula, Tree.Leaf> lookup = new HashMap<>();
    private final BitSet sharedAutomata = new BitSet();

    private TreeBuilder(List<String> atomicPropositions) {
      this.atomicPropositions = List.copyOf(atomicPropositions);
    }

    private Tree.Leaf createLeaf(Formula formula) {
      assert SyntacticFragment.NNF.contains(formula);
      Tree.Leaf leaf = lookup.get(formula);

      if (leaf != null) {
        sharedAutomata.set(leaf.index);
        return leaf;
      }

      for (Map.Entry<Formula, Tree.Leaf> entry : lookup.entrySet()) {
        int[] mapping = FormulaIsomorphism.compute(formula, entry.getKey());

        if (mapping != null) {
          leaf = entry.getValue();

          int[] newMapping = Arrays.copyOf(mapping, mapping.length);

          // Compose isomorphism mapping and the automaton mapping
          for (int i = 0; i < newMapping.length; i++) {
            int j = newMapping[i];

            if (j != -1) {
              newMapping[i] = leaf.globalToLocalMapping.get(j);
              assert newMapping[i] != -1;
            }
          }

          sharedAutomata.set(leaf.index);
          return new Tree.Leaf(leaf.index, formula, ImmutableIntArray.copyOf(newMapping));
        }
      }

      var shiftedFormula
        = LiteralMapper.shiftLiterals(LabelledFormula.of(formula, atomicPropositions));
      leaf = new Tree.Leaf(automata.size(), formula, shiftedFormula.mapping);
      automata.add(CAutomaton.DeterministicAutomatonWrapper.of(shiftedFormula.formula));
      lookup.put(formula, leaf);
      return leaf;
    }


    private static boolean isInterestingOperator(Formula o) {
      return o instanceof Formula.TemporalOperator && !(o instanceof XOperator);
    }

    private List<Tree> createLeaves(Formula.NaryPropositionalOperator formula) {
      if (!annotations.containsKey(formula)) {
        annotations.putAll(formula.accept(Annotator.INSTANCE));
      }

      class Clusters {
        private final List<Set<Formula>> clusterList = new ArrayList<>();

        private void insert(Formula formula) {
          Set<Formula> cluster = new HashSet<>();
          cluster.add(formula);

          clusterList.removeIf(x -> {
            var temporalOperators1 = formula.subformulas(
              TreeBuilder::isInterestingOperator, Formula.TemporalOperator.class::cast);
            var temporalOperators2 = x.stream().flatMap(
              y -> y.subformulas(
                TreeBuilder::isInterestingOperator, Formula.TemporalOperator.class::cast).stream())
              .collect(Collectors.toSet());

            if (!Collections.disjoint(temporalOperators1, temporalOperators2)) {
              cluster.addAll(x);
              return true;
            }

            return false;
          });

          clusterList.add(cluster);
        }
      }

      // Partition elements.
      var safety = new Clusters();
      var safetySingleStep = new HashMap<Integer, Clusters>();
      var coSafety = new Clusters();

      var weakOrBuchiOrCoBuchi = new TreeSet<Formula>();
      var parity = new TreeSet<Formula>();

      for (Formula x : formula.operands) {
        switch (annotations.get(x)) {
          case SAFETY:
            PullUpXVisitor.XFormula rewrittenX = x.accept(PullUpXVisitor.INSTANCE);

            if (isSingleStep(rewrittenX.rawFormula())) {
              safetySingleStep
                .computeIfAbsent(rewrittenX.depth(), ignore -> new Clusters())
                .insert(XOperator.of(rewrittenX.rawFormula(), rewrittenX.depth()));
            } else {
              safety.insert(x);
            }

            break;

          case CO_SAFETY:
            coSafety.insert(x);
            break;

          case PARITY:
            parity.add(x);
            break;

          default:
            assert annotations.get(x).isLessThanParity();
            weakOrBuchiOrCoBuchi.add(x);
            break;
        }
      }

      // Process elements.
      List<Tree> children = new ArrayList<>();
      Function<Collection<Formula>, Formula> merger = formula instanceof Conjunction
        ? Conjunction::of
        : Disjunction::of;

      safetySingleStep.values().forEach(x -> x.clusterList.forEach(y -> {
        assert !y.isEmpty();
        children.add(createLeaf(merger.apply(y)));
      }));

      safety.clusterList.forEach(x -> {
        assert !x.isEmpty();
        children.add(createLeaf(merger.apply(x)));
      });

      coSafety.clusterList.forEach(x -> {
        assert !x.isEmpty();
        children.add(createLeaf(merger.apply(x)));
      });

      for (Formula child : weakOrBuchiOrCoBuchi) {
        children.add(child.accept(this));
      }

      if (parity.size() == 1) {
        children.add(parity.first().accept(this));
      } else if (parity.size() > 1) {
        var mergedFormula = merger.apply(parity).nnf();
        children.add(createLeaf(mergedFormula));
      }

      return children;
    }

    @Override
    protected Tree visit(Formula.TemporalOperator formula) {
      return createLeaf(formula);
    }

    @Override
    public Tree visit(Literal literal) {
      return createLeaf(literal);
    }

    private boolean keepTreeStructureBiconditional(Formula formula) {
      if (formula instanceof Conjunction || formula instanceof Disjunction) {
        if (formula.operands.stream()
          .filter(x -> annotations.get(x) == CAutomaton.Acceptance.PARITY).count() > 1) {
          return false;
        }
      } else {
        return false;
      }

      return formula.accept(new PropositionalVisitor<Boolean>() {
        @Override
        protected Boolean visit(Formula.TemporalOperator formula) {
          return (SyntacticFragments.isAlmostAll(formula)
            || SyntacticFragments.isInfinitelyOften(formula))
            && SyntacticFragment.SINGLE_STEP.contains(formula.operands.get(0).operands.get(0));
        }

        @Override
        public Boolean visit(Literal literal) {
          return false;
        }

        @Override
        public Boolean visit(Biconditional biconditional) {
          return biconditional.leftOperand().accept(this)
            && biconditional.rightOperand().accept(this);
        }

        @Override
        public Boolean visit(Conjunction conjunction) {
          return conjunction.operands.stream().allMatch(this::apply);
        }

        @Override
        public Boolean visit(Disjunction disjunction) {
          return disjunction.operands.stream().allMatch(this::apply);
        }
      });
    }

    @Override
    public Tree visit(Biconditional biconditional) {
      if (annotations.get(biconditional.leftOperand()) == CAutomaton.Acceptance.PARITY
        && annotations.get(biconditional.rightOperand()) == CAutomaton.Acceptance.PARITY) {

        if (keepTreeStructureBiconditional(biconditional.leftOperand())
          || keepTreeStructureBiconditional(biconditional.rightOperand())) {

          return new Tree.Node(NodeType.BICONDITIONAL,
            List.of(biconditional.leftOperand().accept(this),
              biconditional.rightOperand().accept(this)));
        }

        var nnf = biconditional.nnf();
        annotations.putAll(nnf.accept(Annotator.INSTANCE));
        return nnf.accept(this);
      }

      return new Tree.Node(
        NodeType.BICONDITIONAL,
        List
          .of(biconditional.leftOperand().accept(this), biconditional.rightOperand().accept(this)));
    }

    @Override
    public Tree visit(BooleanConstant booleanConstant) {
      return createLeaf(booleanConstant);
    }

    @Override
    public Tree visit(Conjunction conjunction) {
      var leaves = createLeaves(conjunction);
      return leaves.size() == 1 ? leaves.get(0) : new Tree.Node(NodeType.CONJUNCTION, leaves);
    }

    @Override
    public Tree visit(Disjunction disjunction) {
      var leaves = createLeaves(disjunction);
      return leaves.size() == 1 ? leaves.get(0) : new Tree.Node(NodeType.DISJUNCTION, leaves);
    }
  }

  /**
   * This class does not implement equals() and hashCode(). This is required for correctness while
   * installing a filter.
   */
  abstract static class Tree {

    abstract IntStream leafIndices();

    abstract void initializeFilter(
      List<CAutomaton.DeterministicAutomatonWrapper<?, ?>> referencedAutomata,
      BitSet sharedAutomata,
      ValuationSet globalFilter);

    static final class Leaf extends Tree {

      static final Set<Integer> ALLOWED_CONJUNCTION_STATES_PATTERN = Set.of(
        CAutomaton.DeterministicAutomatonWrapper.REJECTING,
        CAutomaton.DeterministicAutomatonWrapper.INITIAL);

      static final Set<Integer> ALLOWED_DISJUNCTION_STATES_PATTERN = Set.of(
        CAutomaton.DeterministicAutomatonWrapper.ACCEPTING,
        CAutomaton.DeterministicAutomatonWrapper.INITIAL);

      final int index;
      final Formula formula;
      final ImmutableIntArray globalToLocalMapping;
      final ImmutableIntArray localToGlobalMapping;

      Leaf(int index,
        Formula formula,
        ImmutableIntArray globalToLocalMapping) {

        int[] localToGlobalMapping = new int[globalToLocalMapping.length()];
        int maxLocal = -1;

        for (int global = 0; global < localToGlobalMapping.length; global++) {
          int local = globalToLocalMapping.get(global);

          if (local == -1) {
            continue;
          }

          maxLocal = Math.max(maxLocal, local);
          localToGlobalMapping[local] = global;
        }

        this.index = index;
        this.formula = formula;

        this.globalToLocalMapping = globalToLocalMapping;
        this.localToGlobalMapping = ImmutableIntArray
          .copyOf(localToGlobalMapping).subArray(0, maxLocal + 1);

        assert isConsistent(this.globalToLocalMapping, this.localToGlobalMapping);
      }

      private static boolean isConsistent(
        ImmutableIntArray globalToLocalMapping,
        ImmutableIntArray localToGlobalMapping) {

        for (int global = 0; global < globalToLocalMapping.length(); global++) {
          int local = globalToLocalMapping.get(global);

          if (local != -1 && global != localToGlobalMapping.get(local)) {
            return false;
          }
        }

        for (int local = 0; local < localToGlobalMapping.length(); local++) {
          int global = localToGlobalMapping.get(local);

          if (local != globalToLocalMapping.get(global)) {
            return false;
          }
        }

        return true;
      }

      @Override
      IntStream leafIndices() {
        return IntStream.of(index);
      }

      @Override
      void initializeFilter(
        List<CAutomaton.DeterministicAutomatonWrapper<?, ?>> referencedAutomata,
        BitSet sharedAutomata,
        ValuationSet globalFilter) {

        // Skip, if there is a trivial filter or this automaton is shared and
        // filtering is not allowed.
        if (globalFilter.isUniverse() || sharedAutomata.get(index)) {
          return;
        }

        var referencedAutomaton = referencedAutomata.get(index);
        BitSet unusedAtomicPropositions = new BitSet();

        for (int i = 0; i < globalToLocalMapping.length(); i++) {
          if (globalToLocalMapping.get(i) == -1) {
            unusedAtomicPropositions.set(i);
          }
        }

        int globalApSize = globalFilter.factory().atomicPropositions().size();
        unusedAtomicPropositions.set(globalToLocalMapping.length(), globalApSize);

        var newFilter = globalFilter
          .project(unusedAtomicPropositions)
          .relabel(x -> x < globalToLocalMapping.length() ? globalToLocalMapping.get(x) : -1);

        if (!newFilter.isUniverse()) {
          assert referencedAutomaton.filter == null;
          referencedAutomaton.filter = newFilter;
        }
      }
    }

    static final class Node extends Tree {

      final NodeType label;
      final List<Tree> children;

      Node(NodeType nodeType, List<Tree> children) {
        this.label = nodeType;
        this.children = List.copyOf(children);
      }

      @Override
      IntStream leafIndices() {
        return children.stream().flatMapToInt(Tree::leafIndices);
      }

      @Override
      void initializeFilter(
        List<CAutomaton.DeterministicAutomatonWrapper<?, ?>> referencedAutomata,
        BitSet sharedAutomata,
        ValuationSet globalFilter) {

        // Skip biconditionals.
        if (label == NodeType.BICONDITIONAL) {
          return;
        }

        assert label == NodeType.CONJUNCTION || label == NodeType.DISJUNCTION;

        ValuationSet nodeFilter = globalFilter;
        Set<Leaf> filterSources = new HashSet<>();

        for (Tree child : children) {
          // We don't compute filters for composed nodes.
          if (child instanceof Node) {
            continue;
          }

          var leaf = (Leaf) child;

          var automaton = referencedAutomata.get(leaf.index);
          var initialStateSuccessors = automaton.initialStateSuccessors;

          if (initialStateSuccessors == null) {
            continue;
          }

          ValuationTree<Boolean> leafFilter;

          if (label == NodeType.CONJUNCTION
            && Leaf.ALLOWED_CONJUNCTION_STATES_PATTERN.containsAll(initialStateSuccessors)) {

            leafFilter = automaton.initialStateEdgeTree.map(
              x -> x.isEmpty()
                ? Set.of()
                : Set.of(true));
          } else if (label == NodeType.DISJUNCTION
            && automaton.acceptance == Acceptance.CO_SAFETY
            && Leaf.ALLOWED_DISJUNCTION_STATES_PATTERN.containsAll(initialStateSuccessors)) {

            var initialState = automaton.automaton.onlyInitialState();
            leafFilter = automaton.initialStateEdgeTree.map(
              x -> initialState.equals(Iterables.getOnlyElement(x).successor())
                ? Set.of(true)
                : Set.of());
          } else {
            continue;
          }

          filterSources.add(leaf);
          nodeFilter = leafFilter
            .inverse(globalFilter.factory(), leaf.localToGlobalMapping::get)
            .getOrDefault(Boolean.TRUE, globalFilter.factory().empty())
            .intersection(nodeFilter);
        }

        for (var child : children) {
          // Skip filter sources.
          if (child instanceof Leaf && filterSources.contains(child)) {
            continue;
          }

          child.initializeFilter(referencedAutomata, sharedAutomata, nodeFilter);
        }
      }
    }
  }

  static class Annotator extends PropositionalVisitor<Map<Formula, Acceptance>> {
    static final Annotator INSTANCE = new Annotator();

    @Override
    protected Map<Formula, Acceptance> visit(Formula.TemporalOperator formula) {
      // safety
      if (SyntacticFragments.isSafety(formula)) {
        return Map.of(formula, Acceptance.SAFETY);
      }

      // coSafety
      if (SyntacticFragments.isCoSafety(formula)) {
        return Map.of(formula, Acceptance.CO_SAFETY);
      }

      // GF(coSafety), G(coSafety), and arbitrary X-prefixes are also contained in the fragment.
      if (SyntacticFragments.isSafetyCoSafety(formula)) {
        return Map.of(formula, Acceptance.BUCHI);
      }

      // FG(Safety), F(Safety), and arbitrary X-prefixes are also contained in the fragment.
      if (SyntacticFragments.isCoSafetySafety(formula)) {
        return Map.of(formula, Acceptance.CO_BUCHI);
      }

      return Map.of(formula, Acceptance.PARITY);
    }

    @Override
    public Map<Formula, Acceptance> visit(Biconditional biconditional) {
      var acceptanceMap = new HashMap<Formula, Acceptance>();

      acceptanceMap.putAll(biconditional.leftOperand().accept(this));
      acceptanceMap.putAll(biconditional.rightOperand().accept(this));

      var leftAcceptance = acceptanceMap.get(biconditional.leftOperand());
      var rightAcceptance = acceptanceMap.get(biconditional.rightOperand());

      if (leftAcceptance.lub(rightAcceptance).isLessOrEqualWeak()) {
        acceptanceMap.put(biconditional, Acceptance.WEAK);
      } else {
        acceptanceMap.put(biconditional, Acceptance.PARITY);
      }

      return acceptanceMap;
    }

    @Override
    public Map<Formula, Acceptance> visit(BooleanConstant booleanConstant) {
      return Map.of(booleanConstant, Acceptance.BOTTOM);
    }

    @Override
    public Map<Formula, Acceptance> visit(Conjunction conjunction) {
      return visitPropositional(conjunction);
    }

    @Override
    public Map<Formula, Acceptance> visit(Disjunction disjunction) {
      return visitPropositional(disjunction);
    }

    @Override
    public Map<Formula, Acceptance> visit(Literal literal) {
      return Map.of(literal, Acceptance.WEAK);
    }

    private Map<Formula, Acceptance> visitPropositional(Formula.NaryPropositionalOperator formula) {
      Acceptance acceptance = Acceptance.BOTTOM;
      Map<Formula, Acceptance> acceptanceMap = new HashMap<>();

      for (Formula child : formula.operands) {
        Map<Formula, Acceptance> childDecisions = child.accept(this);
        acceptanceMap.putAll(childDecisions);
        acceptance = acceptance.lub(acceptanceMap.get(child));
      }

      acceptanceMap.put(formula, acceptance);
      return acceptanceMap;
    }
  }
}
