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

import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.OPTIMISE_INITIAL_STATE;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
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
import owl.translations.ltl2dra.LTL2DRAFunction;

public class DelagBuilder<T> implements Function<LabelledFormula, Automaton<State<T>, ?>> {
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final Function<LabelledFormula, ? extends Automaton<?, ?>> FAIL = formula -> {
    throw new IllegalArgumentException("Not supported: " + formula);
  };

  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("delag")
    .description("Translates LTL to deterministic Emerson-Lei automata")
    .optionsDirect(new Options().addOption("f", "fallback", true,
      "Fallback tool for input outside the fragment ('none' for strict mode)"))
    .parser(settings -> environment -> {
      String fallbackTool = settings.getOptionValue("fallback");

      if (fallbackTool == null) {
        return Transformers.instanceFromFunction(LabelledFormula.class,
          new DelagBuilder(environment));
      }

      Function<LabelledFormula, ? extends Automaton<?, ?>> fallback = "none".equals(fallbackTool)
        ? FAIL
        : new ExternalTranslator(environment, fallbackTool);

      return Transformers.instanceFromFunction(LabelledFormula.class,
        new DelagBuilder(environment, fallback));
    }).build();

  private final Environment env;
  private final Function<LabelledFormula, ? extends Automaton<T, ?>> fallback;
  @Nullable
  private LoadingCache<ProductState<T>, History> requiredHistoryCache = null;

  public DelagBuilder(Environment environment) {
    this(environment, (Function) new LTL2DRAFunction(environment,
      EnumSet.of(OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, EXISTS_SAFETY_CORE)));
  }

  public DelagBuilder(Environment environment,
    Function<LabelledFormula, ? extends Automaton<T, ?>> fallback) {
    this.env = environment;
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
  public Automaton<State<T>, ?> apply(LabelledFormula inputFormula) {
    LabelledFormula formula = SyntacticFragments.normalize(inputFormula, SyntacticFragment.NNF);
    Factories factories = env.factorySupplier().getFactories(formula.variables());

    if (formula.formula().equals(BooleanConstant.FALSE)) {
      return AutomatonFactory.empty(factories.vsFactory);
    }

    if (formula.formula().equals(BooleanConstant.TRUE)) {
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

    EmersonLeiAcceptance acceptance = new EmersonLeiAcceptance(sets, expression);
    return AutomatonFactory.create(initialState, factories.vsFactory,
      (x, y) -> this.getSuccessor(tree, x, y), acceptance
    );
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