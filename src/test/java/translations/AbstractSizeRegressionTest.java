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

package translations;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import jhoafparser.consumer.HOAConsumerNull;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import ltl.Formula;
import ltl.parser.Parser;
import omega_automaton.output.HOAPrintable;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.Function;

import static org.junit.Assert.assertThat;

public abstract class AbstractSizeRegressionTest<T extends HOAPrintable> {

    private final static BiMap<String, Integer> ALIASES;
    private final static EnumMap<FormulaGroup, String[]> FORMULA_GROUP_MAP;

    static {
        ALIASES = ImmutableBiMap.of("a", 0, "b", 1, "c", 2, "d", 3, "e", 4);
        FORMULA_GROUP_MAP = new EnumMap<>(FormulaGroup.class);

        FORMULA_GROUP_MAP.put(FormulaGroup.VOLATILE, new String[]{
                "F G (a | X b)",
                "F G (a & X b)",
                "F G (a | X b | X X c)",
                "F G (a & X b & X X c)"
        });

        FORMULA_GROUP_MAP.put(FormulaGroup.FG, new String[]{
                "(G F a) -> (G F b)",
                "(G F a) -> ((G F b) & (G F c))",
                "((G F a) & (G F b)) -> (G F c)",
                "((G F a) & (G F b)) -> ((G F c) & (G F d))",
                "((G F a)) -> ((G F a) & (G F b))",
                "!(((G F a)) -> ((G F a) & (G F b)))"
        });

        FORMULA_GROUP_MAP.put(FormulaGroup.FG_UNSTABLE, new String[]{
                "(G F a) -> (G a) & (G F b)",
                "!((G F a) -> (G a) & (G F b))",
                "!((G (F (a))) U b)",
        });

        FORMULA_GROUP_MAP.put(FormulaGroup.ROUND_ROBIN, new String[]{
                "F G a",
                "F G a | F G b",
                "F G a | F G b | F G c",
                "F G a | F G b | F G c | F G d",
                "F G a | F G b | F G c | F G d | F G e"
        });

        FORMULA_GROUP_MAP.put(FormulaGroup.CONJUNCTION, new String[]{
                "G a & G b",
                "(F G a) & (F G b)"
        });

        FORMULA_GROUP_MAP.put(FormulaGroup.DISJUNCTION, new String[]{
                "G a | G b",
                "(F G a) | (F G b)"
        });

        FORMULA_GROUP_MAP.put(FormulaGroup.REACH, new String[]{
                "F (a | b)",
                "X a"
        });

        FORMULA_GROUP_MAP.put(FormulaGroup.IMMEDIATE, new String[]{
                "G a",
                "X X G a"
        });

        FORMULA_GROUP_MAP.put(FormulaGroup.MIXED, new String[]{
                "(F G a) | ((F G b) & (G X (X c U F d)))",
                "(G (F (a))) U b",
                "F((a) & ((a) W ((b) & ((b) W (a)))))",
                "F((a) & ((a) W ((b) & ((c) W (a)))))",
                "G (s | G (p | (s & F t)))",
                "! F((a) & ((a) W ((b) & ((c) W (a)))))",
                "F (a R X b)",
                "G (a U X b)",
                "G((F((a) & (X(X(b))))) & (F((b) & (X(c)))))",
                "(F (((F X x) | (F a)) & (G (a | b)))) | (F (((F X y) | (F c)) & (G (c | d))))"
        });

        FORMULA_GROUP_MAP.put(FormulaGroup.ORDERINGS, new String[]{
                "F (G (a1 | (b1 & X F b1))) | F (G (a2 | (b2 & X F b2))) | F (G (a3 | (b3 & X F b3)))",
                "F (G (a1 | (b1 & X F b1))) & F (G (a2 | (b2 & X F b2))) & F (G (a3 | (b3 & X F b3)))"
        });
    }

    protected enum FormulaGroup {VOLATILE, FG, FG_UNSTABLE, ROUND_ROBIN, DISJUNCTION, CONJUNCTION, REACH, IMMEDIATE, MIXED, ORDERINGS}

    @Parameterized.Parameters(name = "Group: {0}")
    public static Iterable<?> data() {
        return EnumSet.allOf(FormulaGroup.class);
    }

    private final Function<Formula, T> translator;
    private final FormulaGroup selectedClass;

    protected AbstractSizeRegressionTest(FormulaGroup selectedClass, Function<Formula, T> translator) {
        this.selectedClass = selectedClass;
        this.translator = translator;
    }

    @Test
    public void testSize() {
        String[] formulas = FORMULA_GROUP_MAP.get(selectedClass);
        int[] size = getExpectedSize(selectedClass);

        for (int i = 0; i < formulas.length; i++) {
            T automaton = translator.apply(Parser.formula(formulas[i]));
            assertThat("States for " + formulas[i] + " (index " + i + ')', getSize(automaton), Matchers.lessThanOrEqualTo(size[i]));
        }
    }

    @Test
    public void testAcceptanceSize() {
        String[] formulas = FORMULA_GROUP_MAP.get(selectedClass);
        int[] accSize = getExpectedAccSize(selectedClass);

        for (int i = 0; i < formulas.length; i++) {
            T automaton = translator.apply(Parser.formula(formulas[i]));
            assertThat("Acceptance Sets for " + formulas[i] + " (index " + i + ')', getAccSize(automaton), Matchers.lessThanOrEqualTo(accSize[i]));
        }
    }

    @Test
    public void testHOAOutput() {
        String[] formulas = FORMULA_GROUP_MAP.get(selectedClass);

        for (String formula : formulas) {
            translator.apply(Parser.formula(formula)).toHOA(new HOAIntermediateCheckValidity(new HOAConsumerNull()));
        }
    }

    abstract protected int getSize(T automaton);

    abstract protected int getAccSize(T automaton);

    abstract protected int[] getExpectedSize(FormulaGroup t);

    abstract protected int[] getExpectedAccSize(FormulaGroup t);
}
