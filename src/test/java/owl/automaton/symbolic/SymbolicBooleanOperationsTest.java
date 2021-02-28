package owl.automaton.symbolic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.automaton.BooleanOperations;
import owl.automaton.algorithm.LanguageContainment;
import owl.bdd.FactorySupplier;
import owl.ltl.parser.LtlParser;
import owl.translations.canonical.DeterministicConstructions;

public class SymbolicBooleanOperationsTest {

  @Test
  void productTest() {
    var aps = List.of("a", "b");
    var automaton1 =
      DeterministicConstructions.CoSafetySafety.of(FactorySupplier.defaultSupplier()
        .getFactories(aps), LtlParser.parse("FG (a & X b)", aps).formula());
    var automaton2 =
      DeterministicConstructions.CoSafetySafety.of(FactorySupplier.defaultSupplier()
        .getFactories(aps), LtlParser.parse("FG (b & X a)", aps).formula());
    var explicitProduct = BooleanOperations.intersection(automaton1, automaton2);
    var symbolicProduct = SymbolicBooleanOperations.intersection(
      SymbolicAutomaton.of(automaton1),
      SymbolicAutomaton.of(automaton2)
    );
    var explicitProduct2 = symbolicProduct.toAutomaton();
    assertTrue(LanguageContainment.contains(explicitProduct, explicitProduct2));
    assertTrue(LanguageContainment.contains(explicitProduct2, explicitProduct));
  }

  @Test
  void productTest2() {
    var aps = List.of("a", "b", "c");
    var automaton1 = DeterministicConstructions.CoSafetySafety.of(FactorySupplier.defaultSupplier()
      .getFactories(aps), LtlParser.parse("a | X b | F G c", aps).formula());
    var automaton2 =
      DeterministicConstructions.SafetyCoSafety.of(FactorySupplier.defaultSupplier()
        .getFactories(aps), LtlParser.parse("GF (b & X a)", aps).formula());
    var explicitProduct = BooleanOperations.intersection(automaton1, automaton2);
    var symbolicProduct = SymbolicBooleanOperations.intersection(
      SymbolicAutomaton.of(automaton1),
      SymbolicAutomaton.of(automaton2)
    );
    var explicitProduct2 = symbolicProduct.toAutomaton();
    assertTrue(LanguageContainment.contains(explicitProduct, explicitProduct2));
    assertTrue(LanguageContainment.contains(explicitProduct2, explicitProduct));
  }

  @Test
  void productTest3() {
    var aps = List.of("a", "b", "c");
    var automaton1 = DeterministicConstructions.CoSafetySafety.of(FactorySupplier.defaultSupplier()
      .getFactories(aps), LtlParser.parse("a | X b | F G c", aps).formula());
    var automaton2 =
      DeterministicConstructions.SafetyCoSafety.of(FactorySupplier.defaultSupplier()
        .getFactories(aps), LtlParser.parse("GF (b & X a)", aps).formula());
    var explicitProduct = BooleanOperations.deterministicUnion(automaton1, automaton2);
    var symbolicProduct = SymbolicBooleanOperations.deterministicUnion(
      SymbolicAutomaton.of(automaton1),
      SymbolicAutomaton.of(automaton2)
    );
    var explicitProduct2 = symbolicProduct.toAutomaton();
    assertTrue(LanguageContainment.contains(explicitProduct, explicitProduct2));
    assertTrue(LanguageContainment.contains(explicitProduct2, explicitProduct));
  }
}
