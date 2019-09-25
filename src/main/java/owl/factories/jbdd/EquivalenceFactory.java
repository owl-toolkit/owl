/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.factories.jbdd;

import de.tum.in.jbdd.Bdd;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.collections.ValuationTree;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.PropositionalIntVisitor;

final class EquivalenceFactory extends GcManagedFactory<EquivalenceFactory.BddEquivalenceClass>
  implements EquivalenceClassFactory {

  private final List<String> alphabet;
  private final boolean keepRepresentatives;
  private final BddVisitor visitor;
  private final BddEquivalenceClass falseClass;
  private final BddEquivalenceClass trueClass;

  private Formula.TemporalOperator[] reverseMapping;
  private final Object2IntMap<Formula.TemporalOperator> mapping;

  // Compose maps.
  private int[] temporalStepSubstitution;
  private int[] unfoldSubstitution;

  // Protect objects from the GC.
  private EquivalenceClass[] temporalStepSubstitutes;
  private EquivalenceClass[] unfoldSubstitutes;

  private boolean registerActive = false;

  public EquivalenceFactory(Bdd bdd, List<String> alphabet, boolean keepRepresentatives) {
    super(bdd);
    this.alphabet = List.copyOf(alphabet);
    this.keepRepresentatives = keepRepresentatives;

    int alphabetSize = this.alphabet.size();
    mapping = new Object2IntOpenHashMap<>();
    mapping.defaultReturnValue(-1);
    reverseMapping = new Formula.TemporalOperator[alphabetSize];
    visitor = new BddVisitor();

    unfoldSubstitution = new int[alphabetSize];
    unfoldSubstitutes = new EquivalenceClass[alphabetSize];
    temporalStepSubstitution = new int[alphabetSize];
    temporalStepSubstitutes = new EquivalenceClass[alphabetSize];

    // Register literals.
    for (int i = 0; i < alphabetSize; i++) {
      Literal literal = Literal.of(i);
      int node = this.bdd.createVariable();
      assert this.bdd.variable(node) == i;
      mapping.put(literal, i);
      reverseMapping[i] = literal;
    }

    // Literals are not unfolded.
    Arrays.fill(unfoldSubstitution, -1);

    trueClass = new BddEquivalenceClass(this, this.bdd.trueNode(), BooleanConstant.TRUE);
    falseClass = new BddEquivalenceClass(this, this.bdd.falseNode(), BooleanConstant.FALSE);
  }

  @Override
  public List<String> variables() {
    return alphabet;
  }


  @Override
  public EquivalenceClass of(Formula formula) {
    return create(formula, toBdd(formula));
  }

  @Override
  public EquivalenceClass getFalse() {
    return falseClass;
  }

  @Override
  public EquivalenceClass getTrue() {
    return trueClass;
  }


  @Override
  public BitSet atomicPropositions(EquivalenceClass clazz, boolean includeNested) {
    if (!includeNested) {
      return bdd.support(getNode(clazz), alphabet.size());
    }

    BitSet atomicPropositions = bdd.support(getNode(clazz));

    int i = atomicPropositions.nextSetBit(alphabet.size());
    for (; i >= 0; i = atomicPropositions.nextSetBit(i + 1)) {
      atomicPropositions.or(reverseMapping[i].atomicPropositions(true));
    }

    if (atomicPropositions.length() >= alphabet.size()) {
      atomicPropositions.clear(alphabet.size(), atomicPropositions.length());
    }

    return atomicPropositions;
  }

  @Override
  public Set<Formula.ModalOperator> modalOperators(EquivalenceClass clazz) {
    BitSet support = bdd.support(getNode(clazz));
    support.clear(0, alphabet.size());

    return new AbstractSet<>() {
      @Override
      public boolean contains(Object o) {
        int i = mapping.getInt(o);
        return i != -1 && support.get(i);
      }

      @Override
      public Stream<Formula.ModalOperator> stream() {
        return support.stream().mapToObj(i -> (Formula.ModalOperator) reverseMapping[i]);
      }

      @Override
      public Iterator<Formula.ModalOperator> iterator() {
        return stream().iterator();
      }

      @Override
      public int size() {
        return support.cardinality();
      }
    };
  }

  @Override
  public boolean implies(EquivalenceClass clazz, EquivalenceClass other) {
    return bdd.implies(getNode(clazz), getNode(other));
  }

  @Override
  public EquivalenceClass conjunction(Iterator<EquivalenceClass> classes) {
    int resultBdd = bdd.trueNode();
    @Nullable
    List<Formula> representatives = new ArrayList<>();

    while (classes.hasNext()) {
      EquivalenceClass next = classes.next();
      Formula representative = next.representative();
      if (representative == null || representatives == null) {
        representatives = null;
      } else {
        representatives.add(representative);
      }
      resultBdd = bdd.and(resultBdd, getNode(next));
    }

    return create(representatives == null ? null : Conjunction.of(representatives), resultBdd);
  }

  @Override
  public EquivalenceClass disjunction(Iterator<EquivalenceClass> classes) {
    int resultBdd = bdd.falseNode();
    @Nullable
    List<Formula> representatives = new ArrayList<>();

    while (classes.hasNext()) {
      EquivalenceClass next = classes.next();
      Formula representative = next.representative();
      if (representative == null || representatives == null) {
        representatives = null;
      } else {
        representatives.add(representative);
      }
      resultBdd = bdd.or(resultBdd, getNode(next));
    }

    return create(representatives == null ? null : Disjunction.of(representatives), resultBdd);
  }

  @Override
  public EquivalenceClass substitute(EquivalenceClass clazz,
    Function<? super Formula.ModalOperator, ? extends Formula> substitution) {
    BitSet support = bdd.support(getNode(clazz));

    Function<Formula.TemporalOperator, Formula> guardedSubstitution = x -> {
      if (x instanceof Formula.ModalOperator) {
        return substitution.apply((Formula.ModalOperator) x);
      } else {
        return x;
      }
    };

    int[] substitutionMap = new int[reverseMapping.length];
    Arrays.setAll(substitutionMap,
      i -> support.get(i) ? toBdd(guardedSubstitution.apply(reverseMapping[i])) : -1);

    return transform(clazz, node -> bdd.compose(node, substitutionMap),
      f -> f.substitute(guardedSubstitution));
  }


  @Override
  public EquivalenceClass temporalStep(EquivalenceClass clazz, BitSet valuation) {
    return transform(clazz, bdd -> temporalStepBdd(bdd, valuation), f -> f.temporalStep(valuation));
  }

  @Override
  public EquivalenceClass temporalStepUnfold(EquivalenceClass clazz, BitSet valuation) {
    return transform(clazz, bdd -> unfold(temporalStepBdd(bdd, valuation)),
      f -> f.temporalStepUnfold(valuation));
  }

  private int temporalStepBdd(int node, BitSet valuation) {
    // Adjust valuation literals in substitution. This is not thread-safe!
    for (int i = 0; i < alphabet.size(); i++) {
      temporalStepSubstitution[i] = valuation.get(i) ? bdd.trueNode() : bdd.falseNode();
    }

    return bdd.compose(node, temporalStepSubstitution);
  }

  @Override
  public EquivalenceClass unfold(EquivalenceClass clazz) {
    return transform(clazz, this::unfold, Formula::unfold);
  }

  @Override
  public EquivalenceClass unfoldTemporalStep(EquivalenceClass clazz, BitSet valuation) {
    return transform(clazz, bdd -> temporalStepBdd(unfold(bdd), valuation),
      f -> f.unfoldTemporalStep(valuation));
  }

  private int unfold(int node) {
    return bdd.compose(node, unfoldSubstitution);
  }


  @Override
  public String toString(EquivalenceClass clazz) {
    int node = getNode(clazz);

    if (bdd.isVariable(node)) {
      return PrintVisitor.toString(reverseMapping[bdd.variable(node)], alphabet, false);
    }

    if (bdd.isVariableNegated(node)) {
      return PrintVisitor.toString(reverseMapping[bdd.variable(node)].not(),
        alphabet, false);
    }

    Formula representative = clazz.representative();
    return representative == null
      ? String.format("%d", node)
      : PrintVisitor.toString(representative, alphabet, false);
  }

  @Override
  public <T> ValuationTree<T> temporalStepTree(EquivalenceClass clazz,
    Function<EquivalenceClass, Set<T>> mapper) {
    return temporalStepTree(clazz, mapper, new HashMap<>());
  }

  @Override
  public double trueness(EquivalenceClass clazz) {
    var satisfyingAssignments = new BigDecimal(bdd.countSatisfyingAssignments(getNode(clazz)));
    var assignments = BigDecimal.valueOf(2).pow(bdd.numberOfVariables());
    return satisfyingAssignments.divide(assignments, 24, RoundingMode.HALF_DOWN).doubleValue();
  }

  private <T> ValuationTree<T> temporalStepTree(EquivalenceClass clazz,
    Function<EquivalenceClass, Set<T>> mapper, Map<EquivalenceClass, ValuationTree<T>> cache) {
    var tree = cache.get(clazz);

    if (tree != null) {
      return tree;
    }

    int node = getNode(clazz);
    int pivot = bdd.isNodeRoot(node) ? alphabet.size() : bdd.variable(node);

    if (pivot >= alphabet.size()) {
      Arrays.fill(temporalStepSubstitution, 0, alphabet.size(), -1);
      Set<T> value = mapper.apply(transform(clazz,
        x -> bdd.compose(x, temporalStepSubstitution),
        Formula::temporalStep));
      tree = ValuationTree.of(value);
    } else {
      int[] substitution = new int[pivot + 1];
      Arrays.fill(substitution, 0, pivot, -1);

      substitution[pivot] = bdd.trueNode();
      var trueSubTree = temporalStepTree(transform(clazz,
        x -> bdd.compose(getNode(clazz), substitution),
        x -> x.temporalStep(pivot, true)), mapper, cache);

      substitution[pivot] = bdd.falseNode();
      var falseSubTree = temporalStepTree(transform(clazz,
        x -> bdd.compose(getNode(clazz), substitution),
        x -> x.temporalStep(pivot, false)), mapper, cache);

      tree = ValuationTree.of(pivot, trueSubTree, falseSubTree);
    }

    cache.put(clazz, tree);
    return tree;
  }

  private BddEquivalenceClass getClass(EquivalenceClass clazz) {
    assert this.equals(clazz.factory());
    return (BddEquivalenceClass) clazz;
  }

  private int getNode(EquivalenceClass clazz) {
    int node = getClass(clazz).node;
    assert bdd.getReferenceCount(node) == 1 || bdd.getReferenceCount(node) == -1;
    return node;
  }

  private int toBdd(Formula formula) {
    // Scan for unknown proper subformulas.
    List<Formula.ModalOperator> newPropositions = formula.subformulas(Formula.ModalOperator.class)
      .stream().filter(x -> !mapping.containsKey(x)).sorted().collect(Collectors.toList());

    if (!newPropositions.isEmpty()) {
      if (registerActive) {
        throw new ConcurrentModificationException(
          "Detected recursive or parallel modification of BDD data-structures.");
      }

      registerActive = true;

      checkLiteralAlphabetRange(newPropositions);

      // Create variables.
      int newSize = mapping.size() + newPropositions.size();
      reverseMapping = Arrays.copyOf(reverseMapping, newSize);

      for (Formula.ModalOperator proposition : newPropositions) {
        int variable = bdd.variable(bdd.createVariable());
        mapping.put(proposition, variable);
        reverseMapping[variable] = proposition;
      }

      // Compute unfolding and temporal step.
      unfoldSubstitution = Arrays.copyOf(unfoldSubstitution, newSize);
      unfoldSubstitutes = Arrays.copyOf(unfoldSubstitutes, newSize);
      temporalStepSubstitution = Arrays.copyOf(temporalStepSubstitution, newSize);
      temporalStepSubstitutes = Arrays.copyOf(temporalStepSubstitutes, newSize);

      for (Formula.ModalOperator proposition : newPropositions) {
        int variable = mapping.getInt(proposition);

        if (proposition instanceof XOperator) {
          Formula operand = ((XOperator) proposition).operand;
          BddEquivalenceClass clazz = create(operand, toBdd(operand));

          unfoldSubstitution[variable] = -1;
          temporalStepSubstitutes[variable] = clazz;
          temporalStepSubstitution[variable] = clazz.node;
        } else {
          Formula unfold = proposition.unfold();
          BddEquivalenceClass clazz = create(unfold, toBdd(unfold));

          unfoldSubstitutes[variable] = clazz;
          unfoldSubstitution[variable] = clazz.node;
          temporalStepSubstitution[variable] = -1;
        }
      }

      if (!registerActive) {
        throw new ConcurrentModificationException(
          "Detected recursive or parallel modification of BDD data-structures.");
      }

      registerActive = false;
    }

    return bdd.dereference(formula.accept(visitor));
  }

  private BddEquivalenceClass create(@Nullable Formula representative, int node) {
    if (node == bdd.trueNode()) {
      return trueClass;
    }

    if (node == bdd.falseNode()) {
      return falseClass;
    }

    BddEquivalenceClass clazz =
      new BddEquivalenceClass(this, node, keepRepresentatives ? representative : null);
    return canonicalize(clazz);
  }

  private BddEquivalenceClass transform(EquivalenceClass clazz, IntUnaryOperator bddTransformer,
    UnaryOperator<Formula> representativeTransformer) {
    BddEquivalenceClass casted = getClass(clazz);

    int newBdd = bddTransformer.applyAsInt(casted.node);

    @Nullable
    Formula representative = clazz.representative();
    @Nullable
    Formula newRepresentative;
    if (casted.node == newBdd) {
      newRepresentative = representative;
    } else if (representative == null) {
      newRepresentative = null;
    } else {
      newRepresentative = representativeTransformer.apply(representative);
    }
    return create(newRepresentative, newBdd);
  }

  // Translates a formula into a BDD under the assumption every subformula is already registered.
  private final class BddVisitor extends PropositionalIntVisitor {
    @Override
    protected int visit(Formula.TemporalOperator formula) {
      if (formula instanceof Literal) {
        Literal literal = (Literal) formula;
        checkLiteralAlphabetRange(List.of(literal));
        int variableBdd = bdd.variableNode(literal.getAtom());
        return literal.isNegated() ? bdd.not(variableBdd) : variableBdd;
      }

      int value = mapping.getInt(formula);
      assert formula instanceof Formula.ModalOperator;
      assert value >= 0;
      return bdd.variableNode(value);
    }

    @Override
    public int visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? bdd.trueNode() : bdd.falseNode();
    }

    @Override
    public int visit(Conjunction conjunction) {
      int x = bdd.trueNode();
      for (Formula child : conjunction.children) {
        int y = child.accept(this);
        x = bdd.consume(bdd.and(x, y), x, y);
      }
      return x;
    }

    @Override
    public int visit(Disjunction disjunction) {
      int x = bdd.falseNode();
      for (Formula child : disjunction.children) {
        int y = child.accept(this);
        x = bdd.consume(bdd.or(x, y), x, y);
      }
      return x;
    }
  }

  private void checkLiteralAlphabetRange(Collection<? extends Formula> formulas) {
    for (Formula x : formulas) {
      if (x instanceof Literal && ((Literal) x).getAtom() >= alphabet.size()) {
        throw new IllegalArgumentException("Literal " + x + " is not within alphabet.");
      }
    }
  }

  static final class BddEquivalenceClass extends EquivalenceClass implements BddNode {
    final int node;

    BddEquivalenceClass(EquivalenceClassFactory factory, int node,
      @Nullable Formula representative) {
      super(factory, representative);
      this.node = node;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }

      BddEquivalenceClass that = (BddEquivalenceClass) obj;
      assert this.factory().equals(that.factory());
      return node == that.node;
    }

    @Override
    public int hashCode() {
      return HashCommon.mix(node);
    }

    @Override
    public int node() {
      return node;
    }
  }
}
