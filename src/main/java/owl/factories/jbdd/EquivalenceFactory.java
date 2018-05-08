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

import de.tum.in.jbdd.Bdd;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.collections.LabelledTree;
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
import owl.ltl.visitors.PrintVisitor;
import owl.ltl.visitors.PropositionalIntVisitor;
import owl.ltl.visitors.SubstitutionVisitor;
import owl.ltl.visitors.Visitor;

final class EquivalenceFactory extends GcManagedFactory<EquivalenceFactory.BddEquivalenceClass>
  implements EquivalenceClassFactory {

  private final List<String> alphabet;
  private final boolean keepRepresentatives;
  private final BddVisitor visitor;
  private final BddEquivalenceClass falseClass;
  private final BddEquivalenceClass trueClass;

  private Formula[] reverseMapping;
  private final Object2IntMap<Formula> mapping;

  // Compose maps.
  private int[] temporalStepSubstitution;
  private int[] unfoldSubstitution;

  // Protect objects from the GC.
  private EquivalenceClass[] temporalStepSubstitutes;
  private EquivalenceClass[] unfoldSubstitutes;

  public EquivalenceFactory(Bdd factory, List<String> alphabet, boolean keepRepresentatives) {
    super(factory);

    this.alphabet = List.copyOf(alphabet);
    this.keepRepresentatives = keepRepresentatives;

    int alphabetSize = this.alphabet.size();
    mapping = new Object2IntOpenHashMap<>();
    mapping.defaultReturnValue(-1);
    reverseMapping = new Formula[alphabetSize];
    visitor = new BddVisitor();

    unfoldSubstitution = new int[alphabetSize];
    unfoldSubstitutes = new EquivalenceClass[alphabetSize];
    temporalStepSubstitution = new int[alphabetSize];
    temporalStepSubstitutes = new EquivalenceClass[alphabetSize];

    // Register literals.
    for (int i = 0; i < alphabetSize; i++) {
      Literal literal = new Literal(i);
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
  public BitSet atomicPropositions(EquivalenceClass clazz) {
    return factory.support(getBdd(clazz), alphabet.size());
  }

  @Override
  public Set<Formula> modalOperators(EquivalenceClass clazz) {
    BitSet support = factory.support(getBdd(clazz));
    support.clear(0, alphabet.size());

    return new AbstractSet<>() {
      @Override
      public boolean contains(Object o) {
        int i = mapping.getInt(o);
        return i != -1 && support.get(i);
      }

      @Override
      public Stream<Formula> stream() {
        return support.stream().mapToObj(i -> reverseMapping[i]);
      }

      @Override
      public Iterator<Formula> iterator() {
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
      Formula representative = next.representative();
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
  public EquivalenceClass substitute(EquivalenceClass clazz,
    Function<Formula, Formula> substitution) {
    BitSet support = factory.support(getBdd(clazz));

    Function<Formula, Formula> guardedSubstitution = x -> {
      if (x instanceof UnaryModalOperator || x instanceof BinaryModalOperator) {
        return substitution.apply(x);
      } else {
        return x;
      }
    };

    int[] substitutionMap = new int[reverseMapping.length];
    Arrays.setAll(substitutionMap,
      i -> support.get(i) ? toBdd(guardedSubstitution.apply(reverseMapping[i])) : -1);

    return transform(clazz, bdd -> factory.compose(bdd, substitutionMap),
      f -> f.accept(new SubstitutionVisitor(guardedSubstitution)));
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
      ? String.format("(%d)", bdd)
      : PrintVisitor.toString(representative, alphabet, false);
  }

  @Override
  public LabelledTree<Integer, EquivalenceClass> temporalStepTree(EquivalenceClass clazz) {
    return temporalStepTree(clazz, new HashMap<>());
  }

  private LabelledTree<Integer, EquivalenceClass> temporalStepTree(EquivalenceClass clazz,
    Map<EquivalenceClass, LabelledTree<Integer, EquivalenceClass>> cache) {
    var tree = cache.get(clazz);

    if (tree != null) {
      return tree;
    }

    int pivot = clazz.atomicPropositions().nextSetBit(0);

    if (pivot == -1) {
      for (int i = 0; i < alphabet.size(); i++) {
        temporalStepSubstitution[i] = -1;
      }

      tree = new LabelledTree.Leaf<>(transform(clazz,
        x -> factory.compose(x, temporalStepSubstitution),
        x -> x.accept(REMOVE_X)));
    } else {
      int[] substitution = new int[pivot + 1];
      Arrays.fill(substitution, 0, pivot, -1);

      substitution[pivot] = factory.getTrueNode();
      var trueSubTree = temporalStepTree(transform(clazz,
        x -> factory.compose(getBdd(clazz), substitution),
        x -> x.accept(replaceLiteralByTrue(pivot))), cache);

      substitution[pivot] = factory.getFalseNode();
      var falseSubTree = temporalStepTree(transform(clazz,
        x -> factory.compose(getBdd(clazz), substitution),
        x -> x.accept(replaceLiteralByFalse(pivot))), cache);

      tree = new LabelledTree.Node<>(pivot, List.of(trueSubTree, falseSubTree));
    }

    cache.put(clazz, tree);
    return tree;
  }

  private static Visitor<Formula> REMOVE_X =
    new SubstitutionVisitor(x -> (x instanceof XOperator) ? ((XOperator) x).operand : x);

  private static Visitor<Formula> replaceLiteralByTrue(int literal) {
    return new SubstitutionVisitor(x -> {
      if (!(x instanceof Literal)) {
        return x;
      }

      Literal castedX = (Literal) x;

      if (castedX.getAtom() != literal) {
        return x;
      }

      return BooleanConstant.of(!castedX.isNegated());
    });
  }

  private static Visitor<Formula> replaceLiteralByFalse(int literal) {
    return new SubstitutionVisitor(x -> {
      if (!(x instanceof Literal)) {
        return x;
      }

      Literal castedX = (Literal) x;

      if (castedX.getAtom() != literal) {
        return x;
      }

      return BooleanConstant.of(castedX.isNegated());
    });
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
    checkLiteralAlphabetRange(propositions);

    List<Formula> newPropositions = propositions.stream()
      .filter(x -> !(x instanceof Literal) && !mapping.containsKey(x))
      .distinct()
      .collect(Collectors.toList());

    int size = mapping.size() + newPropositions.size();
    reverseMapping = Arrays.copyOf(reverseMapping, size);

    unfoldSubstitution = Arrays.copyOf(unfoldSubstitution, size);
    unfoldSubstitutes = Arrays.copyOf(unfoldSubstitutes, size);
    temporalStepSubstitution = Arrays.copyOf(temporalStepSubstitution, size);
    temporalStepSubstitutes = Arrays.copyOf(temporalStepSubstitutes, size);

    for (Formula proposition : newPropositions) {
      assert proposition instanceof UnaryModalOperator
        || proposition instanceof BinaryModalOperator;

      int variable = factory.getVariable(factory.createVariable());
      mapping.put(proposition, variable);
      reverseMapping[variable] = proposition;

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
      assert this.factory().equals(that.factory());
      return bdd == that.bdd;
    }
  }

  private final class BddVisitor extends PropositionalIntVisitor {
    @Override
    protected int modalOperatorAction(Formula formula) {
      if (formula instanceof Literal) {
        Literal literal = (Literal) formula;
        checkLiteralAlphabetRange(List.of(literal));
        int bdd = factory.getVariableNode(literal.getAtom());
        return literal.isNegated() ? factory.not(bdd) : bdd;
      }

      assert formula instanceof UnaryModalOperator || formula instanceof BinaryModalOperator;

      int value = mapping.getInt(formula);

      if (value < 0) {
        // All literals should have been already discovered.
        register(PropositionVisitor.extractPropositions(formula));
        value = mapping.getInt(formula);
        assert value >= 0;
      }

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

  private void checkLiteralAlphabetRange(Collection<Formula> formulas) {
    Optional<Formula> literal = formulas.stream()
      .filter(x -> x instanceof Literal && ((Literal) x).getAtom() >= alphabet.size())
      .findAny();

    if (literal.isPresent()) {
      throw new IllegalArgumentException("Literal " + literal.get() + " is not within alphabet.");
    }
  }
}
