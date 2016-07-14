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

package ltl.simplifier;

import ltl.*;
import ltl.visitors.RestrictToFGXU;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

class AggressiveSimplifier extends ModalSimplifier {

    @Override
    public Formula visit(Conjunction c) {
        Formula con = super.visit(c);

        if (!(con instanceof Conjunction)) {
            return con;
        }

        c = (Conjunction) con;
        Set<Formula> set = new HashSet<>(c.children);

        // remove ltl that are implied by other Formulas
        // or do a PseudoSubstitution by a fix-point-iteration
        for (; innerConjunctionLoop(set); )
            ;

        if (set.size() == 1) {
            return set.iterator().next();
        } else if (set.size() == 0) {
            return BooleanConstant.TRUE;
        }

        return new Conjunction(set);
    }

    @Override
    public Formula visit(Disjunction d) {
        Formula dis = super.visit(d);
        if (!(dis instanceof Disjunction)) {
            return dis;
        }

        d = (Disjunction) dis;
        Set<Formula> set = new HashSet<>(d.children);

        // remove ltl that imply other Formulas
        // or do a PseudoSubstitution by a fix-point-iteration
        for (; innerDisjunctionLoop(set); )
            ;

        if (set.size() == 1) {
            return set.iterator().next();
        } else if (set.size() == 0) {
            return BooleanConstant.FALSE;
        }

        return new Disjunction(set);

    }

    @Override
    public Formula visit(FOperator f) {
        Formula newF = super.visit(f);
        if (newF instanceof FOperator) {
            Formula child = ((FOperator) newF).operand;
            if (child instanceof XOperator) {
                return new XOperator(new FOperator(((UnaryModalOperator) child).operand)).accept(this);
            }

            if (child instanceof Disjunction) {
                return (new Disjunction(((PropositionalFormula) child).children.stream().map(FOperator::new))).accept(this);
            }
        }
        return newF;
    }

    @Override
    public Formula visit(GOperator g) {
        Formula newG = super.visit(g);

        if (newG instanceof GOperator) {
            Formula child = ((GOperator) newG).operand;

            if (child instanceof Conjunction) {
                return (new Conjunction(((PropositionalFormula) child).children.stream().map(GOperator::new))).accept(this);
            }

            if (child instanceof UOperator) {
                Formula l = new GOperator(new Disjunction(((UOperator) child).left, ((UOperator) child).right));
                Formula r = new GOperator(new FOperator(((UOperator) child).right));
                return new Conjunction(l, r).accept(this);
            }
        }

        return newG;
    }

    @Override
    public Formula visit(UOperator u) {
        Formula newU = super.visit(u);

        if (newU instanceof UOperator) {
            Formula l = ((UOperator) newU).left;
            Formula r = ((UOperator) newU).right;
            ImplicationVisitor imp = ImplicationVisitor.getVisitor();
            if (l.accept(imp, r) || r instanceof BooleanConstant) {
                return r;
            }

            if (l instanceof XOperator && r instanceof XOperator) {
                return new XOperator(new UOperator(((UnaryModalOperator) l).operand, ((UnaryModalOperator) r).operand));
            }

            if (l instanceof Conjunction) {
                return Simplifier.simplify(new Conjunction(((Conjunction) l).children.stream().map(left -> new UOperator(left, r)).collect(Collectors.toSet())),
                        Simplifier.Strategy.AGGRESSIVELY);
            }

            if (r instanceof Disjunction) {
                return Simplifier.simplify(new Disjunction(((Disjunction) r).children.stream().map(right -> new UOperator(l, right)).collect(Collectors.toSet())),
                        Simplifier.Strategy.AGGRESSIVELY);
            }
        }

        return newU;
    }

    /**
     * this method helps simplifyAgressively by performing one change of the
     * children set, and returning true, if something has changed
     */
    private boolean innerConjunctionLoop(Set<Formula> set) {
        for (Formula form : set) {
            for (Formula form2 : set) {
                if (!form.equals(form2)) {
                    ImplicationVisitor imp = ImplicationVisitor.getVisitor();

                    if (form.accept(imp, form2) && set.remove(form2)) {
                        return true;
                    }

                    // TODO @ Christopher: Remove this work-around.
                    if (form.accept(imp, form2.not().accept(new RestrictToFGXU()))) {
                        set.clear();
                        set.add(BooleanConstant.FALSE);
                        return true;
                    }

                    PseudoSubstitutionVisitor visitor = new PseudoSubstitutionVisitor(form2, BooleanConstant.TRUE);
                    Formula f = form.accept(visitor);

                    if (!f.equals(form)) {
                        boolean possibleResult = set.remove(form);
                        set.remove(form);
                        f = f.accept(this);
                        possibleResult = set.add(f) || possibleResult;
                        if (possibleResult) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * this method helps simplifyAgressively by performing one change of the
     * children set, and returning true, if something has changed
     */
    private boolean innerDisjunctionLoop(Set<Formula> set) {
        for (Formula form : set) {
            for (Formula form2 : set) {
                if (!form.equals(form2)) {
                    ImplicationVisitor imp = ImplicationVisitor.getVisitor();

                    if (form.accept(imp, form2) && set.remove(form)) {
                        return true;
                    }

                    // TODO @ Christopher: Remove this work-around.
                    if (form.not().accept(new RestrictToFGXU()).accept(imp, form2)) {
                        set.clear();
                        set.add(BooleanConstant.TRUE);
                        return true;
                    }

                    PseudoSubstitutionVisitor visitor = new PseudoSubstitutionVisitor(form2, BooleanConstant.FALSE);
                    Formula f = form.accept(visitor);

                    if (!f.equals(form)) {
                        boolean possibleResult = set.remove(form);
                        f = f.accept(this);
                        possibleResult = set.add(f) || possibleResult;
                        if (possibleResult) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
