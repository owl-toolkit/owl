/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.jni;

import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.collections.LabelledTree;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.rewriter.LiteralMapper;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.util.FormulaIsomorphism;
import owl.ltl.visitors.PropositionalVisitor;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;
import owl.translations.canonical.BreakpointState;
import owl.util.annotation.CEntryPoint;

// This is a JNI entry point. No touching.
public final class JniEmersonLeiAutomaton {

  public static final AutomatonRepository cache = new AutomatonRepository();

  public final LabelledTree<Tag, Reference> structure;
  public final List<JniAutomaton<?>> automata;

  private JniEmersonLeiAutomaton(LabelledTree<Tag, Reference> structure,
    List<JniAutomaton<?>> automata) {
    this.structure = structure;
    this.automata = automata;
  }

  public static JniEmersonLeiAutomaton of(Formula formula, boolean simplify, boolean monolithic,
    SafetySplittingMode mode, BitSet inputVariables) {
    Formula nnfLight = formula.substitute(Formula::nnf);

    Formula processedFormula = simplify
      ? RealizabilityRewriter.removeSingleValuedInputLiterals(inputVariables,
        SimplifierFactory.apply(nnfLight, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT))
      : nnfLight;

    Builder builder = new Builder(mode,
      processedFormula.accept(JniAcceptanceAnnotator.INSTANCE));

    LabelledTree<Tag, Reference> structure = monolithic
      ? Builder.createLeaf(processedFormula)
      : processedFormula.accept(builder);

    return new JniEmersonLeiAutomaton(structure, List.copyOf(cache.automata));
  }

  public static JniEmersonLeiAutomaton of(Formula formula, boolean simplify, boolean monolithic,
    SafetySplittingMode mode, int firstOutputVariable) {
    BitSet inputVariables = new BitSet();
    inputVariables.set(0, firstOutputVariable);
    return of(formula, simplify, monolithic, mode, inputVariables);
  }

  @CEntryPoint
  public static JniEmersonLeiAutomaton of(Formula formula, boolean simplify, boolean monolithic,
    int mode, int firstOutputVariable) {
    return of(formula, simplify, monolithic, SafetySplittingMode.values()[mode],
      firstOutputVariable);
  }

  @CEntryPoint
  public static void clearCache() {
    cache.automata.clear();
    cache.lookup.clear();
  }

  enum Tag {
    BICONDITIONAL, CONJUNCTION, DISJUNCTION
  }

  public enum SafetySplittingMode {
    NEVER, AUTO, ALWAYS
  }

  public static final class AutomatonRepository {
    private final List<JniAutomaton<?>> automata = new ArrayList<>();
    private final Map<Formula, Reference> lookup = new HashMap<>();
    private final LTL2DAFunction translator = new LTL2DAFunction(DefaultEnvironment.standard(),
      true, EnumSet.of(LTL2DAFunction.Constructions.SAFETY,
      LTL2DAFunction.Constructions.CO_SAFETY,
      LTL2DAFunction.Constructions.BUCHI,
      LTL2DAFunction.Constructions.CO_BUCHI,
      LTL2DAFunction.Constructions.PARITY));

    private Reference get(Formula formula) {
      assert SyntacticFragment.NNF.contains(formula);
      Reference reference = lookup.get(formula);

      if (reference != null) {
        return reference;
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

          return new Reference(formula, reference.index, newMapping);
        }
      }

      LiteralMapper.ShiftedFormula shiftedFormula = LiteralMapper.shiftLiterals(formula);
      LabelledFormula labelledFormula = Hacks.attachDummyAlphabet(shiftedFormula.formula);

      Automaton<?, ?> automaton = translator.apply(labelledFormula);
      JniAutomaton<?> jniAutomaton;

      if (SyntacticFragment.SAFETY.contains(shiftedFormula.formula)) {
        jniAutomaton = new JniAutomaton<>(
          AutomatonUtil.cast(automaton, EquivalenceClass.class, AllAcceptance.class),
          EquivalenceClass::isTrue, edge -> edge.successor().trueness());
      } else if (SyntacticFragment.CO_SAFETY.contains(shiftedFormula.formula)) {
        // Acceptance needs to be overridden, since detection does not work in this case.
        jniAutomaton = new JniAutomaton<>(
          AutomatonUtil.cast(automaton, EquivalenceClass.class, BuchiAcceptance.class),
          EquivalenceClass::isTrue, edge -> edge.successor().trueness(), JniAcceptance.CO_SAFETY);
      } else {
        var acceptance = automaton.acceptance();

        jniAutomaton = new JniAutomaton<>(automaton, x -> false, edge -> {
          var successor = edge.successor();

          if (acceptance instanceof BuchiAcceptance && edge.inSet(0)) {
            return 1.0d;
          }

          if (acceptance instanceof CoBuchiAcceptance && edge.inSet(0)) {
            return 0.0d;
          }

          if (successor instanceof EquivalenceClass) {
            return ((EquivalenceClass) successor).trueness();
          }

          if (successor instanceof BreakpointState) {
            var successorCasted = (BreakpointState<EquivalenceClass>) successor;
            return successorCasted.current().and(successorCasted.next()).trueness();
          }

          return 0.5d;
        });
      }

      Reference newReference = new Reference(formula, automata.size(), shiftedFormula.mapping);
      automata.add(jniAutomaton);
      lookup.put(formula, newReference);
      return newReference;
    }
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
    private final SafetySplittingMode safetySplittingMode;
    private final Map<Formula, JniAcceptance> annotatedTree;

    Builder(SafetySplittingMode safetySplittingMode, Map<Formula, JniAcceptance> annotatedTree) {
      this.safetySplittingMode = safetySplittingMode;
      this.annotatedTree = new HashMap<>(annotatedTree);
    }

    private static LabelledTree<Tag, Reference> createLeaf(Formula formula) {
      return new LabelledTree.Leaf<>(cache.get(formula));
    }

    private List<LabelledTree<Tag, Reference>> createLeaves(FormulaPartition partition,
      Function<Iterable<Formula>, Formula> merger) {
      List<LabelledTree<Tag, Reference>> children = new ArrayList<>();

      Set<Formula> safety = partition.safety();
      Set<Formula> coSafety = partition.cosafety();

      switch (safetySplittingMode) {
        case NEVER:
          if (!safety.isEmpty()) {
            children.add(createLeaf(merger.apply(safety)));
          }

          if (!coSafety.isEmpty()) {
            children.add(createLeaf(merger.apply(coSafety)));
          }

          break;

        case AUTO:
          partition.singleStepSafetyClusters.values()
            .forEach(x -> x.clusterList.forEach(y -> children.add(createLeaf(merger.apply(y)))));
          partition.safetyClusters.clusterList
            .forEach(x -> children.add(createLeaf(merger.apply(x))));
          partition.cosafetyClusters.clusterList
            .forEach(x -> children.add(createLeaf(merger.apply(x))));
          break;

        case ALWAYS:
          safety.forEach(x -> children.add(createLeaf(x)));
          coSafety.forEach(x -> children.add(createLeaf(x)));
          break;

        default:
          throw new AssertionError("Unreachable Code!");
      }

      partition.dba.forEach(x -> children.add(createLeaf(x)));
      partition.dca.forEach(x -> children.add(createLeaf(x)));

      List<Formula> lessThanParityRequired = new ArrayList<>();
      List<Formula> parityRequired = new ArrayList<>();

      partition.dpa.forEach(x -> {
        if (annotatedTree.get(x) == JniAcceptance.PARITY) {
          parityRequired.add(x);
        } else {
          assert annotatedTree.get(x).isLessThanParity();
          lessThanParityRequired.add(x);
        }
      });

      for (Formula child : lessThanParityRequired) {
        children.add(child.accept(this));
      }

      if (parityRequired.size() == 1) {
        children.add(parityRequired.get(0).accept(this));
      } else if (parityRequired.size() > 1) {
        var mergedFormula = merger.apply(parityRequired).nnf();

        if (mergedFormula instanceof Disjunction) {
          var dnfNormalForm = NormalForms.toDnfFormula(mergedFormula);

          if (!mergedFormula.equals(dnfNormalForm)) {
            annotatedTree.putAll(dnfNormalForm.accept(JniAcceptanceAnnotator.INSTANCE));
            children.add(dnfNormalForm.accept(this));
            return children;
          }
        }

        if (mergedFormula instanceof Conjunction) {
          var cnfNormalForm = NormalForms.toCnfFormula(mergedFormula);

          if (!mergedFormula.equals(cnfNormalForm)) {
            annotatedTree.putAll(cnfNormalForm.accept(JniAcceptanceAnnotator.INSTANCE));
            children.add(cnfNormalForm.accept(this));
            return children;
          }
        }

        children.add(createLeaf(mergedFormula));
      }

      return children;
    }

    @Override
    protected LabelledTree<Tag, Reference> visit(Formula.TemporalOperator formula) {
      return createLeaf(formula);
    }

    @Override
    public LabelledTree<Tag, Reference> visit(Biconditional biconditional) {
      if (annotatedTree.get(biconditional.left) == JniAcceptance.PARITY
        && annotatedTree.get(biconditional.right) == JniAcceptance.PARITY) {
        var nnf = biconditional.nnf();
        annotatedTree.putAll(nnf.accept(JniAcceptanceAnnotator.INSTANCE));
        return nnf.accept(this);
      }

      return new LabelledTree.Node<>(Tag.BICONDITIONAL,
        List.of(biconditional.left.accept(this), biconditional.right.accept(this)));
    }

    @Override
    public LabelledTree<Tag, Reference> visit(BooleanConstant booleanConstant) {
      return createLeaf(booleanConstant);
    }

    @Override
    public LabelledTree<Tag, Reference> visit(Conjunction conjunction) {
      var partition = FormulaPartition.of(conjunction.children);
      var leaves = createLeaves(partition, Conjunction::of);
      return leaves.size() == 1 ? leaves.get(0) : new LabelledTree.Node<>(Tag.CONJUNCTION, leaves);
    }

    @Override
    public LabelledTree<Tag, Reference> visit(Disjunction disjunction) {
      var partition = FormulaPartition.of(disjunction.children);
      var leaves = createLeaves(partition, Disjunction::of);
      return leaves.size() == 1 ? leaves.get(0) : new LabelledTree.Node<>(Tag.DISJUNCTION, leaves);
    }
  }
}
