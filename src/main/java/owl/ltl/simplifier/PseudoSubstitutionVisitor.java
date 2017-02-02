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

package owl.ltl.simplifier;

import java.util.HashSet;
import java.util.Set;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Visitor;

/**
 * this method tries to substitute the subformula b in the first argument by the
 * boolean constant specified by c, s.t. the returned formula is made up by
 * assuming the subformula b has the value c.
 */
class PseudoSubstitutionVisitor implements Visitor<Formula> {

  private final BooleanConstant replacement;
  private final Formula toReplace;

  PseudoSubstitutionVisitor(Formula toReplace, BooleanConstant replacement) {
    this.toReplace = toReplace;
    this.replacement = replacement;
  }

  @Override
  public Formula visit(BooleanConstant booleanConstant) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction co) {
    if (toReplace.equals(co)) {
      return replacement;
    }

    Set<Formula> set = new HashSet<>(co.children);
    Set<Formula> toAdd = new HashSet<>();
    Set<Formula> toRemove = new HashSet<>();
    for (Formula form : set) {
      Formula f = form.accept(this);
      if (!f.equals(form)) {
        toAdd.add(f);
        toRemove.add(form);
      }
    }

    set.removeAll(toRemove);
    set.addAll(toAdd);
    if (!set.equals(co.children)) {
      return Simplifier.simplify(new Conjunction(set), Simplifier.Strategy.AGGRESSIVELY);
    }

    return co;
  }

  @Override
  public Formula visit(Disjunction d) {
    if (toReplace.equals(d)) {
      return replacement;
    }

    Set<Formula> set = new HashSet<>(d.children);
    Set<Formula> toAdd = new HashSet<>();
    Set<Formula> toRemove = new HashSet<>();
    for (Formula form : set) {
      Formula f = form.accept(this);
      if (!f.equals(form)) {
        toAdd.add(f);
        toRemove.add(form);
      }
    }
    set.removeAll(toRemove);
    set.addAll(toAdd);

    if (!set.equals(d.children)) {
      return Simplifier.simplify(new Disjunction(set), Simplifier.Strategy.AGGRESSIVELY);
    }

    return d;
  }

  @Override
  public Formula visit(FOperator f) {
    if (toReplace.equals(f) || (replacement.value && f.operand.equals(toReplace))) {
      return replacement;
    }

    return f;
  }

  @Override
  public Formula visit(FrequencyG freq) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Formula visit(GOperator g) {
    if (toReplace.equals(g) || (!replacement.value && g.operand.equals(toReplace))) {
      return replacement;
    }

    return g;
  }

  @Override
  public Formula visit(Literal l) {
    if (toReplace.equals(l)) {
      return replacement;
    }

    return l;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Formula visit(UOperator u) {
    if (u.equals(toReplace) || (u.right.equals(toReplace) && replacement.value)) {
      return replacement;
    }

    return u;
  }

  @Override
  public Formula visit(WOperator wOperator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Formula visit(ROperator r) {
    if (r.equals(toReplace) || (r.left.equals(toReplace) && replacement.value)) {
      return r.right;
    }

    return r;
  }

  @Override
  public Formula visit(XOperator x) {
    if (x.equals(toReplace)) {
      return replacement;
    }

    if (toReplace instanceof XOperator) {
      PseudoSubstitutionVisitor visitor = new PseudoSubstitutionVisitor(
        ((XOperator) toReplace).operand, replacement);
      return new XOperator(x.operand.accept(visitor));
    }

    return x;
  }
}
