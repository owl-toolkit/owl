package owl.translations.frequency;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.EnumSet;
import java.util.Set;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import org.apache.commons.cli.CommandLine;
import owl.automaton.output.HoaPrinter.HoaOption;
import owl.factories.Factories;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragment;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.visitors.Converter;
import owl.run.modules.InputReaders;
import owl.run.modules.OwlModuleParser;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class RabinizerFrequencyMain implements OwlModuleParser.TransformerParser {
  private static final UnabbreviateVisitor UNABBREVIATE_VISITOR = new UnabbreviateVisitor();

  private RabinizerFrequencyMain() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("fltl2dgmra")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.fromFunction(LabelledFormula.class,
        l -> l.wrap(l.formula().nnf().accept(UNABBREVIATE_VISITOR))))
      .addTransformer(new RabinizerFrequencyMain())
      .writer((writer, environment) -> {
        HOAConsumer consumer = new HOAConsumerPrint(writer);
        EnumSet<HoaOption> options = EnumSet.noneOf(HoaOption.class);

        if (environment.annotations()) {
          options.add(HoaOption.ANNOTATIONS);
        }

        return input -> {
          Preconditions.checkArgument(input instanceof Automaton);
          Automaton<?, ?> automaton = (Automaton<?, ?>) input;
          automaton.toHoa(consumer, options);
        };
      }).build());
  }

  @Override
  public String getKey() {
    return "fltl2dgrma";
  }

  @Override
  public Transformer parse(CommandLine commandLine) {
    return environment ->
      Transformers.instanceFromFunction(LabelledFormula.class, (input) -> {
        Factories factories = environment.factorySupplier().getFactories(input.variables(), true);
        return new DtgrmaFactory(input.formula(), factories,
          EnumSet.allOf(Optimisation.class)).constructAutomaton();
      });
  }

  private static final class UnabbreviateVisitor extends Converter {
    public UnabbreviateVisitor() {
      super(Set.copyOf(Sets.union(SyntacticFragment.NNF.classes(), Set.of(FrequencyG.class))));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      Formula left = rOperator.left.accept(this);
      Formula right = rOperator.right.accept(this);

      return Disjunction.of(GOperator.of(right), UOperator.of(right, Conjunction.of(left, right)));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      Formula left = wOperator.left.accept(this);
      Formula right = wOperator.right.accept(this);

      return Disjunction.of(GOperator.of(left), UOperator.of(left, right));
    }

    @Override
    public Formula visit(MOperator mOperator) {
      Formula left = mOperator.left.accept(this);
      Formula right = mOperator.right.accept(this);

      return UOperator.of(right, Conjunction.of(left, right));
    }
  }
}
