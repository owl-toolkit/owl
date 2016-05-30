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

package ltl;

import ltl.simplifier.Simplifier;

/**
 * visit(a,b) returns true if a=>b, and if we don't know it or a doesn't imply b
 * then false. it is highly recommended to have the subformulae aggressively
 * simplified before the Visitor is applied.
 */
public class ImplicationVisitor implements BinaryVisitor<Boolean, Formula> {

    private static final ImplicationVisitor instance = new ImplicationVisitor();

    private ImplicationVisitor() {
    }

    public static ImplicationVisitor getVisitor() {
        return instance;
    }

    @Override
    public Boolean visit(BooleanConstant b, Formula fo) {
        if (b.value) {
            return b.equals(fo);
        } else {
            return true;
        }
    }

    @Override
    public Boolean visit(Conjunction c, Formula fo) {
        if (c.equals(fo) || fo.equals(BooleanConstant.TRUE)) {
            return true;
        }
        if (fo instanceof Conjunction) {
            boolean imp = true;
            for (Formula fochild : ((PropositionalFormula) fo).children) {
                imp = imp && c.children.stream().anyMatch(ch -> ch.accept(this, fochild));
            }
            return imp;
        } else if (fo instanceof UOperator) {
            if (c.accept(this, ((UOperator) fo).right)) {
                return true;
            }
        } else if (fo instanceof Disjunction) {
            if (((Disjunction) fo).children.stream().anyMatch(ch -> c.accept(this, ch)))
                return true;
        }

        return c.children.stream().anyMatch(ch -> ch.accept(this, fo));
    }

    @Override
    public Boolean visit(Disjunction d, Formula fo) {
        if (d.equals(fo) || fo.equals(BooleanConstant.TRUE)) {
            return true;
        } else if (fo instanceof UOperator) {
            if (d.accept(this, ((UOperator) fo).right)) {
                return true;
            }
        } else if (fo instanceof Conjunction) {
            if (((Conjunction) fo).allMatch(ch -> d.accept(this, ch)))
                return true;
        } else if (fo instanceof Disjunction) {
            if (((Disjunction) fo).anyMatch(ch -> d.accept(this, ch)))
                return true;
        }
        return d.allMatch(c -> c.accept(this, fo));
    }

    @Override
    public Boolean visit(FOperator f, Formula fo) {
        if (f.equals(fo) || fo.equals(BooleanConstant.TRUE)) {
            return true;
        }
        if (fo instanceof FOperator) {
            return f.operand.accept(this, ((ModalOperator) fo).operand);
        } else if (fo instanceof UOperator) {
            if (f.accept(this, ((UOperator) fo).right)) {
                return true;
            }
        } else if (fo instanceof Conjunction) {
            return ((Conjunction) fo).children.stream().allMatch(ch -> f.accept(this, ch));
        } else if (fo instanceof Disjunction) {
            return ((Disjunction) fo).children.stream().anyMatch(ch -> f.accept(this, ch));
        }
        return false;
    }

    @Override
    public Boolean visit(GOperator g, Formula fo) {
        if (g.equals(fo) || fo.equals(BooleanConstant.TRUE)) {
            return true;
        }
        if (g.operand.accept(this, fo)) {
            return true;
        } else if (fo instanceof Conjunction) {
            return ((Conjunction) fo).children.stream().allMatch(ch -> g.accept(this, ch));
        } else if (fo instanceof Disjunction) {
            return ((Disjunction) fo).children.stream().anyMatch(ch -> g.accept(this, ch));
        } else if (fo instanceof FOperator || fo instanceof GOperator) {
            return g.operand.accept(this, ((ModalOperator) fo).operand) || g.accept(this, ((ModalOperator) fo).operand);
        } else if (fo instanceof Literal) {
            return g.operand.accept(this, fo);
        } else if (fo instanceof UOperator) {
            return g.accept(this, ((UOperator) fo).right) || g.accept(this, new Conjunction(new GOperator(((UOperator) fo).left), new FOperator(((UOperator) fo).right)));
        } else if (fo instanceof XOperator) {
            return g.accept(this, ((ModalOperator) fo).operand) || g.operand.accept(this, fo);
        }
        return false;
    }

    @Override
    public Boolean visit(Literal l, Formula fo) {
        if (l.equals(fo) || fo.equals(BooleanConstant.TRUE)) {
            return true;
        }
        if (fo instanceof Conjunction) {
            return ((Conjunction) fo).children.stream().allMatch(ch -> l.accept(this, ch));
        } else if (fo instanceof Disjunction) {
            return ((Disjunction) fo).children.stream().anyMatch(ch -> l.accept(this, ch));
        } else if (fo instanceof FOperator) {
            return l.accept(this, ((ModalOperator) fo).operand);
        }
        return false;
    }

    @Override
    public Boolean visit(UOperator u, Formula fo) {
        if (u.equals(fo) || fo.equals(BooleanConstant.TRUE)) {
            return true;
        }
        if (fo instanceof UOperator) {
            return u.left.accept(this, ((UOperator) fo).left) && u.right.accept(this, ((UOperator) fo).right)
                    || u.accept(this, ((UOperator) fo).right);
        } else if (fo instanceof FOperator) {
            return u.right.accept(this, ((ModalOperator) fo).operand);
        } else if (fo instanceof Conjunction) {
            if (((Conjunction) fo).children.stream().allMatch(ch -> u.accept(this, ch)))
                return true;
        } else if (fo instanceof Disjunction) {
            if (((Disjunction) fo).children.stream().anyMatch(ch -> u.accept(this, ch)))
                return true;
        }
        return Simplifier.simplify(new Disjunction(u.left, u.right), Simplifier.Strategy.AGGRESSIVELY).accept(this, fo);
    }

    @Override
    public Boolean visit(XOperator x, Formula fo) {
        if (x.equals(fo) || fo.equals(BooleanConstant.TRUE)) {
            return true;
        } else if (fo instanceof Conjunction) {
            return ((Conjunction) fo).children.stream().allMatch(ch -> x.accept(this, ch));
        } else if (fo instanceof Disjunction) {
            return ((Disjunction) fo).children.stream().anyMatch(ch -> x.accept(this, ch));
        } else if (fo instanceof FOperator) {
            return x.operand.accept(this, fo) || x.accept(this, ((ModalOperator) fo).operand)
                    || x.operand.accept(this, ((ModalOperator) fo).operand);
        } else if (fo instanceof GOperator || fo instanceof Literal || fo instanceof UOperator) {
            return false;
        } else if (fo instanceof XOperator) {
            return x.operand.accept(this, ((ModalOperator) fo).operand);
        }
        return false;

    }

}
