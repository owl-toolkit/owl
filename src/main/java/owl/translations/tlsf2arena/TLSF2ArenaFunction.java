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

package owl.translations.tlsf2arena;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.Game;
import owl.game.GameViews;
import owl.ltl.LabelledFormula;
import owl.ltl.tlsf.Tlsf;
import owl.run.Environment;
import owl.run.modules.OwlModuleParser;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public class TLSF2ArenaFunction implements Function<Tlsf, Game<?, ParityAcceptance>> {
  public static final OwlModuleParser.TransformerParser settings =
    new OwlModuleParser.TransformerParser() {
    @Override
    public Transformer parse(CommandLine settings) {
      boolean breakpoints = settings.hasOption("breakpoint");
      boolean complement = settings.hasOption("complement");
      Set<LTL2DPAFunction.Configuration> configuration = new HashSet<>();

      if (breakpoints) {
        configuration.addAll(LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG);
      } else {
        configuration.addAll(LTL2DPAFunction.RECOMMENDED_SYMMETRIC_CONFIG);
      }

      if (!complement) {
        configuration.remove(LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION);
      }

      return (env) -> Transformers
        .fromFunction(Tlsf.class, new TLSF2ArenaFunction(env, configuration)).create(env);
    }

    @Override
    public String getKey() {
      return "tlsf2arena";
    }

    @Override
    public Options getOptions() {
      return new Options()
        .addOption("bp", "breakpoint", false,
          "Use breakpoint construction instead of breakpoint-free one")
        .addOption("c", "complement", false, "Also compute the automaton for the negation and take "
          + "the smaller of the two");
    }
  };

  private final LTL2DPAFunction fun;

  private TLSF2ArenaFunction(Environment env, Set<LTL2DPAFunction.Configuration> configuration) {
    this.fun = new LTL2DPAFunction(env, configuration);
  }

  @Override
  public Game<?, ParityAcceptance> apply(Tlsf tlsfFormula) {
    // first, translate the tlsf to ltl
    System.out.println("Just before building the formula");
    LabelledFormula formula = tlsfFormula.toFormula();
    System.out.println("The formula is ready");
    // now, use the ltl to build the automaton
    Automaton<?, ParityAcceptance> automaton = fun.apply(formula);
    System.out.println("The automaton is ready");
    // finally, split the automaton into an arena
    return Game.of(automaton, getPlayer1Propositions(tlsfFormula));
  }

  private static List<String> getPlayer1Propositions(Tlsf tlsfFormula) {
    BitSet inputs = tlsfFormula.inputs();
    int propListSize = inputs.cardinality();
    assert (propListSize > 0);
    String[] props = new String[propListSize];

    for (int i = inputs.nextSetBit(0); i >= 0; i = inputs.nextSetBit(i + 1)) {
      props[i] = tlsfFormula.variables().get(i);
    }

    return List.of(props);
  }
}
