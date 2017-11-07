package owl.translations.ltl2ldba;

import java.util.EnumSet;
import java.util.function.Function;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.output.HoaPrintable;
import owl.cli.ModuleSettings.TransformerSettings;
import owl.cli.parser.ImmutableSingleModuleConfiguration;
import owl.cli.parser.SimpleModuleParser;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.rewriter.RewriterTransformer;
import owl.run.input.LtlInput;
import owl.run.meta.ToHoa;
import owl.run.transformer.Transformer.Factory;
import owl.run.transformer.Transformers;
import owl.translations.Optimisation;

public class LTL2LDBAModule implements TransformerSettings {
  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .inputParser(new LtlInput())
      .addPreProcessors(new RewriterTransformer(RewriterEnum.MODAL_ITERATIVE))
      .transformer(new LTL2LDBAModule())
      .outputWriter(new ToHoa())
      .build());
  }

  @Override
  public Factory parseTransformerSettings(CommandLine settings) throws ParseException {
    boolean generalized = settings.hasOption("generalized");
    boolean breakpoint = settings.hasOption("breakpoint");
    boolean deterministic = settings.hasOption("deterministic");
    boolean removeEpsilon = settings.hasOption("noepsislon");

    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    if (!deterministic) {
      optimisations.remove(Optimisation.DETERMINISTIC_INITIAL_COMPONENT);
    }
    if (!removeEpsilon) {
      optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    }

    return environment -> {
      Function<LabelledFormula, ? extends HoaPrintable> translator;
      if (generalized) {
        translator = breakpoint
          ? LTL2LDBAFunction.createGeneralizedBreakpointLDBABuilder(environment, optimisations)
          : LTL2LDBAFunction.createGeneralizedBreakpointFreeLDBABuilder(environment, optimisations);
      } else {
        translator = breakpoint
          ? LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(environment, optimisations)
          : LTL2LDBAFunction
            .createDegeneralizedBreakpointFreeLDBABuilder(environment, optimisations);
      }

      return Transformers.fromFunction(LabelledFormula.class, translator);
    };
  }

  @Override
  public String getKey() {
    return "ltl2ldba";
  }

  @Override
  public Options getOptions() {
    return new Options()
      .addOption("gen", "generalized", false, "Produce generalized Büchi acceptance")
      .addOption("det", "deterministic", false, "Produce deterministic initial component")
      .addOption("bp", "breakpoint", false, "Use breakpoint construction")
      .addOption("ne", "noepsislon", false, "Remove epsilon transitions");
  }
}
