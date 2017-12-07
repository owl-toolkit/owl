package owl.jni;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import owl.collections.LabelledTree;
import owl.collections.LabelledTree.Leaf;
import owl.collections.LabelledTree.Node;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.visitors.DefaultVisitor;

public class Splitter {

  private static final Visitor VISITOR = new Visitor();

  private static LabelledTree<Tag, IntAutomaton> createLeaf(Formula formula) {
    return new Leaf<>(IntAutomaton.of(formula));
  }

  public static LabelledTree<Tag, IntAutomaton> split(Formula formula) {
    return RewriterFactory.apply(formula).accept(VISITOR);
  }

  enum Tag {
    CONJUNCTION, DISJUNCTION
  }

  private static final class Grouping {
    List<Formula> cosafety = new ArrayList<>();
    List<Formula> dba = new ArrayList<>();
    List<Formula> dca = new ArrayList<>();
    List<Formula> mixed = new ArrayList<>();
    List<Formula> safety = new ArrayList<>();
  }

  static class Visitor extends DefaultVisitor<LabelledTree<Tag, IntAutomaton>> {
    private static Grouping group(Iterable<Formula> input) {
      Grouping grouping = new Grouping();

      input.forEach(x -> {
        if (Fragments.isSafety(x)) {
          grouping.safety.add(x);
        } else if (Fragments.isCoSafety(x)) {
          grouping.cosafety.add(x);
        } else if (Fragments.isDetBuchiRecognisable(x)) {
          grouping.dba.add(x);
        } else if (Fragments.isDetCoBuchiRecognisable(x)) {
          grouping.dca.add(x);
        } else {
          grouping.mixed.add(x);
        }
      });

      return grouping;
    }

    @Override
    protected LabelledTree<Tag, IntAutomaton> defaultAction(Formula formula) {
      return new Leaf<>(IntAutomaton.of(formula));
    }

    @Override
    public LabelledTree<Tag, IntAutomaton> visit(Conjunction conjunction) {
      Grouping grouping = group(conjunction.children);

      List<LabelledTree<Tag, IntAutomaton>> children = new ArrayList<>();

      if (!grouping.safety.isEmpty()) {
        children.add(createLeaf(Conjunction.of(grouping.safety)));
      }

      if (!grouping.cosafety.isEmpty()) {
        children.add(createLeaf(Conjunction.of(grouping.cosafety)));
      }

      grouping.dba.forEach(x -> children.add(createLeaf(x)));
      grouping.dca.forEach(x -> children.add(createLeaf(x)));

      if (grouping.mixed.size() == 1) {
        children.add(Iterables.getOnlyElement(grouping.mixed).accept(this));
      } else if (grouping.mixed.size() > 1) {
        children.add(createLeaf(Conjunction.of(grouping.mixed)));
      }

      return new Node<>(Tag.CONJUNCTION, children);
    }

    @Override
    public LabelledTree<Tag, IntAutomaton> visit(Disjunction disjunction) {
      Grouping grouping = group(disjunction.children);

      List<LabelledTree<Tag, IntAutomaton>> children = new ArrayList<>();

      if (!grouping.safety.isEmpty()) {
        children.add(createLeaf(Disjunction.of(grouping.safety)));
      }

      if (!grouping.cosafety.isEmpty()) {
        children.add(createLeaf(Disjunction.of(grouping.cosafety)));
      }

      grouping.dba.forEach(x -> children.add(createLeaf(x)));
      grouping.dca.forEach(x -> children.add(createLeaf(x)));

      if (grouping.mixed.size() == 1) {
        children.add(Iterables.getOnlyElement(grouping.mixed).accept(this));
      } else if (grouping.mixed.size() > 1) {
        children.add(createLeaf(Disjunction.of(grouping.mixed)));
      }

      return new Node<>(Tag.DISJUNCTION, children);
    }
  }
}