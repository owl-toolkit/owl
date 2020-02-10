/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.delag;

import static owl.run.modules.OwlModule.Transformer;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import org.apache.commons.cli.Options;
import owl.automaton.AbstractImmutableAutomaton;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.SingletonAutomaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.Environment;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ExternalTranslator;
import owl.translations.LTL2DAFunction;

public class DelagBuilder
  implements Function<LabelledFormula, Automaton<State<Object>, EmersonLeiAcceptance>> {

  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "delag",
    "Translates LTL to deterministic Emerson-Lei automata",
    new Options().addOption("f", "fallback", true,
      "Fallback tool for input outside the fragment. "
        + "If no tool is specified an internal LTL to DGRA translation is used."),
    (commandLine, environment) -> {
      String command = commandLine.getOptionValue("fallback");

      if (command == null) {
        return OwlModule.LabelledFormulaTransformer.of(new DelagBuilder(environment));
      }

      return OwlModule.LabelledFormulaTransformer.of(new DelagBuilder(environment,
        new ExternalTranslator(command, ExternalTranslator.InputMode.STDIN, environment)));
    });

  private final Environment environment;
  private final Function<LabelledFormula, ? extends Automaton<?, ?>> fallback;
  @Nullable
  private LoadingCache<ProductState<Object>, History> requiredHistoryCache = null;

  public DelagBuilder(Environment environment) {
    this.environment = environment;
    this.fallback = new LTL2DAFunction(GeneralizedRabinAcceptance.class, environment);
  }

  private DelagBuilder(Environment environment, ExternalTranslator fallback) {
    this.environment = environment;
    this.fallback = fallback;
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  @Override
  public Automaton<State<Object>, EmersonLeiAcceptance> apply(LabelledFormula inputFormula) {
    LabelledFormula formula = inputFormula.nnf();
    Factories factories = environment.factorySupplier().getFactories(formula.atomicPropositions());

    if (formula.formula().equals(BooleanConstant.FALSE)) {
      return EmptyAutomaton.of(factories.vsFactory,
        new EmersonLeiAcceptance(0, new BooleanExpression<>(false)));
    }

    if (formula.formula().equals(BooleanConstant.TRUE)) {
      return SingletonAutomaton.of(factories.vsFactory,
        new State<>(),
        new EmersonLeiAcceptance(0, new BooleanExpression<>(true)),
        Set.of());
    }

    DependencyTreeFactory<Object> treeConverter =
      new DependencyTreeFactory<>(factories, x -> (Automaton<Object, ?>) fallback.apply(x));
    DependencyTree<Object> tree = formula.formula().accept(treeConverter);
    BooleanExpression<AtomAcceptance> expression = tree.getAcceptanceExpression();
    int sets = treeConverter.setNumber;

    //noinspection ConstantConditions
    requiredHistoryCache = CacheBuilder.newBuilder().maximumSize(1024L).build(
      CacheLoader.from(key -> History.create(tree.getRequiredHistory(key))));

    ProductState<Object> initialProduct = treeConverter.buildInitialState();
    State<Object> initialState = new State<>(initialProduct,
      getHistory(null, new BitSet(), initialProduct));

    return new AbstractImmutableAutomaton.SemiDeterministicEdgesAutomaton<>(factories.vsFactory,
      Set.of(initialState), new EmersonLeiAcceptance(sets, expression)) {
      @Override
      public Edge<State<Object>> edge(State<Object> state, BitSet valuation) {
        return DelagBuilder.this.edge(tree, state, valuation);
      }
    };
  }

  private History getHistory(@Nullable History past, BitSet present, ProductState<Object> state) {
    assert requiredHistoryCache != null;

    try {
      History requiredHistory = requiredHistoryCache.getUnchecked(state);
      return History.stepHistory(past, present, requiredHistory);
    } catch (UncheckedExecutionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      } else {
        throw new UnsupportedOperationException(e);
      }
    }
  }

  @Nullable
  private Edge<State<Object>> edge(DependencyTree<Object> tree, State<Object> state,
    BitSet valuation) {
    ProductState.Builder<Object> builder = ProductState.builder();
    Boolean acc = tree.buildSuccessor(state, valuation, builder);

    if (acc != null && !acc) {
      return null;
    }

    ProductState<Object> successor = builder.build();
    History history = getHistory(state.past, valuation, successor);
    return Edge.of(new State<>(successor, history), tree.getAcceptance(state, valuation, acc));
  }
}