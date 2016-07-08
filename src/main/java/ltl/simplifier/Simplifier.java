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
import ltl.visitors.Visitor;

import java.util.*;
import java.util.stream.Collectors;

public final class Simplifier {

    public static final int ITERATIONS = 3;

    private static final Visitor<Formula> MODAL_SIMPLIFIER = new ModalSimplifier();
    private static final Visitor<XFormula> PULLUP_X = new PullupXVisitor();
    private static final Visitor<Formula> AGGRESSIVE_SIMPLIFIER = new AggressiveSimplifier();

    private Simplifier() {
    }

    public static Formula simplify(Formula formula, Strategy strategy) {
        switch (strategy) {
            case PULLUP_X:
                return formula.accept(PULLUP_X).toFormula();

            case MODAL_EXT:
                Formula step0 = formula;

                for (int i = 0; i < ITERATIONS; i++) {
                    Formula step2 = step0.accept(PULLUP_X).toFormula();
                    Formula step3 = step2.accept(MODAL_SIMPLIFIER);

                    if (step0.equals(step3)) {
                        return step0;
                    }

                    step0 = step3;
                }

                return step0;

            case MODAL:
                return formula.accept(Simplifier.MODAL_SIMPLIFIER);

            case AGGRESSIVELY:
                return formula.accept(Simplifier.AGGRESSIVE_SIMPLIFIER);

            case NONE:
                return formula;

            default:
                throw new AssertionError();
        }
    }

    public enum Strategy {
        NONE, MODAL, PULLUP_X, MODAL_EXT, AGGRESSIVELY
    }

    /* Pushes down F,G in the syntax tree */
    static class ModalSimplifier implements Visitor<Formula> {

        @Override
        public Formula visit(FOperator fOperator) {
            if (fOperator.operand instanceof Disjunction) {
                Disjunction disjunction = (Disjunction) fOperator.operand;
                return Disjunction.create(disjunction.children.stream().map(e -> new FOperator(e).accept(this)));
            }

            Formula operand = fOperator.operand.accept(this);

            if (operand instanceof UOperator) {
                return new FOperator(((UOperator) operand).right);
            }

            if (operand.isPureEventual() || operand.isSuspendable()) {
                return operand;
            }

            // Only call constructor, when necessary.
            if (operand == fOperator.operand) {
                return fOperator;
            }

            return new FOperator(operand);
        }

        @Override
        public Formula visit(GOperator gOperator) {
            if (gOperator.operand instanceof Conjunction) {
                Conjunction conjunction = (Conjunction) gOperator.operand;
                return Conjunction.create(conjunction.children.stream().map(e -> new GOperator(e).accept(this)));
            }

            Formula operand = gOperator.operand.accept(this);

            if (operand.isPureUniversal() || operand.isSuspendable()) {
                return operand;
            }

            // Only call constructor, when necessary.
            if (operand == gOperator.operand) {
                return gOperator;
            }

            return new GOperator(operand);
        }

        @Override
        public Formula visit(UOperator uOperator) {
            Formula left = uOperator.left.accept(this);
            Formula right = uOperator.right.accept(this);

            if (right.isSuspendable() || right.isPureEventual()) {
                return right;
            }

            if (left == BooleanConstant.TRUE) {
                return new FOperator(right);
            }

            if (left == BooleanConstant.FALSE) {
                return right;
            }

            if (left.isSuspendable() || left.isPureUniversal()) {
                return Disjunction.create(Conjunction.create(left, new FOperator(right)), right);
            }

            if (left.isPureEventual()) {
                return Disjunction.create(new FOperator(Conjunction.create(left, new XOperator(right))), right);
            }

            // Only call constructor, when necessary.
            if (left == uOperator.left && right == uOperator.right) {
                return uOperator;
            }

            return new UOperator(left, right);
        }

        @Override
        public Formula visit(XOperator xOperator) {
            Formula operand = xOperator.operand.accept(this);

            if (operand.isSuspendable()) {
                return operand;
            }

            // Only call constructor, when necessary.
            if (operand == xOperator.operand) {
                return xOperator;
            }

            return new XOperator(operand);
        }

        @Override
        public Formula visit(Conjunction conjunction) {
            boolean newElement = false;
            List<Formula> newChildren = new ArrayList<>(conjunction.children.size());

            for (Formula child : conjunction.children) {
                Formula newChild = child.accept(this);

                if (newChild == BooleanConstant.FALSE) {
                    return BooleanConstant.FALSE;
                }

                newElement |= (child != newChild);
                newChildren.add(newChild);
            }

            // Only call constructor, when necessary.
            Formula c = newElement ? Conjunction.create(newChildren.stream()) : conjunction;

            if (c instanceof Conjunction) {
                Conjunction c2 = (Conjunction) c;

                if (c2.children.stream().anyMatch(e -> c2.children.contains(e.not()))) {
                    return BooleanConstant.FALSE;
                }
            }

            return c;
        }

        @Override
        public Formula visit(Disjunction disjunction) {
            boolean newElement = false;
            List<Formula> newChildren = new ArrayList<>(disjunction.children.size());

            for (Formula child : disjunction.children) {
                Formula newChild = child.accept(this);

                if (newChild == BooleanConstant.TRUE) {
                    return BooleanConstant.TRUE;
                }

                newElement |= (child != newChild);
                newChildren.add(newChild);
            }

            // Only call constructor, when necessary.
            Formula d = newElement ? Disjunction.create(newChildren.stream()) : disjunction;

            if (d instanceof Disjunction) {
                Disjunction d2 = (Disjunction) d;

                if (d2.children.stream().anyMatch(e -> d2.children.contains(e.not()))) {
                    return BooleanConstant.TRUE;
                }
            }

            return d;
        }

        @Override
        public Formula defaultAction(Formula formula) {
            return formula;
        }
    }

    static class XFormula {
        int depth;
        Formula formula;

        XFormula(int depth, Formula formula) {
            this.depth = depth;
            this.formula = formula;
        }

        Formula toFormula(int newDepth) {
            int i = depth - newDepth;

            for (; i > 0; i--) {
                formula = new XOperator(formula);
            }

            return formula;
        }

        Formula toFormula() {
            return toFormula(0);
        }
    }

    static class PullupXVisitor implements Visitor<XFormula> {
        @Override
        public XFormula defaultAction(Formula formula) {
            return new XFormula(0, formula);
        }

        @Override
        public XFormula visit(Conjunction conjunction) {
            Collection<XFormula> children = conjunction.children.stream().map(c -> c.accept(this)).collect(Collectors.toList());
            int depth = children.stream().mapToInt(c -> c.depth).min().orElse(0);
            return new XFormula(depth, new Conjunction(children.stream().map(c -> c.toFormula(depth))));
        }

        @Override
        public XFormula visit(Disjunction disjunction) {
            Collection<XFormula> children = disjunction.children.stream().map(c -> c.accept(this)).collect(Collectors.toList());
            int depth = children.stream().mapToInt(c -> c.depth).min().orElse(0);
            return new XFormula(depth, new Disjunction(children.stream().map(c -> c.toFormula(depth))));
        }

        @Override
        public XFormula visit(FOperator fOperator) {
            XFormula r = fOperator.operand.accept(this);
            r.formula = new FOperator(r.formula);
            return r;
        }

        @Override
        public XFormula visit(GOperator gOperator) {
            XFormula r = gOperator.operand.accept(this);
            r.formula = new GOperator(r.formula);
            return r;
        }

        @Override
        public XFormula visit(UOperator uOperator) {
            XFormula r = uOperator.right.accept(this);
            XFormula l = uOperator.left.accept(this);
            l.formula = new UOperator(l.toFormula(r.depth), r.toFormula(l.depth));
            l.depth = Math.min(l.depth, r.depth);
            return l;
        }

        @Override
        public XFormula visit(XOperator xOperator) {
            XFormula r = xOperator.operand.accept(this);
            r.depth++;
            return r;
        }
    }

    public static class AggressiveSimplifier extends ModalSimplifier {

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
            for (; innerDisjunctionLoop(set);)
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
                    return new XOperator(new FOperator(((ModalOperator) child).operand)).accept(this);
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
                    return new XOperator(new UOperator(((ModalOperator) l).operand, ((ModalOperator) r).operand));
                }

                if (l instanceof Conjunction) {
                    return Simplifier.simplify(new Conjunction(((Conjunction) l).children.stream().map(left -> new UOperator(left, r)).collect(Collectors.toSet())),
                            Strategy.AGGRESSIVELY);
                }

                if (r instanceof Disjunction) {
                    return Simplifier.simplify(new Disjunction(((Disjunction) r).children.stream().map(right -> new UOperator(l, right)).collect(Collectors.toSet())),
                            Strategy.AGGRESSIVELY);
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

                        if (form.accept(imp, form2)) {
                            if (set.remove(form2))
                                return true;
                        }

                        if (form.accept(imp, form2.not())) {
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

                        if (form.accept(imp, form2)) {
                            if (set.remove(form))
                                return true;
                        }

                        if (form.not().accept(imp, form2)) {
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
}
