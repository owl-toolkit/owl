/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.command;

import static owl.automaton.acceptance.OmegaAcceptanceCast.cast;
import static owl.automaton.acceptance.OmegaAcceptanceCast.isInstanceOf;
import static owl.command.Mixins.AcceptanceSimplifier;
import static owl.command.Mixins.AutomatonReader;
import static owl.command.Mixins.Diagnostics;
import static owl.command.Mixins.Verifier;
import static owl.thirdparty.picocli.CommandLine.Command;
import static owl.thirdparty.picocli.CommandLine.Mixin;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import owl.Bibliography;
import owl.automaton.Automaton;
import owl.automaton.BooleanOperations;
import owl.automaton.ParityUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations;
import owl.automaton.algorithm.simulations.BuchiSimulation;
import owl.automaton.minimization.GfgCoBuchiMinimization;
import owl.command.Mixins.AutomatonWriter;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerException;
import owl.thirdparty.picocli.CommandLine;
import owl.thirdparty.picocli.CommandLine.Option;
import owl.translations.nba2ldba.NBA2LDBA;
import owl.translations.nbadet.NbaDet;
import owl.translations.nbadet.NbaDetConf;
import owl.translations.nbadet.NbaLangInclusions;

@SuppressWarnings("PMD.ImmutableField")
public class AutomatonConversionCommands {

  private abstract static class AbstractAutomaton2AutomatonCommand
    <A extends EmersonLeiAcceptance, B extends EmersonLeiAcceptance> extends AbstractOwlSubcommand {

    @Mixin
    private AutomatonReader automatonReader = null;

    @Mixin
    private AutomatonWriter automatonWriter = null;

    @Mixin
    private AcceptanceSimplifier acceptanceSimplifier = null;

    @Mixin
    private Diagnostics diagnostics = null;

    @Override
    protected int run() throws IOException, HOAConsumerException {
      var conversion = conversion();

      String subcommand = getClass().getAnnotation(Command.class).name();
      int counter = 0;

      try (var source = automatonReader.source(acceptanceClass());
           var sink = automatonWriter.sink(subcommand, rawArgs())) {

        var automatonIterator = source.iterator();

        while (automatonIterator.hasNext()) {
          var automaton1 = automatonIterator.next();

          if (!acceptanceSimplifier.skipAcceptanceSimplifier) {
            automaton1 = AcceptanceOptimizations.transform(automaton1);
          }

          diagnostics.start(String.format("%s (%s)", subcommand, rawArgs()), automaton1);
          var automaton2 = conversion.apply(automaton1);
          diagnostics.finish(automaton2);

          if (allowSimplifierOnOutput() && !acceptanceSimplifier.skipAcceptanceSimplifier) {
            automaton2 = AcceptanceOptimizations.transform(automaton2);
          }

          sink.accept(automaton2, String.format("Converted Automaton (index: %d)", counter));
          counter++;
        }
      }

      return 0;
    }

    protected abstract Class<A> acceptanceClass();

    protected abstract
      Function<? super Automaton<?, ? extends A>, ? extends Automaton<?, ? extends B>> conversion();

    protected boolean allowSimplifierOnOutput() {
      return true;
    }
  }

  @Command(
    name = "aut2parity",
    description = {
      "Convert any type of automaton into a parity automaton. The branching mode of the automaton "
        + "is preserved, e.g., if the input automaton is deterministic then the output automaton "
        + "is also deterministic.",
      "Usage Examples: ",
      "  owl aut2parity -i input-file -o output-file",
      "  owl ltl2dela -f 'F (a & G b) & G F c' | owl aut2parity",
      "The construction is described in [" + Bibliography.UNDER_SUBMISSION_22_CITEKEY + "] and is "
        + "based on [" + Bibliography.ICALP_21_CITEKEY + "]. "
        + MiscCommands.BibliographyCommand.HOW_TO_USE
    }
  )
  static final class Aut2ParityCommand
    extends AbstractAutomaton2AutomatonCommand<EmersonLeiAcceptance, ParityAcceptance> {

    @Override
    protected Class<EmersonLeiAcceptance> acceptanceClass() {
      return EmersonLeiAcceptance.class;
    }

    @Override
    protected Function<Automaton<?, ?>, Automaton<?, ? extends ParityAcceptance>> conversion() {
      return Aut2ParityCommand::convert;
    }

    private static Automaton<?, ? extends ParityAcceptance> convert(Automaton<?, ?> automaton) {
      if (isInstanceOf(automaton.acceptance().getClass(), ParityAcceptance.class)) {
        return cast(automaton, ParityAcceptance.class);
      }

      return ZielonkaTreeTransformations.transform(automaton);
    }
  }

  @Command(
    name = "gfg-minimisation",
    description = "Compute the minimal, equivalent, transition-based Good-for-Games Co-Büchi "
      + "automaton for the given deterministic Co-Büchi automaton. The polynomial construction is "
      + "described in [" + Bibliography.ICALP_19_2_CITEKEY + "]."
  )
  static final class GfgMinimisation
    extends AbstractAutomaton2AutomatonCommand<CoBuchiAcceptance, CoBuchiAcceptance> {

    @Override
    protected Class<CoBuchiAcceptance> acceptanceClass() {
      return CoBuchiAcceptance.class;
    }

    @Override
    protected Function<Automaton<?, ? extends CoBuchiAcceptance>,
      Automaton<?, ? extends CoBuchiAcceptance>> conversion() {

      return GfgCoBuchiMinimization::minimize;
    }
  }

  @Command(
    name = "aut-utilities",
    description = {
      "A collection of various automata related utilities.",
    }
  )
  static final class AutUtilities
    extends AbstractAutomaton2AutomatonCommand<EmersonLeiAcceptance, EmersonLeiAcceptance> {

    @CommandLine.ArgGroup
    private Action action = null;

    private static class Action {

      @Option(
        names = "--degeneralize",
        description = {
          "Degeneralize a generalized Büchi or Rabin automata into Büchi or Rabin automaton, "
            + "respectively. If the input automaton is deterministic, so is the output."
        }
      )
      private boolean degeneralise = false;

      @Option(
        names = "--deterministic-complement",
        description = "Complement a deterministic automaton by negating the acceptance condition."
      )
      private boolean deterministicComplement = false;

      @Option(
        names = "--convert-parity-condition",
        description = "Convert the parity condition of an automaton to another type of parity "
          + "condition. Possible values are: ${COMPLETION-CANDIDATES}.",
        hidden = true
      )
      private ParityAcceptance.Parity parity = null;

    }

    @Override
    protected Class<EmersonLeiAcceptance> acceptanceClass() {
      return EmersonLeiAcceptance.class;
    }

    @Override
    protected Function<Automaton<?, ? extends EmersonLeiAcceptance>, Automaton<?, ?
      extends EmersonLeiAcceptance>> conversion() {

      return this::convert;
    }

    private Automaton<?, ?> convert(Automaton<?, ?> automaton) {
      if (action == null) {
        return automaton;
      }

      var acceptance = automaton.acceptance();

      if (action.degeneralise) {
        if (acceptance instanceof GeneralizedBuchiAcceptance
          && !(acceptance instanceof BuchiAcceptance)) {

          return owl.automaton.acceptance.degeneralization.BuchiDegeneralization
            .degeneralize(cast(automaton, GeneralizedBuchiAcceptance.class));
        }

        if (acceptance instanceof GeneralizedRabinAcceptance
          && !(acceptance instanceof RabinAcceptance)) {

          return owl.automaton.acceptance.degeneralization.RabinDegeneralization
            .degeneralize(cast(automaton, GeneralizedRabinAcceptance.class));
        }
      } else if (action.deterministicComplement) {
        return BooleanOperations.deterministicComplement(automaton);
      } else if (action.parity != null) {
        return ParityUtil.convert(
          cast(Views.complete(automaton), ParityAcceptance.class), action.parity);
      }

      return automaton;
    }
  }

  @Command(
    name = "ngba2ldba",
    description = {
      "Convert a non-deterministic (generalised) Büchi automaton to a limit-deterministic Büchi "
        + "automaton.",
      "The construction is a generalisation of the construction described in ["
        + Bibliography.JACM_95_CITEKEY + "]. " + MiscCommands.BibliographyCommand.HOW_TO_USE
    }
  )
  static final class Ngba2LdbaCommand
    extends AbstractAutomaton2AutomatonCommand<GeneralizedBuchiAcceptance, BuchiAcceptance> {

    @Override
    protected Class<GeneralizedBuchiAcceptance> acceptanceClass() {
      return GeneralizedBuchiAcceptance.class;
    }

    @Override
    public Function<Automaton<?, ? extends GeneralizedBuchiAcceptance>,
      Automaton<?, ? extends BuchiAcceptance>> conversion() {

      return automaton -> NBA2LDBA.applyLDBA(automaton).automaton();
    }
  }

  @Command(
    name = "nbasim",
    description = "Computes the quotient automaton based on a computed set of similar state pairs."
  )
  @SuppressWarnings("PMD.DataClass")
  public static final class NbaSimCommand extends
    AutomatonConversionCommands
      .AbstractAutomaton2AutomatonCommand<BuchiAcceptance, BuchiAcceptance> {

    @Option(
      names = {"-s", "--simulation"},
      description = {"By default ${DEFAULT-VALUE} is selected. The following simulation relations "
        + "are available: ${COMPLETION-CANDIDATES}.",
        "DIRECT_SIMULATION: direct simulation relation",
        "DIRECT_SIMULATION_COLOUR_REFINEMENT: "
          + "direct simulation relation using color refinement (fast)",
        "DELAYED_SIMULATION: delayed simulation relation",
        "FAIR_SIMULATION: fair simulation relation",
        "BACKWARD_SIMULATION: backwards simulation relation",
        "LOOKAHEAD_DIRECT_SIMULATION: direct simulation with lookahead"
      },
      defaultValue = "DIRECT_SIMULATION"
    )
    private BuchiSimulation.SimulationType simulationType
      = BuchiSimulation.SimulationType.DIRECT_SIMULATION;

    @Option(
      names = {"-c", "--pebbles"},
      description = "Allow duplicator to have the set amount of pebbles"
    )
    private int pebbleCount = 1;

    @Option(
      names = {"-l", "--lookahead"},
      description = "Make this many moves of Spoiler available to Duplicator."
    )
    private int lookahead = 1;

    @Mixin
    private Verifier verifier = null;

    @Option(
      names = {"--verbose"},
      description = "Use logging level FINE for more output"
    )
    private boolean verboseFine = false;

    public BuchiSimulation.SimulationType simulationType() {
      return simulationType;
    }

    public int pebbleCount() {
      return pebbleCount;
    }

    public int maxLookahead() {
      return lookahead;
    }

    public boolean sanity() {
      return verifier != null && verifier.verify;
    }

    public boolean verboseFine() {
      return verboseFine;
    }

    @Override
    protected Class<BuchiAcceptance> acceptanceClass() {
      return BuchiAcceptance.class;
    }

    @Override
    protected Function<Automaton<?, ? extends BuchiAcceptance>,
      Automaton<?, ? extends BuchiAcceptance>> conversion() {

      return aut -> BuchiSimulation.compute((Automaton) aut, this);
    }

    @Override
    protected boolean allowSimplifierOnOutput() {
      return false;
    }

    @Override
    public String toString() {
      return String.format(
        "NbaSimCommand{simulationType=%s, pebbleCount=%d, lookahead=%d, sanity=%s, verboseFine=%s}",
        simulationType, pebbleCount, lookahead, sanity(), verboseFine);
    }
  }

  @Command(
    name = "nba2dpa",
    aliases = "nbadet",
    description = {
      "Convert a non-deterministic Büchi automaton to a deterministic parity automaton.",
      "Usage Examples: ",
      "  owl nba2dpa -i input-file -o output-file",
      "  owl ltl2nba -f 'F (a & G b)' | owl nba2dpa -m MUELLER_SCHUPP",
      "The construction and the optimisations are described in [" + Bibliography.ICALP_19_1_CITEKEY
        + "] and [" + Bibliography.ATVA_19_CITEKEY + "], respectively. "
        + MiscCommands.BibliographyCommand.HOW_TO_USE
    },
    showDefaultValues = true
  )
  @SuppressWarnings("PMD.DataClass")
  public static final class Nba2DpaCommand extends
    AbstractAutomaton2AutomatonCommand<BuchiAcceptance, ParityAcceptance> {

    private static final String DEFAULT_TRUE = "By default this option is enabled.";
    private static final String DEFAULT_FALSE = "By default this option is disabled.";

    @Option(
      names = {"-m", "--merge-mode"},
      description = "Which merge method to use in construction (${COMPLETION-CANDIDATES}).",
      defaultValue = "SAFRA"
    )
    private NbaDetConf.UpdateMode mergeMode = NbaDetConf.UpdateMode.SAFRA;

    @Option(
      names = {"--verbosity"},
      description = "Set verbosity level (e.g. INFO, WARNING, FINE, FINER, FINEST)."
    )
    private String verbosity = Level.WARNING.toString();

    @Option(
      names = {"-l", "--compute-lang-inclusions"},
      description =
        "List of algorithms to use on NBA to obtain language inclusions. Possible values: "
          + "${COMPLETION-CANDIDATES}.",
      defaultValue = "DIRECT_REFINEMENT_SIM"
    )
    private NbaLangInclusions.SimType[] computeSims = null;

    @Option(
      names = {"-e", "--use-sim-external"},
      description = "Use results of simulation calculation for preprocessing and optimization. "
        + "This optimisation is broken and might produce wrong results. "
        + DEFAULT_FALSE,
      negatable = true,
      defaultValue = "false",
      hidden = true
    )
    private boolean simExt = false; // Deactivate.

    @Option(
      names = {"-j", "--use-sim-internal"},
      description = "Use results of simulation calculation to prune the deterministic states. "
        + DEFAULT_TRUE,
      negatable = true
    )
    private boolean simInt = true;

    @Option(
      names = {"-t", "--use-powersets"},
      description = "Use powerset structure of NBA to guide determinization. "
        + DEFAULT_TRUE,
      negatable = true
    )
    private boolean usePowersets = true;

    @Option(
      names = {"-s", "--use-smart-succ"},
      description = "Try to redirect edges to suitable already existing states on-the-fly. "
        + DEFAULT_TRUE,
      negatable = true
    )
    private boolean useSmartSucc = true;

    @Option(
      names = {"-r", "--sep-rej"},
      description = "Separate simplified handling for states in rejecting SCCs. "
        + DEFAULT_TRUE,
      negatable = true
    )
    private boolean sepRej = true;

    @Option(
      names = {"-A", "--sep-acc"},
      description = "Separate simplified handling for states in accepting SCCs. "
        + DEFAULT_FALSE,
      negatable = true
    )
    private boolean sepAcc = false; // <- good-ish on rnd. NBA, but much worse on rnd. LTL

    @Option(
      names = {"-b", "--sep-acc-cycle"},
      description = "Cycle breakpoint construction for accepting SCCs. "
        + DEFAULT_FALSE,
      negatable = true
    )
    private boolean sepAccCyc = false;  // <- usually better variant of previous option

    @Option(
      names = {"-d", "--sep-det"},
      description = "Separate simplified handling for deterministic SCCs. "
        + DEFAULT_TRUE,
      negatable = true
    )
    private boolean sepDet = true;

    @Option(
      names = {"-c", "--sep-sccs"},
      description = "Separate handling of all SCCs (that are not already specially handled). "
        + DEFAULT_TRUE,
      negatable = true
    )
    private boolean sepMix = true;

    public Nba2DpaCommand() {
      // default constructor for picocli.
    }

    public Nba2DpaCommand(Nba2DpaCommand that, NbaDetConf.UpdateMode mergeMode) {
      this.mergeMode = mergeMode;
      this.verbosity = that.verbosity();
      this.computeSims = that.computeSims().toArray(NbaLangInclusions.SimType[]::new);
      this.simExt = that.simExt();
      this.simInt = that.simInt();
      this.usePowersets = that.usePowersets();
      this.useSmartSucc = that.useSmartSucc();
      this.sepRej = that.sepRej();
      this.sepAcc = that.sepAcc();
      this.sepAccCyc = that.sepAccCyc();
      this.sepDet = that.sepDet();
      this.sepMix = that.sepMix();
    }

    @Override
    protected Class<BuchiAcceptance> acceptanceClass() {
      return BuchiAcceptance.class;
    }

    @Override
    protected Function<Automaton<?, ? extends BuchiAcceptance>,
      Automaton<?, ? extends ParityAcceptance>> conversion() {

      return automaton -> NbaDet.determinize(automaton, this);
    }

    public NbaDetConf.UpdateMode mergeMode() {
      return mergeMode;
    }

    public String verbosity() {
      return verbosity;
    }

    public List<NbaLangInclusions.SimType> computeSims() {
      return List.of(computeSims);
    }

    public boolean simExt() {
      return simExt;
    }

    public boolean simInt() {
      return simInt;
    }

    public boolean usePowersets() {
      return usePowersets;
    }

    public boolean useSmartSucc() {
      return useSmartSucc;
    }

    public boolean sepRej() {
      return sepRej;
    }

    public boolean sepAcc() {
      return sepAcc;
    }

    public boolean sepAccCyc() {
      return sepAccCyc;
    }

    public boolean sepDet() {
      return sepDet;
    }

    public boolean sepMix() {
      return sepMix;
    }

    @Override
    public String toString() {
      return String.format(
        "Nba2DpaCommand{mergeMode=%s, verbosity='%s', computeSims=%s, simExt=%s, simInt=%s, "
          + "usePowersets=%s, useSmartSucc=%s, sepRej=%s, sepAcc=%s, sepAccCyc=%s, sepDet=%s, "
          + "sepMix=%s}",
        mergeMode, verbosity, Arrays.toString(computeSims), simExt, simInt, usePowersets,
        useSmartSucc, sepRej, sepAcc, sepAccCyc, sepDet, sepMix);
    }
  }
}
