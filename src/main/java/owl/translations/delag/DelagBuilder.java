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

package owl.translations.delag;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.GenericAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.run.modules.ImmutableTransformerSettings;
import owl.run.modules.InputReaders;
import owl.run.modules.ModuleSettings.TransformerSettings;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ExternalTranslator;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public class DelagBuilder<T> implements Function<LabelledFormula, Automaton<State<T>, ?>> {
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static final TransformerSettings SETTINGS = ImmutableTransformerSettings.builder()
    .key("delag")
    .optionsDirect(new Options().addOption("f", "fallback", true,
      "Fallback tool for input outside the fragment ('none' for strict mode)"))
    .transformerSettingsParser(settings -> environment -> {
      String fallbackTool = settings.getOptionValue("fallback");

      Function<LabelledFormula, ? extends Automaton<?, ?>> fallback;
      if ("none".equals(fallbackTool)) {
        fallback = formula -> {
          throw new IllegalArgumentException("Formula " + formula
            + " outside of supported fragment");
        };
      } else {

        fallback = fallbackTool == null
          ? new LTL2DPAFunction(environment, LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG)
          : new ExternalTranslator(environment, fallbackTool);
      }

      return Transformers.instanceFromFunction(LabelledFormula.class,
        new DelagBuilder(environment, fallback));
    }).build();

  private final Environment env;
  private final Function<LabelledFormula, ? extends Automaton<T, ?>> fallback;
  @Nullable
  private LoadingCache<ProductState<T>, History> requiredHistoryCache = null;

  public DelagBuilder(Environment env,
    Function<LabelledFormula, ? extends Automaton<T, ?>> fallback) {
    this.env = env;
    this.fallback = fallback;
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("delag")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.SIMPLIFY_MODAL_ITER)
      .addTransformer(SETTINGS)
      .writer(OutputWriters.ToHoa.DEFAULT)
      .build());
  }

  @Override
  public Automaton<State<T>, ?> apply(LabelledFormula formula) {
    Factories factories = env.factorySupplier().getFactories(formula);

    if (formula.formula.equals(BooleanConstant.FALSE)) {
      return AutomatonFactory.empty(factories.vsFactory);
    }

    if (formula.formula.equals(BooleanConstant.TRUE)) {
      return AutomatonFactory.singleton(new State<>(), factories.vsFactory, AllAcceptance.INSTANCE,
        Set.of());
    }

    DependencyTreeFactory<T> treeConverter = new DependencyTreeFactory<>(factories, fallback);
    DependencyTree<T> tree = formula.accept(treeConverter);
    BooleanExpression<AtomAcceptance> expression = tree.getAcceptanceExpression();
    int sets = treeConverter.setNumber;

    //noinspection ConstantConditions
    requiredHistoryCache = CacheBuilder.newBuilder().maximumSize(1024L).build(
      CacheLoader.from(key -> History.create(tree.getRequiredHistory(key))));

    ProductState<T> initialProduct = treeConverter.buildInitialState();
    State<T> initialState = new State<>(initialProduct,
      getHistory(null, new BitSet(), initialProduct));

    GenericAcceptance acceptance = new GenericAcceptance(sets, expression);
    return AutomatonFactory.createStreamingAutomaton(acceptance, initialState,
      factories.vsFactory, (x, y) -> this.getSuccessor(tree, x, y));
  }

  private History getHistory(@Nullable History past, BitSet present, ProductState<T> state) {
    assert requiredHistoryCache != null;
    History requiredHistory = requiredHistoryCache.getUnchecked(state);
    return History.stepHistory(past, present, requiredHistory);
  }

  @Nullable
  private Edge<State<T>> getSuccessor(DependencyTree<T> tree, State<T> state, BitSet valuation) {
    ProductState.Builder<T> builder = ProductState.builder();
    Boolean acc = tree.buildSuccessor(state, valuation, builder);

    if (acc != null && !acc) {
      return null;
    }

    ProductState<T> successor = builder.build();
    History history = getHistory(state.past, valuation, successor);
    return Edge.of(new State<>(successor, history), tree.getAcceptance(state, valuation, acc));
  }
}