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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;

import java.util.*;
import java.util.stream.Collectors;
import ltl.BooleanConstant;
import ltl.Conjunction;
import ltl.Disjunction;
import ltl.Formula;
import ltl.Literal;
import ltl.parser.Parser;
import ltl.simplifier.Simplifier;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public abstract class EquivalenceClassTest {
    private EquivalenceClassFactory factory;
    private Formula contradiction;
    private Formula tautology;
    private Formula literal;

    public abstract EquivalenceClassFactory setUpFactory(Formula domain);

    @Before
    public void setUp() {
        contradiction = BooleanConstant.FALSE;
        tautology = BooleanConstant.TRUE;
        literal = new Literal(0);

        factory = setUpFactory(new Conjunction(contradiction, tautology, literal));
    }

    @Test
    public void testGetRepresentative() throws Exception {
        Assert.assertEquals(contradiction, factory.createEquivalenceClass(contradiction).getRepresentative());
    }

    @Test
    public void testImplies() throws Exception {
        EquivalenceClass c = factory.createEquivalenceClass(contradiction);
        EquivalenceClass t = factory.createEquivalenceClass(tautology);
        EquivalenceClass l = factory.createEquivalenceClass(literal);

        assertTrue(c.implies(c));

        assertTrue(c.implies(t));
        assertTrue(c.implies(l));

        assertTrue(l.implies(t));
        assertTrue(!l.implies(c));

        assertTrue(!t.implies(c));
        assertTrue(!t.implies(l));
    }

    @Test
    public void testEquivalent() throws Exception {
        EquivalenceClass c = factory.createEquivalenceClass(contradiction);

        assertTrue(c.equals(c));
        assertTrue(c.equals(factory.createEquivalenceClass(Simplifier.simplify(new Conjunction(literal, new Literal(0, true)), Simplifier.Strategy.MODAL_EXT))));
    }

    @Test
    public void testEqualsAndHashCode() throws Exception {
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
    public void testEmptyDomain() {
        EquivalenceClassFactory factory = setUpFactory(BooleanConstant.TRUE);
        assertNotEquals(factory, null);
    }

    private static final List<String> formulaeStrings = ImmutableList.of("G a", "F G a", "G a | G b", "(G a) U (G b)", "X G b", "F F ((G a) & b)", "a & G b");
    private static final List<Formula> formulae = ImmutableList.copyOf(formulaeStrings.stream().map(Parser::formula).collect(Collectors.toList()));

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

    @Test
    public void testFrequencyGNotFalse() {
        Formula f = Parser.formula("G { >= 0.4} a");
        EquivalenceClassFactory factory = setUpFactory(f);
        EquivalenceClass clazz = factory.createEquivalenceClass(f);
        assertNotEquals(factory.getFalse(), clazz.unfold().temporalStep(new BitSet(0)));
    }

    @Test
    public void testGetAtoms() {
        Formula f = Parser.formula("a & (a | b) & (F c)");
        EquivalenceClassFactory factory = setUpFactory(f);
        EquivalenceClass clazz = factory.createEquivalenceClass(f);
        BitSet atoms = new BitSet();
        atoms.set(0);
        assertEquals(atoms, clazz.getAtoms());
        atoms.set(2);
        assertEquals(atoms, clazz.unfold().getAtoms());
    }

    @Test
    public void testGetAtoms2() {
        Formula f = Parser.formula("(a | (b & X a) | (F a)) & (c | (b & X a) | (F a))");
        EquivalenceClassFactory factory = setUpFactory(f);
        EquivalenceClass clazz = factory.createEquivalenceClass(f);
        BitSet atoms = new BitSet();
        atoms.set(0, 3);
        assertEquals(atoms, clazz.getAtoms());
    }


    @Test
    public void testGetAtomsEmpty() {
        Formula f = Parser.formula("G a");
        EquivalenceClassFactory factory = setUpFactory(f);
        EquivalenceClass clazz = factory.createEquivalenceClass(f);
        BitSet atoms = new BitSet();
        assertEquals(atoms, clazz.getAtoms());
        atoms.set(0);
        assertEquals(atoms, clazz.unfold().getAtoms());
    }

    @Test
    public void testTemporalStep() {
        Formula formula = Parser.formula("a & X (! a)");
        EquivalenceClassFactory factory = setUpFactory(formula);
        assertEquals(factory.createEquivalenceClass(Parser.formula("! a")), factory.createEquivalenceClass(Parser.formula("X ! a")).temporalStep(new BitSet()));
        assertEquals(factory.createEquivalenceClass(Parser.formula("a")), factory.createEquivalenceClass(Parser.formula("X a")).temporalStep(new BitSet()));

        formula = Parser.formula("(! a) & X (a)");
        factory = setUpFactory(formula);
        assertEquals(factory.createEquivalenceClass(Parser.formula("! a")), factory.createEquivalenceClass(Parser.formula("X ! a")).temporalStep(new BitSet()));
        assertEquals(factory.createEquivalenceClass(Parser.formula("a")), factory.createEquivalenceClass(Parser.formula("X a")).temporalStep(new BitSet()));

        formula = Parser.formula("(a) & X (a)");
        factory = setUpFactory(formula);
        assertEquals(factory.createEquivalenceClass(Parser.formula("! a")), factory.createEquivalenceClass(Parser.formula("X ! a")).temporalStep(new BitSet()));
        assertEquals(factory.createEquivalenceClass(Parser.formula("a")), factory.createEquivalenceClass(Parser.formula("X a")).temporalStep(new BitSet()));

        formula = Parser.formula("(! a) & X (! a)");
        factory = setUpFactory(formula);
        assertEquals(factory.createEquivalenceClass(Parser.formula("! a")), factory.createEquivalenceClass(Parser.formula("X ! a")).temporalStep(new BitSet()));
        assertEquals(factory.createEquivalenceClass(Parser.formula("a")), factory.createEquivalenceClass(Parser.formula("X a")).temporalStep(new BitSet()));
    }
}
