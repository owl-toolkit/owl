package owl.collections;

import java.util.List;

public abstract class LabelledTree<L1, L2> {
  public static class Leaf<L1, L2> extends LabelledTree<L1, L2> {
    private final L2 label;

    public Leaf(L2 label) {
      this.label = label;
    }

    public L2 getLabel() {
      return label;
    }
  }

  public static class Node<L1, L2> extends LabelledTree<L1, L2> {
    private final L1 label;
    private final List<LabelledTree<L1, L2>> children;

    public Node(L1 label, List<LabelledTree<L1, L2>> children) {
      this.label = label;
      this.children = List.copyOf(children);
    }

    public L1 getLabel() {
      return label;
    }

    public List<LabelledTree<L1, L2>> getChildren() {
      return children;
    }
  }
}
