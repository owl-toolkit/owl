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

package owl.factories.sylvan;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import jsylvan.JSylvan;
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
import owl.ltl.visitors.DefaultVisitor;
import owl.ltl.visitors.SubstitutionVisitor;

public final class EquivalenceFactory implements EquivalenceClassFactory {
  private final Object lock = new Object();

  private final int alphabetSize;
  private final BddEquivalenceClass falseClass;
  private final Object2IntMap<Formula> mapping;
  private final BddEquivalenceClass trueClass;
  private final BddVisitor visitor;
  private List<String> atomMapping;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private Formula[] reverseMapping;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private long[] vars;

  private EquivalenceFactory(Formula formula, int alphabetSize, List<String> atomMapping) {
    Deque<Formula> queuedFormulas = PropositionVisitor.extractPropositions(formula);
    this.alphabetSize = alphabetSize;

    mapping = new Object2IntOpenHashMap<>();
    int size = alphabetSize + queuedFormulas.size();
    visitor = new BddVisitor();

    vars = new long[size];
    reverseMapping = new Formula[size];

    int i;

    for (i = 0; i < alphabetSize; i++) {
      Literal literal = new Literal(i);
      vars[i] = JSylvan.ithvar(i);

      // In order to "distinguish" -0 and +0 we shift the variables by 1 -> -1, 1.
      mapping.put(literal, i + 1);
      mapping.put(literal.not(), -(i + 1));
      reverseMapping[i] = literal;
    }

    i = register(queuedFormulas, i);
    resize(i);

    trueClass = new BddEquivalenceClass(BooleanConstant.TRUE, JSylvan.getTrue());
    falseClass = new BddEquivalenceClass(BooleanConstant.FALSE, JSylvan.getFalse());
    this.atomMapping = ImmutableList.copyOf(atomMapping);
  }

  public static EquivalenceFactory create(Formula formula, int alphabetSize) {
    return create(formula, alphabetSize, ImmutableList.of());
  }

  public static EquivalenceFactory create(Formula formula, int alphabetSize,
    List<String> atomMapping) {
    return new EquivalenceFactory(formula, alphabetSize, atomMapping);
  }

  @Override
  public BddEquivalenceClass createEquivalenceClass(Formula formula) {
    return createEquivalenceClass(formula, formula.accept(visitor));
  }

  private BddEquivalenceClass createEquivalenceClass(@Nullable Formula representative, long bdd) {
    if (bdd == JSylvan.getTrue()) {
      return trueClass;
    }

    if (bdd == JSylvan.getFalse()) {
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

  private long getVariable(Formula formula) {
    assert formula instanceof Literal || formula instanceof UnaryModalOperator
      || formula instanceof BinaryModalOperator;

    int value = mapping.getInt(formula);

    if (value == 0) {
      // All literals should have been already discovered.
      assert !(formula instanceof Literal);
      Deque<Formula> propositions = PropositionVisitor.extractPropositions(formula);

      value = vars.length;
      resize(vars.length + propositions.size());
      value = register(propositions, value);
      resize(value);
    }

    // We don't need to increment the reference-counter, since all variables are protected.
    if (value > 0) {
      return vars[value - 1];
    } else {
      return JSylvan.makeNot(vars[-(value + 1)]);
    }
  }

  private void register(Formula proposition, int i) {
    assert !(proposition instanceof Literal);

    synchronized (lock) {
      vars[i] = JSylvan.ithvar(i);
      mapping.put(proposition, i + 1);
      reverseMapping[i] = proposition;

      if (Fragments.isX(proposition)) {
        JSylvan.nithvar(i);
        mapping.put(proposition.not(), -(i + 1));
      }
    }
  }

  private int register(Deque<Formula> propositions, int i) {
    int counter = i;
    synchronized (lock) {
      for (Formula proposition : propositions) {
        if (mapping.containsKey(proposition)) {
          continue;
        }

        register(proposition, counter);
        counter++;
      }
    }

    return counter;
  }

  // TODO: Use size counter to reduce number of copies.
  private void resize(int size) {
    vars = Arrays.copyOf(vars, size);
    reverseMapping = Arrays.copyOf(reverseMapping, size);
  }

  @Override
  public void setVariables(List<String> variables) {
    atomMapping = ImmutableList.copyOf(variables);
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
    return bitSet.stream().mapToObj(x -> reverseMapping[x]).collect(Collectors.toSet());
  }

  private final class BddEquivalenceClass implements EquivalenceClass {
    private static final long INVALID_BDD = -1L;
    @Nullable
    private final Formula representative;
    private long bdd;

    BddEquivalenceClass(@Nullable Formula representative, long bdd) {
      this.representative = representative;
      this.bdd = bdd;
    }

    @Override
    public EquivalenceClass and(EquivalenceClass equivalenceClass) {
      assert equivalenceClass instanceof BddEquivalenceClass;
      BddEquivalenceClass that = (BddEquivalenceClass) equivalenceClass;
      return createEquivalenceClass(Conjunction.create(representative, that.representative),
        JSylvan.and(bdd, that.bdd));
    }

    @Override
    public EquivalenceClass andWith(EquivalenceClass equivalenceClass) {
      EquivalenceClass and = and(equivalenceClass);
      free();
      return and;
    }

    @Override
    public EquivalenceClass duplicate() {
      return new BddEquivalenceClass(representative, JSylvan.ref(bdd));
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

      return createEquivalenceClass(null, JSylvan.ref(JSylvan.makeExists(bdd, exists)));
    }

    @Override
    public void free() {
      if (bdd == INVALID_BDD) {
        throw new IllegalStateException("double free");
      }

      if (bdd != trueClass.bdd && bdd != falseClass.bdd) {
        JSylvan.deref(bdd);
        bdd = INVALID_BDD;
      }
    }

    @Override
    public void freeRepresentative() {
      // NOP.
    }

    @Override
    public BitSet getAtoms() {
      return JSylvan.support(bdd, alphabetSize);
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
      BitSet support = JSylvan.support(bdd);
      //noinspection unchecked - We want to save a .map(clazz::cast) here
      return (Set<T>) support.stream().mapToObj(i -> reverseMapping[i]).filter(clazz::isInstance)
        .collect(Collectors.toSet());
    }

    @Override
    public int hashCode() {
      return Long.hashCode(bdd);
    }

    @Override
    public boolean implies(EquivalenceClass equivalenceClass) {
      assert equivalenceClass instanceof BddEquivalenceClass;
      BddEquivalenceClass that = (BddEquivalenceClass) equivalenceClass;
      return JSylvan.implies(bdd, that.bdd);
    }

    @Override
    public boolean isFalse() {
      return bdd == JSylvan.getFalse();
    }

    @Override
    public boolean isTrue() {
      return bdd == JSylvan.getTrue();
    }

    @Override
    public EquivalenceClass or(EquivalenceClass equivalenceClass) {
      assert equivalenceClass instanceof BddEquivalenceClass;
      BddEquivalenceClass that = (BddEquivalenceClass) equivalenceClass;
      return createEquivalenceClass(Disjunction.create(representative, that.representative),
        JSylvan.or(bdd, that.bdd));
    }

    @Override
    public ImmutableList<Set<Formula>> satisfyingAssignments(
      Iterable<? extends Formula> support) {
      BitSet supportBitSet = toBitSet(support);
      Iterator<BitSet> minimalSolutions = JSylvan.getMinimalSolutions(bdd);
      Set<BitSet> closure = EquivalenceClassUtil.upwardClosure(supportBitSet, minimalSolutions);
      return closure.stream().map(EquivalenceFactory.this::toSet)
        .collect(ImmutableList.toImmutableList());
    }

    @Override
    public EquivalenceClass substitute(Function<? super Formula, ? extends Formula> substitution) {
      assert representative != null;
      SubstitutionVisitor visitor = new SubstitutionVisitor(substitution);
      return createEquivalenceClass(representative.accept(visitor));
    }

    @Override
    public EquivalenceClass temporalStep(BitSet valuation) {
      assert representative != null;
      return createEquivalenceClass(representative.temporalStep(valuation));
    }

    @Override
    public EquivalenceClass temporalStepUnfold(BitSet valuation) {
      assert representative != null;
      return createEquivalenceClass(representative.temporalStepUnfold(valuation));
    }

    @Override
    public boolean testSupport(Predicate<Formula> predicate) {
      BitSet support = JSylvan.support(bdd);
      return support.stream().allMatch(i -> predicate.test(reverseMapping[i]));
    }

    @Override
    public String toString() {
      assert Ordering.natural().isStrictlyOrdered(new LongArrayList(vars));

      String representativeString;

      if (JSylvan.isVariableOrNegated(bdd)) {
        int variablePos = Arrays.binarySearch(vars, bdd);

        if (variablePos >= 0) {
          representativeString = reverseMapping[variablePos].toString(atomMapping, false);
        } else {
          int notVariablePosition = Arrays.binarySearch(vars, JSylvan.makeNot(bdd));
          representativeString = reverseMapping[notVariablePosition].not()
            .toString(atomMapping, false);
        }
      } else if (representative == null) {
        representativeString = "?";
      } else {
        representativeString = representative.toString(atomMapping, false);
      }

      return String.format("%s (%d)", representativeString, bdd);
    }

    @Override
    public EquivalenceClass unfold() {
      assert representative != null;
      return createEquivalenceClass(representative.unfold());
    }

    @Override
    public EquivalenceClass unfoldTemporalStep(BitSet valuation) {
      assert representative != null;
      return createEquivalenceClass(representative.unfoldTemporalStep(valuation));
    }
  }

  private final class BddVisitor extends DefaultVisitor<Long> {
    @Override
    protected Long defaultAction(Formula formula) {
      return getVariable(formula);
    }

    @Override
    public Long visit(Conjunction conjunction) {
      long x = JSylvan.getTrue();

      for (Formula child : conjunction.children) {
        x = JSylvan.andConsuming(x, child.accept(this));
      }

      return x;
    }

    @Override
    public Long visit(Disjunction disjunction) {
      long x = JSylvan.getFalse();

      for (Formula child : disjunction.children) {
        x = JSylvan.orConsuming(x, child.accept(this));
      }

      return x;
    }

    @Override
    public Long visit(BooleanConstant booleanConstant) {
      if (booleanConstant.value) {
        return JSylvan.getTrue();
      } else {
        return JSylvan.getFalse();
      }
    }
  }
}
