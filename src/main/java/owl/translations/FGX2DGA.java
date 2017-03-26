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
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.output.HoaPrintable;
import owl.ltl.Formula;
import owl.ltl.parser.ParserException;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.translations.fgx2generic.Builder;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public final class FGX2DGA extends AbstractLtlCommandLineTool {

  private final boolean strict;
  private final Function<Formula, Automaton<Object, OmegaAcceptance>> fallback;

  private FGX2DGA(boolean strict) {
    this.strict = strict;
    EnumSet<Optimisation> fallbackOptimisations = EnumSet.allOf(Optimisation.class);
    fallback = ((Function) new LTL2DPAFunction(fallbackOptimisations));
  }

  public static void main(String... argsArray) throws ParserException, ParseException, IOException {
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));
    new FGX2DGA(args.remove("--strict")).execute(args);
  }

  private HoaPrintable translateWithFallback(Formula formula,
    EnumSet<Optimisation> optimisations) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
    return new Builder<>(fallback, optimisations).apply(rewritten);
  }

  private HoaPrintable translateWithoutFallback(Formula formula,
    EnumSet<Optimisation> optimisations) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
    return new Builder<>((x) -> {
      throw new UnsupportedOperationException("Outside of supported fragment." + rewritten);
    }, optimisations).apply(rewritten);
  }

  @Override
  protected Function<Formula, HoaPrintable> getTranslation(EnumSet<Optimisation> optimisations) {
    if (strict) {
      return x -> translateWithoutFallback(x, optimisations);
    } else {
      return x -> translateWithFallback(x, optimisations);
    }
  }
}
