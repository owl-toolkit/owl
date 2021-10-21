/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import owl.bdd.EquivalenceClassFactory;
import owl.collections.BitSet2;
import owl.ltl.parser.LtlParser;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.rewriter.SimplifierRepository;
import owl.translations.TranslationAutomatonSummaryTest;

abstract class EquivalenceClassTest {

  private static final List<LabelledFormula> formulas = Stream.of(
      "G a", "F G a", "G a | G b", "(G a) U (G b)", "X G b", "F F ((G a) & b)", "a & G b")
    .map(LtlParser::parse)
    .toList();
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
    assertEquals(equivalenceClass, factory.of(SimplifierRepository.SYNTACTIC_FIXPOINT
      .apply(Conjunction.of(Literal.of(0), Literal.of(0, true)))));
  }

  @Test
  void testAtomicPropositions() {
    LabelledFormula formula = LtlParser.parse("a & (a | b) & (F c)");
    EquivalenceClass clazz = obtainFactory(formula).of(formula.formula());

    assertEquals(
      BitSet2.of(0),
      clazz.atomicPropositions(false));
    assertEquals(
      BitSet2.of(0, 2),
      clazz.atomicPropositions(true));
    assertEquals(
      BitSet2.of(0, 2),
      clazz.unfold().atomicPropositions(false));
    assertEquals(
      BitSet2.of(0, 2),
      clazz.unfold().atomicPropositions(true));
  }

  @Test
  void testAtomicPropositionsRegression() {
    LabelledFormula formula = LtlParser.parse("F((a) | ((b) W (!(a))))");
    EquivalenceClass clazz = obtainFactory(formula).of(formula.formula());

    assertEquals(
      BitSet2.of(),
      clazz.atomicPropositions(false));
    assertEquals(
      BitSet2.of(0, 1),
      clazz.atomicPropositions(true));

    assertEquals(
      BitSet2.of(),
      clazz.unfold().atomicPropositions(false));
    assertEquals(
      BitSet2.of(),
      clazz.unfold().atomicPropositions(true));
  }

  @Test
  void testGetAtoms2() {
    LabelledFormula formula = LtlParser.parse("(a | (b & X a) | (F a)) & (c | (b & X a) | (F a))");
    EquivalenceClass clazz = obtainFactory(formula).of(formula.formula());
    assertEquals(BitSet2.of(0, 1, 2), clazz.atomicPropositions(false));
  }

  @Test
  void testAtomicPropositionsEmpty() {
    LabelledFormula formula = LtlParser.parse("G a");
    EquivalenceClass clazz = obtainFactory(formula).of(formula.formula());
    assertEquals(BitSet2.of(), clazz.atomicPropositions(false));
    assertEquals(BitSet2.of(0), clazz.unfold().atomicPropositions(false));
  }

  @Test
  void testDisjunctiveNormalForm() {
    var formula = LtlParser.parse("(Fa & Gb) | (Fc & Ga)");
    var clazz = factory.of(formula.formula());
    assertEquals(
      Set.of(
        Set.of(
          LtlParser.parse("F a", formula.atomicPropositions()).formula(),
          LtlParser.parse("G b", formula.atomicPropositions()).formula()
        ),
        Set.of(
          LtlParser.parse("G a", formula.atomicPropositions()).formula(),
          LtlParser.parse("F c", formula.atomicPropositions()).formula()
        )),
      clazz.disjunctiveNormalForm());
  }

  @Test
  void testDisjunctiveNormalForm2() {
    var formula = LtlParser.parse("(a & !b) | (c & !d)");
    var clazz = factory.of(formula.formula());
    assertEquals(
      Set.of(
        Set.of(
          LtlParser.parse("a", formula.atomicPropositions()).formula(),
          LtlParser.parse("!b", formula.atomicPropositions()).formula()
        ),
        Set.of(
          LtlParser.parse("c", formula.atomicPropositions()).formula(),
          LtlParser.parse("!d", formula.atomicPropositions()).formula()
        )),
      clazz.disjunctiveNormalForm());
  }

  @Test
  void testConjunctiveNormalForm() {
    var formula = LtlParser.parse("(Fa | Gb) & (Fc | Ga)");
    var clazz = factory.of(formula.formula());
    assertEquals(
      Set.of(
        Set.of(
          LtlParser.parse("F a", formula.atomicPropositions()).formula(),
          LtlParser.parse("G b", formula.atomicPropositions()).formula()
        ),
        Set.of(
          LtlParser.parse("G a", formula.atomicPropositions()).formula(),
          LtlParser.parse("F c", formula.atomicPropositions()).formula()
        )),
      clazz.conjunctiveNormalForm());
  }

  @Test
  void testConjunctiveNormalForm2() {
    var formula = LtlParser.parse("(a | !b) & (c | !d)");
    var clazz = factory.of(formula.formula());
    assertEquals(
      Set.of(
        Set.of(
          LtlParser.parse("a", formula.atomicPropositions()).formula(),
          LtlParser.parse("!b", formula.atomicPropositions()).formula()
        ),
        Set.of(
          LtlParser.parse("c", formula.atomicPropositions()).formula(),
          LtlParser.parse("!d", formula.atomicPropositions()).formula()
        )),
      clazz.conjunctiveNormalForm());
  }

  @Test
  void testCanonicalRepresentativeFormulaDatabase() throws IOException {
    Set<LabelledFormula> formulas = new HashSet<>();

    for (var x : TranslationAutomatonSummaryTest.FormulaSet.values()) {
      formulas.addAll(x.loadAndDeduplicateFormulaSet());
    }

    for (var formula : formulas) {
      var factory = obtainFactory(formula);
      var clazz = factory.of(formula.formula());
      var canonicalRepresentative = clazz.canonicalRepresentativeDnf();

      assertEquals(clazz.disjunctiveNormalForm(),
        NormalForms.toDnf(canonicalRepresentative));
      assertEquals(clazz,
        factory.of(canonicalRepresentative));
      assertEquals(clazz.unfold(),
        factory.of(canonicalRepresentative.unfold()));
      assertEquals(clazz.temporalStep(new BitSet()),
        factory.of(canonicalRepresentative.temporalStep(new BitSet())));
    }
  }

  @Test
  void testCnfFormulaDatabase() throws IOException {
    Set<LabelledFormula> formulas = new HashSet<>();

    for (var x : TranslationAutomatonSummaryTest.FormulaSet.values()) {
      formulas.addAll(x.loadAndDeduplicateFormulaSet());
    }

    for (var formula : formulas) {
      var factory = obtainFactory(formula);
      var clazz = factory.of(formula.formula());

      var canonicalRepresentative = clazz.canonicalRepresentativeCnf();

      assertEquals(clazz.conjunctiveNormalForm(),
        NormalForms.toCnf(canonicalRepresentative));
      assertEquals(clazz,
        factory.of(canonicalRepresentative));
      assertEquals(clazz.unfold(),
        factory.of(canonicalRepresentative.unfold()));
      assertEquals(clazz.temporalStep(new BitSet()),
        factory.of(canonicalRepresentative.temporalStep(new BitSet())));
    }
  }


  @Test
  void testModalOperators() {
    var formulas = List.of(
      LtlParser.parse("a").formula(),
      LtlParser.parse("F a").formula(),
      LtlParser.parse("G a").formula());

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
      factory.of(LtlParser.parse("a").formula()),
      factory.of(LtlParser.parse("G a").formula()),
      factory.of(LtlParser.parse("G a & a").formula())
    };

    assertEquals(formulas[1].substitute(Formula::unfold), formulas[2]);
    assertEquals(formulas[2].substitute(x -> x instanceof GOperator ? BooleanConstant.TRUE : x),
      formulas[0]);
  }

  @Test
  void testSubstitute2() {
    Formula formula = LtlParser.parse("G (a | X!b) | F c | c").formula();
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
    assertEquals(factory.of(LtlParser.parse("! a").formula()),
      factory.of(LtlParser.parse("X ! a").formula()).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.parse("a").formula()),
      factory.of(LtlParser.parse("X a").formula()).temporalStep(stepSet));

    formula = LtlParser.parse("(! a) & X (a)");
    factory = obtainFactory(formula);
    assertEquals(factory.of(LtlParser.parse("! a").formula()),
      factory.of(LtlParser.parse("X ! a").formula()).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.parse("a").formula()),
      factory.of(LtlParser.parse("X a").formula()).temporalStep(stepSet));

    formula = LtlParser.parse("(a) & X (a)");
    factory = obtainFactory(formula);
    assertEquals(factory.of(LtlParser.parse("! a").formula()),
      factory.of(LtlParser.parse("X ! a").formula()).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.parse("a").formula()),
      factory.of(LtlParser.parse("X a").formula()).temporalStep(stepSet));

    formula = LtlParser.parse("(! a) & X (! a)");
    factory = obtainFactory(formula);
    assertEquals(factory.of(LtlParser.parse("! a").formula()),
      factory.of(LtlParser.parse("X ! a").formula()).temporalStep(stepSet));
    assertEquals(factory.of(LtlParser.parse("a").formula()),
      factory.of(LtlParser.parse("X a").formula()).temporalStep(stepSet));
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

    for (BitSet set : BitSet2.powerSet(4)) {
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
    assertEquals(0.75d, factory.of(LtlParser.parse("a | b").formula()).trueness(), precision);
    assertEquals(0.5d, factory.of(LtlParser.parse("a").formula()).trueness(), precision);
    assertEquals(0.25d, factory.of(LtlParser.parse("a & b").formula()).trueness(), precision);
    assertEquals(0.0d, factory.of(BooleanConstant.FALSE).trueness(), precision);
  }

  @Test
  void testNot() {
    for (LabelledFormula formula : formulas) {
      EquivalenceClassFactory factory = obtainFactory(formula);
      EquivalenceClass expected = factory.of(formula.formula().not());
      EquivalenceClass actual = factory.of(formula.formula()).not();
      EquivalenceClass actualUnfolded = factory.of(formula.formula()).unfold().not();
      assertEquals(expected, actual);
      assertEquals(expected.unfold(), actualUnfolded);
    }
  }
}
