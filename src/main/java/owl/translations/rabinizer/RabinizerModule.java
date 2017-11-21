package owl.translations.rabinizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.factories.Factories;
import owl.ltl.LabelledFormula;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.Transformer;
import owl.run.Transformers;
import owl.run.env.Environment;

public class RabinizerModule implements TransformerSettings {
  @Override
  public Transformer create(CommandLine settings, Environment environment)
    throws ParseException {
    boolean eager = !settings.hasOption("noeager");
    boolean support = !settings.hasOption("nosupport");
    boolean acceptance = !settings.hasOption("noacceptance");
    boolean complete = settings.hasOption("complete");
    boolean suspend = !settings.hasOption("nosuspend");
    ImmutableRabinizerConfiguration configuration = ImmutableRabinizerConfiguration.builder()
      .eager(eager)
      .supportBasedRelevantFormulaAnalysis(support)
      .computeAcceptance(acceptance)
      .completeAutomaton(complete)
      .suspendableFormulaDetection(suspend)
      .build();

    return Transformers.fromFunction(LabelledFormula.class, formula -> {
      Factories factories = environment.factorySupplier().getFactories(formula);
      return RabinizerBuilder.rabinize(formula.formula, factories, configuration, environment);
    });
  }

  @Override
  public String getKey() {
    return "ltl2dgra";
  }

  @Override
  public Options getOptions() {
    return new Options()
      .addOption("ne", "noeager", false, "Disable eager construction")
      .addOption("np", "nosuspend", false, "Disable suspension detection")
      .addOption("ns", "nosupport", false, "Disable support based relevant formula analysis")
      .addOption("na", "noacceptance", false, "Disable generation of acceptance condition")
      .addOption("c", "complete", false, "Build complete automaton");
  }
}
