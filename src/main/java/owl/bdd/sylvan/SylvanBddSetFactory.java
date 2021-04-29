package owl.bdd.sylvan;

import com.google.common.base.Preconditions;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.sql.Ref;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;
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
    falseNode = create(SylvanBddNativeInterface::falseNode);
    trueNode = create(SylvanBddNativeInterface::trueNode);
  }

  @SuppressWarnings("PMD.DoNotCallGarbageCollectionExplicitly")
  Map<Long, SylvanNodeReference> getReferencedNodes() {
    System.gc();
    purgeNodeMapping();
    return Collections.unmodifiableMap(referencedNodes);
  }

  private SylvanBddSet create(LongSupplier nodeSupplier) {
    purgeNodeMapping();
    // Invoke the nodeSupplier, which calls Sylvan and possibly triggers a GC.
    long node = nodeSupplier.getAsLong();
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
    return create(() -> SylvanBddNativeInterface.var(variable));
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
    ).normalise();
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
      // We call create here to ensure the nodeMapping is up-to-date in case
      // sylvan decides to do a garbage collection. It does NOT protect the bdd
      // as the wrapper could be GCed before completion of the method.
      // This is not a problem since we do not create new nodes while using
      // the unprotected node, and therefore we do not trigger the sylvan GC.
      long cubeNode = create(() -> SylvanBddNativeInterface.satOneBdd(node)).node;
      if (cubeNode == falseNode.getNode()) {
        return Optional.empty();
      }
      return Optional.of(cubeToBitset(cubeNode));
    }

    @Override
    public BddSet complement() {
      return create(() -> SylvanBddNativeInterface.not(node));
    }

    @Override
    public BddSet union(BddSet other) {
      Preconditions.checkArgument(other instanceof SylvanBddSet);
      return create(() -> SylvanBddNativeInterface.or(node, ((SylvanBddSet) other).node));
    }

    @Override
    public BddSet intersection(BddSet other) {
      Preconditions.checkArgument(other instanceof SylvanBddSet);
      return create(() -> SylvanBddNativeInterface.and(node, ((SylvanBddSet) other).node));
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
      SylvanBddSet vars = create(() -> SylvanBddNativeInterface.varsetFromBitset(
        quantifiedAtomicPropositions.copyInto(new BitSet()))
      );
      SylvanBddSet result = create(() -> SylvanBddNativeInterface.exists(node, vars.node));
      Reference.reachabilityFence(vars);
      return result;
    }

    @Override
    public BddSet restrict(BitSet restriction, BitSet support) {
      SylvanBddSet map = falseNode;
      for (int i = support.nextSetBit(0); i >= 0; i = support.nextSetBit(i + 1)) {
        SylvanBddSet node = restriction.get(i) ? trueNode : falseNode;
        SylvanBddSet finalMap = map;
        int finalI = i;
        map = create(() -> SylvanBddNativeInterface.mapAdd(finalMap.node, finalI, node.node));
        Reference.reachabilityFence(finalMap);
      }
      SylvanBddSet finalMap1 = map;
      SylvanBddSet result = create(() -> SylvanBddNativeInterface.compose(node, finalMap1.node));
      Reference.reachabilityFence(finalMap1);
      return result;
    }

    @Override
    public BddSet relabel(IntUnaryOperator mapping) {
      BitSet support = support();
      SylvanBddSet map = falseNode;
      for (int i = support.nextSetBit(0); i >= 0; i = support.nextSetBit(i + 1)) {
        int mappedTo = mapping.applyAsInt(i);
        if (mappedTo != i) {
          SylvanBddSet var = (SylvanBddSet) of(mappedTo);
          SylvanBddSet finalMap = map;
          int finalI = i;
          map = create(() -> SylvanBddNativeInterface.mapAdd(finalMap.node, finalI, var.node));
          Reference.reachabilityFence(finalMap);
          Reference.reachabilityFence(var);
        }
      }
      SylvanBddSet finalMap1 = map;
      SylvanBddSet result = create(() -> SylvanBddNativeInterface.compose(node, finalMap1.node));
      Reference.reachabilityFence(finalMap1);
      return result;
    }

    @Override
    public BitSet support() {
      // See element() for the reasoning behind using create() here.
      return cubeToBitset(create(() -> SylvanBddNativeInterface.support(node)).node);
    }

    @Override
    public PropositionalFormula<Integer> toExpression() {
      return SylvanBddSetFactory.this.toExpression(node);
    }

    @Override
    public BddSet determinizeRange(int from, int until) {
      return determinizeRange(from, until, this, new BitSet());
    }

    private SylvanBddSet determinizeRange(int from, int until, SylvanBddSet node, BitSet support) {
      if (node.equals(falseNode)) {
        return node;
      }
      if (node.equals(trueNode)) {
        BitSet relevantVariables = new BitSet();
        relevantVariables.set(from, until);
        support.and(relevantVariables);
        support.flip(from, until);
        return (SylvanBddSet) of(new BitSet(), support);
      }
      int currentVariable = SylvanBddNativeInterface.getvar(node.node);
      support.set(currentVariable);
      SylvanBddSet low = determinizeRange(from, until, create(
        () -> SylvanBddNativeInterface.low(node.node)
      ), BitSet2.copyOf(support));
      Reference.reachabilityFence(node);
      SylvanBddSet variableNode = (SylvanBddSet) of(currentVariable);
      if (support.get(from) && !low.equals(falseNode)) {
        SylvanBddSet negatedVariableNode = create(
          () -> SylvanBddNativeInterface.not(variableNode.node)
        );
        Reference.reachabilityFence(variableNode);
        SylvanBddSet result = create(
          () -> SylvanBddNativeInterface.and(negatedVariableNode.node, low.node)
        );
        Reference.reachabilityFence(negatedVariableNode);
        Reference.reachabilityFence(low);
        return result;
      }
      SylvanBddSet high = determinizeRange(from, until, create(
        () -> SylvanBddNativeInterface.high(node.node)
      ), BitSet2.copyOf(support));
      if (support.get(from)) {
        assert !high.equals(falseNode);
        SylvanBddSet result = create(
          () -> SylvanBddNativeInterface.and(variableNode.node, high.node)
        );
        Reference.reachabilityFence(variableNode);
        Reference.reachabilityFence(high);
        return result;
      }
      SylvanBddSet result = create(() -> SylvanBddNativeInterface.ite(variableNode.node, high.node, low.node));
      Reference.reachabilityFence(variableNode);
      Reference.reachabilityFence(high);
      Reference.reachabilityFence(low);
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
}

