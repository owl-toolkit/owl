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

package owl.ltl.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;
import owl.collections.Collections3;
import owl.grammar.LTLParser.AndExpressionContext;
import owl.grammar.LTLParser.BinaryOpContext;
import owl.grammar.LTLParser.BinaryOperationContext;
import owl.grammar.LTLParser.BoolContext;
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
import owl.ltl.Negation;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;


final class LtlfParseTreeVisitor extends LTLParserBaseVisitor<Formula> {

  private final List<String> atomicPropositions;
  private final List<Literal> literalCache;

  LtlfParseTreeVisitor() {
    this.atomicPropositions = new ArrayList<>();
    this.literalCache = new ArrayList<>();
  }

  LtlfParseTreeVisitor(List<String> atomicPropositions) {
    this.atomicPropositions = List.copyOf(atomicPropositions);

    if (!Collections3.isDistinct(this.atomicPropositions)) {
      throw new IllegalArgumentException();
    }

    Literal[] literalList = new Literal[this.atomicPropositions.size()];
    Arrays.setAll(literalList, Literal::of);
    this.literalCache = List.of(literalList);
  }

  List<String> atomicPropositions() {
    return List.copyOf(atomicPropositions);
  }

  @Override
  public Formula visitAndExpression(AndExpressionContext ctx) {
    assert ctx.getChildCount() > 0;
    if (ctx.getChildCount() == 1) { //avoid Conjunctions with only one child
      return ctx.getChild(0).accept(this);
    }

    return new Conjunction(ctx.children.stream()
      .filter(child -> !(child instanceof TerminalNode))
      .map(this::visit).collect(Collectors.toUnmodifiableSet()));
  }

  @Override
  public Formula visitBinaryOperation(BinaryOperationContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    BinaryOpContext binaryOp = ctx.binaryOp();
    Formula left = visit(ctx.left);
    Formula right = visit(ctx.right);

    if (binaryOp.BIIMP() != null) {
      return new Biconditional(left, right);
    }

    if (binaryOp.IMP() != null) {
      return new Disjunction(new Negation(left), right);
    }

    if (binaryOp.XOR() != null) {
      return new Biconditional(left.not(), right);
    }

    if (binaryOp.UNTIL() != null) {
      return new UOperator(left, right);
    }

    if (binaryOp.WUNTIL() != null) {
      return new WOperator(left, right);
    }

    if (binaryOp.RELEASE() != null) {
      return new ROperator(left, right);
    }

    if (binaryOp.SRELEASE() != null) {
      return new MOperator(left, right);
    }

    throw new ParseCancellationException("Unknown operator");
  }

  @Override
  public Formula visitBoolean(BooleanContext ctx) {
    assert ctx.getChildCount() == 1;
    BoolContext constant = ctx.bool();

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

    if (ctx.getChildCount() == 1) { //avoid Disjunctions with only one child
      return ctx.getChild(0).accept(this);
    }
    return new Disjunction(ctx.children.stream()
      .filter(child -> !(child instanceof TerminalNode))
      .map(this::visit).collect(Collectors.toUnmodifiableSet()));
  }

  @Override
  @SuppressWarnings("PMD.ConfusingTernary")
  public Formula visitUnaryOperation(UnaryOperationContext ctx) {
    assert ctx.getChildCount() == 2;
    UnaryOpContext unaryOp = ctx.unaryOp();
    Formula operand = visit(ctx.inner);

    if (unaryOp.NOT() != null) {
      return new Negation(operand);
    }

    if (unaryOp.FINALLY() != null) {
      return new FOperator(operand);
    }

    if (unaryOp.GLOBALLY() != null) {
      return new GOperator(operand);
    }

    if (unaryOp.NEXT() != null) {
      return new XOperator(operand);
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
    assert atomicPropositions.size() == literalCache.size();
    int index = atomicPropositions.indexOf(name);

    if (index == -1) {
      if (!(atomicPropositions instanceof ArrayList)) {
        throw new IllegalStateException("Encountered unknown variable " + name
          + " with fixed set " + atomicPropositions);
      }

      int newIndex = atomicPropositions.size();
      Literal literal = Literal.of(newIndex);
      atomicPropositions.add(name);
      literalCache.add(literal);
      return literal;
    }

    return literalCache.get(index);
  }
}
