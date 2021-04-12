package owl.translations.ltl2dpa;

import java.util.Optional;
import java.util.function.Function;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.symbolic.SymbolicAutomaton;
import owl.automaton.symbolic.SymbolicBooleanOperations;
import owl.automaton.symbolic.SymbolicDRA2DPAConstruction;
import owl.bdd.FactorySupplier;
import owl.ltl.LabelledFormula;
import owl.translations.ltl2dra.SymbolicNormalformDRAConstruction;

public final class SymbolicDPAConstruction
  implements Function<LabelledFormula, SymbolicAutomaton<ParityAcceptance>> {

  private SymbolicDPAConstruction() {}

  public static SymbolicDPAConstruction of() {
    return new SymbolicDPAConstruction();
  }

  @Override
  public SymbolicAutomaton<ParityAcceptance> apply(LabelledFormula labelledFormula) {
    var factory = FactorySupplier.defaultSupplier().getBddSetFactory();
    var draConstructor = new SymbolicNormalformDRAConstruction(factory);
    var symbolicRabinProductAutomaton = draConstructor.apply(labelledFormula);

    Optional<SymbolicAutomaton<ParityAcceptance>> parity = SymbolicDRA2DPAConstruction
      .of(symbolicRabinProductAutomaton)
      .tryToParity();

    if (parity.isPresent()) {
      return parity.get();
    }

    var symbolicStreettProductAutomaton = draConstructor.apply(labelledFormula.not());

    SymbolicAutomaton<?> symbolicDpwStructure = SymbolicBooleanOperations
      .deterministicStructureProduct(
        symbolicRabinProductAutomaton,
        symbolicStreettProductAutomaton
      );

    return SymbolicDRA2DPAConstruction.of(symbolicDpwStructure).toParity();
  }
}
