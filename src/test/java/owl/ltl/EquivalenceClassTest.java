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

package owl.ltl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static owl.util.Assertions.assertThat;

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.rewriter.SimplifierFactory.Mode;

abstract class EquivalenceClassTest {
  private static final List<String> formulaStrings = List
    .of("G a", "F G a", "G a | G b", "(G a) U (G b)", "X G b", "F F ((G a) & b)", "a & G b");
  private static final List<LabelledFormula> formulas = formulaStrings.stream()
    .map(LtlParser::parse)
    .collect(Collectors.toUnmodifiableList());
  private EquivalenceClassFactory factory;

  @BeforeEach
  void initialiseFactory() {
    factory = obtainFactory(LtlParser.parse("a & b & c & d"));
  }

  protected abstract EquivalenceClassFactory obtainFactory(LabelledFormula domain);

  @Test
  void testEmptyDomain() {
    assertNotNull(obtainFactory(LabelledFormula.of(BooleanConstant.TRUE, List.of())));
  }

  @Test
  void testEqualsAndHashCode() {
    var classes = List.of(
      factory.of(BooleanConstant.FALSE),
      factory.of(BooleanConstant.TRUE),
      factory.of(Literal.of(0)));

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
  void testEquivalent() {
    EquivalenceClass equivalenceClass = factory.of(BooleanConstant.FALSE);

    assertEquals(equivalenceClass, equivalenceClass);
    assertEquals(equivalenceClass, factory.of(SimplifierFactory
      .apply(Conjunction.of(Literal.of(0), Literal.of(0, true)), Mode.SYNTACTIC_FIXPOINT)));
  }

  @Test
  void testAtomicPropositions() {
    LabelledFormula formula = LtlParser.parse("a & (a | b) & (F c)");
    EquivalenceClass clazz = obtainFactory(formula).of(formula.formula());

    assertThat(clazz.atomicPropositions(false),
      x -> x.get(0) && x.length() == 1);
    assertThat(clazz.atomicPropositions(true),
      x -> x.get(0) && !x.get(1) && x.get(2) && x.length() == 3);

    assertThat(clazz.unfold().atomicPropositions(false),
      x -> x.get(0) && !x.get(1) && x.get(2) && x.length() == 3);
    assertThat(clazz.unfold().atomicPropositions(true),
      x -> x.get(0) && !x.get(1) && x.get(2) && x.length() == 3);
  }

  @Test
  void testAtomicPropositionsRegression() {
    LabelledFormula formula = LtlParser.parse("F((a) | ((b) W (!(a))))");
    EquivalenceClass clazz = obtainFactory(formula).of(formula.formula());

    assertThat(clazz.atomicPropositions(false),
      x -> x.length() == 0);
    assertThat(clazz.atomicPropositions(true),
      x -> x.get(0) && x.get(1) && x.length() == 2);

    assertThat(clazz.unfold().atomicPropositions(false),
      x -> x.length() == 0);
    assertThat(clazz.unfold().atomicPropositions(true),
      x -> x.length() == 0);
  }

  @Test
  void testGetAtoms2() {
    LabelledFormula formula = LtlParser.parse("(a | (b & X a) | (F a)) & (c | (b & X a) | (F a))");
    EquivalenceClassFactory factory = obtainFactory(formula);
    EquivalenceClass clazz = factory.of(formula.formula());
    BitSet atoms = new BitSet();
    atoms.set(0, 3);
    assertEquals(atoms, clazz.atomicPropositions());
  }

  @Test
  void testAtomicPropositionsEmpty() {
    LabelledFormula formula = LtlParser.parse("G a");
    EquivalenceClassFactory factory = obtainFactory(formula);
    EquivalenceClass clazz = factory.of(formula.formula());
    BitSet atoms = new BitSet();
    assertEquals(atoms, clazz.atomicPropositions());
    atoms.set(0);
    assertEquals(atoms, clazz.unfold().atomicPropositions());
  }

  @Test
  void testRepresentative() {
    assertEquals(BooleanConstant.FALSE, factory.of(BooleanConstant.FALSE).representative());
  }

  @Test
  void testModalOperators() {
    var formulas = List.of(
      LtlParser.syntax("a"),
      LtlParser.syntax("F a"),
      LtlParser.syntax("G a"));

    EquivalenceClass clazz = factory.of(Conjunction.of(formulas));
    assertEquals(Set.copyOf(formulas.subList(1, 3)), clazz.temporalOperators());
  }

  @Test
  void testImplies() {
    EquivalenceClass contradictionClass = factory.of(BooleanConstant.FALSE);
    EquivalenceClass tautologyClass = factory.of(BooleanConstant.TRUE);
    EquivalenceClass literalClass = factory.of(Literal.of(0));

    assertTrue(contradictionClass.implies(contradictionClass));

    assertTrue(contradictionClass.implies(tautologyClass));
    assertTrue(contradictionClass.implies(literalClass));

    assertTrue(literalClass.implies(tautologyClass));
    assertFalse(literalClass.implies(contradictionClass));

    assertFalse(tautologyClass.implies(contradictionClass));
    assertFalse(tautologyClass.implies(literalClass));
  }

  @Test
  void testSubstitute() {
    EquivalenceClass[] formulas = {
      factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("G a")),
      factory.of(LtlParser.syntax("G a & a"))
    };

    assertEquals(formulas[1].substitute(Formula::unfold), formulas[2]);
    assertEquals(formulas[2].substitute(x -> x instanceof GOperator ? BooleanConstant.TRUE : x),
      formulas[0]);
  }

  @Test
  void testSubstitute2() {
    Formula formula = LtlParser.syntax("G (a | X!b) | F c | c");
    EquivalenceClass clazz = factory.of(formula).unfold();

    Function<Formula, Formula> substitution = x -> {
      assertFalse(x instanceof Literal);

      if (x instanceof FOperator) {
        return BooleanConstant.FALSE;
      }

      return BooleanConstant.TRUE;
    };

    EquivalenceClass core = clazz.substitute(substitution);
    assertThat(core, EquivalenceClass::isTrue);
  }

  @Test
  void testTemporalStep() {
    BitSet stepSet = new BitSet();
    LabelledFormula formula = LtlParser.parse("a & X (! a)");
    EquivalenceClassFactory factory = obtainFactory(formula);
    assertEquals(factory.of(LtlParser.syntax("! a")),
      factory.of(LtlParser.syntax("X ! a")).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("X a")).temporalStep(stepSet));

    formula = LtlParser.parse("(! a) & X (a)");
    factory = obtainFactory(formula);
    assertEquals(factory.of(LtlParser.syntax("! a")),
      factory.of(LtlParser.syntax("X ! a")).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("X a")).temporalStep(stepSet));

    formula = LtlParser.parse("(a) & X (a)");
    factory = obtainFactory(formula);
    assertEquals(factory.of(LtlParser.syntax("! a")),
      factory.of(LtlParser.syntax("X ! a")).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("X a")).temporalStep(stepSet));

    formula = LtlParser.parse("(! a) & X (! a)");
    factory = obtainFactory(formula);
    assertEquals(factory.of(LtlParser.syntax("! a")),
      factory.of(LtlParser.syntax("X ! a")).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.syntax("a")),
      factory.of(LtlParser.syntax("X a")).temporalStep(stepSet));
  }

  @Test
  void testTemporalStepTree() {
    var formula = LtlParser.parse("G (a | b | X c)");
    var factory = obtainFactory(formula);
    var clazz = factory.of(formula.formula());

    var formula1 = formula.formula();
    var tree1 = clazz.temporalStepTree();

    var formula2 = formula1.unfold();
    var tree2 = clazz.unfold().temporalStepTree();

    var formula3 = formula2.temporalStep(new BitSet()).unfold();
    var tree3 = tree2.get(new BitSet()).iterator().next().unfold().temporalStepTree();

    for (BitSet set : BitSets.powerSet(4)) {
      assertEquals(Set.of(factory.of(formula1.temporalStep(set))), tree1.get(set));
      assertEquals(Set.of(factory.of(formula2.temporalStep(set))), tree2.get(set));
      assertEquals(Set.of(factory.of(formula3.temporalStep(set))), tree3.get(set));
    }
  }

  @Test
  void testUnfoldUnfold() {
    for (LabelledFormula formula : formulas) {
      EquivalenceClassFactory factory = obtainFactory(formula);
      EquivalenceClass ref = factory.of(formula.formula().unfold());
      EquivalenceClass clazz = factory.of(formula.formula()).unfold();
      assertEquals(ref, clazz);
      assertEquals(clazz, clazz.unfold());
    }
  }

  @Test
  void testTruthness() {
    double precision = 0.000_000_01d;
    assertEquals(1.0d, factory.of(BooleanConstant.TRUE).trueness(), precision);
    assertEquals(0.75d, factory.of(LtlParser.syntax("a | b")).trueness(), precision);
    assertEquals(0.5d, factory.of(LtlParser.syntax("a")).trueness(), precision);
    assertEquals(0.25d, factory.of(LtlParser.syntax("a & b")).trueness(), precision);
    assertEquals(0.0d, factory.of(BooleanConstant.FALSE).trueness(), precision);
  }
}
