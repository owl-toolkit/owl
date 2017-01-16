package owl.bdd;

import static owl.bdd.SyntaxTree.SyntaxTreeTernaryOperation.TernaryType.ITE;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import owl.bdd.SyntaxTree.SyntaxTreeBinaryOperation.BinaryType;

/**
 * Utility class used to represent propositional formulae.
 */
@SuppressWarnings({"AccessingNonPublicFieldOfAnotherObject", "QuestionableName", "unused",
                    "WeakerAccess", "PMD.GodClass", "checkstyle:javadoc"})
// TODO Add a "toBddNode(BDD bdd)" method
final class SyntaxTree {
  private final SyntaxTreeNode rootNode;
  private final Map<BitSet, Boolean> valuationCache = new HashMap<>();

  SyntaxTree(final SyntaxTreeNode rootNode) {
    this.rootNode = rootNode;
  }

  static SyntaxTree and(final SyntaxTree left, final SyntaxTree right) {
    return new SyntaxTree(new SyntaxTreeBinaryOperation(left.getRootNode(), right.getRootNode(),
      BinaryType.AND));
  }

  static SyntaxTree buildReplacementTree(final SyntaxTree base,
    final Int2ObjectMap<SyntaxTree> replacements) {
    return new SyntaxTree(buildReplacementTreeRecursive(base.rootNode, replacements));
  }

  private static SyntaxTreeNode buildReplacementTreeRecursive(final SyntaxTreeNode currentNode,
    final Int2ObjectMap<SyntaxTree> replacements) {
    if (currentNode instanceof SyntaxTreeConstant) {
      return currentNode;
    }
    if (currentNode instanceof SyntaxTreeLiteral) {
      final int variableNumber = ((SyntaxTreeLiteral) currentNode).getVariableNumber();
      if (replacements.containsKey(variableNumber)) {
        return replacements.get(variableNumber).rootNode;
      }
      return currentNode;
    }
    if (currentNode instanceof SyntaxTreeNot) {
      final SyntaxTreeNot unaryNode = (SyntaxTreeNot) currentNode;
      return new SyntaxTreeNot(buildReplacementTreeRecursive(unaryNode.child, replacements));
    }
    if (currentNode instanceof SyntaxTreeBinaryOperation) {
      final SyntaxTreeBinaryOperation binaryNode = (SyntaxTreeBinaryOperation) currentNode;
      return new SyntaxTreeBinaryOperation(
        buildReplacementTreeRecursive(binaryNode.getLeft(), replacements),
        buildReplacementTreeRecursive(binaryNode.getRight(), replacements),
        binaryNode.getType()
      );
    }
    if (currentNode instanceof SyntaxTreeTernaryOperation) {
      final SyntaxTreeTernaryOperation ternaryNode = (SyntaxTreeTernaryOperation) currentNode;
      return new SyntaxTreeTernaryOperation(
        buildReplacementTreeRecursive(ternaryNode.getFirst(), replacements),
        buildReplacementTreeRecursive(ternaryNode.getSecond(), replacements),
        buildReplacementTreeRecursive(ternaryNode.getThird(), replacements),
        ternaryNode.getType()
      );
    }
    throw new IllegalArgumentException("Unknown type " + currentNode.getClass().getSimpleName());
  }

  static SyntaxTree constant(final boolean value) {
    return new SyntaxTree(new SyntaxTreeConstant(value));
  }

  static SyntaxTree equivalence(final SyntaxTree left, final SyntaxTree right) {
    return new SyntaxTree(new SyntaxTreeBinaryOperation(left.getRootNode(), right.getRootNode(),
      BinaryType.EQUIVALENCE));
  }

  static SyntaxTree ifThenElse(final SyntaxTree first, final SyntaxTree second,
    final SyntaxTree third) {
    return new SyntaxTree(new SyntaxTreeTernaryOperation(first.getRootNode(), second.getRootNode(),
      third.getRootNode(), ITE));
  }

  static SyntaxTree implication(final SyntaxTree left, final SyntaxTree right) {
    return new SyntaxTree(new SyntaxTreeBinaryOperation(left.getRootNode(), right.getRootNode(),
      BinaryType.IMPLICATION));
  }

  static SyntaxTree literal(final int variableNumber) {
    return new SyntaxTree(new SyntaxTreeLiteral(variableNumber));
  }

  static SyntaxTree not(final SyntaxTree tree) {
    return new SyntaxTree(new SyntaxTreeNot(tree.getRootNode()));
  }

  static SyntaxTree or(final SyntaxTree left, final SyntaxTree right) {
    return new SyntaxTree(new SyntaxTreeBinaryOperation(left.getRootNode(), right.getRootNode(),
      BinaryType.OR));
  }

  static SyntaxTree xor(final SyntaxTree left, final SyntaxTree right) {
    return new SyntaxTree(
      new SyntaxTreeBinaryOperation(left.getRootNode(), right.getRootNode(), BinaryType.XOR));
  }

  public IntSet containedVariables() {
    final IntSet set = new IntOpenHashSet();
    rootNode.gatherVariables(set);
    return set;
  }

  public int depth() {
    return rootNode.depth();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SyntaxTree)) {
      return false;
    }
    final SyntaxTree that = (SyntaxTree) o;
    return Objects.equals(rootNode, that.rootNode);
  }

  boolean evaluate(final BitSet valuation) {
    Boolean value = valuationCache.get(valuation);
    if (value == null) {
      value = rootNode.evaluate(valuation);
      valuationCache.put(valuation, value);
    }
    return value;
  }

  SyntaxTreeNode getRootNode() {
    return rootNode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(rootNode);
  }

  @Override
  public String toString() {
    return rootNode.toString();
  }

  static final class SyntaxTreeBinaryOperation extends SyntaxTreeNode {
    private final SyntaxTreeNode left;
    private final SyntaxTreeNode right;
    private final BinaryType type;

    SyntaxTreeBinaryOperation(final SyntaxTreeNode left, final SyntaxTreeNode right,
      final BinaryType type) {
      super();
      this.left = left;
      this.right = right;
      this.type = type;
    }

    @Override
    public int depth() {
      return Math.max(left.depth(), right.depth()) + 1;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SyntaxTreeBinaryOperation)) {
        return false;
      }
      final SyntaxTreeBinaryOperation that = (SyntaxTreeBinaryOperation) o;
      return Objects.equals(left, that.left) &&
        Objects.equals(right, that.right) &&
        type == that.type;
    }

    @Override
    boolean evaluate(final BitSet valuation) {
      switch (type) {
        case AND:
          return left.evaluate(valuation) && right.evaluate(valuation);
        case OR:
          return left.evaluate(valuation) || right.evaluate(valuation);
        case XOR:
          return left.evaluate(valuation) ^ right.evaluate(valuation);
        case IMPLICATION:
          return !left.evaluate(valuation) || right.evaluate(valuation);
        case EQUIVALENCE:
          return left.evaluate(valuation) == right.evaluate(valuation);
        default:
          throw new IllegalStateException("Unknown type");
      }
    }

    @Override
    public void gatherVariables(final IntSet set) {
      left.gatherVariables(set);
      right.gatherVariables(set);
    }

    SyntaxTreeNode getLeft() {
      return left;
    }

    SyntaxTreeNode getRight() {
      return right;
    }

    BinaryType getType() {
      return type;
    }

    @Override
    public boolean hasVariable(final int number) {
      return left.hasVariable(number) || right.hasVariable(number);
    }

    @Override
    public int hashCode() {
      return Objects.hash(left, right, type);
    }

    @Override
    public String toString() {
      return type + "[" + left + "," + right + "]";
    }

    enum BinaryType {
      AND, OR, XOR, IMPLICATION, EQUIVALENCE
    }
  }

  static final class SyntaxTreeConstant extends SyntaxTreeNode {
    private final boolean value;

    SyntaxTreeConstant(final boolean value) {
      super();
      this.value = value;
    }

    @Override
    public int depth() {
      return 1;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SyntaxTreeConstant)) {
        return false;
      }
      final SyntaxTreeConstant that = (SyntaxTreeConstant) o;
      return value == that.value;
    }

    @Override
    boolean evaluate(final BitSet valuation) {
      return value;
    }

    @Override
    public void gatherVariables(final IntSet set) {
      // No variables in this leaf
    }

    @Override
    public boolean hasVariable(final int number) {
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  static final class SyntaxTreeLiteral extends SyntaxTreeNode {
    private final int variableNumber;

    SyntaxTreeLiteral(final int variableNumber) {
      super();
      this.variableNumber = variableNumber;
    }

    @Override
    public int depth() {
      return 1;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SyntaxTreeLiteral)) {
        return false;
      }
      final SyntaxTreeLiteral that = (SyntaxTreeLiteral) o;
      return variableNumber == that.variableNumber;
    }

    @Override
    boolean evaluate(final BitSet valuation) {
      return valuation.get(variableNumber);
    }

    @Override
    public void gatherVariables(final IntSet set) {
      set.add(variableNumber);
    }

    public int getVariableNumber() {
      return variableNumber;
    }

    @Override
    public boolean hasVariable(final int number) {
      return variableNumber == number;
    }

    @Override
    public int hashCode() {
      return Objects.hash(variableNumber);
    }

    @Override
    public String toString() {
      return String.valueOf(variableNumber);
    }
  }

  abstract static class SyntaxTreeNode {
    public abstract int depth();

    abstract boolean evaluate(BitSet valuation);

    public abstract void gatherVariables(IntSet set);

    public abstract boolean hasVariable(int number);
  }

  static final class SyntaxTreeNot extends SyntaxTreeNode {
    private final SyntaxTreeNode child;

    SyntaxTreeNot(final SyntaxTreeNode child) {
      super();
      this.child = child;
    }

    @Override
    public int depth() {
      // Unary operations are not relevant for bdd complexity
      return child.depth();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SyntaxTreeNot)) {
        return false;
      }
      final SyntaxTreeNot that = (SyntaxTreeNot) o;
      return Objects.equals(child, that.child);
    }

    @Override
    boolean evaluate(final BitSet valuation) {
      return !child.evaluate(valuation);
    }

    @Override
    public void gatherVariables(final IntSet set) {
      child.gatherVariables(set);
    }

    SyntaxTreeNode getChild() {
      return child;
    }

    @Override
    public boolean hasVariable(final int number) {
      return child.hasVariable(number);
    }

    @Override
    public int hashCode() {
      return Objects.hash(child);
    }

    @Override
    public String toString() {
      return "NOT[" + child + "]";
    }
  }

  static final class SyntaxTreeTernaryOperation extends SyntaxTreeNode {
    private final SyntaxTreeNode first;
    private final SyntaxTreeNode second;
    private final SyntaxTreeNode third;
    private final TernaryType type;

    SyntaxTreeTernaryOperation(final SyntaxTreeNode first, final SyntaxTreeNode second,
      final SyntaxTreeNode third, final TernaryType type) {
      super();
      this.first = first;
      this.second = second;
      this.third = third;
      this.type = type;
    }

    @Override
    public int depth() {
      return Math.max(Math.max(first.depth(), second.depth()), third.depth()) + 1;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SyntaxTreeTernaryOperation)) {
        return false;
      }
      final SyntaxTreeTernaryOperation that = (SyntaxTreeTernaryOperation) o;
      return Objects.equals(first, that.first) &&
        Objects.equals(second, that.second) &&
        Objects.equals(third, that.third) &&
        type == that.type;
    }

    @Override
    boolean evaluate(final BitSet valuation) {
      if (type == ITE) {
        if (first.evaluate(valuation)) {
          return second.evaluate(valuation);
        } else {
          return third.evaluate(valuation);
        }
      }
      throw new IllegalStateException("Unknown type");
    }

    @Override
    public void gatherVariables(final IntSet set) {
      first.gatherVariables(set);
      second.gatherVariables(set);
      third.gatherVariables(set);
    }

    SyntaxTreeNode getFirst() {
      return first;
    }

    SyntaxTreeNode getSecond() {
      return second;
    }

    SyntaxTreeNode getThird() {
      return third;
    }

    TernaryType getType() {
      return type;
    }

    @Override
    public boolean hasVariable(final int number) {
      return first.hasVariable(number) || second.hasVariable(number) || third.hasVariable(number);
    }

    @Override
    public int hashCode() {
      return Objects.hash(first, second, third, type);
    }

    @Override
    public String toString() {
      return type + "[" + first + "," + second + "," + third + "]";
    }

    enum TernaryType {
      ITE
    }
  }
}
