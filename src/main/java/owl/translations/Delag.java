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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.output.HoaPrintable;
import owl.ltl.Formula;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.translations.delag.Builder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.util.ExternalTranslator;

public final class Delag extends AbstractLtlCommandLineTool {

  private final boolean strict;
  private final Function<Formula, Automaton<?, OmegaAcceptance>> fallback;

  private Delag(boolean strict, Function<Formula, Automaton<?, OmegaAcceptance>> fallback) {
    this.strict = strict;
    this.fallback = fallback;
  }

  public static void main(String... argsArray) {
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));

    Function<Formula, Automaton<?, OmegaAcceptance>> fallback =
      (Function) new LTL2DPAFunction(EnumSet.allOf(Optimisation.class));

    for (String arg : args) {
      if (arg.startsWith("--fallback")) {
        String tool = arg.substring(11, arg.length());
        fallback = (Function) new ExternalTranslator(tool);
        args.remove(arg);
        break;
      }
    }

    new Delag(args.remove("--strict"), fallback).execute(args);
  }

  private HoaPrintable translateWithFallback(Formula formula) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
    return new Builder(fallback).apply(rewritten);
  }

  private HoaPrintable translateWithoutFallback(Formula formula) {
    Formula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
    return new Builder<>((x) -> {
      throw new UnsupportedOperationException("Outside of supported fragment." + rewritten);
    }).apply(rewritten);
  }

  @Override
  protected Function<Formula, HoaPrintable> getTranslation(EnumSet<Optimisation> optimisations) {
    if (strict) {
      return this::translateWithoutFallback;
    } else {
      return this::translateWithFallback;
    }
  }
}
