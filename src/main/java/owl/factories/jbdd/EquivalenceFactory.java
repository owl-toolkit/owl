/*
 * Copyright (C) 2016  (See AUTHORS)
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import de.tum.in.jbdd.Bdd;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.factories.EquivalenceClassUtil;
import owl.factories.PropositionVisitor;
import owl.ltl.BinaryModalOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.Literal;
import owl.ltl.UnaryModalOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultIntVisitor;
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.SubstitutionVisitor;

final class EquivalenceFactory implements EquivalenceClassFactory {
  // TODO We should be able to treat "true" formulas and atoms completely separate
  // Then, the literals won't litter the mappings etc.
  // TODO Make the bdd reusable, i.e. don't assume that reverseMapping.length == bdd.variableCount()

  private final ImmutableList<String> alphabet;
  private final int alphabetSize;
  private final Bdd factory;
  private final BddEquivalenceClass falseClass;
  private final Object2IntMap<Formula> mapping;
  private final BddEquivalenceClass trueClass;
  private final BddVisitor visitor;
  private Formula[] reverseMapping;
  private int[] temporalStepSubstitution;
  private int[] unfoldSubstitution;

  public EquivalenceFactory(Bdd factory, List<String> alphabet) {
    this.alphabetSize = alphabet.size();
    this.alphabet = ImmutableList.copyOf(alphabet);
    this.factory = factory;

    mapping = new Object2IntOpenHashMap<>();
    mapping.defaultReturnValue(0);
    visitor = new BddVisitor();

    unfoldSubstitution = new int[alphabetSize];
    temporalStepSubstitution = new int[alphabetSize];
    reverseMapping = new Formula[alphabetSize];

    for (int i = 0; i < alphabetSize; i++) {
      Literal literal = new Literal(i);
      int variableNode = factory.createVariable();
      assert factory.getVariable(variableNode) == i;

      // In order to "distinguish" -0 and +0 we shift the variables by 1 -> -1, 1.
      mapping.put(literal, i + 1);
      mapping.put(literal.not(), -(i + 1));
      reverseMapping[i] = literal;

      // Literals are not unfolded.
      unfoldSubstitution[i] = -1;
    }

    trueClass = new BddEquivalenceClass(BooleanConstant.TRUE, factory.getTrueNode());
    falseClass = new BddEquivalenceClass(BooleanConstant.FALSE, factory.getFalseNode());
  }

  @Override
  public BddEquivalenceClass createEquivalenceClass(Formula formula) {
    return createEquivalenceClass(formula, formula.accept(visitor));
  }

  private BddEquivalenceClass createEquivalenceClass(@Nullable Formula representative, int bdd) {
    if (bdd == factory.getTrueNode()) {
      return trueClass;
    }

    if (bdd == factory.getFalseNode()) {
      return falseClass;
    }

    return new BddEquivalenceClass(representative, bdd);
  }

  @Override
  public EquivalenceClass getFalse() {
    return falseClass;
  }

  @Override
  public EquivalenceClass getTrue() {
    return trueClass;
  }

  private int getVariable(Formula formula) {
    assert formula instanceof Literal
      || formula instanceof UnaryModalOperator
      || formula instanceof BinaryModalOperator;

    int value = mapping.getInt(formula);

    if (value == 0) {
      // All literals should have been already discovered.
      assert !(formula instanceof Literal);
      Deque<Formula> propositions = PropositionVisitor.extractPropositions(formula);

      value = factory.numberOfVariables();
      resize(value + propositions.size());
      value = register(propositions, value);
      resize(value);
    }

    // We don't need to increment the reference-counter, since all variables are saturated.
    int result;

    if (value > 0) {
      result = factory.getVariableNode(value - 1);
    } else {
      result = factory.not(factory.getVariableNode(-(value + 1)));
    }

    assert factory.isVariableOrNegated(result);
    return result;
  }

  @Override
  public ImmutableList<String> getVariables() {
    return alphabet;
  }

  private void register(Formula proposition, int i) {
    assert !(proposition instanceof Literal);

    int variableNode = factory.createVariable();
    assert factory.getVariable(variableNode) == i;
    mapping.put(proposition, i + 1);
    reverseMapping[i] = proposition;

    if (Fragments.isX(proposition)) {
      mapping.put(proposition.not(), -(i + 1));
    }

    if (proposition instanceof XOperator) {
      unfoldSubstitution[i] = -1;
      temporalStepSubstitution[i] = ((XOperator) proposition).operand.accept(visitor);
    } else {
      unfoldSubstitution[i] = proposition.unfold().accept(visitor);
      temporalStepSubstitution[i] = -1;
    }
  }

  private int register(Deque<Formula> propositions, int i) {
    int counter = i;
    for (Formula proposition : propositions) {
      if (mapping.containsKey(proposition)) {
        continue;
      }

      register(proposition, counter);
      counter++;
    }

    return counter;
  }

  // TODO: Use size counter to reduce number of copies.
  private void resize(int size) {
    reverseMapping = Arrays.copyOf(reverseMapping, size);
    unfoldSubstitution = Arrays.copyOf(unfoldSubstitution, size);
    temporalStepSubstitution = Arrays.copyOf(temporalStepSubstitution, size);
  }

  private int temporalStepBdd(int bdd, BitSet valuation) {
    // Adjust valuation literals in substitution. This is not thread-safe!
    for (int i = 0; i < alphabetSize; i++) {
      if (valuation.get(i)) {
        temporalStepSubstitution[i] = factory.getTrueNode();
      } else {
        temporalStepSubstitution[i] = factory.getFalseNode();
      }
    }

    return factory.compose(bdd, temporalStepSubstitution);
  }

  private BitSet toBitSet(Iterable<? extends Formula> formulas) {
    BitSet bitSet = new BitSet();
    formulas.forEach(x -> {
      getVariable(x);
      bitSet.set(mapping.getInt(x) - 1);
    });
    return bitSet;
  }

  private Set<Formula> toSet(BitSet bitSet) {
    Set<Formula> formulas = new HashSet<>();
    bitSet.stream().forEach(x -> formulas.add(reverseMapping[x]));
    return formulas;
  }

  private int unfoldBdd(int bdd) {
    return factory.compose(bdd, unfoldSubstitution);
  }

  private final class BddEquivalenceClass implements EquivalenceClass {
    private static final int INVALID_BDD = -1;
    private int bdd;
    @Nullable
    private Formula representative;

    BddEquivalenceClass(@Nullable Formula representative, int bdd) {
      this.representative = representative;
      this.bdd = bdd;
    }

    @Override
    public EquivalenceClass and(EquivalenceClass equivalenceClass) {
      assert equivalenceClass instanceof BddEquivalenceClass;
      BddEquivalenceClass that = (BddEquivalenceClass) equivalenceClass;
      @Nullable Formula representative;
      if (this.representative == null || that.representative == null) {
        representative = null;
      } else {
        representative = Conjunction.create(this.representative, that.representative);
      }
      return createEquivalenceClass(representative, factory.reference(factory.and(bdd, that.bdd)));
    }

    @Override
    public EquivalenceClass duplicate() {
      return new BddEquivalenceClass(representative, factory.reference(bdd));
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
      return bdd == that.bdd;
    }

    @Override
    public EquivalenceClass exists(Predicate<Formula> predicate) {
      BitSet exists = new BitSet();

      for (int i = 0; i < reverseMapping.length; i++) {
        if (predicate.test(reverseMapping[i])) {
          exists.set(i);
        }
      }

      return createEquivalenceClass(null, factory.reference(factory.exists(bdd, exists)));
    }

    @Override
    public void forEachSatisfyingAssignment(BiConsumer<BitSet, BitSet> action) {
      if (alphabetSize == 0) {
        action.accept(new BitSet(0), new BitSet(0));
      } else {
        factory.forEachNonEmptyPath(bdd, alphabetSize - 1, action);
      }
    }

    @Override
    public void free() {
      Preconditions.checkState(bdd != INVALID_BDD, "Double free");

      // Only remove BDD nodes for unsaturated nodes.
      if (!factory.isVariableOrNegated(bdd) && !factory.isNodeRoot(bdd)) {
        factory.dereference(bdd);
        bdd = INVALID_BDD;
        representative = null;
      }
    }

    @Override
    public void freeRepresentative() {
      if (!factory.isNodeRoot(bdd)) {
        representative = null;
      }
    }

    @Override
    public BitSet getAtoms() {
      return factory.support(bdd, alphabetSize);
    }

    @Override
    public EquivalenceClassFactory getFactory() {
      return EquivalenceFactory.this;
    }

    @Override
    @Nullable
    public Formula getRepresentative() {
      return representative;
    }

    @Override
    public <T extends Formula> Set<T> getSupport(Class<T> clazz) {
      BitSet support = factory.support(bdd);
      //noinspection unchecked - We want to save a .map(clazz::cast) here
      return (Set<T>) support.stream().mapToObj(i -> reverseMapping[i]).filter(clazz::isInstance)
        .collect(Collectors.toSet());
    }

    @Override
    public int hashCode() {
      return bdd;
    }

    @Override
    public boolean implies(EquivalenceClass equivalenceClass) {
      assert equivalenceClass instanceof BddEquivalenceClass;
      BddEquivalenceClass that = (BddEquivalenceClass) equivalenceClass;
      return factory.implies(bdd, that.bdd);
    }

    @Override
    public boolean isFalse() {
      return bdd == factory.getFalseNode();
    }

    @Override
    public boolean isTrue() {
      return bdd == factory.getTrueNode();
    }

    @Override
    public EquivalenceClass or(EquivalenceClass equivalenceClass) {
      assert equivalenceClass instanceof BddEquivalenceClass;
      BddEquivalenceClass that = (BddEquivalenceClass) equivalenceClass;
      @Nullable Formula representative;
      if (this.representative == null || that.representative == null) {
        representative = null;
      } else {
        representative = Disjunction.create(this.representative, that.representative);
      }
      return createEquivalenceClass(representative, factory.reference(factory.or(bdd, that.bdd)));
    }

    @Override
    public ImmutableList<Set<Formula>> satisfyingAssignments(Iterable<? extends Formula> support) {
      BitSet supportBitSet = toBitSet(support);
      Set<BitSet> minimalSolutions = new HashSet<>();
      //noinspection UseOfClone
      factory.forEachMinimalSolution(bdd, solution ->
        minimalSolutions.add((BitSet) solution.clone()));

      Set<BitSet> closure = EquivalenceClassUtil.upwardClosure(supportBitSet, minimalSolutions);
      return closure.stream().map(EquivalenceFactory.this::toSet)
        .collect(ImmutableList.toImmutableList());
    }

    @Override
    public EquivalenceClass substitute(Function<? super Formula, ? extends Formula> substitution) {
      BitSet support = factory.support(bdd);

      int[] substitutionMap = new int[reverseMapping.length];
      for (int i = 0; i < substitutionMap.length; i++) {
        if (support.get(i)) {
          substitutionMap[i] = substitution.apply(reverseMapping[i]).accept(visitor);
        } else {
          substitutionMap[i] = -1;
        }
      }

      int substitutedBdd = factory.reference(factory.compose(bdd, substitutionMap));

      if (bdd == substitutedBdd) {
        return createEquivalenceClass(representative, bdd);
      }

      @Nullable
      Formula substitutionRepresentative =
        representative == null ? null
          : representative.accept(new SubstitutionVisitor(substitution));
      return createEquivalenceClass(substitutionRepresentative, substitutedBdd);
    }

    @Override
    public EquivalenceClass temporalStep(BitSet valuation) {
      int newBdd = factory.reference(temporalStepBdd(bdd, valuation));

      if (bdd == newBdd) {
        return createEquivalenceClass(representative, bdd);
      }

      @Nullable
      Formula newRepresentative =
        representative == null ? null : representative.temporalStep(valuation);
      return createEquivalenceClass(newRepresentative, newBdd);
    }

    @Override
    public EquivalenceClass temporalStepUnfold(BitSet valuation) {
      int newBdd = factory.reference(unfoldBdd(temporalStepBdd(bdd, valuation)));

      if (bdd == newBdd) {
        return createEquivalenceClass(representative, bdd);
      }

      @Nullable
      Formula newRepresentative =
        representative == null ? null : representative.temporalStepUnfold(valuation);
      return createEquivalenceClass(newRepresentative, newBdd);
    }

    @Override
    public boolean testSupport(Predicate<Formula> predicate) {
      BitSet support = factory.support(bdd);
      return support.stream().allMatch(i -> predicate.test(reverseMapping[i]));
    }

    @Override
    public String toString() {
      if (factory.isVariable(bdd)) {
        return PrintVisitor.toString(reverseMapping[factory.getVariable(bdd)], alphabet, false);
      }

      if (factory.isVariableNegated(bdd)) {
        return PrintVisitor.toString(reverseMapping[factory.getVariable(bdd)].not(),
          alphabet, false);
      }

      if (representative == null) {
        return String.format("(%d)", bdd);
      }
      // Maybe apply simplifier here
      return PrintVisitor.toString(representative, alphabet, false);
    }

    @Override
    public EquivalenceClass unfold() {
      int newBdd = factory.reference(unfoldBdd(bdd));

      if (bdd == newBdd) {
        return createEquivalenceClass(representative, bdd);
      }

      @Nullable
      Formula newRepresentative = representative == null ? null : representative.unfold();
      return createEquivalenceClass(newRepresentative, newBdd);
    }

    @Override
    public EquivalenceClass unfoldTemporalStep(BitSet valuation) {
      int newBdd = factory.reference(temporalStepBdd(unfoldBdd(bdd), valuation));

      if (bdd == newBdd) {
        return createEquivalenceClass(representative, bdd);
      }

      @Nullable
      Formula newRepresentative =
        representative == null ? null : representative.unfoldTemporalStep(valuation);
      return createEquivalenceClass(newRepresentative, newBdd);
    }
  }

  private final class BddVisitor extends DefaultIntVisitor {
    @Override
    protected int defaultAction(Formula formula) {
      return getVariable(formula);
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

    @Override
    public int visit(BooleanConstant booleanConstant) {
      if (booleanConstant.value) {
        return factory.getTrueNode();
      } else {
        return factory.getFalseNode();
      }
    }
  }
}
