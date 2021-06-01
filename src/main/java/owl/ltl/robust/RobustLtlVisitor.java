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

package owl.ltl.robust;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;
import owl.collections.Collections3;
import owl.grammar.LTLParser;
import owl.grammar.LTLParserBaseVisitor;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.XOperator;


/**
 * Visitor for translating rLTL expressions (which are represented as classical LTL expressions)
 * to LTL expressions using some optimizations.
 *
 * <p>This visitor implements an optimized translation. The underlying observation is that
 * subformulas without the robust globally and release operator correspond to classical LTL formulas
 * and, hence, all four LTL formulas obtained during the translation are identical. This fact can
 * be used to improve the translation of implications and negations since one does not need to recur
 * to all four subformulas in these cases.
 */
class RobustLtlVisitor extends LTLParserBaseVisitor<Split> {
  private final List<Split> literalCache = new ArrayList<>();
  private final List<String> variables = new ArrayList<>();

  public List<String> variables() {
    return List.copyOf(variables);
  }


  // Structure

  @Override
  public Split visitExpression(LTLParser.ExpressionContext ctx) {
    assert ctx.getChildCount() == 1;
    return visit(ctx.getChild(0));
  }

  @Override
  public Split visitFormula(LTLParser.FormulaContext ctx) {
    // Contained formula + EOF
    assert ctx.getChildCount() == 2 : ctx.getChildCount();
    return visit(ctx.getChild(0));
  }

  @Override
  public Split visitNested(LTLParser.NestedContext ctx) {
    assert ctx.getChildCount() == 3;
    return visit(ctx.nested);
  }


  // AST Leafs

  @Override
  public Split visitBoolean(LTLParser.BooleanContext ctx) {
    assert ctx.getChildCount() == 1;
    LTLParser.BoolContext constant = ctx.bool();

    if (constant.FALSE() != null) {
      return Split.FALSE;
    }

    if (constant.TRUE() != null) {
      return Split.TRUE;
    }

    throw new ParseCancellationException("Unknown constant");
  }

  @Override
  public Split visitVariable(LTLParser.VariableContext ctx) {
    assert ctx.getChildCount() == 1;
    assert variables.size() == literalCache.size();
    String name = ctx.getText();
    int index = variables.indexOf(name);

    if (index == -1) {
      int newIndex = variables.size();
      Literal literal = Literal.of(newIndex);
      variables.add(name);

      Split literalSplit = Split.of(literal, true);
      literalCache.add(literalSplit);
      return literalSplit;
    }

    return literalCache.get(index);
  }


  // Boolean Logic

  @Override
  public Split visitAndExpression(LTLParser.AndExpressionContext ctx) {
    assert ctx.getChildCount() > 0;

    return ctx.children.stream()
      .filter(child -> !(child instanceof TerminalNode))
      .map(this::visit)
      .reduce(Split.TRUE, Split.combiner(Conjunction::of));
  }

  @Override
  public Split visitOrExpression(LTLParser.OrExpressionContext ctx) {
    assert ctx.getChildCount() > 0;

    return ctx.children.stream()
      .filter(child -> !(child instanceof TerminalNode))
      .map(this::visit)
      .reduce(Split.FALSE, Split.combiner(Disjunction::of));
  }


  // Temporal Logic

  @Override
  public Split visitUnaryOperation(LTLParser.UnaryOperationContext ctx) {
    assert ctx.getChildCount() == 2;
    LTLParser.UnaryOpContext unaryOp = ctx.unaryOp();
    Split operand = visit(ctx.inner);

    if (unaryOp.NOT() != null) {
      return operand.grFree()
        ? operand.map(Formula::not)
        : Split.of(Conjunction.of(operand.all()).not(), operand.grFree());
    }

    if (unaryOp.FINALLY() != null) {
      return operand.map(FOperator::of);
    }

    if (unaryOp.GLOBALLY() != null) {
      return Split.of(
        GOperator.of(operand.always()),
        FOperator.of(GOperator.of(operand.eventuallyAlways())),
        GOperator.of(FOperator.of(operand.infinitelyOften())),
        FOperator.of(operand.eventually()),
        false);
    }

    if (unaryOp.NEXT() != null) {
      return operand.map(XOperator::of);
    }

    throw new ParseCancellationException("Unsupported operator");
  }

  @Override
  public Split visitBinaryOperation(LTLParser.BinaryOperationContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    LTLParser.BinaryOpContext binaryOp = ctx.binaryOp();
    Split left = visit(ctx.left);
    Split right = visit(ctx.right);

    if (binaryOp.IMP() != null) {
      if (left.grFree() && right.grFree()) {
        // Should be l => r
        return Split.combiner((l, r) -> Disjunction.of(l.not(), r)).apply(left, right);
      }
      Collection<Formula> conjuncts = new ArrayList<>(4);
      Collections3.forEachPair(left.all(), right.all(),
        (l, r) -> conjuncts.add(Conjunction.of(l, r.not())));
      Formula antecedent = Disjunction.of(conjuncts).not();

      // Should be antecedent => r
      return right.map(r -> Disjunction.of(antecedent, r));
    }

    throw new ParseCancellationException("Unsupported operator");
  }
}
