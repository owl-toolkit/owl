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

package owl.translations;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.function.Function;
import jhoafparser.parser.generated.ParseException;
import owl.automaton.output.HoaPrintable;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.XOperator;
import owl.ltl.parser.ParserException;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.DefaultVisitor;
import owl.translations.fgx2generic.Builder;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public final class FGX2DGA extends AbstractLtlCommandLineTool {

  private static final IsSupportedFragmentChecker CHECKER = new IsSupportedFragmentChecker();
  private final boolean allowFallback;

  private FGX2DGA(boolean allowFallback) {
    this.allowFallback = allowFallback;
  }

  public static void main(String... argsArray) throws ParserException, ParseException, IOException {
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));
    new FGX2DGA(args.remove("--fallback")).execute(args);
  }

  private static HoaPrintable translateWithFallback(Formula formula,
    EnumSet<Optimisation> optimisations) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);

    if (!rewritten.accept(FGX2DGA.CHECKER)) {
      return new LTL2DPAFunction().apply(rewritten);
    }

    return new Builder(optimisations).apply(rewritten);
  }

  private static HoaPrintable translateWithoutFallback(Formula formula,
    EnumSet<Optimisation> optimisations) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);

    if (!rewritten.accept(FGX2DGA.CHECKER)) {
      throw new UnsupportedOperationException("Outside of supported fragment." + rewritten);
    }

    return new Builder(optimisations).apply(rewritten);
  }

  @Override
  protected Function<Formula, ? extends HoaPrintable> getTranslation(
    EnumSet<Optimisation> optimisations) {
    return allowFallback
           ? x -> FGX2DGA.translateWithFallback(x, optimisations)
           : x -> FGX2DGA.translateWithoutFallback(x, optimisations);
  }

  private static class IsSupportedFragmentChecker extends DefaultVisitor<Boolean> {
    @Override
    protected Boolean defaultAction(Formula formula) {
      return Fragments.isSafety(formula) || Fragments.isCoSafety(formula);
    }

    @Override
    public Boolean visit(BooleanConstant booleanConstant) {
      return true;
    }

    @Override
    public Boolean visit(Conjunction conjunction) {
      return conjunction.children.stream().allMatch(x -> x.accept(this));
    }

    @Override
    public Boolean visit(Disjunction disjunction) {
      return disjunction.children.stream().allMatch(x -> x.accept(this));
    }

    @Override
    public Boolean visit(FOperator fOperator) {
      return Fragments.isCoSafety(fOperator) || fOperator.operand instanceof GOperator && Fragments
        .isFgx(fOperator);
    }

    @Override
    public Boolean visit(GOperator gOperator) {
      return Fragments.isSafety(gOperator) || gOperator.operand instanceof FOperator && Fragments
        .isFgx(gOperator);
    }

    @Override
    public Boolean visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }
  }
}
