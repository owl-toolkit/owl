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

package owl.factories.jdd;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.factories.EquivalenceClassUtil;
import owl.factories.PropositionVisitor;
import owl.factories.jdd.bdd.Bdd;
import owl.factories.jdd.bdd.BddFactory;
import owl.ltl.BinaryModalOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.UnaryModalOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultIntVisitor;
import owl.ltl.visitors.SubstitutionVisitor;
import owl.ltl.visitors.predicates.XFragmentPredicate;

public final class EquivalenceFactory implements EquivalenceClassFactory {
  private final int alphabetSize;
  private final Bdd factory;
  private final BddEquivalenceClass falseClass;
  private final Object2IntMap<Formula> mapping;
  private final BddEquivalenceClass trueClass;
  private final BddVisitor visitor;
  private ImmutableList<String> atomMapping;
  private Formula[] reverseMapping;
  private int[] temporalStepSubstitution;
  private int[] unfoldSubstitution;

  private EquivalenceFactory(Formula formula, int alphabetSize, List<String> atomMapping) {
    this.alphabetSize = alphabetSize;
    this.atomMapping = ImmutableList.copyOf(atomMapping);

    Deque<Formula> queuedFormulas = PropositionVisitor.extractPropositions(formula);
    mapping = new Object2IntOpenHashMap<>();
    mapping.defaultReturnValue(0);
    int size = alphabetSize + queuedFormulas.size();
    factory = BddFactory.buildBdd(1024 * (size + 1));
    visitor = new BddVisitor();

    unfoldSubstitution = new int[size];
    temporalStepSubstitution = new int[size];
    reverseMapping = new Formula[size];

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

    int finalSize = register(queuedFormulas, alphabetSize);
    resize(finalSize);

    trueClass = new BddEquivalenceClass(BooleanConstant.TRUE, factory.getTrueNode());
    falseClass = new BddEquivalenceClass(BooleanConstant.FALSE, factory.getFalseNode());
  }

  public static EquivalenceFactory create(Formula formula, int alphabetSize) {
    return create(formula, alphabetSize, ImmutableList.of());
  }

  public static EquivalenceFactory create(Formula formula, int alphabetSize,
    List<String> atomMapping) {
    return new EquivalenceFactory(formula, alphabetSize, atomMapping);
  }

  @Override
  public BddEquivalenceClass createEquivalenceClass(Formula representative) {
    return createEquivalenceClass(representative, representative.accept(visitor));
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
    assert formula instanceof Literal || formula instanceof UnaryModalOperator
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
    final int result;
    if (value > 0) {
      result = factory.getVariableNode(value - 1);
    } else {
      result = factory.not(factory.getVariableNode(-(value + 1)));
    }
    assert factory.isVariableOrNegated(result);
    return result;
  }

  private void register(Formula proposition, int i) {
    assert !(proposition instanceof Literal);

    int variableNode = factory.createVariable();
    assert factory.getVariable(variableNode) == i;
    mapping.put(proposition, i + 1);
    reverseMapping[i] = proposition;

    if (proposition.accept(XFragmentPredicate.INSTANCE)) {
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

  @Override
  public void setVariables(List<String> variables) {
    atomMapping = ImmutableList.copyOf(variables);
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
      assert mapping.containsKey(x);
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

  @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
  private final class BddEquivalenceClass implements EquivalenceClass {
    private static final int INVALID_BDD = -1;
    private int bdd;
    @Nullable
    private Formula representative;

    BddEquivalenceClass(int bdd) {
      this.representative = null;
      this.bdd = bdd;
    }

    BddEquivalenceClass(@Nullable Formula representative, int bdd) {
      this.representative = representative;
      this.bdd = bdd;
    }

    @Override
    public EquivalenceClass and(EquivalenceClass equivalenceClass) {
      assert equivalenceClass instanceof BddEquivalenceClass;
      final BddEquivalenceClass that = (BddEquivalenceClass) equivalenceClass;
      @Nullable
      final Formula representative;
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
      final BitSet exists = new BitSet();

      for (int i = 0; i < reverseMapping.length; i++) {
        if (predicate.test(reverseMapping[i])) {
          exists.set(i);
        }
      }

      return createEquivalenceClass(null, factory.reference(factory.exists(bdd, exists)));
    }

    @Override
    public void free() {
      Preconditions.checkState(bdd != INVALID_BDD, "Double free");

      // TODO If this check is removed, tests fail
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
      final BddEquivalenceClass that = (BddEquivalenceClass) equivalenceClass;
      @Nullable
      final Formula representative;
      if (this.representative == null || that.representative == null) {
        representative = null;
      } else {
        representative = Disjunction.create(this.representative, that.representative);
      }
      return createEquivalenceClass(representative, factory.reference(factory.or(bdd, that.bdd)));
    }

    @Override
    public ImmutableList<Set<Formula>> satisfyingAssignments(
      Iterable<? extends Formula> supportIterable) {
      BitSet support = toBitSet(supportIterable);
      Iterator<BitSet> minimalSolutions = factory.getMinimalSolutions(bdd);
      Set<BitSet> closure = EquivalenceClassUtil.upwardClosure(support, minimalSolutions);
      return closure.stream().map(EquivalenceFactory.this::toSet)
        .collect(ImmutableList.toImmutableList());
    }

    @Override
    public EquivalenceClass substitute(Function<? super Formula, ? extends Formula> substitution) {
      // TODO: Only construct map for elements in support.
      final int[] substitutionMap = new int[reverseMapping.length];
      for (int i = 0; i < substitutionMap.length; i++) {
        substitutionMap[i] = substitution.apply(reverseMapping[i]).accept(visitor);
      }

      final int substitutedBdd = factory.reference(factory.compose(bdd, substitutionMap));
      if (representative == null) {
        return new BddEquivalenceClass(substitutedBdd);
      }
      Formula substitutionRepresentative =
        representative.accept(new SubstitutionVisitor(substitution));
      return new BddEquivalenceClass(substitutionRepresentative, substitutedBdd);
    }

    @Override
    public EquivalenceClass temporalStep(BitSet valuation) {
      @Nullable
      final Formula successorRepresentative =
        representative == null ? null : representative.temporalStep(valuation);
      return createEquivalenceClass(successorRepresentative,
        factory.reference(temporalStepBdd(bdd, valuation)));
    }

    @Override
    public EquivalenceClass temporalStepUnfold(BitSet valuation) {
      @Nullable
      final Formula successorRepresentative =
        representative == null ? null : representative.temporalStepUnfold(valuation);
      return createEquivalenceClass(successorRepresentative,
        factory.reference(unfoldBdd(temporalStepBdd(bdd, valuation))));
    }

    @Override
    public boolean testSupport(Predicate<Formula> predicate) {
      final BitSet support = factory.support(bdd);
      return support.stream().allMatch(i -> predicate.test(reverseMapping[i]));
    }

    @Override
    public String toString() {
      final String representativeString;

      if (factory.isVariableOrNegated(bdd)) {
        representativeString = reverseMapping[factory.getVariable(bdd)].toString(atomMapping);
      } else if (representative == null) {
        representativeString = "?";
      } else {
        representativeString = representative.toString(atomMapping);
      }

      return representativeString + " (" + bdd + ")";
    }

    @Override
    public EquivalenceClass unfold() {
      @Nullable
      final Formula successorRepresentative =
        representative == null ? null : representative.unfold();
      return createEquivalenceClass(successorRepresentative, factory.reference(unfoldBdd(bdd)));
    }

    @Override
    public EquivalenceClass unfoldTemporalStep(BitSet valuation) {
      @Nullable
      final Formula successorRepresentative =
        representative == null ? null : representative.unfoldTemporalStep(valuation);
      return createEquivalenceClass(successorRepresentative,
        factory.reference(temporalStepBdd(unfoldBdd(bdd), valuation)));
    }
  }

  private final class BddVisitor extends DefaultIntVisitor {
    @Override
    protected int defaultAction(Formula formula) {
      return getVariable(formula);
    }

    @Override
    public int visit(Conjunction c) {
      int x = factory.getTrueNode();

      for (Formula child : c.children) {
        int y = child.accept(this);
        x = factory.consume(factory.and(x, y), x, y);
      }

      return x;
    }

    @Override
    public int visit(Disjunction d) {
      int x = factory.getFalseNode();

      for (Formula child : d.children) {
        int y = child.accept(this);
        x = factory.consume(factory.or(x, y), x, y);
      }

      return x;
    }

    @Override
    public int visit(BooleanConstant b) {
      if (b.value) {
        return factory.getTrueNode();
      } else {
        return factory.getFalseNode();
      }
    }
  }
}
