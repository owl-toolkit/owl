/*
 * Copyright (C) 2016, 2022  (Salomon Sickert, Tobias Meggendorfer)
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

package owl.ltl.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import owl.grammar.LTLParser.AndExpressionContext;
import owl.grammar.LTLParser.BinaryOpContext;
import owl.grammar.LTLParser.BinaryOperationContext;
import owl.grammar.LTLParser.BooleanContext;
import owl.grammar.LTLParser.DoubleQuotedVariableContext;
import owl.grammar.LTLParser.ExpressionContext;
import owl.grammar.LTLParser.FormulaContext;
import owl.grammar.LTLParser.NestedContext;
import owl.grammar.LTLParser.OrExpressionContext;
import owl.grammar.LTLParser.SingleQuotedVariableContext;
import owl.grammar.LTLParser.UnaryOpContext;
import owl.grammar.LTLParser.UnaryOperationContext;
import owl.grammar.LTLParser.VariableContext;
import owl.grammar.LTLParserBaseVisitor;
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

final class LtlParseTreeVisitor extends LTLParserBaseVisitor<Formula> {

  private final List<String> atomicPropositions;
  private final Map<String, Integer> atomicPropositionsLookup;

  LtlParseTreeVisitor() {
    this.atomicPropositions = new ArrayList<>();
    this.atomicPropositionsLookup = new HashMap<>();
  }

  LtlParseTreeVisitor(List<String> atomicPropositions) {
    this.atomicPropositions = List.copyOf(atomicPropositions);
    this.atomicPropositionsLookup = new HashMap<>();

    for (int i = 0, s = this.atomicPropositions.size(); i < s; i++) {
      var oldValue = atomicPropositionsLookup.put(this.atomicPropositions.get(i), i);

      if (oldValue != null) {
        throw new IllegalArgumentException();
      }
    }
  }

  List<String> atomicPropositions() {
    return List.copyOf(atomicPropositions);
  }

  @Override
  public Formula visitAndExpression(AndExpressionContext ctx) {
    assert ctx.getChildCount() > 0;

    var children = new ArrayList<Formula>(ctx.getChildCount());

    for (ParseTree child : ctx.children) {
      if (!(child instanceof TerminalNode)) {
        children.add(visit(child));
      }
    }

    return Conjunction.of(children);
  }

  @Override
  public Formula visitBinaryOperation(BinaryOperationContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null;
    assert ctx.right != null;

    BinaryOpContext binaryOp = ctx.op;
    Formula left = visit(ctx.left);
    Formula right = visit(ctx.right);

    if (binaryOp.BIIMP() != null) {
      return Biconditional.of(left, right);
    }

    if (binaryOp.IMP() != null) {
      return Disjunction.of(left.not(), right);
    }

    if (binaryOp.XOR() != null) {
      return Biconditional.of(left.not(), right);
    }

    if (binaryOp.UNTIL() != null) {
      return UOperator.of(left, right);
    }

    if (binaryOp.WUNTIL() != null) {
      return WOperator.of(left, right);
    }

    if (binaryOp.RELEASE() != null) {
      return ROperator.of(left, right);
    }

    if (binaryOp.SRELEASE() != null) {
      return MOperator.of(left, right);
    }

    throw new ParseCancellationException("Unknown operator");
  }

  @Override
  public Formula visitBoolean(BooleanContext ctx) {
    assert ctx.getChildCount() == 1;
    var constant = ctx.constant;

    if (constant.FALSE() != null) {
      return BooleanConstant.FALSE;
    }

    if (constant.TRUE() != null) {
      return BooleanConstant.TRUE;
    }

    throw new ParseCancellationException("Unknown constant");
  }

  @Override
  public Formula visitExpression(ExpressionContext ctx) {
    assert ctx.getChildCount() == 1;
    return visit(ctx.getChild(0));
  }

  @Override
  public Formula visitFormula(FormulaContext ctx) {
    // Contained formula + EOF
    assert ctx.getChildCount() == 2 : ctx.getChildCount();
    return visit(ctx.getChild(0));
  }

  @Override
  public Formula visitNested(NestedContext ctx) {
    assert ctx.getChildCount() == 3;
    return visit(ctx.nested);
  }

  @Override
  public Formula visitOrExpression(OrExpressionContext ctx) {
    assert ctx.getChildCount() > 0;

    var children = new ArrayList<Formula>(ctx.getChildCount());

    for (ParseTree child : ctx.children) {
      if (!(child instanceof TerminalNode)) {
        children.add(visit(child));
      }
    }

    return Disjunction.of(children);
  }

  @Override
  @SuppressWarnings("PMD.ConfusingTernary")
  public Formula visitUnaryOperation(UnaryOperationContext ctx) {
    assert ctx.getChildCount() == 2;
    UnaryOpContext unaryOp = ctx.op;
    Formula operand = visit(ctx.inner);

    if (unaryOp.NOT() != null) {
      return operand.not();
    }

    if (unaryOp.FINALLY() != null) {
      return FOperator.of(operand);
    }

    if (unaryOp.GLOBALLY() != null) {
      return GOperator.of(operand);
    }

    if (unaryOp.NEXT() != null) {
      return XOperator.of(operand);
    }

    throw new AssertionError("Unreachable Code");
  }

  @Override
  public Formula visitVariable(VariableContext ctx) {
    assert ctx.getChildCount() == 1;
    return lookupLiteral(ctx.getText());
  }

  @Override
  public Formula visitSingleQuotedVariable(SingleQuotedVariableContext ctx) {
    assert ctx.getChildCount() == 3;
    return lookupLiteral(ctx.variable.getText());
  }

  @Override
  public Formula visitDoubleQuotedVariable(DoubleQuotedVariableContext ctx) {
    assert ctx.getChildCount() == 3;
    return lookupLiteral(ctx.variable.getText());
  }

  private Literal lookupLiteral(String name) {
    Integer index = atomicPropositionsLookup.get(name);

    if (index != null) {
      return Literal.of(index);
    }

    // We need to add a new element, but atomicPropositions is read-only
    if (!(atomicPropositions instanceof ArrayList)) {
      throw new IllegalStateException(
          "Encountered unknown variable %s with fixed set %s".formatted(name, atomicPropositions));
    }

    int newIndex = atomicPropositions.size();
    atomicPropositions.add(name);
    var oldValue = atomicPropositionsLookup.put(name, newIndex);
    assert oldValue == null;
    return Literal.of(newIndex);
  }
}
