/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.grammar.SPECTRAParser.AdditiveContext;
import owl.grammar.SPECTRAParser.AndContext;
import owl.grammar.SPECTRAParser.ConstContext;
import owl.grammar.SPECTRAParser.IffContext;
import owl.grammar.SPECTRAParser.ImpliesContext;
import owl.grammar.SPECTRAParser.LtlContext;
import owl.grammar.SPECTRAParser.MultiplicativeContext;
import owl.grammar.SPECTRAParser.NestedContext;
import owl.grammar.SPECTRAParser.OrContext;
import owl.grammar.SPECTRAParser.PastBinaryContext;
import owl.grammar.SPECTRAParser.PastBinaryOpContext;
import owl.grammar.SPECTRAParser.PastUnaryContext;
import owl.grammar.SPECTRAParser.PastUnaryOpContext;
import owl.grammar.SPECTRAParser.PredPattContext;
import owl.grammar.SPECTRAParser.PrimaryContext;
import owl.grammar.SPECTRAParser.ReferableContext;
import owl.grammar.SPECTRAParser.RelationalContext;
import owl.grammar.SPECTRAParser.RemainderContext;
import owl.grammar.SPECTRAParser.SpecialNextContext;
import owl.grammar.SPECTRAParserBaseVisitor;
import owl.ltl.Biconditional;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.HOperator;
import owl.ltl.OOperator;
import owl.ltl.SOperator;
import owl.ltl.TOperator;
import owl.ltl.XOperator;
import owl.ltl.YOperator;
import owl.ltl.parser.SpectraParser.HigherOrderExpression;
import owl.ltl.parser.SpectraParser.SpectraBoolean;
import owl.ltl.parser.SpectraParser.SpectraType;

final class SpectraParseTreeVisitor extends SPECTRAParserBaseVisitor<Formula> {
  private final Map<String, HigherOrderExpression> variables;
  private final Set<String> typeConstants;

  SpectraParseTreeVisitor(Map<String, HigherOrderExpression> variables,
                          Set<String> typeConstants) {
    this.variables = variables;
    this.typeConstants = typeConstants;
  }

  @Override
  public Formula visitLtl(LtlContext ctx) {
    assert (ctx.getChildCount() >= 3 && ctx.getChildCount() <= 6) : ctx.getChildCount();
    Formula operand = visit(ctx.temporalExpression);
    if (ctx.justice != null) {
      return GOperator.of(FOperator.of(operand));
    }
    return operand;
  }

  @Override
  public Formula visitImplies(ImpliesContext ctx) {
    return Disjunction.of(visit(ctx.left).not(), visit(ctx.right));
  }

  @Override
  public Formula visitIff(IffContext ctx) {
    return Biconditional.of(visit(ctx.left), visit(ctx.right));
  }

  @Override
  public Formula visitOr(OrContext ctx) {
    return Disjunction.of(visit(ctx.left), visit(ctx.right));
  }

  @Override
  public Formula visitAnd(AndContext ctx) {
    return Conjunction.of(visit(ctx.left), visit(ctx.right));
  }

  @Override
  public Formula visitRelational(RelationalContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    SpectraTypeVisitor typeParser = new SpectraTypeVisitor(variables, typeConstants);
    Optional<SpectraType> expressionType = typeParser.visitRelational(ctx);

    if (expressionType.isPresent()) {
      SpectraExpressionVisitor expressionParser =
        new SpectraExpressionVisitor(variables, typeConstants, expressionType.get());
      return expressionParser.visitRelational(ctx).toFormula();
    } else {
      throw new ParseCancellationException("Empty expression type has been returned");
    }
  }

  @Override
  public Formula visitRemainder(RemainderContext ctx) {
    throw new ParseCancellationException("Mod operator shouldn't be called in this context");
  }

  @Override
  public Formula visitAdditive(AdditiveContext ctx) {
    throw new ParseCancellationException("Add/Diff operators shouldn't be called in this context");
  }

  @Override
  public Formula visitMultiplicative(MultiplicativeContext ctx) {
    throw new ParseCancellationException("Mul/Div operators shouldn't be called in this context");
  }

  @Override
  public Formula visitPastBinary(PastBinaryContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    PastBinaryOpContext pastBinaryOp = ctx.pastBinaryOp();
    Formula left = visit(ctx.left);
    Formula right = visit(ctx.right);

    if (pastBinaryOp.SINCE() != null) {
      return SOperator.of(left, right);
    }

    if (pastBinaryOp.TRIGGERED() != null) {
      return TOperator.of(left, right);
    }

    throw new ParseCancellationException("Unknown past binary operator");
  }

  @Override
  public Formula visitPastUnary(PastUnaryContext ctx) {
    assert ctx.getChildCount() == 2;
    assert ctx.right != null;

    PastUnaryOpContext pastUnaryOp = ctx.pastUnaryOp();
    Formula right = visit(ctx.right);

    if (pastUnaryOp.ONCE() != null) {
      return OOperator.of(right);
    }

    if (pastUnaryOp.HISTORICALLY() != null) {
      return HOperator.of(right);
    }

    if (pastUnaryOp.PREV() != null) {
      return YOperator.of(right);
    }

    throw new ParseCancellationException("Unknown past unary operator");
  }

  @Override
  public Formula visitPrimary(PrimaryContext ctx) {
    return visit(ctx.getChild(0));
  }

  @Override
  public Formula visitConst(ConstContext ctx) {
    assert ctx.getChildCount() == 1;
    throw new ParseCancellationException("A constant shouldn't be called in this context");
  }

  @Override
  public Formula visitNested(NestedContext ctx) {
    assert ctx.getChildCount() == 3;
    return visit(ctx.temporalExpr());
  }

  @Override
  public Formula visitPredPatt(PredPattContext ctx) {
    //TODO:
    throw new ParseCancellationException("Predicate and patterns not implemented yet");
  }

  @Override
  public Formula visitReferable(ReferableContext ctx) {
    if (variables.containsKey(ctx.pointer.getText())) {
      SpectraParser.HigherOrderExpression expr = variables.get(ctx.pointer.getText());
      if (expr.getType() instanceof SpectraBoolean) {
        SpectraExpressionVisitor expressionParser =
          new SpectraExpressionVisitor(variables, typeConstants, expr.getType());
        return expressionParser.visitReferable(ctx).getBit(0);
      } else {
        throw new ParseCancellationException(
          "A none boolean variable shouldn't be called in this context");
      }
    } else {
      throw new ParseCancellationException("Unsupported cross reference as of now");
    }
  }

  @Override
  public Formula visitSpecialNext(SpecialNextContext ctx) {
    assert ctx.getChildCount() == 4;

    return XOperator.of(visit(ctx.temporalExpr()));
  }
}
