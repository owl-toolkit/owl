/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import static owl.ltl.SyntacticFragment.SINGLE_STEP;

import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import owl.collections.LabelledTree;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.ltl.rewriter.LiteralMapper;
import owl.ltl.rewriter.PullUpXVisitor;
import owl.ltl.util.FormulaIsomorphism;
import owl.ltl.visitors.PropositionalVisitor;
import owl.util.annotation.CEntryPoint;

// This is a JNI entry point. No touching.
public final class DecomposedDPA {

  public final LabelledTree<Tag, Reference> structure;
  public final List<FormulaPreprocessor.VariableStatus> variableStatuses;
  public final List<DeterministicAutomaton<?, ?>> automata;

  private final List<Reference> leaves;
  private final Map<ImmutableIntArray, Status> profiles = new HashMap<>();

  private DecomposedDPA(LabelledTree<Tag, Reference> structure,
    List<FormulaPreprocessor.VariableStatus> variableStatuses,
    List<DeterministicAutomaton<?, ?>> automata) {
    this.automata = automata;
    this.variableStatuses = List.copyOf(variableStatuses);
    this.leaves = structure.leavesStream().collect(Collectors.toUnmodifiableList());
    this.structure = structure;
  }

  @CEntryPoint
  public static DecomposedDPA of(Formula formula, boolean simplify, boolean monolithic,
    int firstOutputVariable) {
    var preprocessedFormula = FormulaPreprocessor.apply(formula, firstOutputVariable, simplify);

    // Push X in front of
    Builder builder = new Builder(preprocessedFormula.formula.accept(Annotator.INSTANCE));

    LabelledTree<Tag, Reference> structure = monolithic
      ? builder.createLeaf(preprocessedFormula.formula)
      : preprocessedFormula.formula.accept(builder);

    return new DecomposedDPA(structure,
      preprocessedFormula.variableStatuses,
      List.copyOf(builder.automata));
  }

  @CEntryPoint
  public boolean declare(int status, int... profile) {
    var normalisedProfile = normalise(profile);
    var newStatus = Status.values()[status];
    assert newStatus == Status.REALIZABLE || newStatus == Status.UNREALIZABLE;
    var oldStatus = profiles.put(normalisedProfile, newStatus);
    assert oldStatus == null || oldStatus == newStatus;
    return oldStatus == null;
  }

  @CEntryPoint
  public int query(int... profile) {
    return profiles.getOrDefault(normalise(profile), Status.UNKNOWN).ordinal();
  }

  private ImmutableIntArray normalise(int... stateIndices) {
    var builder = ImmutableIntArray.builder(stateIndices.length);
    int i = 0;

    for (Reference reference : leaves) {
      builder.add(automata.get(reference.index).normalise(stateIndices[i]));
      i++;
    }

    assert stateIndices.length == i : "Length mismatch.";
    return builder.build();
  }

  private static boolean isSingleStep(Formula formula) {
    if (formula instanceof Conjunction) {
      return formula.operands.stream().allMatch(DecomposedDPA::isSingleStep);
    }

    return formula instanceof GOperator && SINGLE_STEP.contains(((GOperator) formula).operand());
  }

  enum Status {
    REALIZABLE, UNREALIZABLE, UNKNOWN
  }

  enum Tag {
    BICONDITIONAL, CONJUNCTION, DISJUNCTION
  }

  public static final class Reference {
    public final Formula formula;
    public final int index;
    public final ImmutableIntArray alphabetMapping;

    Reference(Formula formula, int index, ImmutableIntArray alphabetMapping) {
      this.formula = formula;
      this.index = index;
      this.alphabetMapping = alphabetMapping;
    }

    Reference(Formula formula, int index, int[] alphabetMapping) {
      this(formula, index, ImmutableIntArray.copyOf(alphabetMapping));
    }

    @CEntryPoint
    public int[] alphabetMapping() {
      return alphabetMapping.toArray();
    }
  }

  static final class Builder extends PropositionalVisitor<LabelledTree<Tag, Reference>> {
    private final Map<Formula, Acceptance> annotatedTree;
    private final List<DeterministicAutomaton<?, ?>> automata = new ArrayList<>();
    private final Map<Formula, Reference> lookup = new HashMap<>();

    Builder(Map<Formula, Acceptance> annotatedTree) {
      this.annotatedTree = new HashMap<>(annotatedTree);
    }

    private LabelledTree<Tag, Reference> createLeaf(Formula formula) {
      assert SyntacticFragment.NNF.contains(formula);
      Reference reference = lookup.get(formula);

      if (reference != null) {
        return new LabelledTree.Leaf<>(reference);
      }

      for (Map.Entry<Formula, Reference> entry : lookup.entrySet()) {
        int[] mapping = FormulaIsomorphism.compute(formula, entry.getKey());

        if (mapping != null) {
          reference = entry.getValue();

          int[] newMapping = Arrays.copyOf(mapping, mapping.length);

          // Compose isomorphism mapping and the automaton mapping
          for (int i = 0; i < newMapping.length; i++) {
            int j = newMapping[i];

            if (j != -1) {
              newMapping[i] = reference.alphabetMapping.get(j);
              assert newMapping[i] != -1;
            }
          }

          return new LabelledTree.Leaf<>(new Reference(formula, reference.index, newMapping));
        }
      }

      LiteralMapper.ShiftedFormula shiftedFormula = LiteralMapper.shiftLiterals(formula);
      Reference newReference = new Reference(formula, automata.size(), shiftedFormula.mapping);
      automata.add(DeterministicAutomaton.of(Hacks.attachDummyAlphabet(shiftedFormula.formula)));
      lookup.put(formula, newReference);
      return new LabelledTree.Leaf<>(newReference);
    }

    private List<LabelledTree<Tag, Reference>> createLeaves(
      Formula.NaryPropositionalOperator formula) {
      // Partition elements.
      var safety = new Clusters();
      var safetySingleStep = new HashMap<Integer, Clusters>();
      var coSafety = new Clusters();

      var weakOrBuchiOrCoBuchi = new TreeSet<Formula>();
      var parity = new TreeSet<Formula>();

      for (Formula x : formula.operands) {
        switch (annotatedTree.get(x)) {
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
            assert annotatedTree.get(x).isLessThanParity();
            weakOrBuchiOrCoBuchi.add(x);
            break;
        }
      }

      // Process elements.
      List<LabelledTree<Tag, Reference>> children = new ArrayList<>();
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
    protected LabelledTree<Tag, Reference> visit(Formula.TemporalOperator formula) {
      return createLeaf(formula);
    }

    @Override
    public LabelledTree<Tag, Reference> visit(Literal literal) {
      return createLeaf(literal);
    }

    private boolean keepTreeStructureBiconditional(Formula formula) {
      if (formula instanceof Conjunction || formula instanceof Disjunction) {
        if (formula.operands.stream()
          .filter(x -> annotatedTree.get(x) == Acceptance.PARITY).count() > 1) {
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
          return biconditional.leftOperand().accept(this) && biconditional.rightOperand()
            .accept(this);
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
    public LabelledTree<Tag, Reference> visit(Biconditional biconditional) {
      if (annotatedTree.get(biconditional.leftOperand()) == Acceptance.PARITY
        && annotatedTree.get(biconditional.rightOperand()) == Acceptance.PARITY) {

        if (keepTreeStructureBiconditional(biconditional.leftOperand())
          || keepTreeStructureBiconditional(biconditional.rightOperand())) {

          return new LabelledTree.Node<>(Tag.BICONDITIONAL,
            List.of(biconditional.leftOperand().accept(this),
              biconditional.rightOperand().accept(this)));
        }

        var nnf = biconditional.nnf();
        annotatedTree.putAll(nnf.accept(Annotator.INSTANCE));
        return nnf.accept(this);
      }

      return new LabelledTree.Node<>(Tag.BICONDITIONAL,
        List
          .of(biconditional.leftOperand().accept(this), biconditional.rightOperand().accept(this)));
    }

    @Override
    public LabelledTree<Tag, Reference> visit(BooleanConstant booleanConstant) {
      return createLeaf(booleanConstant);
    }

    @Override
    public LabelledTree<Tag, Reference> visit(Conjunction conjunction) {
      var leaves = createLeaves(conjunction);
      return leaves.size() == 1 ? leaves.get(0) : new LabelledTree.Node<>(Tag.CONJUNCTION, leaves);
    }

    @Override
    public LabelledTree<Tag, Reference> visit(Disjunction disjunction) {
      var leaves = createLeaves(disjunction);
      return leaves.size() == 1 ? leaves.get(0) : new LabelledTree.Node<>(Tag.DISJUNCTION, leaves);
    }
  }

  static class Clusters {
    private static final Predicate<Formula> INTERESTING_OPERATOR =
    o -> o instanceof Formula.TemporalOperator && !(o instanceof XOperator);

    List<Set<Formula>> clusterList = new ArrayList<>();

    void insert(Formula formula) {
      Set<Formula> cluster = new HashSet<>();
      cluster.add(formula);

      clusterList.removeIf(x -> {
        var temporalOperators1 = formula.subformulas(
          INTERESTING_OPERATOR, Formula.TemporalOperator.class::cast);
        var temporalOperators2 = x.stream().flatMap(
          y -> y.subformulas(INTERESTING_OPERATOR, Formula.TemporalOperator.class::cast).stream())
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

  static class Annotator extends PropositionalVisitor<Map<Formula, Acceptance>> {
    static final Annotator INSTANCE = new Annotator();

    @Override
    protected Map<Formula, Acceptance> visit(Formula.TemporalOperator formula) {
      if (SyntacticFragments.isSafety(formula)) {
        return Map.of(formula, Acceptance.SAFETY);
      }

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
      Map<Formula, Acceptance> acceptanceMap = new HashMap<>();

      acceptanceMap.putAll(biconditional.leftOperand().accept(this));
      acceptanceMap.putAll(biconditional.rightOperand().accept(this));

      Acceptance leftAcceptance = acceptanceMap.get(biconditional.leftOperand());
      Acceptance rightAcceptance = acceptanceMap.get(biconditional.rightOperand());

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
