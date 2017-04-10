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

package owl.ltl.rewriter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import owl.ltl.BinaryModalOperator;
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

/* Pushes down F,G in the syntax tree
* TODO: Reuse objects! */
@SuppressWarnings("PMD.GodClass")
class ModalSimplifier implements Visitor<Formula>, UnaryOperator<Formula> {

  static final UnaryOperator<Formula> INSTANCE = new ModalSimplifier();

  @Override
  public Formula apply(Formula formula) {
    return formula.accept(this);
  }

  @Override
  public Formula visit(FOperator fOperator) {
    Formula operand = fOperator.operand.accept(this);

    if (operand.isPureEventual() || operand.isSuspendable()) {
      return operand;
    }

    if (operand instanceof MOperator) {
      MOperator mOperator = (MOperator) operand;
      return FOperator.create(Conjunction.create(mOperator.left, mOperator.right));
    }

    if (operand instanceof ROperator) {
      ROperator rOperator = (ROperator) operand;

      return new Disjunction(new FOperator(new Conjunction(rOperator.left, rOperator.right)),
        new FOperator(new GOperator(rOperator.right)));
    }

    if (operand instanceof Conjunction && ((Conjunction) operand)
      .children.stream().allMatch(Formula::isPureUniversal)) {
      return Conjunction.create(((Conjunction) operand).children.stream().map(FOperator::create));
    }

    return FOperator.create(operand);
  }

  @Override
  public Formula visit(FrequencyG freq) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Formula visit(GOperator gOperator) {
    Formula operand = gOperator.operand.accept(this);

    if (operand.isPureUniversal() || operand.isSuspendable()) {
      return operand;
    }

    if (operand instanceof MOperator) {
      MOperator mOperator = (MOperator) operand;

      return Conjunction.create(GOperator.create(mOperator.right),
        GOperator.create(FOperator.create(Conjunction.create(mOperator.left))));
    }

    if (operand instanceof ROperator) {
      ROperator rOperator = (ROperator) operand;
      return GOperator.create(rOperator.right);
    }

    if (operand instanceof UOperator) {
      UOperator uOperator = (UOperator) operand;

      return Conjunction.create(
        GOperator.create(Disjunction.create(uOperator.left, uOperator.right)),
        GOperator.create(FOperator.create(uOperator.right)));
    }

    if (operand instanceof WOperator) {
      WOperator wOperator = (WOperator) operand;
      return GOperator.create(Disjunction.create(wOperator.left, wOperator.right));
    }

    if (operand instanceof Disjunction && ((Disjunction) operand)
      .children.stream().allMatch(Formula::isPureEventual)) {
      return Disjunction.create(((Disjunction) operand).children.stream().map(GOperator::create));
    }

    return GOperator.create(operand);
  }

  @Override
  public Formula visit(Literal literal) {
    return literal;
  }

  @Override
  public Formula visit(MOperator mOperator) {
    Formula left = mOperator.left.accept(this);
    Formula right = mOperator.right.accept(this);

    return MOperator.create(left, right);
  }

  @Override
  public Formula visit(UOperator uOperator) {
    Formula right = uOperator.right.accept(this);

    if (right.isSuspendable() || right.isPureEventual()) {
      return right;
    }

    Formula left = uOperator.left.accept(this);

    if (left.isSuspendable() || left.isPureUniversal()) {
      return Disjunction.create(Conjunction.create(left, FOperator.create(right)), right);
    }

    return UOperator.create(left, right);
  }

  @Override
  public Formula visit(WOperator wOperator) {
    Formula left = wOperator.left.accept(this);
    Formula right = wOperator.right.accept(this);

    if (left.isPureUniversal() || left.isSuspendable()) {
      return Disjunction.create(left, right);
    }

    if (right.isSuspendable() || right.isPureEventual()) {
      return Disjunction.create(GOperator.create(left), right);
    }

    return WOperator.create(left, right);
  }

  @Override
  public Formula visit(ROperator rOperator) {
    Formula right = rOperator.right.accept(this);

    if (right.isSuspendable() || right.isPureUniversal()) {
      return right;
    }

    Formula left = rOperator.left.accept(this);

    if (left.isSuspendable()) {
      return Disjunction.create(Conjunction.create(left, right), GOperator.create(right));
    }

    return ROperator.create(left, right);
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
  public Formula visit(BooleanConstant booleanConstant) {
    return booleanConstant;
  }

  @Override
  public Formula visit(Conjunction conjunction) {
    boolean elementsChanged = false;
    List<Formula> newChildren = new ArrayList<>(conjunction.children.size());

    for (Formula child : conjunction.children) {
      Formula newChild = child.accept(this);

      if (newChild == BooleanConstant.FALSE) {
        return BooleanConstant.FALSE;
      }

      if (newChild instanceof FOperator && conjunction.children
        .contains(((FOperator) newChild).operand)) {
        elementsChanged = true;
        continue;
      }

      if ((newChild instanceof UOperator || newChild instanceof WOperator) && conjunction.children
        .contains(((BinaryModalOperator) newChild).right)) {
        elementsChanged = true;
        continue;
      }

      elementsChanged |= (child != newChild); // NOPMD
      newChildren.add(newChild);
    }

    // Only call constructor, when necessary.
    Formula c;

    if (elementsChanged) {
      c = Conjunction.create(newChildren.stream());
    } else {
      c = conjunction;
    }

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
    boolean elementsChanged = false;
    List<Formula> newChildren = new ArrayList<>(disjunction.children.size());

    for (Formula child : disjunction.children) {
      Formula newChild = child.accept(this);

      if (newChild == BooleanConstant.TRUE) {
        return BooleanConstant.TRUE;
      }

      if (newChild instanceof GOperator && disjunction.children
        .contains(((GOperator) newChild).operand)) {
        elementsChanged = true;
        continue;
      }

      elementsChanged |= (child != newChild); // NOPMD
      newChildren.add(newChild);
    }

    // Only call constructor, when necessary.
    Formula d;
    if (elementsChanged) {
      d = Disjunction.create(newChildren.stream());
    } else {
      d = disjunction;
    }

    if (d instanceof Disjunction) {
      Disjunction d2 = (Disjunction) d;

      if (d2.children.stream().anyMatch(e -> d2.children.contains(e.not()))) {
        return BooleanConstant.TRUE;
      }
    }

    return d;
  }
}
