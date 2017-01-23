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

package ltl.equivalence;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import ltl.BinaryModalOperator;
import ltl.BooleanConstant;
import ltl.Conjunction;
import ltl.Disjunction;
import ltl.Formula;
import ltl.Literal;
import ltl.UnaryModalOperator;
import ltl.visitors.AlphabetVisitor;
import ltl.visitors.DefaultVisitor;
import ltl.visitors.predicates.XFragmentPredicate;
import omega_automaton.collections.Collections3;
import jsylvan.JSylvan;

public class SylvanEquivalenceClassFactory implements EquivalenceClassFactory {

  private final int alphabetSize;
  private final BddEquivalenceClass falseClass;
  private final Object2IntMap<Formula> mapping;
  private final BddEquivalenceClass trueClass;
  private final BddVisitor visitor;
  private Map<Integer, String> atomMapping;
  private Formula[] reverseMapping;
  private long[] vars;

  public SylvanEquivalenceClassFactory(Formula formula) {
    this(formula, AlphabetVisitor.extractAlphabet(formula), null);
  }

  public SylvanEquivalenceClassFactory(Formula formula, int alphabetSize, Map<Integer, String> atomMapping) {
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
      vars[i] = JSylvan.ref(JSylvan.makeVar(i));

      // In order to "distinguish" -0 and +0 we shift the variables by 1 -> -1, 1.
      mapping.put(literal, i + 1);
      mapping.put(literal.not(), -(i + 1));
      reverseMapping[i] = literal;
    }

    i = register(queuedFormulas, i);
    resize(i);

    trueClass = new BddEquivalenceClass(BooleanConstant.TRUE, JSylvan.getTrue());
    falseClass = new BddEquivalenceClass(BooleanConstant.FALSE, JSylvan.getFalse());
    this.atomMapping = (atomMapping != null) ? atomMapping : new HashMap<>();
  }

  @Override
  public BddEquivalenceClass createEquivalenceClass(Formula representative) {
    return createEquivalenceClass(representative, representative.accept(visitor));
  }

  private BddEquivalenceClass createEquivalenceClass(Formula representative, long bdd) {
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

  private long getVariable(@Nonnull Formula formula) {
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

    // We don't need to increment the reference-counter, since all variables are saturated.
    return value > 0 ? JSylvan.ref(vars[value - 1]) : JSylvan.ref(JSylvan.makeNot(vars[-(value + 1)]));
  }

  private void register(Formula proposition, int i) {
    assert !(proposition instanceof Literal);

    vars[i] = JSylvan.ref(JSylvan.makeVar(i));
    mapping.put(proposition, i + 1);
    reverseMapping[i] = proposition;

    if (proposition.accept(XFragmentPredicate.INSTANCE)) {
      JSylvan.ref(JSylvan.makeNot(JSylvan.makeVar(i)));
      mapping.put(proposition.not(), -(i + 1));
    }
  }

  private int register(Deque<Formula> propositions, int i) {
    for (Formula proposition : propositions) {
      if (mapping.containsKey(proposition)) {
        continue;
      }

      register(proposition, i);
      i++;
    }

    return i;
  }

  // TODO: Use size counter to reduce number of copies.
  private void resize(int size) {
    vars = Arrays.copyOf(vars, size);
    reverseMapping = Arrays.copyOf(reverseMapping, size);
  }

  public void setAtomMapping(Map<Integer, String> mapping) {
    atomMapping = new HashMap<>(mapping);
  }

  private BitSet toBitSet(Iterable<? extends Formula> formulas) {
    BitSet bitSet = new BitSet();
    formulas.forEach(x -> bitSet.set(mapping.getInt(x) - 1));
    return bitSet;
  }

  private Set<Formula> toSet(BitSet bitSet) {
    Set<Formula> formulas = new HashSet<>();
    bitSet.stream().forEach(x -> formulas.add(reverseMapping[x]));
    return formulas;
  }

  private class BddEquivalenceClass implements EquivalenceClass {

    private static final long INVALID_BDD = -1;

    private long bdd;
    private final Formula representative;

    private BddEquivalenceClass(Formula representative, long bdd) {
      this.representative = representative;
      this.bdd = bdd;
    }

    @Override
    public EquivalenceClass and(EquivalenceClass eq) {
      BddEquivalenceClass that = (BddEquivalenceClass) eq;
      return createEquivalenceClass(Conjunction.create(representative, that.representative),
        JSylvan.ref(JSylvan.makeAnd(bdd, that.bdd)));
    }

    @Override
    public EquivalenceClass andWith(EquivalenceClass eq) {
      EquivalenceClass and = and(eq);
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
    @Nullable
    public Formula getRepresentative() {
      return representative;
    }

    @Override
    public <T extends Formula> Set<T> getSupport(Class<T> clazz) {
      BitSet support = JSylvan.support(bdd);
      return (Set<T>) support.stream().mapToObj(i -> reverseMapping[i]).filter(clazz::isInstance)
        .collect(Collectors.toSet());
    }

    @Override
    public int hashCode() {
      return Long.hashCode(bdd);
    }

    @Override
    public boolean implies(EquivalenceClass equivalenceClass) {
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
    public EquivalenceClass or(EquivalenceClass eq) {
      BddEquivalenceClass that = (BddEquivalenceClass) eq;
      return createEquivalenceClass(Disjunction.create(representative, that.representative),
        JSylvan.ref(JSylvan.makeOr(bdd, that.bdd)));
    }

    @Override
    public EquivalenceClass orWith(EquivalenceClass eq) {
      EquivalenceClass or = or(eq);
      free();
      return or;
    }

    @Override
    public ImmutableList<Set<Formula>> satisfyingAssignments(Iterable<? extends Formula> supportIterable) {
      final BitSet support = toBitSet(supportIterable);
      final Set<BitSet> satisfyingAssignments = new HashSet<>();

      JSylvan.getMinimalSolutions(bdd)
        .forEachRemaining(x -> satisfyingAssignments.add((BitSet) x.clone()));

      // Build restricted upward closure

      Deque<BitSet> candidates = new ArrayDeque<>(satisfyingAssignments);

      while (!candidates.isEmpty()) {
        BitSet valuation = candidates.removeFirst();
        assert Collections3.subset(valuation, support);

        for (int i = support.nextSetBit(0); i >= 0; i = support.nextSetBit(i + 1)) {
          if (valuation.get(i)) {
            continue;
          }

          BitSet nextValuation = (BitSet) valuation.clone();
          nextValuation.set(i);

          // Skip processed elements
          if (satisfyingAssignments.contains(nextValuation)) {
            continue;
          }

          candidates.add(nextValuation);
          satisfyingAssignments.add(nextValuation);
        }
      }

      return satisfyingAssignments.stream().map(SylvanEquivalenceClassFactory.this::toSet)
        .collect(ImmutableList.toImmutableList());
    }

    @Override
    public EquivalenceClass substitute(Function<? super Formula, ? extends Formula> substitution) {
      SubstVisitor visitor = new SubstVisitor(substitution);
      return createEquivalenceClass(representative.accept(visitor));
    }

    @Override
    public EquivalenceClass temporalStep(BitSet valuation) {
      return createEquivalenceClass(representative.temporalStep(valuation));
    }

    @Override
    public EquivalenceClass temporalStepUnfold(BitSet valuation) {
      return createEquivalenceClass(representative.temporalStepUnfold(valuation));
    }

    @Override
    public boolean testSupport(Predicate<Formula> predicate) {
      BitSet support = JSylvan.support(bdd);
      return support.stream().allMatch(i -> predicate.test(reverseMapping[i]));
    }

    @Override
    public String toString() {
      String representativeString;

      if (JSylvan.isVariableOrNegated(bdd)) {
        int pos = Arrays.binarySearch(vars, bdd);

        if (pos >= 0) {
          representativeString = reverseMapping[pos].toString(atomMapping);
        } else {
          pos = Arrays.binarySearch(vars, JSylvan.makeNot(bdd));
          representativeString = reverseMapping[pos].not().toString(atomMapping);
        }
      } else if (representative == null) {
        representativeString = "?";
      } else {
        representativeString = representative.toString(atomMapping);
      }

      return representativeString + " (" + bdd + ")";
    }

    @Override
    public EquivalenceClass unfold() {
      return createEquivalenceClass(representative.unfold());
    }

    @Override
    public EquivalenceClass unfoldTemporalStep(BitSet valuation) {
      return createEquivalenceClass(representative.unfoldTemporalStep(valuation));
    }
  }

  private class BddVisitor extends DefaultVisitor<Long> {
    @Override
    protected Long defaultAction(Formula formula) {
      return getVariable(formula);
    }

    @Override
    public Long visit(Conjunction c) {
      long x = JSylvan.getTrue();

      for (Formula child : c.children) {
        long y = child.accept(this);
        x = JSylvan.consume(JSylvan.makeAnd(x, y), x, y);
      }

      return x;
    }

    @Override
    public Long visit(Disjunction d) {
      long x = JSylvan.getFalse();

      for (Formula child : d.children) {
        long y = child.accept(this);
        x = JSylvan.consume(JSylvan.makeOr(x, y), x, y);
      }

      return x;
    }

    @Override
    public Long visit(BooleanConstant b) {
      return b.value ? JSylvan.getTrue() : JSylvan.getFalse();
    }
  }

  private static class SubstVisitor extends DefaultVisitor<Formula> {

    private final Function<? super Formula, ? extends Formula> subst;

    private SubstVisitor(Function<? super Formula, ? extends Formula> subst) {
      this.subst = subst;
    }

    @Override
    protected Formula defaultAction(Formula formula) {
      return subst.apply(formula);
    }

    @Override
    public Formula visit(final Conjunction conjunction) {
      return Conjunction.create(conjunction.children.stream().map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(final Disjunction disjunction) {
      return Disjunction.create(disjunction.children.stream().map(x -> x.accept(this)));
    }
  }
}
