package owl.jni;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import owl.collections.LabelledTree;
import owl.collections.LabelledTree.Leaf;
import owl.collections.LabelledTree.Node;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.visitors.Collector;
import owl.ltl.visitors.DefaultVisitor;

public final class Splitter {
  private Splitter() {}

  public static LabelledTree<Tag, IntAutomaton> split(Formula formula, boolean onTheFly,
    SafetySplitting safetySplitting) {
    return SimplifierFactory.apply(formula, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT)
      .accept(new Visitor(onTheFly, safetySplitting));
  }

  public static LabelledTree<Tag, IntAutomaton> split(Formula formula, boolean onTheFly,
    int safetySplitting) {
    return split(formula, onTheFly, SafetySplitting.values()[safetySplitting]);
  }

  enum Tag {
    CONJUNCTION, DISJUNCTION
  }

  enum SafetySplitting {
    NEVER, AUTO, ALWAYS
  }

  private static boolean isIndependent(Iterable<Formula> x, Formula y) {
    BitSet atoms1 = Collector.collectAtoms(x);
    BitSet atoms2 = Collector.collectAtoms(y);
    return BitSets.isDisjoint(atoms1, atoms2);
  }

  static Set<Formula> merge(List<Set<Formula>> formulas, Formula formula) {
    Set<Formula> toBeMerged = new HashSet<>();
    toBeMerged.add(formula);

    formulas.removeIf(x -> {
      if (isIndependent(x, formula)) {
        return false;
      }

      toBeMerged.addAll(x);
      return true;
    });

    return toBeMerged;
  }

  static final class Partition {
    List<Set<Formula>> cosafety = new ArrayList<>();
    List<Formula> dba = new ArrayList<>();
    List<Formula> dca = new ArrayList<>();
    List<Formula> mixed = new ArrayList<>();
    List<Set<Formula>> safety = new ArrayList<>();
    List<Formula> singleStepSafety = new ArrayList<>();

    Set<Formula> safety() {
      return Sets.newHashSet(Iterables.concat(singleStepSafety, Iterables.concat(safety)));
    }

    Set<Formula> cosafety() {
      return Sets.newHashSet(Iterables.concat(cosafety));
    }

    static Partition partition(Set<Formula> input) {
      Partition partition = new Partition();

      input.forEach(x -> {
        if (Fragments.isSafety(x)) {
          if (x instanceof GOperator && Fragments.isSingleStep(((GOperator) x).operand)) {
            partition.singleStepSafety.add(x);
          } else {
            partition.safety.add(merge(partition.safety, x));
          }
        } else if (Fragments.isCoSafety(x)) {
          partition.cosafety.add(merge(partition.cosafety, x));
        } else if (Fragments.isDetBuchiRecognisable(x)) {
          partition.dba.add(x);
        } else if (Fragments.isDetCoBuchiRecognisable(x)) {
          partition.dca.add(x);
        } else {
          partition.mixed.add(x);
        }
      });

      return partition;
    }
  }

  static final class Visitor extends DefaultVisitor<LabelledTree<Tag, IntAutomaton>> {
    private final boolean onTheFly;
    private final SafetySplitting safetySplitting;

    public Visitor(boolean onTheFly, SafetySplitting safetySplitting) {
      this.onTheFly = onTheFly;
      this.safetySplitting = safetySplitting;
    }

    private LabelledTree<Tag, IntAutomaton> createLeaf(Formula formula) {
      return new Leaf<>(IntAutomaton.of(formula, onTheFly));
    }

    private List<LabelledTree<Tag, IntAutomaton>> createLeaves(Partition partition,
      Function<Iterable<Formula>, Formula> merger) {
      List<LabelledTree<Tag, IntAutomaton>> children = new ArrayList<>();

      Set<Formula> safety = partition.safety();
      Set<Formula> coSafety = partition.cosafety();

      switch (safetySplitting) {
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
    protected LabelledTree<Tag, IntAutomaton> defaultAction(Formula formula) {
      return createLeaf(formula);
    }

    @Override
    public LabelledTree<Tag, IntAutomaton> visit(Conjunction conjunction) {
      Partition partition = Partition.partition(conjunction.children);
      return new Node<>(Tag.CONJUNCTION, createLeaves(partition, Conjunction::of));
    }

    @Override
    public LabelledTree<Tag, IntAutomaton> visit(Disjunction disjunction) {
      Partition partition = Partition.partition(disjunction.children);
      return new Node<>(Tag.DISJUNCTION, createLeaves(partition, Disjunction::of));
    }
  }
}