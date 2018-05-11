package owl.jni;

import static owl.jni.JniAutomaton.Acceptance;

import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayList;
import java.util.Arrays;
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
import owl.collections.LabelledTree;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.PropositionalFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.rewriter.LiteralMapper;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.util.FormulaIsomorphism;
import owl.ltl.visitors.PropositionalVisitor;
import owl.ltl.visitors.SubstitutionVisitor;
import owl.run.DefaultEnvironment;
import owl.translations.LTL2DAFunction;

// This is a JNI entry point. No touching.
@SuppressWarnings("unused")
public class JniEmersonLeiAutomaton {

  public final LabelledTree<Tag, Reference> structure;
  public final List<JniAutomaton<?>> automata;

  private JniEmersonLeiAutomaton(LabelledTree<Tag, Reference> structure,
    List<JniAutomaton<?>> automata) {
    this.structure = structure;
    this.automata = automata;
  }

  public static JniEmersonLeiAutomaton of(Formula formula, boolean simplify, boolean monolithic,
    SafetySplittingMode mode, boolean onTheFly) {
    Formula nnfLight = formula.accept(new SubstitutionVisitor(Formula::nnf));

    Formula processedFormula = simplify
      ? SimplifierFactory.apply(nnfLight, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT)
      : nnfLight;

    Builder builder = new Builder(mode, onTheFly,
      processedFormula.accept(AcceptanceAnnotator.INSTANCE));

    LabelledTree<Tag, Reference> structure = monolithic
      ? builder.modalOperatorAction(processedFormula)
      : processedFormula.accept(builder);

    return new JniEmersonLeiAutomaton(structure, List.copyOf(builder.automata));
  }

  public static JniEmersonLeiAutomaton of(Formula formula, boolean simplify, boolean monolithic,
    int mode, boolean onTheFly) {
    return of(formula, simplify, monolithic, SafetySplittingMode.values()[mode], onTheFly);
  }

  enum Tag {
    BICONDITIONAL, CONJUNCTION, DISJUNCTION
  }

  public enum SafetySplittingMode {
    NEVER, AUTO, ALWAYS
  }

  @SuppressWarnings("PMD.DataClass")
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

    public int[] alphabetMapping() {
      return alphabetMapping.toArray();
    }
  }

  static final class Builder extends PropositionalVisitor<LabelledTree<Tag, Reference>> {
    private final SafetySplittingMode safetySplittingMode;
    private final List<JniAutomaton<?>> automata = new ArrayList<>();
    private final Map<Formula, Reference> lookup = new HashMap<>();
    private final Map<Formula, Acceptance> annotatedTree;
    private final LTL2DAFunction translator;

    public Builder(SafetySplittingMode safetySplittingMode, boolean onTheFly,
      Map<Formula, Acceptance> annotatedTree) {
      this.safetySplittingMode = safetySplittingMode;
      this.annotatedTree = new HashMap<>(annotatedTree);

      translator = new LTL2DAFunction(DefaultEnvironment.standard(), onTheFly, EnumSet.of(
        LTL2DAFunction.Constructions.SAFETY,
        LTL2DAFunction.Constructions.CO_SAFETY,
        LTL2DAFunction.Constructions.BUCHI,
        LTL2DAFunction.Constructions.CO_BUCHI,
        LTL2DAFunction.Constructions.PARITY));
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
      LabelledFormula labelledFormula = Hacks.attachDummyAlphabet(shiftedFormula.formula);

      Automaton<?, ?> automaton = translator.apply(labelledFormula);
      JniAutomaton<?> jniAutomaton;

      if (SyntacticFragment.SAFETY.contains(shiftedFormula.formula)) {
        jniAutomaton = new JniAutomaton<>(AutomatonUtil.cast(automaton, EquivalenceClass.class,
          AllAcceptance.class), EquivalenceClass::isTrue);
      } else if (SyntacticFragment.CO_SAFETY.contains(shiftedFormula.formula)) {
        // Acceptance needs to be overridden, since detection does not work in this case.
        jniAutomaton = new JniAutomaton<>(AutomatonUtil.cast(automaton, EquivalenceClass.class,
          BuchiAcceptance.class), EquivalenceClass::isTrue, Acceptance.CO_SAFETY);
      } else {
        jniAutomaton = new JniAutomaton<>(automaton, x -> false);
      }

      Reference newReference = new Reference(formula, automata.size(), shiftedFormula.mapping);
      automata.add(jniAutomaton);
      lookup.put(formula, newReference);
      return new LabelledTree.Leaf<>(newReference);
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
        if (annotatedTree.get(x) == Acceptance.PARITY) {
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
            annotatedTree.putAll(dnfNormalForm.accept(AcceptanceAnnotator.INSTANCE));
            children.add(dnfNormalForm.accept(this));
            return children;
          }
        }

        if (mergedFormula instanceof Conjunction) {
          var cnfNormalForm = NormalForms.toCnfFormula(mergedFormula);

          if (!mergedFormula.equals(cnfNormalForm)) {
            annotatedTree.putAll(cnfNormalForm.accept(AcceptanceAnnotator.INSTANCE));
            children.add(cnfNormalForm.accept(this));
            return children;
          }
        }

        children.add(createLeaf(mergedFormula));
      }

      return children;
    }

    @Override
    protected LabelledTree<Tag, Reference> modalOperatorAction(Formula formula) {
      return createLeaf(formula);
    }

    @Override
    public LabelledTree<Tag, Reference> visit(Biconditional biconditional) {
      if (annotatedTree.get(biconditional.left) == Acceptance.PARITY
        && annotatedTree.get(biconditional.right) == Acceptance.PARITY) {
        var nnf = biconditional.nnf();
        annotatedTree.putAll(nnf.accept(AcceptanceAnnotator.INSTANCE));
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

  private static class AcceptanceAnnotator extends PropositionalVisitor<Map<Formula, Acceptance>> {
    static final AcceptanceAnnotator INSTANCE = new AcceptanceAnnotator();

    @Override
    protected Map<Formula, Acceptance> modalOperatorAction(Formula formula) {
      if (SyntacticFragment.SAFETY.contains(formula)) {
        return Map.of(formula, Acceptance.SAFETY);
      }

      if (SyntacticFragment.CO_SAFETY.contains(formula)) {
        return Map.of(formula, Acceptance.CO_SAFETY);
      }

      if (SyntacticFragments.isDetBuchiRecognisable(formula)) {
        return Map.of(formula, Acceptance.BUCHI);
      }

      if (SyntacticFragments.isDetCoBuchiRecognisable(formula)) {
        return Map.of(formula, Acceptance.CO_BUCHI);
      }

      return Map.of(formula, Acceptance.PARITY);
    }

    @Override
    public Map<Formula, Acceptance> visit(Biconditional biconditional) {
      Map<Formula, Acceptance> acceptanceMap = new HashMap<>();

      acceptanceMap.putAll(biconditional.left.accept(this));
      acceptanceMap.putAll(biconditional.right.accept(this));

      Acceptance leftAcceptance = acceptanceMap.get(biconditional.left);
      Acceptance rightAcceptance = acceptanceMap.get(biconditional.right);

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

    private Map<Formula, Acceptance> visitPropositional(PropositionalFormula propositionalFormula) {
      Acceptance acceptance = Acceptance.BOTTOM;
      Map<Formula, Acceptance> acceptanceMap = new HashMap<>();

      for (Formula child : propositionalFormula.children) {
        Map<Formula, Acceptance> childDecisions = child.accept(this);
        acceptanceMap.putAll(childDecisions);
        acceptance = acceptance.lub(acceptanceMap.get(child));
      }

      acceptanceMap.put(propositionalFormula, acceptance);
      return acceptanceMap;
    }
  }
}
