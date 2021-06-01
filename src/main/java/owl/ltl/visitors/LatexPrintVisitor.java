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

package owl.ltl.visitors;

import com.google.common.collect.Comparators;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

public class LatexPrintVisitor implements Visitor<String> {

  private final List<String> alphabet;

  public LatexPrintVisitor(List<String> alphabet) {
    this.alphabet = List.copyOf(alphabet);
  }

  @Override
  public String visit(BooleanConstant booleanConstant) {
    return booleanConstant.value ? " \\true " : " \\false ";
  }

  @Override
  public String visit(Conjunction conjunction) {
    return visit(conjunction, " \\wedge ");
  }

  @Override
  public String visit(Disjunction disjunction) {
    return visit(disjunction, " \\vee ");
  }

  @Override
  public String visit(FOperator fOperator) {
    return visit((Formula.UnaryTemporalOperator) fOperator);
  }

  @Override
  public String visit(GOperator gOperator) {
    return visit((Formula.UnaryTemporalOperator) gOperator);
  }

  @Override
  public String visit(Literal literal) {
    String name = alphabet.get(literal.getAtom());
    return literal.isNegated() ? "\\overline{" + name + '}' : name;
  }

  @Override
  public String visit(MOperator mOperator) {
    return visit((Formula.BinaryTemporalOperator) mOperator);
  }

  @Override
  public String visit(ROperator rOperator) {
    return visit((Formula.BinaryTemporalOperator) rOperator);
  }

  @Override
  public String visit(UOperator uOperator) {
    return visit((Formula.BinaryTemporalOperator) uOperator);
  }

  @Override
  public String visit(WOperator wOperator) {
    return visit((Formula.BinaryTemporalOperator) wOperator);
  }

  @Override
  public String visit(XOperator xOperator) {
    return visit((Formula.UnaryTemporalOperator) xOperator);
  }

  private String visit(Formula.UnaryTemporalOperator operator) {
    if (operator.operand() instanceof Formula.UnaryTemporalOperator
      || operator.operand() instanceof Literal) {
      return '\\' + operator.operatorSymbol() + ' ' + operator.operand().accept(this);
    }

    return '\\' + operator.operatorSymbol() + " (" + operator.operand().accept(this) + ')';
  }

  private String visit(Formula.BinaryTemporalOperator operator) {
    return Stream.of(operator.leftOperand(), operator.rightOperand())
      .map(x -> {
        if (x instanceof Literal) {
          return x.accept(this);
        } else {
          return '(' + x.accept(this) + ')';
        }
      })
      .collect(Collectors.joining(" \\" + operator.operatorSymbol() + ' '));
  }

  private String visit(Formula.NaryPropositionalOperator propositionalFormula, String latexString) {
    // NOPMD
    assert Comparators.isInStrictOrder(propositionalFormula.operands, Comparator.naturalOrder());
    return propositionalFormula.operands.stream()
      .map(x -> {
        if (x instanceof Formula.NaryPropositionalOperator || x instanceof Biconditional) {
          return '(' + x.accept(this) + ')';
        } else {
          return x.accept(this);
        }
      })
      .collect(Collectors.joining(latexString));
  }
}
