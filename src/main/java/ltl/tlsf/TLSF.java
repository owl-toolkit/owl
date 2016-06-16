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

package ltl.tlsf;

import com.google.common.base.Converter;
import com.google.common.collect.BiMap;
import ltl.*;
import org.immutables.value.Value;
import sun.text.resources.cldr.mua.FormatData_mua;

import java.util.BitSet;

@Value.Immutable
public abstract class TLSF {

    public enum Semantics {
        MEALY, MEALY_STRICT, MOORE, MOORE_STRICT;

        public boolean isStrict() {
            switch (this) {
                case MEALY_STRICT:
                case MOORE_STRICT:
                    return true;

                default:
                    return false;
            }
        }

        public boolean isMealy() {
            switch (this) {
                case MEALY:
                case MEALY_STRICT:
                    return true;

                default:
                    return false;
            }
        }

        public boolean isMoore() {
            return !isMealy();
        }
    }

    // Info Header
    public abstract String title();
    public abstract String description();
    public abstract Semantics semantics();
    public abstract Semantics target();

    // Main / Body
    public abstract BitSet inputs();
    public abstract BitSet outputs();
    public abstract BiMap<String, Integer> mapping();

    @Value.Default
    public Formula initially() {
        return BooleanConstant.TRUE;
    }

    @Value.Default
    public Formula preset() {
        return BooleanConstant.TRUE;
    }

    @Value.Default
    public Formula require() {
        return BooleanConstant.TRUE;
    }

    @Value.Default
    public Formula assert_() {
        return BooleanConstant.TRUE;
    }

    @Value.Default
    public Formula assume() {
        return BooleanConstant.TRUE;
    }

    @Value.Default
    public Formula guarantee() {
        return BooleanConstant.TRUE;
    }

    @Value.Derived
    public Formula toFormula() {
        Formula formula = Disjunction.create(FOperator.create(require().not()), assume().not(), Conjunction.create(GOperator.create(assert_()), guarantee()));

        if (!semantics().isStrict()) {
            formula = Conjunction.create(preset(), formula);
        } else {
            formula = Conjunction.create(preset(), formula, Disjunction.create(UOperator.create(assert_(), require().not()), GOperator.create(assert_())));
        }

        return convert(Disjunction.create(initially().not(), formula));
    }

    private Formula convert(Formula formula) {
        if (semantics().isMealy() && target().isMoore()) {
            return formula.accept(new MealyToMooreConverter());
        }

        if (semantics().isMoore() && target().isMealy()) {
            return formula.accept(new MooreToMealyConverter());
        }

        return formula;
    }

    private class MealyToMooreConverter extends DefaultConverter {
        @Override
        public Formula visit(Literal literal) {
            if (outputs().get(literal.getAtom())) {
                return new XOperator(literal);
            }

            return literal;
        }
    }

    private class MooreToMealyConverter extends DefaultConverter {
        @Override
        public Formula visit(Literal literal) {
            if (inputs().get(literal.getAtom())) {
                return new XOperator(literal);
            }

            return literal;
        }
    }
}
