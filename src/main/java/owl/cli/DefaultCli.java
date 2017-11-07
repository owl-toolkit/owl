package owl.cli;

import owl.arena.ArenaFactory;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.transformations.RabinDegeneralization;
import owl.cli.env.DefaultEnvironmentSettings;
import owl.cli.parser.CliParser;
import owl.ltl.rewriter.RewriterTransformer;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.run.coordinator.Coordinator;
import owl.run.coordinator.ServerCoordinator;
import owl.run.coordinator.SingleStreamCoordinator;
import owl.run.input.HoaInput;
import owl.run.input.LtlInput;
import owl.run.input.TlsfInput;
import owl.run.meta.AutomatonStats;
import owl.run.meta.ToHoa;
import owl.run.meta.ToString;
import owl.run.output.NullOutput;
import owl.translations.ExternalTranslator;
import owl.translations.LTL2DA;
import owl.translations.delag.DelagBuilder;
import owl.translations.dra2dpa.IARBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2ldba.LTL2LDBAModule;
import owl.translations.nba2dpa.NBA2DPAModule;
import owl.translations.nba2ldba.NBA2LDBAModule;
import owl.translations.rabinizer.RabinizerModule;

public final class DefaultCli {
  /**
   * A preconfigured {@link CommandLineRegistry registry}, holding commonly used utility modules.
   */
  public static final CommandLineRegistry defaultRegistry;

  static {
    defaultRegistry = new CommandLineRegistry(new DefaultEnvironmentSettings());
    // Coordinators
    defaultRegistry.register(SingleStreamCoordinator.settings, ServerCoordinator.settings);

    // Inputs
    defaultRegistry.register(LtlInput.settings, HoaInput.settings, TlsfInput.settings);

    // Transformer
    defaultRegistry.register(RewriterTransformer.settings, ImplicitMinimizeTransformer.settings,
      RabinDegeneralization.settings, UnabbreviateVisitor.settings, ArenaFactory.settings);

    // Outputs
    defaultRegistry.register(ToString.settings, AutomatonStats.settings, NullOutput.settings,
      ToHoa.settings);
  }

  private DefaultCli() {}

  public static void main(String... args) {
    defaultRegistry.register(new RabinizerModule(), IARBuilder.settings, DelagBuilder.settings,
      new LTL2LDBAModule(), LTL2DA.settings, LTL2DPAFunction.settings, new NBA2DPAModule(),
      new NBA2LDBAModule(), ExternalTranslator.settings);

    Coordinator coordinator = CliParser.parse(args, defaultRegistry);
    if (coordinator == null) {
      return;
    }
    coordinator.run();
  }
}
