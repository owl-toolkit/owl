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
import java.util.stream.IntStream;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import owl.cinterface.CAutomaton.Acceptance;
import owl.cinterface.CDecomposedDPA.RealizabilityStatus;
import owl.cinterface.CDecomposedDPA.Structure.NodeType;
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

  public final DecomposedDPAStructure structure;
  public final List<VariableStatus> variableStatuses;
  public final List<CAutomaton.DeterministicAutomatonWrapper<?, ?>> automata;

  private final ImmutableIntArray leaves;
  private final Map<ImmutableIntArray, RealizabilityStatus> profiles = new HashMap<>();

  private DecomposedDPA(DecomposedDPAStructure structure,
    List<VariableStatus> variableStatuses,
    List<CAutomaton.DeterministicAutomatonWrapper<?, ?>> automata) {
    this.automata = automata;
    this.variableStatuses = List.copyOf(variableStatuses);
    this.leaves = ImmutableIntArray.copyOf(structure.leavesStream());
    this.structure = structure;
  }

  public static DecomposedDPA of(Formula formula, boolean simplify, boolean monolithic,
    int firstOutputVariable) {
    var preprocessedFormula = FormulaPreprocessor.apply(formula, firstOutputVariable, simplify);

    // Push X in front of
    Builder builder = new Builder(preprocessedFormula.formula.accept(Annotator.INSTANCE));

    DecomposedDPAStructure structure = monolithic
      ? builder.createLeaf(preprocessedFormula.formula)
      : preprocessedFormula.formula.accept(builder);

    return new DecomposedDPA(structure,
      preprocessedFormula.variableStatuses,
      List.copyOf(builder.automata));
  }

  public boolean declare(RealizabilityStatus newStatus, CIntPointer profile, int length) {
    assert newStatus == REALIZABLE || newStatus == UNREALIZABLE;
    var oldStatus = profiles.put(normalise(profile, length), newStatus);
    assert oldStatus == null || oldStatus == newStatus;
    return oldStatus == null;
  }

  public RealizabilityStatus query(CIntPointer profile, int length) {
    return profiles.getOrDefault(normalise(profile, length), UNKNOWN);
  }

  private ImmutableIntArray normalise(CIntPointer profile, int length) {
    var builder = ImmutableIntArray.builder(length);
    int i = 0;

    for (int reference : leaves.asList()) {
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

  @CContext(CInterface.CDirectives.class)
  @CEnum("variable_status_t")
  public enum VariableStatus {
    CONSTANT_TRUE, CONSTANT_FALSE, USED, UNUSED;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native VariableStatus fromCValue(int value);
  }

  public static final class Reference {
    public final Formula formula;
    public final int index;
    public final ImmutableIntArray alphabetMapping;

    Reference(Formula formula, int index, int[] alphabetMapping) {
      this.formula = formula;
      this.index = index;
      this.alphabetMapping = ImmutableIntArray.copyOf(alphabetMapping);
    }
  }

  static final class Builder extends PropositionalVisitor<DecomposedDPAStructure> {
    private final Map<Formula, Acceptance> annotatedTree;
    private final List<CAutomaton.DeterministicAutomatonWrapper<?, ?>> automata = new ArrayList<>();
    private final Map<Formula, Reference> lookup = new HashMap<>();

    Builder(Map<Formula, Acceptance> annotatedTree) {
      this.annotatedTree = new HashMap<>(annotatedTree);
    }

    private DecomposedDPAStructure createLeaf(Formula formula) {
      assert SyntacticFragment.NNF.contains(formula);
      Reference reference = lookup.get(formula);

      if (reference != null) {
        return new DecomposedDPAStructure.Leaf(reference);
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

          return new DecomposedDPAStructure.Leaf(
            new Reference(formula, reference.index, newMapping));
        }
      }

      LiteralMapper.ShiftedFormula shiftedFormula = LiteralMapper.shiftLiterals(formula);
      Reference newReference = new Reference(formula, automata.size(), shiftedFormula.mapping);
      automata.add(CAutomaton.DeterministicAutomatonWrapper.of(
        LabelledFormula.of(shiftedFormula.formula, IntStream
          .range(0, shiftedFormula.formula.atomicPropositions(true).length())
          .mapToObj(i -> "p" + i)
          .collect(Collectors.toUnmodifiableList()))));
      lookup.put(formula, newReference);
      return new DecomposedDPAStructure.Leaf(newReference);
    }

    private List<DecomposedDPAStructure> createLeaves(
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
      List<DecomposedDPAStructure> children = new ArrayList<>();
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
    protected DecomposedDPAStructure visit(Formula.TemporalOperator formula) {
      return createLeaf(formula);
    }

    @Override
    public DecomposedDPAStructure visit(Literal literal) {
      return createLeaf(literal);
    }

    private boolean keepTreeStructureBiconditional(Formula formula) {
      if (formula instanceof Conjunction || formula instanceof Disjunction) {
        if (formula.operands.stream()
          .filter(x -> annotatedTree.get(x) == CAutomaton.Acceptance.PARITY).count() > 1) {
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
    public DecomposedDPAStructure visit(Biconditional biconditional) {
      if (annotatedTree.get(biconditional.leftOperand()) == CAutomaton.Acceptance.PARITY
        && annotatedTree.get(biconditional.rightOperand()) == CAutomaton.Acceptance.PARITY) {

        if (keepTreeStructureBiconditional(biconditional.leftOperand())
          || keepTreeStructureBiconditional(biconditional.rightOperand())) {

          return new DecomposedDPAStructure.Node(NodeType.BICONDITIONAL,
            List.of(biconditional.leftOperand().accept(this),
              biconditional.rightOperand().accept(this)));
        }

        var nnf = biconditional.nnf();
        annotatedTree.putAll(nnf.accept(Annotator.INSTANCE));
        return nnf.accept(this);
      }

      return new DecomposedDPAStructure.Node(NodeType.BICONDITIONAL,
        List
          .of(biconditional.leftOperand().accept(this), biconditional.rightOperand().accept(this)));
    }

    @Override
    public DecomposedDPAStructure visit(BooleanConstant booleanConstant) {
      return createLeaf(booleanConstant);
    }

    @Override
    public DecomposedDPAStructure visit(Conjunction conjunction) {
      var leaves = createLeaves(conjunction);
      return leaves.size() == 1 ? leaves.get(0) : new DecomposedDPAStructure.Node(
        NodeType.CONJUNCTION, leaves);
    }

    @Override
    public DecomposedDPAStructure visit(Disjunction disjunction) {
      var leaves = createLeaves(disjunction);
      return leaves.size() == 1 ? leaves.get(0) : new DecomposedDPAStructure.Node(
        NodeType.DISJUNCTION, leaves);
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
        return Map.of(formula, CAutomaton.Acceptance.SAFETY);
      }

      if (SyntacticFragments.isCoSafety(formula)) {
        return Map.of(formula, CAutomaton.Acceptance.CO_SAFETY);
      }

      // GF(coSafety), G(coSafety), and arbitrary X-prefixes are also contained in the fragment.
      if (SyntacticFragments.isSafetyCoSafety(formula)) {
        return Map.of(formula, CAutomaton.Acceptance.BUCHI);
      }

      // FG(Safety), F(Safety), and arbitrary X-prefixes are also contained in the fragment.
      if (SyntacticFragments.isCoSafetySafety(formula)) {
        return Map.of(formula, CAutomaton.Acceptance.CO_BUCHI);
      }

      return Map.of(formula, CAutomaton.Acceptance.PARITY);
    }

    @Override
    public Map<Formula, Acceptance> visit(Biconditional biconditional) {
      Map<Formula, Acceptance> acceptanceMap = new HashMap<>();

      acceptanceMap.putAll(biconditional.leftOperand().accept(this));
      acceptanceMap.putAll(biconditional.rightOperand().accept(this));

      Acceptance leftAcceptance = acceptanceMap.get(biconditional.leftOperand());
      Acceptance rightAcceptance = acceptanceMap.get(biconditional.rightOperand());

      if (leftAcceptance.lub(rightAcceptance).isLessOrEqualWeak()) {
        acceptanceMap.put(biconditional, CAutomaton.Acceptance.WEAK);
      } else {
        acceptanceMap.put(biconditional, CAutomaton.Acceptance.PARITY);
      }

      return acceptanceMap;
    }

    @Override
    public Map<Formula, Acceptance> visit(BooleanConstant booleanConstant) {
      return Map.of(booleanConstant, CAutomaton.Acceptance.BOTTOM);
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
      return Map.of(literal, CAutomaton.Acceptance.WEAK);
    }

    private Map<Formula, Acceptance> visitPropositional(Formula.NaryPropositionalOperator formula) {
      Acceptance acceptance = CAutomaton.Acceptance.BOTTOM;
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

  abstract static class DecomposedDPAStructure {
    final NodeType label;

    private DecomposedDPAStructure(NodeType label) {
      this.label = label;
    }

    abstract IntStream leavesStream();

    static final class Leaf extends DecomposedDPAStructure {
      final Reference reference;

      Leaf(Reference reference) {
        super(NodeType.AUTOMATON);
        this.reference = reference;
      }

      @Override
      IntStream leavesStream() {
        return IntStream.of(reference.index);
      }
    }

    static final class Node extends DecomposedDPAStructure {
      final List<DecomposedDPAStructure> children;

      Node(NodeType nodeType, List<DecomposedDPAStructure> children) {
        super(nodeType);
        this.children = List.copyOf(children);
      }

      @Override
      IntStream leavesStream() {
        return children.stream().flatMapToInt(DecomposedDPAStructure::leavesStream);
      }
    }
  }
}
