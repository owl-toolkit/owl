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
import owl.automaton.output.HoaPrintable;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.parser.ParseException;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.DefaultVisitor;
import owl.ltl.visitors.predicates.FGXFragment;
import owl.translations.fgx2generic.Builder;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public final class FGX2DGA extends AbstractLtlCommandLineTool {

  private static final IsSupportedFragmentChecker CHECKER = new IsSupportedFragmentChecker();
  private static final Builder BUILDER = new Builder();

  private final boolean allowFallback;

  private FGX2DGA(boolean allowFallback) {
    this.allowFallback = allowFallback;
  }

  public static void main(String... argsArray)
    throws ParseException, jhoafparser.parser.generated.ParseException, IOException {
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));
    new FGX2DGA(args.remove("--fallback")).execute(args);
  }

  @Override
  protected Function<Formula, ? extends HoaPrintable> getTranslation(
    EnumSet<Optimisation> optimisations) {

    return allowFallback ? FGX2DGA::translateWithFallback : FGX2DGA::translateWithoutFallback;
  }

  private static HoaPrintable translateWithFallback(Formula formula) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);

    if (!rewritten.accept(FGX2DGA.CHECKER)) {
      return new LTL2DPAFunction().apply(rewritten);
    }

    return BUILDER.apply(rewritten);
  }

  private static HoaPrintable translateWithoutFallback(Formula formula) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);

    if (!rewritten.accept(FGX2DGA.CHECKER)) {
      throw new UnsupportedOperationException("Outside of supported fragment.");
    }

    return BUILDER.apply(rewritten);
  }

  private static class IsSupportedFragmentChecker extends DefaultVisitor<Boolean> {
    @Override
    protected Boolean defaultAction(Formula formula) {
      return false;
    }

    @Override
    public Boolean visit(BooleanConstant booleanConstant) {
      return true;
    }

    @Override
    public Boolean visit(Conjunction conjunction) {
      return conjunction.allMatch(x -> x.accept(this));
    }

    @Override
    public Boolean visit(Disjunction disjunction) {
      return disjunction.allMatch(x -> x.accept(this));
    }

    @Override
    public Boolean visit(FOperator fOperator) {
      return fOperator.operand instanceof GOperator && FGXFragment.testStatic(fOperator);
    }

    @Override
    public Boolean visit(GOperator gOperator) {
      return gOperator.operand instanceof FOperator && FGXFragment.testStatic(gOperator);
    }
  }
}
