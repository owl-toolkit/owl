package owl.translations.ltl2ldba;

import java.util.EnumSet;
import java.util.function.Function;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.output.HoaPrintable;
import owl.ltl.LabelledFormula;
import owl.run.InputReaders;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.OutputWriters;
import owl.run.Transformer;
import owl.run.Transformers;
import owl.run.env.Environment;
import owl.run.parser.ImmutableSingleModuleConfiguration;
import owl.run.parser.SimpleModuleParser;
import owl.translations.Optimisation;

public class LTL2LDBAModule implements TransformerSettings {
  public static void main(String... args) {
    SimpleModuleParser.run(args, ImmutableSingleModuleConfiguration.builder()
      .readerModule(InputReaders.LTL)
      .addPreProcessors(Transformers.SIMPLIFY_MODAL_ITER)
      .transformer(new LTL2LDBAModule())
      .writerModule(OutputWriters.HOA)
      .build());
  }

  @Override
  public Transformer create(CommandLine settings, Environment environment)
    throws ParseException {
    boolean generalized = settings.hasOption("generalized");
    boolean breakpoint = settings.hasOption("breakpoint");
    boolean deterministic = settings.hasOption("deterministic");
    boolean removeEpsilon = settings.hasOption("noepsilon");

    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);

    if (!deterministic) {
      optimisations.remove(Optimisation.DETERMINISTIC_INITIAL_COMPONENT);
    }

    if (!removeEpsilon) {
      optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);
    }

    Function<LabelledFormula, ? extends HoaPrintable> translator;
    if (generalized) {
      translator = breakpoint
                   ? LTL2LDBAFunction
                     .createGeneralizedBreakpointLDBABuilder(environment, optimisations)
                   : LTL2LDBAFunction
                     .createGeneralizedBreakpointFreeLDBABuilder(environment, optimisations);
    } else {
      translator = breakpoint
                   ? LTL2LDBAFunction
                     .createDegeneralizedBreakpointLDBABuilder(environment, optimisations)
                   : LTL2LDBAFunction
                     .createDegeneralizedBreakpointFreeLDBABuilder(environment, optimisations);
    }

    return Transformers.fromFunction(LabelledFormula.class, translator);
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
      .addOption("ne", "noepsilon", false, "Remove epsilon transitions");
  }
}
