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
import de.tum.in.jbdd.Bdd;
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

final class EquivalenceFactory extends GcManagedFactory<EquivalenceClass>
  implements EquivalenceClassFactory {

  private final ImmutableList<String> alphabet;
  private final int alphabetSize;
  private final BddEquivalenceClass falseClass;
  private final Object2IntMap<Formula> mapping;
  private final BddEquivalenceClass trueClass;
  private final BddVisitor visitor;
  private Formula[] reverseMapping;
  private int[] temporalStepSubstitution;
  private int[] unfoldSubstitution;

  public EquivalenceFactory(Bdd factory, List<String> alphabet) {
    super(factory);

    this.alphabetSize = alphabet.size();
    this.alphabet = ImmutableList.copyOf(alphabet);

    mapping = new Object2IntOpenHashMap<>();
    mapping.defaultReturnValue(-1);
    visitor = new BddVisitor();

    unfoldSubstitution = new int[2 * alphabetSize];
    temporalStepSubstitution = new int[2 * alphabetSize];
    reverseMapping = new Formula[2 * alphabetSize];

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

      // Literals are not unfolded.
      unfoldSubstitution[2 * i] = -1;
      unfoldSubstitution[2 * i + 1] = -1;
    }

    trueClass = new BddEquivalenceClass(BooleanConstant.TRUE, factory.getTrueNode());
    falseClass = new BddEquivalenceClass(BooleanConstant.FALSE, factory.getFalseNode());
  }

  @Override
  public EquivalenceClass of(Formula formula) {
    return createEquivalenceClass(formula, formula.accept(visitor));
  }

  @Override
  public EquivalenceClass conjunction(Iterator<EquivalenceClass> classes) {
    EquivalenceClass result = trueClass;

    while (classes.hasNext()) {
      result = result.and(classes.next());
    }

    return result;
  }

  @Override
  public EquivalenceClass disjunction(Iterator<EquivalenceClass> classes) {
    EquivalenceClass result = trueClass;

    while (classes.hasNext()) {
      result = result.or(classes.next());
    }

    return result;
  }

  private EquivalenceClass createEquivalenceClass(@Nullable Formula representative, int bdd) {
    if (bdd == factory.getTrueNode()) {
      return trueClass;
    }

    if (bdd == factory.getFalseNode()) {
      return falseClass;
    }

    summonReaper();
    return canonicalize(bdd, new BddEquivalenceClass(representative, bdd));
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

    if (value < 0) {
      // All literals should have been already discovered.
      assert formula instanceof UnaryModalOperator || formula instanceof BinaryModalOperator;
      register(PropositionVisitor.extractPropositions(formula));
      value = mapping.getInt(formula);
      assert value >= 0;
    }

    // We don't need to increment the reference-counter, since all variables are saturated.
    return factory.getVariableNode(value);
  }

  @Override
  public ImmutableList<String> getVariables() {
    return alphabet;
  }

  private void register(Deque<Formula> propositions) {
    List<Formula> newPropositions = propositions.stream().distinct()
      .filter(x -> !mapping.containsKey(x)).collect(Collectors.toList());

    int size = mapping.size() + newPropositions.size();
    reverseMapping = Arrays.copyOf(reverseMapping, size);
    unfoldSubstitution = Arrays.copyOf(unfoldSubstitution, size);
    temporalStepSubstitution = Arrays.copyOf(temporalStepSubstitution, size);

    for (Formula proposition : newPropositions) {
      assert proposition instanceof UnaryModalOperator
        || proposition instanceof BinaryModalOperator;
      assert !mapping.containsKey(proposition);

      int variableNode = factory.createVariable();
      int variable = factory.getVariable(variableNode);
      mapping.put(proposition, variable);
      reverseMapping[variable] = proposition;

      if (proposition instanceof XOperator) {
        unfoldSubstitution[variable] = -1;
        temporalStepSubstitution[variable] = ((XOperator) proposition).operand.accept(visitor);
      } else {
        unfoldSubstitution[variable] = proposition.unfold().accept(visitor);
        temporalStepSubstitution[variable] = -1;
      }
    }
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

  private int unfoldBdd(int bdd) {
    return factory.compose(bdd, unfoldSubstitution);
  }

  private final class BddEquivalenceClass implements EquivalenceClass {
    private int bdd;
    @Nullable
    private Formula representative;

    BddEquivalenceClass(@Nullable Formula representative, int bdd) {
      //assert representative == null || factory.dereference(representative.accept(visitor)) == bdd
      //  : "Representative and BDD do not match.";
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
        representative = Conjunction.of(this.representative, that.representative);
      }

      return createEquivalenceClass(representative, factory.reference(factory.and(bdd, that.bdd)));
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
    public void freeRepresentative() {
      if (!factory.isNodeRoot(bdd)) {
        representative = null;
      }
    }

    @Override
    public BitSet getAtoms() {
      BitSet atoms = factory.support(bdd, 2 * alphabetSize);

      for (int i = 0; i < alphabetSize; i++) {
        atoms.set(i, atoms.get(2 * i) || atoms.get(2 * i + 1));
      }

      atoms.clear(alphabetSize, 2 * alphabetSize);
      return atoms;
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
    public Set<Formula> getSupport() {
      return factory.support(bdd).stream()
        .mapToObj(i -> reverseMapping[i]).collect(Collectors.toSet());
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
        representative = Disjunction.of(this.representative, that.representative);
      }
      return createEquivalenceClass(representative, factory.reference(factory.or(bdd, that.bdd)));
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
      Formula substitutionRepresentative = representative == null ? null
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
      return factory.support(bdd).stream().allMatch(i -> predicate.test(reverseMapping[i]));
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
