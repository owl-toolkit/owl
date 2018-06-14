/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.ltl;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.rewriter.SimplifierFactory.Mode;

public abstract class EquivalenceClassTest {
  private static final List<String> formulaStrings = List
    .of("G a", "F G a", "G a | G b", "(G a) U (G b)", "X G b", "F F ((G a) & b)", "a & G b");
  private static final List<LabelledFormula> formulas = formulaStrings.stream()
    .map(LtlParser::parse)
    .collect(Collectors.toUnmodifiableList());
  private Formula contradiction;
  private EquivalenceClassFactory factory;
  private Formula literal;
  private Formula tautology;

  @Before
  public void setUp() {
    contradiction = BooleanConstant.FALSE;
    tautology = BooleanConstant.TRUE;
    literal = new Literal(0);

    factory = setUpFactory(LtlParser.parse("a & b & c & d"));
  }

  public abstract EquivalenceClassFactory setUpFactory(LabelledFormula domain);

  @Test
  public void testEmptyDomain() {
    EquivalenceClassFactory factory = setUpFactory(LabelledFormula.of(BooleanConstant.TRUE,
      List.of()));
    assertNotEquals(factory, null);
  }

  @Test
  public void testEqualsAndHashCode() {
    Collection<EquivalenceClass> classes = new ArrayList<>();

    classes.add(factory.of(contradiction));
    classes.add(factory.of(tautology));
    classes.add(factory.of(literal));
    classes.add(factory.of(new Disjunction(tautology, contradiction, literal)));
    classes.add(factory.of(new Conjunction(tautology, contradiction, literal)));

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
    EquivalenceClass equivalenceClass = factory.of(contradiction);

    assertEquals(equivalenceClass, equivalenceClass);
    assertEquals(equivalenceClass, factory.of(SimplifierFactory
      .apply(new Conjunction(literal, new Literal(0, true)), Mode.SYNTACTIC_FIXPOINT)));
  }

  @Test
  public void testFrequencyGNotFalse() {
    LabelledFormula formula = LtlParser.parse("G { >= 0.4} a");
    EquivalenceClassFactory factory = setUpFactory(formula);
    EquivalenceClass clazz = factory.of(formula.formula());
    assertNotEquals(factory.getFalse(), clazz.unfold().temporalStep(new BitSet(0)));
  }

  @Test
  public void testGetAtoms() {
    LabelledFormula formula = LtlParser.parse("a & (a | b) & (F c)");
    EquivalenceClassFactory factory = setUpFactory(formula);
    EquivalenceClass clazz = factory.of(formula.formula());
    BitSet atoms = new BitSet();
    atoms.set(0);
    assertThat(clazz.atomicPropositions(), is(atoms));
    atoms.set(2);
    assertThat(clazz.unfold().atomicPropositions(), is(atoms));
  }

  @Test
  public void testGetAtoms2() {
    LabelledFormula formula = LtlParser.parse("(a | (b & X a) | (F a)) & (c | (b & X a) | (F a))");
    EquivalenceClassFactory factory = setUpFactory(formula);
    EquivalenceClass clazz = factory.of(formula.formula());
    BitSet atoms = new BitSet();
    atoms.set(0, 3);
    assertEquals(atoms, clazz.atomicPropositions());
  }

  @Test
  public void testGetAtomsEmpty() {
    LabelledFormula formula = LtlParser.parse("G a");
    EquivalenceClassFactory factory = setUpFactory(formula);
    EquivalenceClass clazz = factory.of(formula.formula());
    BitSet atoms = new BitSet();
    assertEquals(atoms, clazz.atomicPropositions());
    atoms.set(0);
    assertEquals(atoms, clazz.unfold().atomicPropositions());
  }

  @Test
  public void testRepresentative() {
    assertEquals(contradiction, factory.of(contradiction).representative());
  }

  @Test
  public void testModalOperators() {
    List<Formula> formulas = List.of(
      LtlParser.syntax("a"),
      LtlParser.syntax("F a"),
      LtlParser.syntax("G a"));

    EquivalenceClass clazz = factory.of(Conjunction.of(formulas));
    assertThat(clazz.modalOperators(), is(Set.copyOf(formulas.subList(1, 3))));
  }

  @Test
  public void testImplies() {
    EquivalenceClass contradictionClass = factory.of(contradiction);
    EquivalenceClass tautologyClass = factory.of(tautology);
    EquivalenceClass literalClass = factory.of(literal);

    assertTrue(contradictionClass.implies(contradictionClass));

    assertTrue(contradictionClass.implies(tautologyClass));
    assertTrue(contradictionClass.implies(literalClass));

    assertTrue(literalClass.implies(tautologyClass));
    assertFalse(literalClass.implies(contradictionClass));

    assertFalse(tautologyClass.implies(contradictionClass));
    assertFalse(tautologyClass.implies(literalClass));
  }

  @Test
  public void testSubstitute() {
    EquivalenceClass[] formulas = {
      factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("G a")),
      factory.of(LtlParser.syntax("G a & a"))
    };

    assertEquals(formulas[1].substitute(Formula::unfold), formulas[2]);
    assertEquals(formulas[2].substitute(x -> x instanceof GOperator ? BooleanConstant.TRUE : x),
      formulas[0]);
  }

  @SuppressWarnings("ReuseOfLocalVariable")
  @Test
  public void testTemporalStep() {
    BitSet stepSet = new BitSet();
    LabelledFormula formula = LtlParser.parse("a & X (! a)");
    EquivalenceClassFactory factory = setUpFactory(formula);
    assertEquals(factory.of(LtlParser.syntax("! a")),
      factory.of(LtlParser.syntax("X ! a")).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("X a")).temporalStep(stepSet));

    formula = LtlParser.parse("(! a) & X (a)");
    factory = setUpFactory(formula);
    assertEquals(factory.of(LtlParser.syntax("! a")),
      factory.of(LtlParser.syntax("X ! a")).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("X a")).temporalStep(stepSet));

    formula = LtlParser.parse("(a) & X (a)");
    factory = setUpFactory(formula);
    assertEquals(factory.of(LtlParser.syntax("! a")),
      factory.of(LtlParser.syntax("X ! a")).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("X a")).temporalStep(stepSet));

    formula = LtlParser.parse("(! a) & X (! a)");
    factory = setUpFactory(formula);
    assertEquals(factory.of(LtlParser.syntax("! a")),
      factory.of(LtlParser.syntax("X ! a")).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("X a")).temporalStep(stepSet));
  }

  @Test
  public void testUnfoldUnfold() {
    for (LabelledFormula formula : formulas) {
      EquivalenceClassFactory factory = setUpFactory(formula);
      EquivalenceClass ref = factory.of(formula.formula().unfold());
      EquivalenceClass clazz = factory.of(formula.formula()).unfold();
      assertEquals(ref, clazz);
      assertEquals(clazz, clazz.unfold());
    }
  }

  @Test
  public void testRewrite() {
    Formula formula = LtlParser.syntax("G (a | X!b) | F c | c");
    EquivalenceClass clazz = factory.of(formula).unfold();

    Function<Formula, Formula> substitution = x -> {
      assertThat(x, is(not(instanceOf(Literal.class))));

      if (x instanceof FOperator) {
        return BooleanConstant.FALSE;
      }

      return BooleanConstant.TRUE;
    };

    EquivalenceClass core = clazz.substitute(substitution);
    assertThat(core, is(factory.getTrue()));
  }
}
