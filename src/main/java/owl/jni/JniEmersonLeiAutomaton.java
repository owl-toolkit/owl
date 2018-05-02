package owl.jni;

import static owl.jni.JniAutomaton.Acceptance;
import static owl.translations.ltl2dpa.LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG;

import com.google.common.primitives.ImmutableIntArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import owl.collections.LabelledTree;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.PropositionalFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.rewriter.LiteralMapper;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.util.FormulaIsomorphism;
import owl.ltl.visitors.PropositionalVisitor;
import owl.ltl.visitors.SubstitutionVisitor;
import owl.run.DefaultEnvironment;
import owl.run.Environment;
import owl.translations.SimpleTranslations;
import owl.translations.ltl2dpa.LTL2DPAFunction;

// This is a JNI entry point. No touching.
@SuppressWarnings({"unused"})
public class JniEmersonLeiAutomaton {

  public final LabelledTree<Tag, Reference> structure;
  public final List<JniAutomaton> automata;

  private JniEmersonLeiAutomaton(LabelledTree<Tag, Reference> structure,
    List<JniAutomaton> automata) {
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

  enum SafetySplittingMode {
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
    private final Environment environment = DefaultEnvironment.standard();

    private int counter = 0;
    private final List<JniAutomaton> automata = new ArrayList<>();
    private final Map<Formula, Reference> lookup = new HashMap<>();

    // TODO make it a loading cache.
    private final Map<Formula, Acceptance> annotatedTree;
    private final LTL2DPAFunction translator;

    public Builder(SafetySplittingMode safetySplittingMode, boolean onTheFly,
      Map<Formula, Acceptance> annotatedTree) {
      this.safetySplittingMode = safetySplittingMode;
      this.annotatedTree = new HashMap<>(annotatedTree);

      var configuration = EnumSet.copyOf(RECOMMENDED_ASYMMETRIC_CONFIG);

      if (onTheFly) {
        configuration.add(LTL2DPAFunction.Configuration.GREEDY);
        configuration.remove(LTL2DPAFunction.Configuration.COMPRESS_COLOURS);
        configuration.remove(LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE);
      }

      translator = new LTL2DPAFunction(environment, configuration);
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
      JniAutomaton automaton;

      LabelledFormula labelledFormula = Hacks.attachDummyAlphabet(shiftedFormula.formula);

      if (SyntacticFragment.SAFETY.contains(shiftedFormula.formula)) {
        automaton = new JniAutomaton(SimpleTranslations.buildSafety(labelledFormula, environment));
      } else if (SyntacticFragment.CO_SAFETY.contains(shiftedFormula.formula)) {
        // Acceptance needs to be overridden, since detection does not work in this case.
        automaton = new JniAutomaton(SimpleTranslations.buildCoSafety(labelledFormula, environment),
          Acceptance.CO_SAFETY);
      } else if (SyntacticFragment.isDetBuchiRecognisable(shiftedFormula.formula)) {
        automaton = new JniAutomaton(SimpleTranslations.buildBuchi(labelledFormula, environment));
      } else if (SyntacticFragment.isDetCoBuchiRecognisable(shiftedFormula.formula)) {
        automaton = new JniAutomaton(SimpleTranslations.buildCoBuchi(labelledFormula, environment));
      } else {
        // Fallback to DPA
        automaton = new JniAutomaton(translator.apply(labelledFormula));
      }

      automata.add(automaton);
      reference = new Reference(formula, counter, shiftedFormula.mapping);
      counter++;
      lookup.put(formula, reference);
      return new LabelledTree.Leaf<>(reference);
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
          if (!partition.singleStepSafety.isEmpty()) {
            children.add(createLeaf(merger.apply(partition.singleStepSafety)));
          }

          partition.safety.forEach(x -> children.add(createLeaf(merger.apply(x))));
          partition.cosafety.forEach(x -> children.add(createLeaf(merger.apply(x))));
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

      partition.mixed.forEach(x -> {
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

      if (SyntacticFragment.isDetBuchiRecognisable(formula)) {
        return Map.of(formula, Acceptance.BUCHI);
      }

      if (SyntacticFragment.isDetCoBuchiRecognisable(formula)) {
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
