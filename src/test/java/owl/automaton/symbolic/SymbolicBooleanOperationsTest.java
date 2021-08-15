/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.automaton.symbolic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import owl.automaton.BooleanOperations;
import owl.automaton.algorithm.LanguageContainment;
import owl.bdd.BddSetFactory;
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
    BddSetFactory factory = FactorySupplier.defaultSupplier().getBddSetFactory();
    var symbolicProduct = SymbolicBooleanOperations.intersection(
      SymbolicAutomaton.of(automaton1, factory, automaton1.atomicPropositions()),
      SymbolicAutomaton.of(automaton2, factory, automaton2.atomicPropositions())
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
    BddSetFactory factory = FactorySupplier.defaultSupplier().getBddSetFactory();
    var symbolicProduct = SymbolicBooleanOperations.intersection(
      SymbolicAutomaton.of(automaton1, factory, automaton1.atomicPropositions()),
      SymbolicAutomaton.of(automaton2, factory, automaton1.atomicPropositions())
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
    BddSetFactory factory = FactorySupplier.defaultSupplier().getBddSetFactory();
    var symbolicProduct = SymbolicBooleanOperations.deterministicUnion(
      SymbolicAutomaton.of(automaton1, factory, automaton1.atomicPropositions()),
      SymbolicAutomaton.of(automaton2, factory, automaton1.atomicPropositions())
    );
    var explicitProduct2 = symbolicProduct.toAutomaton();
    assertTrue(LanguageContainment.contains(explicitProduct, explicitProduct2));
    assertTrue(LanguageContainment.contains(explicitProduct2, explicitProduct));
  }
}
