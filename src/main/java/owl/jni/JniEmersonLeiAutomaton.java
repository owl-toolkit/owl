package owl.jni;

import static owl.translations.ltl2dpa.LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
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
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.LiteralMapper;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.util.FormulaIsomorphism;
import owl.ltl.visitors.DefaultVisitor;
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
    this.automata = ImmutableList.copyOf(automata);
  }

  public static JniEmersonLeiAutomaton of(Formula formula, boolean simplify, boolean monolithic,
    SafetySplittingMode mode, boolean onTheFly) {
    Formula processedFormula = simplify
      ? SimplifierFactory.apply(formula, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT)
      : formula;

    Builder builder = new Builder(mode, onTheFly);

    LabelledTree<Tag, Reference> structure = monolithic
      ? builder.defaultAction(processedFormula)
      : processedFormula.accept(builder);

    return new JniEmersonLeiAutomaton(structure, builder.automata.build());
  }

  public static JniEmersonLeiAutomaton of(Formula formula, boolean simplify, boolean monolithic,
    int mode, boolean onTheFly) {
    return of(formula, simplify, monolithic, SafetySplittingMode.values()[mode], onTheFly);
  }

  enum Tag {
    CONJUNCTION, DISJUNCTION
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

  static final class Builder extends DefaultVisitor<LabelledTree<Tag, Reference>> {
    private final boolean onTheFly;
    private final SafetySplittingMode safetySplittingMode;
    private final Environment environment = DefaultEnvironment.standard();

    private int counter = 0;
    private final ImmutableList.Builder<JniAutomaton> automata = ImmutableList.builder();
    private final Map<Formula, Reference> lookup = new HashMap<>();

    public Builder(SafetySplittingMode safetySplittingMode, boolean onTheFly) {
      this.onTheFly = onTheFly;
      this.safetySplittingMode = safetySplittingMode;
    }

    private LabelledTree<Tag, Reference> createLeaf(Formula formula) {
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
      JniAutomaton automaton = of(shiftedFormula.formula);
      automata.add(automaton);
      reference = new Reference(formula, counter, shiftedFormula.mapping);
      counter++;
      lookup.put(formula, reference);
      return new LabelledTree.Leaf<>(reference);
    }

    private JniAutomaton of(Formula formula) {
      LabelledFormula labelledFormula = Hacks.attachDummyAlphabet(formula);

      if (Fragments.isSafety(formula)) {
        return new JniAutomaton(SimpleTranslations.buildSafety(labelledFormula, environment));
      }

      if (Fragments.isCoSafety(formula)) {
        // Acceptance needs to be overridden, since detection does not work in this case.
        return new JniAutomaton(SimpleTranslations.buildCoSafety(labelledFormula, environment),
          JniAutomaton.Acceptance.CO_SAFETY);
      }

      if (Fragments.isDetBuchiRecognisable(formula)) {
        return new JniAutomaton(SimpleTranslations.buildBuchi(labelledFormula, environment));
      }

      if (Fragments.isDetCoBuchiRecognisable(formula)) {
        return new JniAutomaton(SimpleTranslations.buildCoBuchi(labelledFormula, environment));
      }

      // Fallback to DPA
      Set<LTL2DPAFunction.Configuration> configuration = EnumSet
        .copyOf(RECOMMENDED_ASYMMETRIC_CONFIG);

      if (onTheFly) {
        configuration.add(LTL2DPAFunction.Configuration.GREEDY);
        configuration.remove(LTL2DPAFunction.Configuration.COMPRESS_COLOURS);
        configuration.remove(LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE);
      }

      return new JniAutomaton(
        new LTL2DPAFunction(environment, configuration).apply(labelledFormula));
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

      if (partition.mixed.size() == 1) {
        children.add(Iterables.getOnlyElement(partition.mixed).accept(this));
      } else if (partition.mixed.size() > 1) {
        children.add(createLeaf(merger.apply(partition.mixed)));
      }

      return children;
    }

    @Override
    protected LabelledTree<Tag, Reference> defaultAction(Formula formula) {
      return createLeaf(formula);
    }

    @Override
    public LabelledTree<Tag, Reference> visit(Conjunction conjunction) {
      FormulaPartition partition = FormulaPartition.of(conjunction.children);
      return new LabelledTree.Node<>(Tag.CONJUNCTION, createLeaves(partition, Conjunction::of));
    }

    @Override
    public LabelledTree<Tag, Reference> visit(Disjunction disjunction) {
      FormulaPartition partition = FormulaPartition.of(disjunction.children);
      return new LabelledTree.Node<>(Tag.DISJUNCTION, createLeaves(partition, Disjunction::of));
    }
  }
}
