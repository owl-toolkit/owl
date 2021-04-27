package owl.translations.modules;

import static owl.automaton.symbolic.SymbolicDPASolver.Solution.Winner.CONTROLLER;
import static owl.run.modules.OwlModule.Transformer;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.symbolic.AigerWriter;
import owl.automaton.symbolic.DFISymbolicDPASolver;
import owl.automaton.symbolic.NaiveStrategyDeterminizer;
import owl.collections.ImmutableBitSet;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dpa.SymbolicDPAConstruction;

public final class LTL2AIGModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2aig",
    "Synthesizes an And-Inverter graph out of an LTL formula, if possible",
    () -> {
      Option controllableAPs = new Option("c", "controllable", true,
        "List of atomic propositions controlled by the system");
      controllableAPs.setArgs(Option.UNLIMITED_VALUES);
      controllableAPs.setRequired(true);
      controllableAPs.setValueSeparator(',');
      return new Options().addOption(controllableAPs);
    },
    (commandLine, environment) ->
      OwlModule.LabelledFormulaTransformer.of((formula) -> {
        String[] controllableAPs = commandLine.getOptionValues("controllable");
        BitSet controlledAPs = getControlledAPsBitset(formula, controllableAPs);
        var dpa = new SymbolicDPAConstruction().apply(formula);
        var solution = new DFISymbolicDPASolver().solve(
          dpa,
          ImmutableBitSet.copyOf(controlledAPs)
        );
        if (solution.winner() == CONTROLLER) {
          return "REALIZABLE\n" + AigerWriter.toAiger(
            new NaiveStrategyDeterminizer().determinize(
              dpa, controlledAPs, solution.strategy()),
            dpa.variableAllocation(),
            controlledAPs,
            formula.atomicPropositions(),
            dpa.variableAllocation().globalToLocal(dpa.initialStates().element().orElseThrow())
              .copyInto(new BitSet())
          );
        } else {
          return "UNREALIZABLE";
        }
      })
  );

  private static BitSet getControlledAPsBitset(LabelledFormula formula, String[] controllableAPs) {
    BitSet bitSet = new BitSet();
    List<String> aps = formula.atomicPropositions();
    for (String controllableAP : controllableAPs) {
      if (aps.contains(controllableAP)) {
        bitSet.set(aps.indexOf(controllableAP));
      }
    }
    return bitSet;
  }

  private LTL2AIGModule() {}

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(),
      OutputWriters.TO_STRING_MODULE));
  }
}
