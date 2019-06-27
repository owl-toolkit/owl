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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.run.Environment;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ExternalTranslator;
import owl.translations.ltl2dra.SymmetricDRAConstruction;

public class DelagBuilder
  implements Function<LabelledFormula, Automaton<State<Object>, EmersonLeiAcceptance>> {

  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("delag")
    .description("Translates LTL to deterministic Emerson-Lei automata")
    .optionsDirect(new Options().addOption("f", "fallback", true,
      "Fallback tool for input outside the fragment. "
        + "If no tool is specified an internal LTL to DGRA translation is used."))
    .parser(settings -> environment -> {
      String fallbackTool = settings.getOptionValue("fallback");

      if (fallbackTool == null) {
        return Transformers.instanceFromFunction(LabelledFormula.class,
          new DelagBuilder(environment));
      }

      return Transformers.instanceFromFunction(LabelledFormula.class,
        new DelagBuilder(environment, new ExternalTranslator(environment, fallbackTool)));
    }).build();

  private final Environment environment;
  private final Function<LabelledFormula, ? extends Automaton<?, ?>> fallback;
  @Nullable
  private LoadingCache<ProductState<Object>, History> requiredHistoryCache = null;

  public DelagBuilder(Environment environment) {
    this.environment = environment;
    this.fallback =
      SymmetricDRAConstruction.of(environment, GeneralizedRabinAcceptance.class, true);
  }

  private DelagBuilder(Environment environment, ExternalTranslator fallback) {
    this.environment = environment;
    this.fallback = fallback;
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("delag")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(CLI)
      .writer(OutputWriters.ToHoa.DEFAULT)
      .build());
  }

  @Override
  public Automaton<State<Object>, EmersonLeiAcceptance> apply(LabelledFormula inputFormula) {
    LabelledFormula formula = SyntacticFragments.normalize(inputFormula, SyntacticFragment.NNF);
    Factories factories = environment.factorySupplier().getFactories(formula.variables());

    if (formula.formula().equals(BooleanConstant.FALSE)) {
      return Views.viewAs(AutomatonFactory.empty(factories.vsFactory), EmersonLeiAcceptance.class);
    }

    if (formula.formula().equals(BooleanConstant.TRUE)) {
      return AutomatonFactory.singleton(factories.vsFactory,
        new State<>(),
        new EmersonLeiAcceptance(0, new BooleanExpression<>(true)),
        Set.of());
    }

    DependencyTreeFactory<Object> treeConverter =
      new DependencyTreeFactory<>(factories, x -> AutomatonUtil.cast(fallback.apply(x)));
    DependencyTree<Object> tree = formula.formula().accept(treeConverter);
    BooleanExpression<AtomAcceptance> expression = tree.getAcceptanceExpression();
    int sets = treeConverter.setNumber;

    //noinspection ConstantConditions
    requiredHistoryCache = CacheBuilder.newBuilder().maximumSize(1024L).build(
      CacheLoader.from(key -> History.create(tree.getRequiredHistory(key))));

    ProductState<Object> initialProduct = treeConverter.buildInitialState();
    State<Object> initialState = new State<>(initialProduct,
      getHistory(null, new BitSet(), initialProduct));

    EmersonLeiAcceptance acceptance = new EmersonLeiAcceptance(sets, expression);
    return AutomatonFactory.create(factories.vsFactory, initialState, acceptance,
      (x, y) -> this.edge(tree, x, y));
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