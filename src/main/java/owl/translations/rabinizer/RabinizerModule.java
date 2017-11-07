package owl.translations.rabinizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.cli.ModuleSettings.TransformerSettings;
import owl.factories.Factories;
import owl.ltl.LabelledFormula;
import owl.run.transformer.Transformer;
import owl.run.transformer.Transformers;

public class RabinizerModule implements TransformerSettings {
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

  @Override
  public Transformer.Factory parseTransformerSettings(CommandLine settings)
    throws ParseException {
    boolean eager = !settings.hasOption("noneager");
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

    return environment -> Transformers.fromFunction(LabelledFormula.class, formula -> {
      Factories factories = environment.factorySupplier().getFactories(formula);
      return RabinizerBuilder.rabinize(formula.formula, factories, configuration, environment);
    });
  }
}
