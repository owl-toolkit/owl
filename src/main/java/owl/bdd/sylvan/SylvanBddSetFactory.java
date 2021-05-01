package owl.bdd.sylvan;

import com.google.common.base.Preconditions;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.graalvm.nativeimage.CurrentIsolate;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;

/*
 * Warning: Concurrent BDD operations are not supported!
 */
public final class SylvanBddSetFactory implements BddSetFactory {
  public static final SylvanBddSetFactory INSTANCE = new SylvanBddSetFactory();
  private final ReferenceQueue<SylvanBddSet> referenceQueue = new ReferenceQueue<>();
  private final Map<Long, SylvanNodeReference> referencedNodes = new HashMap<>();
  private final HashMap<Long, Integer> tempNodes = new HashMap<>();
  private final SylvanBddSet falseNode;
  private final SylvanBddSet trueNode;

  private SylvanBddSetFactory() {
    SylvanBddNativeInterface.init();
    Thread exchangeThread = new Thread(() ->
      SylvanBddNativeInterface.exchangeLoop(CurrentIsolate.getCurrentThread())
    );
    exchangeThread.setDaemon(true);
    exchangeThread.start();
    Runtime.getRuntime().addShutdownHook(new Thread(SylvanBddNativeInterface::exit));
    falseNode = create(SylvanBddNativeInterface.falseNode());
    trueNode = create(SylvanBddNativeInterface.trueNode());
  }

  @SuppressWarnings("PMD.DoNotCallGarbageCollectionExplicitly")
  synchronized Set<Long> getReferencedNodes() {
    System.gc();
    purgeNodeMapping();
    Set<Long> nodes = new HashSet<>(referencedNodes.keySet());
    nodes.addAll(tempNodes.keySet());
    return nodes;
  }

  private synchronized SylvanBddSet create(long node) {
    purgeNodeMapping();
    // We check whether the node is actually new.
    SylvanNodeReference canonicalReference = referencedNodes.get(node);
    if (canonicalReference != null) {
      SylvanBddSet canonicalObject = canonicalReference.get();
      if (canonicalObject != null) {
        // We can use a cached node
        return canonicalObject;
      }
    }
    // The node is new, we create a new wrapper and put it in the mapping
    SylvanBddSet wrapper = new SylvanBddSet(node);
    referencedNodes.put(node, new SylvanNodeReference(wrapper));
    return wrapper;
  }

  private void purgeNodeMapping() {
    for (var reference = referenceQueue.poll();
         reference != null; reference = referenceQueue.poll()) {
      referencedNodes.remove(((SylvanNodeReference) reference).getNode());
    }
  }

  @Override
  public BddSet of(boolean booleanConstant) {
    return booleanConstant ? trueNode : falseNode;
  }

  @Override
  public BddSet of(int variable) {
    return create(SylvanBddNativeInterface.var(variable));
  }

  @Override
  public BddSet of(BitSet valuation, BitSet support) {
    BddSet result = create(_of(valuation, support));
    assert tempNodes.isEmpty();
    return result;
  }

  private long _of(BitSet valuation, BitSet support) {
    long current = protect(trueNode.node);
    for (int i = support.length() - 1; i >= 0; i = support.previousSetBit(i - 1)) {
      long var = protect(SylvanBddNativeInterface.var(i));
      long prev = current;
      if (valuation.get(i)) {
        current = protect(SylvanBddNativeInterface.and(var, current));
        unprotect(var);
      } else {
        long not = protect(SylvanBddNativeInterface.not(var));
        unprotect(var);
        current = protect(SylvanBddNativeInterface.and(not, current));
        unprotect(not);
      }
      unprotect(prev);
    }
    unprotect(current);
    return current;
  }

  private PropositionalFormula<Integer> toExpression(long node) {
    if (node == falseNode.getNode()) {
      return (PropositionalFormula<Integer>) PropositionalFormula.FALSE;
    }
    if (node == trueNode.getNode()) {
      return (PropositionalFormula<Integer>) PropositionalFormula.TRUE;
    }
    var atomicProposition = PropositionalFormula.Variable.of(SylvanBddNativeInterface.getvar(node));
    return PropositionalFormula.Disjunction.of(
      PropositionalFormula.Conjunction.of(
        atomicProposition,
        toExpression(SylvanBddNativeInterface.high(node))
      ),
      PropositionalFormula.Conjunction.of(
        PropositionalFormula.Negation.of(atomicProposition),
        toExpression(SylvanBddNativeInterface.low(node))
      )
    );
  }

  @Override
  public <S> MtBdd<S> toMtBdd(Map<? extends S, ? extends BddSet> sets) {
    MtBdd<S> union = MtBdd.of(Set.of());

    for (Map.Entry<? extends S, ? extends BddSet> entry : sets.entrySet()) {
      union = MtBddOperations.union(union,
        toTree(entry.getKey(), ((SylvanBddSet) entry.getValue()).getNode(), new HashMap<>()));
    }

    return union;
  }

  private <S> MtBdd<S> toTree(S value, long node, Map<Long, MtBdd<S>> cache) {
    var tree = cache.get(node);

    if (tree != null) {
      return tree;
    }

    if (node == falseNode.getNode()) {
      tree = MtBdd.of();
    } else if (node == trueNode.getNode()) {
      tree = MtBdd.of(Set.of(value));
    } else {
      tree = MtBdd.of(SylvanBddNativeInterface.getvar(node),
        toTree(value, SylvanBddNativeInterface.high(node), cache),
        toTree(value, SylvanBddNativeInterface.low(node), cache));
    }

    cache.put(node, tree);
    return tree;
  }

  private final class SylvanNodeReference extends WeakReference<SylvanBddSet> {
    private final long node;

    private SylvanNodeReference(SylvanBddSet set) {
      super(set, referenceQueue);
      node = set.getNode();
    }

    private long getNode() {
      return node;
    }
  }

  private final class SylvanBddSet implements BddSet {
    private final long node;

    private SylvanBddSet(long node) {
      this.node = node;
    }

    private long getNode() {
      return node;
    }

    @Override
    public BddSetFactory factory() {
      return SylvanBddSetFactory.this;
    }

    @Override
    public boolean isEmpty() {
      return equals(falseNode);
    }

    @Override
    public boolean isUniverse() {
      return equals(trueNode);
    }

    @Override
    public boolean contains(BitSet valuation) {
      return !intersection(of(valuation, valuation.length())).isEmpty();
    }

    @Override
    public boolean containsAll(BddSet set) {
      Preconditions.checkArgument(set instanceof SylvanBddSet);
      return intersection(set).equals(set);
    }

    @Override
    public Optional<BitSet> element() {
      protect(node);
      long cubeNode = protect(SylvanBddNativeInterface.satOneBdd(node));
      if (cubeNode == falseNode.getNode()) {
        unprotect(cubeNode, node);
        return Optional.empty();
      }
      var result = Optional.of(cubeToBitset(cubeNode));
      unprotect(cubeNode, node);
      return result;
    }

    @Override
    public BddSet complement() {
      return create(SylvanBddNativeInterface.not(node));
    }

    @Override
    public BddSet union(BddSet other) {
      Preconditions.checkArgument(other instanceof SylvanBddSet);
      protect(node);
      protect(((SylvanBddSet) other).node);
      BddSet result = create(SylvanBddNativeInterface.or(node, ((SylvanBddSet) other).node));
      unprotect(node, ((SylvanBddSet) other).node);
      return result;
    }

    @Override
    public BddSet intersection(BddSet other) {
      Preconditions.checkArgument(other instanceof SylvanBddSet);
      protect(node);
      protect(((SylvanBddSet) other).node);
      BddSet result = create(SylvanBddNativeInterface.and(node, ((SylvanBddSet) other).node));
      unprotect(node, ((SylvanBddSet) other).node);
      return result;
    }

    @Override
    public <E> MtBdd<E> intersection(MtBdd<E> tree) {
      return filter(tree, node);
    }

    private <E> MtBdd<E> filter(MtBdd<E> tree, long bddNode) {
      if (bddNode == falseNode.getNode()) {
        return MtBdd.of();
      }

      if (bddNode == trueNode.getNode()) {
        return tree;
      }

      int bddVariable = SylvanBddNativeInterface.getvar(bddNode);
      long bddHigh = SylvanBddNativeInterface.high(bddNode);
      long bddLow = SylvanBddNativeInterface.low(bddNode);

      if (tree instanceof MtBdd.Leaf) {
        return MtBdd.of(bddVariable, filter(tree, bddHigh), filter(tree, bddLow));
      }

      var node = (MtBdd.Node<E>) tree;

      if (bddVariable == node.variable) {
        return MtBdd.of(node.variable,
          filter(node.trueChild, bddHigh),
          filter(node.falseChild, bddLow));
      } else if (bddVariable < node.variable) {
        return MtBdd.of(bddVariable,
          filter(tree, bddHigh),
          filter(tree, bddLow));
      } else {
        return MtBdd.of(node.variable,
          filter(node.trueChild, bddNode),
          filter(node.falseChild, bddNode));
      }
    }

    @Override
    public BddSet project(ImmutableBitSet quantifiedAtomicPropositions) {
      protect(node);
      long vars = protect(SylvanBddNativeInterface.varsetFromBitset(
        quantifiedAtomicPropositions.copyInto(new BitSet()))
      );
      SylvanBddSet result = create(SylvanBddNativeInterface.exists(node, vars));
      unprotect(vars, node);
      assert tempNodes.isEmpty();
      return result;
    }

    @Override
    public BddSet restrict(BitSet restriction, BitSet support) {
      protect(node);
      long map = protect(falseNode.node);
      for (int i = support.nextSetBit(0); i >= 0; i = support.nextSetBit(i + 1)) {
        long node = restriction.get(i) ? trueNode.node : falseNode.node;
        long prevMap = map;
        map = protect(SylvanBddNativeInterface.mapAdd(map, i, node));
        unprotect(prevMap);
      }
      SylvanBddSet result = create(SylvanBddNativeInterface.compose(node, map));
      unprotect(map, node);
      assert tempNodes.isEmpty();
      return result;
    }

    @Override
    public BddSet relabel(IntUnaryOperator mapping) {
      protect(node);
      BitSet support = support();
      long map = protect(falseNode.node);
      for (int i = support.nextSetBit(0); i >= 0; i = support.nextSetBit(i + 1)) {
        int mappedTo = mapping.applyAsInt(i);
        if (mappedTo != i) {
          long var = protect(SylvanBddNativeInterface.var(mappedTo));
          long prevMap = map;
          map = protect(SylvanBddNativeInterface.mapAdd(map, i, var));
          unprotect(prevMap);
          unprotect(var);
        }
      }
      long result = SylvanBddNativeInterface.compose(node, map);
      unprotect(map, node);
      assert tempNodes.isEmpty();
      return create(result);
    }

    @Override
    public BitSet support() {
      return cubeToBitset(SylvanBddNativeInterface.support(node));
    }

    @Override
    public PropositionalFormula<Integer> toExpression() {
      return SylvanBddSetFactory.this.toExpression(node);
    }

    public BddSet determinizeRange(int from, int until) {
      protect(node);
      long result = determinizeRange(from, until, node, new BitSet());
      BddSet resultBdd = create(result);
      unprotect(node);
      assert tempNodes.isEmpty();
      return resultBdd;
    }

    private long determinizeRange(int from, int until, long node, BitSet support) {
      if (node == falseNode.node) {
        return node;
      }
      if (node == trueNode.node) {
        BitSet relevantVariables = new BitSet();
        relevantVariables.set(from, until);
        support.and(relevantVariables);
        support.flip(from, until);
        return _of(new BitSet(), support);
      }
      int currentVariable = SylvanBddNativeInterface.getvar(node);
      support.set(currentVariable);
      long low = protect(determinizeRange(from, until, SylvanBddNativeInterface.low(node),
        BitSet2.copyOf(support)));
      long variableNode = protect(SylvanBddNativeInterface.var(currentVariable));
      if (support.get(from) && low != falseNode.node) {
        long negatedVariableNode = protect(SylvanBddNativeInterface.not(variableNode));
        unprotect(variableNode);
        long result = SylvanBddNativeInterface.and(negatedVariableNode, low);
        unprotect(low, negatedVariableNode);
        return result;
      }
      long high = protect(determinizeRange(from, until, SylvanBddNativeInterface.high(node),
        BitSet2.copyOf(support)));
      if (support.get(from)) {
        assert high != falseNode.node;
        long result = SylvanBddNativeInterface.and(variableNode, high);
        unprotect(variableNode, high, low);
        return result;
      }
      long result = SylvanBddNativeInterface.ite(variableNode, high, low);
      unprotect(variableNode, high, low);
      return result;
    }

    private BitSet cubeToBitset(long cube) {
      assert cube != falseNode.getNode();
      BitSet result = new BitSet();
      long cubeNode = cube;
      while (cubeNode != trueNode.getNode()) {
        long high = SylvanBddNativeInterface.high(cubeNode);
        if (high == falseNode.getNode()) {
          cubeNode = SylvanBddNativeInterface.low(cubeNode);
        } else {
          result.set(SylvanBddNativeInterface.getvar(cubeNode));
          cubeNode = high;
        }
      }
      return result;
    }
  }

  private long protect(long node) {
    tempNodes.put(node, tempNodes.getOrDefault(node, 0) + 1);
    return node;
  }

  private void unprotect(long... nodes) {
    for (long node : nodes) {
      assert tempNodes.get(node) != null && tempNodes.get(node) > 0;
      int referenceCount = tempNodes.get(node);
      if (tempNodes.get(node) == 1) {
        tempNodes.remove(node);
      } else {
        tempNodes.put(node, referenceCount - 1);
      }
    }
  }
}
