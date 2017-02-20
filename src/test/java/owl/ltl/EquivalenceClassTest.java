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

package owl.ltl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.parser.LtlParser;
import owl.ltl.parser.ParseException;
import owl.ltl.simplifier.Simplifier;
import owl.ltl.simplifier.Simplifier.Strategy;

public abstract class EquivalenceClassTest {
  private static final List<String> formulaeStrings = ImmutableList
    .of("G a", "F G a", "G a | G b", "(G a) U (G b)", "X G b", "F F ((G a) & b)", "a & G b");
  private static final List<Formula> formulae = ImmutableList
    .copyOf(formulaeStrings.stream().map(LtlParser::formula).collect(Collectors.toList()));
  private Formula contradiction;
  private EquivalenceClassFactory factory;
  private Formula literal;
  private Formula tautology;

  @Before
  public void setUp() {
    contradiction = BooleanConstant.FALSE;
    tautology = BooleanConstant.TRUE;
    literal = new Literal(0);

    factory = setUpFactory(LtlParser.formula("a & b & c & d"));
  }

  public abstract EquivalenceClassFactory setUpFactory(Formula domain);

  @Test
  public void testEmptyDomain() {
    EquivalenceClassFactory factory = setUpFactory(BooleanConstant.TRUE);
    assertNotEquals(factory, null);
  }

  @Test
  public void testEqualsAndHashCode() {
    Collection<EquivalenceClass> classes = new ArrayList<>();

    classes.add(factory.createEquivalenceClass(contradiction));
    classes.add(factory.createEquivalenceClass(tautology));
    classes.add(factory.createEquivalenceClass(literal));
    classes.add(factory.createEquivalenceClass(new Disjunction(tautology, contradiction, literal)));
    classes.add(factory.createEquivalenceClass(new Conjunction(tautology, contradiction, literal)));

    for (EquivalenceClass lhs : classes) {
      for (EquivalenceClass rhs : classes) {
        assertEquals(lhs.equals(rhs), lhs.equals(rhs));

        if (lhs.equals(rhs)) {
          assertEquals(lhs.hashCode(), rhs.hashCode());
        }
      }
    }
  }

  @Test
  public void testEquivalent() {
    EquivalenceClass equivalenceClass = factory.createEquivalenceClass(contradiction);

    assertEquals(equivalenceClass, equivalenceClass);
    assertEquals(equivalenceClass, factory.createEquivalenceClass(Simplifier
      .simplify(new Conjunction(literal, new Literal(0, true)), Simplifier.Strategy.MODAL_EXT)));
  }

  @Test
  public void testExistsAndSat() throws ParseException {
    Predicate<Formula> predicate = ((Predicate<Formula>) GOperator.class::isInstance).negate();
    LtlParser parser = new LtlParser();

    Formula[] formulas = {
      parser.parseLtl("a"),
      parser.parseLtl("G a"),
      parser.parseLtl("G a | G b | a"),
      parser.parseLtl("G a & G b & a"),
    };

    EquivalenceClass classA = factory.createEquivalenceClass(formulas[0]);
    EquivalenceClass classExistsA = classA.exists(predicate);
    assertEquals(factory.getTrue(), classExistsA);

    EquivalenceClass classB = factory.createEquivalenceClass(formulas[1]);
    EquivalenceClass classExistsB = classB.exists(predicate);
    assertEquals(classB, classExistsB);
    assertEquals(Collections.singleton(Collections.singleton(formulas[1])),
      Sets.newHashSet(classExistsB.satisfyingAssignments(Collections.singletonList(formulas[1]))));

    EquivalenceClass classC = factory.createEquivalenceClass(formulas[2]);
    EquivalenceClass classExistsC = classC.exists(predicate);
    Set<Formula> allGOperators = Sets.newHashSet(parser.parseLtl("G a"), parser.parseLtl("G b"));
    assertEquals(factory.getTrue(), classExistsC);
    assertEquals(Sets.powerSet(allGOperators),
      Sets.newHashSet(classExistsC.satisfyingAssignments(allGOperators)));

    EquivalenceClass classD = factory.createEquivalenceClass(formulas[3]);
    EquivalenceClass classExistsD = classD.exists(predicate);
    assertEquals(factory.createEquivalenceClass(parser.parseLtl("G a & G b")), classExistsD);
    assertEquals(Collections.singleton(allGOperators),
      Sets.newHashSet(classExistsD.satisfyingAssignments(allGOperators)));
  }

  @Test
  public void testFrequencyGNotFalse() throws ParseException {
    Formula formula = LtlParser.formula("G { >= 0.4} a");
    EquivalenceClassFactory factory = setUpFactory(formula);
    EquivalenceClass clazz = factory.createEquivalenceClass(formula);
    assertNotEquals(factory.getFalse(), clazz.unfold().temporalStep(new BitSet(0)));
  }

  @Test
  public void testGetAtoms() throws ParseException {
    Formula formula = LtlParser.formula("a & (a | b) & (F c)");
    EquivalenceClassFactory factory = setUpFactory(formula);
    EquivalenceClass clazz = factory.createEquivalenceClass(formula);
    BitSet atoms = new BitSet();
    atoms.set(0);
    assertEquals(atoms, clazz.getAtoms());
    atoms.set(2);
    assertEquals(atoms, clazz.unfold().getAtoms());
  }

  @Test
  public void testGetAtoms2() throws ParseException {
    Formula formula = LtlParser.formula("(a | (b & X a) | (F a)) & (c | (b & X a) | (F a))");
    EquivalenceClassFactory factory = setUpFactory(formula);
    EquivalenceClass clazz = factory.createEquivalenceClass(formula);
    BitSet atoms = new BitSet();
    atoms.set(0, 3);
    assertEquals(atoms, clazz.getAtoms());
  }

  @Test
  public void testGetAtomsEmpty() throws ParseException {
    Formula formula = LtlParser.formula("G a");
    EquivalenceClassFactory factory = setUpFactory(formula);
    EquivalenceClass clazz = factory.createEquivalenceClass(formula);
    BitSet atoms = new BitSet();
    assertEquals(atoms, clazz.getAtoms());
    atoms.set(0);
    assertEquals(atoms, clazz.unfold().getAtoms());
  }

  @Test
  public void testGetRepresentative() {
    assertEquals(contradiction, factory.createEquivalenceClass(contradiction).getRepresentative());
  }

  @Test
  public void testGetSupport() throws ParseException {
    LtlParser parser = new LtlParser();
    Formula[] formulas = {
      parser.parseLtl("a"),
      parser.parseLtl("F a"),
      parser.parseLtl("G a")
    };

    EquivalenceClass clazz = factory.createEquivalenceClass(Conjunction.create(formulas));

    assertEquals(Sets.newHashSet(formulas), clazz.getSupport());
    assertEquals(Collections.singleton(formulas[1]), clazz.getSupport(FOperator.class));
  }

  @Test
  public void testImplies() {
    EquivalenceClass contradictionClass = factory.createEquivalenceClass(contradiction);
    EquivalenceClass tautologyClass = factory.createEquivalenceClass(tautology);
    EquivalenceClass literalClass = factory.createEquivalenceClass(literal);

    assertTrue(contradictionClass.implies(contradictionClass));

    assertTrue(contradictionClass.implies(tautologyClass));
    assertTrue(contradictionClass.implies(literalClass));

    assertTrue(literalClass.implies(tautologyClass));
    assertFalse(literalClass.implies(contradictionClass));

    assertFalse(tautologyClass.implies(contradictionClass));
    assertFalse(tautologyClass.implies(literalClass));
  }

  // @Test
  public void testLtlBackgroundTheory1() throws ParseException {
    LtlParser parser = new LtlParser();
    Formula f1 = parser.parseLtl("G p0 & p0");
    Formula f2 = parser.parseLtl("G p0");
    assertEquals(f2, Simplifier.simplify(f1, Strategy.AGGRESSIVELY));
  }

  // @Test
  public void testLtlBackgroundTheory2() throws ParseException {
    LtlParser parser = new LtlParser();
    Formula f1 = parser.parseLtl("G p0 | p0");
    Formula f2 = parser.parseLtl("p0");
    assertEquals(f2, Simplifier.simplify(f1, Strategy.AGGRESSIVELY));
  }

  // @Test
  public void testLtlBackgroundTheory3() {
    Formula f1 = new Literal(1, false);
    Formula f2 = new GOperator(f1);
    Formula f5 = Simplifier.simplify(new Conjunction(
      new GOperator(new FOperator(new XOperator(f1))), f2), Strategy.MODAL);
    assertEquals(Simplifier.simplify(f5, Strategy.AGGRESSIVELY), f2);
  }

  @Test
  public void testSubstitute() throws ParseException {
    LtlParser parser = new LtlParser();
    EquivalenceClass[] formulas = {
      factory.createEquivalenceClass(parser.parseLtl("a")),
      factory.createEquivalenceClass(parser.parseLtl("G a")),
      factory.createEquivalenceClass(parser.parseLtl("G a & a"))
    };

    assertEquals(formulas[1].substitute(Formula::unfold), formulas[2]);
    assertEquals(formulas[2].substitute(x -> x instanceof GOperator ? BooleanConstant.TRUE : x),
      formulas[0]);
  }

  @SuppressWarnings("ReuseOfLocalVariable")
  @Test
  public void testTemporalStep() throws ParseException {
    BitSet stepSet = new BitSet();
    LtlParser parser = new LtlParser();
    Formula formula = parser.parseLtl("a & X (! a)");
    EquivalenceClassFactory factory = setUpFactory(formula);
    assertEquals(factory.createEquivalenceClass(parser.parseLtl("! a")),
      factory.createEquivalenceClass(parser.parseLtl("X ! a")).temporalStep(stepSet));
    assertEquals(factory.createEquivalenceClass(parser.parseLtl("a")),
      factory.createEquivalenceClass(parser.parseLtl("X a")).temporalStep(stepSet));

    formula = parser.parseLtl("(! a) & X (a)");
    factory = setUpFactory(formula);
    assertEquals(factory.createEquivalenceClass(parser.parseLtl("! a")),
      factory.createEquivalenceClass(parser.parseLtl("X ! a")).temporalStep(stepSet));
    assertEquals(factory.createEquivalenceClass(parser.parseLtl("a")),
      factory.createEquivalenceClass(parser.parseLtl("X a")).temporalStep(stepSet));

    formula = parser.parseLtl("(a) & X (a)");
    factory = setUpFactory(formula);
    assertEquals(factory.createEquivalenceClass(parser.parseLtl("! a")),
      factory.createEquivalenceClass(parser.parseLtl("X ! a")).temporalStep(stepSet));
    assertEquals(factory.createEquivalenceClass(parser.parseLtl("a")),
      factory.createEquivalenceClass(parser.parseLtl("X a")).temporalStep(stepSet));

    formula = parser.parseLtl("(! a) & X (! a)");
    factory = setUpFactory(formula);
    assertEquals(factory.createEquivalenceClass(parser.parseLtl("! a")),
      factory.createEquivalenceClass(parser.parseLtl("X ! a")).temporalStep(stepSet));
    assertEquals(factory.createEquivalenceClass(parser.parseLtl("a")),
      factory.createEquivalenceClass(parser.parseLtl("X a")).temporalStep(stepSet));
  }

  @Test
  public void testUnfoldUnfold() {
    for (Formula formula : formulae) {
      EquivalenceClassFactory factory = setUpFactory(formula);
      EquivalenceClass ref = factory.createEquivalenceClass(formula.unfold());
      EquivalenceClass clazz = factory.createEquivalenceClass(formula).unfold();
      assertEquals(ref, clazz);
      assertEquals(clazz, clazz.unfold());
    }
  }
}
