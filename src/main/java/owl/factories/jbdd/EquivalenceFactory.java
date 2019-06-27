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

  public EquivalenceFactory(Bdd factory, List<String> alphabet, boolean keepRepresentatives) {
    super(factory);

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
      int bdd = factory.createVariable();
      assert factory.getVariable(bdd) == i;
      mapping.put(literal, i);
      reverseMapping[i] = literal;
    }

    // Literals are not unfolded.
    Arrays.fill(unfoldSubstitution, -1);

    trueClass = new BddEquivalenceClass(this, factory.getTrueNode(), BooleanConstant.TRUE);
    falseClass = new BddEquivalenceClass(this, factory.getFalseNode(), BooleanConstant.FALSE);
  }

  @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
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
      return factory.support(getBdd(clazz), alphabet.size());
    }

    BitSet atomicPropositions = factory.support(getBdd(clazz));

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
    BitSet support = factory.support(getBdd(clazz));
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
    return factory.implies(getBdd(clazz), getBdd(other));
  }

  @Override
  public EquivalenceClass conjunction(Iterator<EquivalenceClass> classes) {
    int resultBdd = factory.getTrueNode();
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
      resultBdd = factory.and(resultBdd, getBdd(next));
    }

    return create(representatives == null ? null : Conjunction.of(representatives), resultBdd);
  }

  @Override
  public EquivalenceClass disjunction(Iterator<EquivalenceClass> classes) {
    int resultBdd = factory.getFalseNode();
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
      resultBdd = factory.or(resultBdd, getBdd(next));
    }

    return create(representatives == null ? null : Disjunction.of(representatives), resultBdd);
  }

  @Override
  public EquivalenceClass substitute(EquivalenceClass clazz,
    Function<? super Formula.ModalOperator, ? extends Formula> substitution) {
    BitSet support = factory.support(getBdd(clazz));

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

    return transform(clazz, bdd -> factory.compose(bdd, substitutionMap),
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

  private int temporalStepBdd(int bdd, BitSet valuation) {
    // Adjust valuation literals in substitution. This is not thread-safe!
    for (int i = 0; i < alphabet.size(); i++) {
      if (valuation.get(i)) {
        temporalStepSubstitution[i] = factory.getTrueNode();
      } else {
        temporalStepSubstitution[i] = factory.getFalseNode();
      }
    }

    return factory.compose(bdd, temporalStepSubstitution);
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

  private int unfold(int bdd) {
    return factory.compose(bdd, unfoldSubstitution);
  }


  @Override
  public String toString(EquivalenceClass clazz) {
    int bdd = getBdd(clazz);

    if (factory.isVariable(bdd)) {
      return PrintVisitor.toString(reverseMapping[factory.getVariable(bdd)], alphabet, false);
    }

    if (factory.isVariableNegated(bdd)) {
      return PrintVisitor.toString(reverseMapping[factory.getVariable(bdd)].not(),
        alphabet, false);
    }

    Formula representative = clazz.representative();
    return representative == null
      ? String.format("%d", bdd)
      : PrintVisitor.toString(representative, alphabet, false);
  }

  @Override
  public <T> ValuationTree<T> temporalStepTree(EquivalenceClass clazz,
    Function<EquivalenceClass, Set<T>> mapper) {
    return temporalStepTree(clazz, mapper, new HashMap<>());
  }

  @Override
  public double trueness(EquivalenceClass clazz) {
    return factory.countSatisfyingAssignments(getBdd(clazz))
      / StrictMath.pow(2.0d, (double) factory.numberOfVariables());
  }

  private <T> ValuationTree<T> temporalStepTree(EquivalenceClass clazz,
    Function<EquivalenceClass, Set<T>> mapper, Map<EquivalenceClass, ValuationTree<T>> cache) {
    var tree = cache.get(clazz);

    if (tree != null) {
      return tree;
    }

    int bdd = getBdd(clazz);
    int pivot = factory.isNodeRoot(bdd) ? alphabet.size() : factory.getVariable(bdd);

    if (pivot >= alphabet.size()) {
      Arrays.fill(temporalStepSubstitution, 0, alphabet.size(), -1);
      Set<T> value = mapper.apply(transform(clazz,
        x -> factory.compose(x, temporalStepSubstitution),
        Formula::temporalStep));
      tree = ValuationTree.of(value);
    } else {
      int[] substitution = new int[pivot + 1];
      Arrays.fill(substitution, 0, pivot, -1);

      substitution[pivot] = factory.getTrueNode();
      var trueSubTree = temporalStepTree(transform(clazz,
        x -> factory.compose(getBdd(clazz), substitution),
        x -> x.temporalStep(pivot, true)), mapper, cache);

      substitution[pivot] = factory.getFalseNode();
      var falseSubTree = temporalStepTree(transform(clazz,
        x -> factory.compose(getBdd(clazz), substitution),
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

  private int getBdd(EquivalenceClass clazz) {
    int bdd = getClass(clazz).bdd;
    assert factory.getReferenceCount(bdd) == 1 || factory.getReferenceCount(bdd) == -1;
    return bdd;
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
        int variable = factory.getVariable(factory.createVariable());
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
          temporalStepSubstitution[variable] = clazz.bdd;
        } else {
          Formula unfold = proposition.unfold();
          BddEquivalenceClass clazz = create(unfold, toBdd(unfold));

          unfoldSubstitutes[variable] = clazz;
          unfoldSubstitution[variable] = clazz.bdd;
          temporalStepSubstitution[variable] = -1;
        }
      }

      if (!registerActive) {
        throw new ConcurrentModificationException(
          "Detected recursive or parallel modification of BDD data-structures.");
      }

      registerActive = false;
    }

    return factory.dereference(formula.accept(visitor));
  }

  private BddEquivalenceClass create(@Nullable Formula representative, int bdd) {
    if (bdd == factory.getTrueNode()) {
      return trueClass;
    }

    if (bdd == factory.getFalseNode()) {
      return falseClass;
    }

    return canonicalize(new BddEquivalenceClass(this, bdd,
      keepRepresentatives ? representative : null));
  }

  private BddEquivalenceClass transform(EquivalenceClass clazz, IntUnaryOperator bddTransformer,
    UnaryOperator<Formula> representativeTransformer) {
    BddEquivalenceClass casted = getClass(clazz);

    int newBdd = bddTransformer.applyAsInt(casted.bdd);

    @Nullable
    Formula representative = clazz.representative();
    @Nullable
    Formula newRepresentative;
    if (casted.bdd == newBdd) {
      newRepresentative = representative;
    } else if (representative == null) {
      newRepresentative = null;
    } else {
      newRepresentative = representativeTransformer.apply(representative);
    }
    return create(newRepresentative, newBdd);
  }

  public static final class BddEquivalenceClass extends EquivalenceClass implements BddWrapper {
    final int bdd;

    BddEquivalenceClass(EquivalenceClassFactory factory, int bdd,
      @Nullable Formula representative) {
      super(factory, representative);
      this.bdd = bdd;
    }

    @Override
    public int bdd() {
      return bdd;
    }

    @Override
    public int hashCode() {
      return HashCommon.mix(bdd);
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
      return bdd == that.bdd;
    }
  }

  // Translates a formula into a BDD under the assumption every subformula is already registered.
  private final class BddVisitor extends PropositionalIntVisitor {
    @Override
    protected int visit(Formula.TemporalOperator formula) {
      if (formula instanceof Literal) {
        Literal literal = (Literal) formula;
        checkLiteralAlphabetRange(List.of(literal));
        int variableBdd = factory.getVariableNode(literal.getAtom());
        return literal.isNegated() ? factory.not(variableBdd) : variableBdd;
      }

      int value = mapping.getInt(formula);
      assert formula instanceof Formula.ModalOperator;
      assert value >= 0;
      return factory.getVariableNode(value);
    }

    @Override
    public int visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? factory.getTrueNode() : factory.getFalseNode();
    }

    @Override
    public int visit(Conjunction conjunction) {
      int x = factory.getTrueNode();
      for (Formula child : conjunction.children) {
        int y = child.accept(this);
        x = factory.consume(factory.and(x, y), x, y);
      }
      return x;
    }

    @Override
    public int visit(Disjunction disjunction) {
      int x = factory.getFalseNode();
      for (Formula child : disjunction.children) {
        int y = child.accept(this);
        x = factory.consume(factory.or(x, y), x, y);
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
}
