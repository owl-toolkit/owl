package owl.translations.rabinizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.factories.Factories;
import owl.ltl.LabelledFormula;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;

public final class RabinizerCliParser implements TransformerParser {
  public static final RabinizerCliParser INSTANCE = new RabinizerCliParser();

  private RabinizerCliParser() {}

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
  public Transformer parse(CommandLine commandLine) {
    boolean eager = !commandLine.hasOption("noeager");
    boolean support = !commandLine.hasOption("nosupport");
    boolean acceptance = !commandLine.hasOption("noacceptance");
    boolean complete = commandLine.hasOption("complete");
    boolean suspend = !commandLine.hasOption("nosuspend");
    ImmutableRabinizerConfiguration configuration = ImmutableRabinizerConfiguration.builder()
      .eager(eager)
      .supportBasedRelevantFormulaAnalysis(support)
      .computeAcceptance(acceptance)
      .completeAutomaton(complete)
      .suspendableFormulaDetection(suspend)
      .build();

    return environment -> Transformers.instanceFromFunction(LabelledFormula.class, formula -> {
      Factories factories = environment.factorySupplier().getFactories(formula);
      return RabinizerBuilder.rabinize(formula.formula(), factories, configuration, environment);
    });
  }
}
