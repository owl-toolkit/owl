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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.tum.in.jbdd.Bdd;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.collections.Collections3;
import owl.factories.EquivalenceClassFactory;
import owl.factories.PropositionVisitor;
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
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.SubstitutionVisitor;

final class EquivalenceFactory extends GcManagedFactory<EquivalenceFactory.BddEquivalenceClass>
  implements EquivalenceClassFactory {

  private final ImmutableList<String> alphabet;
  private final int alphabetSize;
  private final boolean keepRepresentatives;
  private final BddVisitor visitor;
  private final BddEquivalenceClass falseClass;
  private final BddEquivalenceClass trueClass;

  private Formula[] reverseMapping;
  private final Object2IntMap<Formula> mapping;

  private int[] temporalStepSubstitution;
  private EquivalenceClass[] temporalStepSubstitutes;
  private int[] unfoldSubstitution;
  private EquivalenceClass[] unfoldSubstitutes;

  public EquivalenceFactory(Bdd factory, List<String> alphabet, boolean keepRepresentatives) {
    super(factory);

    this.alphabetSize = alphabet.size();
    this.alphabet = ImmutableList.copyOf(alphabet);
    this.keepRepresentatives = keepRepresentatives;

    mapping = new Object2IntOpenHashMap<>();
    mapping.defaultReturnValue(-1);
    visitor = new BddVisitor();

    int variableCount = 2 * alphabetSize;
    unfoldSubstitution = new int[variableCount];
    // Literals are not unfolded.
    Arrays.fill(unfoldSubstitution, -1);
    unfoldSubstitutes = new EquivalenceClass[variableCount];

    temporalStepSubstitution = new int[variableCount];
    temporalStepSubstitutes = new EquivalenceClass[variableCount];

    reverseMapping = new Formula[variableCount];

    for (int i = 0; i < alphabetSize; i++) {
      Literal literal = new Literal(i);
      int variableNodePos = factory.createVariable();
      int variableNodeNeg = factory.createVariable();

      assert factory.getVariable(variableNodePos) == 2 * i;
      assert factory.getVariable(variableNodeNeg) == 2 * i + 1;

      mapping.put(literal, 2 * i);
      mapping.put(literal.not(), 2 * i + 1);
      reverseMapping[2 * i] = literal;
      reverseMapping[2 * i + 1] = literal.not();
    }

    trueClass = new BddEquivalenceClass(this, factory.getTrueNode(), BooleanConstant.TRUE);
    falseClass = new BddEquivalenceClass(this, factory.getFalseNode(), BooleanConstant.FALSE);
  }

  @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
  @Override
  public ImmutableList<String> variables() {
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
  public BitSet getAtoms(EquivalenceClass clazz) {
    int bdd = getBdd(clazz);
    BitSet atoms = factory.support(bdd, 2 * alphabetSize);

    for (int i = 0; i < alphabetSize; i++) {
      atoms.set(i, atoms.get(2 * i) || atoms.get(2 * i + 1));
    }

    atoms.clear(alphabetSize, 2 * alphabetSize);
    return atoms;
  }

  @Override
  public Set<Formula> getSupport(EquivalenceClass clazz) {
    int bdd = getBdd(clazz);
    BitSet support = factory.support(bdd);
    ImmutableSet.Builder<Formula> builder = ImmutableSet.builder();
    BitSets.forEach(support, i -> builder.add(reverseMapping[i]));
    return builder.build();
  }

  @Override
  public boolean testSupport(EquivalenceClass clazz, Predicate<Formula> predicate) {
    IntIterator iterator = BitSets.iterator(factory.support(getBdd(clazz)));
    while (iterator.hasNext()) {
      if (!predicate.test(reverseMapping[iterator.nextInt()])) {
        return false;
      }
    }
    return true;
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
      Formula representative = next.getRepresentative();
      if (representative == null || representatives == null) {
        representatives = null; // NOPMD
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
      Formula representative = next.getRepresentative();
      if (representative == null || representatives == null) {
        representatives = null; // NOPMD
      } else {
        representatives.add(representative);
      }
      resultBdd = factory.or(resultBdd, getBdd(next));
    }

    return create(representatives == null ? null : Disjunction.of(representatives), resultBdd);
  }

  @Override
  public EquivalenceClass exists(EquivalenceClass clazz, Predicate<Formula> predicate) {
    BitSet exists = new BitSet();

    for (int i = 0; i < reverseMapping.length; i++) {
      if (predicate.test(reverseMapping[i])) {
        exists.set(i);
      }
    }

    return create(null, factory.exists(getBdd(clazz), exists));
  }

  @Override
  public EquivalenceClass substitute(EquivalenceClass clazz,
    Function<? super Formula, ? extends Formula> substitution) {
    BitSet support = factory.support(getBdd(clazz));

    int[] substitutionMap = new int[reverseMapping.length];
    Arrays.setAll(substitutionMap,
      i -> support.get(i) ? toBdd(substitution.apply(reverseMapping[i])) : -1);

    return transform(clazz, bdd -> factory.compose(bdd, substitutionMap),
      f -> f.accept(new SubstitutionVisitor(substitution)));
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
    for (int i = 0; i < alphabetSize; i++) {
      if (valuation.get(i)) {
        temporalStepSubstitution[2 * i] = factory.getTrueNode();
        temporalStepSubstitution[2 * i + 1] = factory.getFalseNode();
      } else {
        temporalStepSubstitution[2 * i] = factory.getFalseNode();
        temporalStepSubstitution[2 * i + 1] = factory.getTrueNode();
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

    Formula representative = clazz.getRepresentative();
    return representative == null
      ? String.format("(%d)", bdd)
      : PrintVisitor.toString(representative, alphabet, false);
  }


  int getVariable(Formula formula) {
    assert formula instanceof Literal
      || formula instanceof UnaryModalOperator
      || formula instanceof BinaryModalOperator;

    int value = mapping.getInt(formula);

    if (value < 0) {
      // All literals should have been already discovered.
      assert formula instanceof UnaryModalOperator || formula instanceof BinaryModalOperator;
      register(PropositionVisitor.extractPropositions(formula));
      value = mapping.getInt(formula);
      assert value >= 0;
    }

    return factory.getVariableNode(value);
  }

  private BddEquivalenceClass getClass(EquivalenceClass clazz) {
    assert this.equals(clazz.getFactory());
    return (BddEquivalenceClass) clazz;
  }

  private int getBdd(EquivalenceClass clazz) {
    int bdd = getClass(clazz).bdd;
    assert factory.getReferenceCount(bdd) > 0 || factory.getReferenceCount(bdd) == -1;
    return bdd;
  }

  private int toBdd(Formula formula) {
    return factory.dereference(formula.accept(visitor));
  }

  private BddEquivalenceClass create(@Nullable Formula representative, int bdd) {
    if (bdd == factory.getTrueNode()) {
      return trueClass;
    }

    if (bdd == factory.getFalseNode()) {
      return falseClass;
    }

    return canonicalize(bdd, new BddEquivalenceClass(this, bdd,
      keepRepresentatives ? representative : null));
  }

  private void register(Deque<Formula> propositions) {
    propositions.removeAll(mapping.keySet());
    List<Formula> newPropositions = new ArrayList<>(propositions.size());
    Collections3.addAllDistinct(newPropositions, propositions);

    int size = mapping.size() + newPropositions.size();
    reverseMapping = Arrays.copyOf(reverseMapping, size);

    unfoldSubstitution = Arrays.copyOf(unfoldSubstitution, size);
    unfoldSubstitutes = Arrays.copyOf(unfoldSubstitutes, size);
    temporalStepSubstitution = Arrays.copyOf(temporalStepSubstitution, size);
    temporalStepSubstitutes = Arrays.copyOf(temporalStepSubstitutes, size);

    for (Formula proposition : newPropositions) {
      assert proposition instanceof UnaryModalOperator
        || proposition instanceof BinaryModalOperator;
      assert !mapping.containsKey(proposition);

      int variableNode = factory.createVariable();
      int variable = factory.getVariable(variableNode);
      mapping.put(proposition, variable);
      reverseMapping[variable] = proposition;

      if (proposition instanceof XOperator) {
        Formula operand = ((XOperator) proposition).operand;
        BddEquivalenceClass clazz = create(operand, toBdd(operand));
        int bdd = clazz.bdd;
        assert factory.getReferenceCount(bdd) == 1 || factory.getReferenceCount(bdd) == -1;

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
  }

  private BddEquivalenceClass transform(EquivalenceClass clazz,
    IntUnaryOperator bddTransformer, Function<Formula, Formula> representativeTransformer) {
    BddEquivalenceClass casted = getClass(clazz);

    int newBdd = bddTransformer.applyAsInt(casted.bdd);

    @Nullable
    Formula representative = clazz.getRepresentative();
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

  static final class BddEquivalenceClass extends EquivalenceClass {
    final int bdd;

    BddEquivalenceClass(EquivalenceClassFactory factory, int bdd,
      @Nullable Formula representative) {
      super(factory, representative);
      this.bdd = bdd;
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
      assert this.getFactory().equals(that.getFactory());
      return bdd == that.bdd;
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
