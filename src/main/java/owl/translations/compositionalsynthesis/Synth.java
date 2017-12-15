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

package owl.translations.compositionalsynthesis;

import static owl.translations.LTL2DAFunction.Constructions.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;
import owl.game.algorithms.CompositionalSolver;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.tlsf.Tlsf;
import owl.run.Environment;
import owl.run.modules.OwlModuleParser;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.translations.LTL2DAFunction;

public class Synth implements Function<Tlsf, Boolean> {

  private static final Logger logger = Logger.getLogger(Synth.class.getName());

  public static final OwlModuleParser.TransformerParser settings = new OwlModuleParser
    .TransformerParser() {

    @Override
    public Transformer parse(CommandLine settings) {

      int kViolations = Integer.parseInt(settings.getOptionValue("violations"));

      return (environment) -> {
        var translation = new Synth(environment, kViolations);
        return Transformers.instanceFromFunction(Tlsf.class, translation);
      };
    }

    @Override
    public String getKey() {
      return "compositionalsynthesis";
    }

    @Override
    public Options getOptions() {
      // Uncontrollable propositions option
      Option uProps = new Option("u", "uncontrollable", true,
        "List of atomic propositions controlled by player one (Environment)");
      uProps.setArgs(Option.UNLIMITED_VALUES);
      // Controllable propositions option
      Option cProps = new Option("p", "player", true,
        "List of atomic propositions controlled by player two (Controller/Player)");
      cProps.setArgs(Option.UNLIMITED_VALUES);
      // The bound on how far to count bad colour visits
      Option kCount = new Option("k", "violations", true,
        "The maximal number of colour violations");
      kCount.setRequired(true);

      // Creating all options
      return new Options()
        .addOption("bp", "breakpoint", false,
          "Use breakpoint construction instead of breakpoint-free one")
        .addOption("c", "complement", false,
          "Also compute the automaton for the negation and take "
            + "the smaller of the two")
        .addOption(uProps)
        .addOption(cProps)
        .addOption(kCount);
    }
  };

  private final int kViolations;
  private final Environment environment;

  public Synth(Environment env, int kViolations) {
    this.kViolations = kViolations;
    this.environment = env;
  }

  @Override
  public Boolean apply(Tlsf tlsf) {
    List<LabelledFormula> conjuncts = tlsf.toAssertGuaranteeConjuncts();
    logger.log(Level.INFO, "Using decomposition into {0}.", conjuncts);

    var patchedEnvironment = patchEnvironment(tlsf.variables());

    // split formulas into buchi, cobuchi, safety, and parity
    List<Automaton<Object, BuchiAcceptance>> buchi = new ArrayList<>();
    List<Automaton<Object, CoBuchiAcceptance>> cobuchi = new ArrayList<>();
    List<Automaton<Object, AllAcceptance>> safety = new ArrayList<>();
    List<Automaton<Object, ParityAcceptance>> parity = new ArrayList<>();

    LTL2DAFunction ltl2DAFunction = new LTL2DAFunction(patchedEnvironment, true,
      EnumSet.of(SAFETY, CO_SAFETY, BUCHI, CO_BUCHI, PARITY));

    for (var formula : conjuncts) {
      var simplifiedFormula = SimplifierFactory.apply(formula, SimplifierFactory.Mode.SYNTACTIC);
      var automaton = ltl2DAFunction.apply(simplifiedFormula);

      var acceptance = automaton.acceptance();

      if (acceptance instanceof AllAcceptance) {
        safety.add(AutomatonUtil.cast(automaton, Object.class, AllAcceptance.class));
      } else if (acceptance instanceof BuchiAcceptance) {
        buchi.add(AutomatonUtil.cast(automaton, Object.class, BuchiAcceptance.class));
      } else if (acceptance instanceof CoBuchiAcceptance) {
        cobuchi.add(AutomatonUtil.cast(automaton, Object.class, CoBuchiAcceptance.class));
      } else {
        parity.add(AutomatonUtil.cast(automaton, Object.class, ParityAcceptance.class));
      }
    }

    return CompositionalSolver.isRealizable(parity, buchi, cobuchi, safety, kViolations,
        tlsf.inputs().stream().mapToObj(i -> tlsf.variables().get(i))
          .collect(Collectors.toUnmodifiableList()));
  }

  private Environment patchEnvironment(List<String> variables) {
    var vsFactory = environment.factorySupplier().getValuationSetFactory(variables);
    return new Environment() {
      @Override
      public boolean annotations() {
        return environment.annotations();
      }

      @Override
      public FactorySupplier factorySupplier() {
        return new FactorySupplier() {
          @Override
          public ValuationSetFactory getValuationSetFactory(List<String> alphabet) {
            assert vsFactory.alphabet().equals(alphabet);
            return vsFactory;
          }

          @Override
          public EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet) {
            return environment.factorySupplier().getEquivalenceClassFactory(alphabet);
          }

          @Override
          public EquivalenceClassFactory getEquivalenceClassFactory(List<String> alphabet,
            boolean keepRepresentatives) {
            return environment.factorySupplier().getEquivalenceClassFactory(alphabet,
              keepRepresentatives);
          }

          @Override
          public Factories getFactories(List<String> alphabet) {
            return new Factories(
              getEquivalenceClassFactory(alphabet),
              getValuationSetFactory(alphabet));
          }
        };
      }

      @Override
      public boolean parallel() {
        return environment.parallel();
      }

      @Override
      public void shutdown() {
        environment.shutdown();
      }

      @Override
      public boolean isShutdown() {
        return environment.isShutdown();
      }
    };
  }
}
