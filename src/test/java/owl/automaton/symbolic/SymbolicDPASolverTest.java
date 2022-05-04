/*
 * Copyright (C) 2021, 2022  (Remco Abraham)
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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static owl.automaton.symbolic.SymbolicDPASolver.Solution.Winner.CONTROLLER;
import static owl.automaton.symbolic.SymbolicDPASolver.Solution.Winner.ENVIRONMENT;
import static owl.translations.LtlTranslationRepository.LtlToDpaTranslation.SLM21;
import static owl.translations.LtlTranslationRepository.Option.COMPLETE;
import static owl.translations.LtlTranslationRepository.Option.SIMPLIFY_FORMULA;
import static owl.translations.LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import owl.automaton.Views;
import owl.automaton.acceptance.ParityAcceptance;
import owl.collections.ImmutableBitSet;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

public class SymbolicDPASolverTest {

  @TestFactory
  Stream<DynamicTest> syntCompSelectionRealizable() throws IOException {
    return tests("synt-comp-selection-realizable.json", true);
  }

  @TestFactory
  Stream<DynamicTest> syntCompSelectionUnrealizable() throws IOException {
    return tests("synt-comp-selection-unrealizable.json", false);
  }

  private static TestCase[] testCases(String file) throws IOException {
    Gson gson = new Gson();
    try (Reader reader = new BufferedReader(new InputStreamReader(
        Objects.requireNonNull(Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream(file))))
    ) {
      return gson.fromJson(reader, TestCase[].class);
    }
  }

  private static Stream<DynamicTest> tests(String file, boolean realizable) throws IOException {
    return Arrays.stream(testCases(file)).map(
        testCase -> DynamicTest.dynamicTest(testCase.formula,
            () -> assertTimeoutPreemptively(Duration.of(10, ChronoUnit.SECONDS), () -> {
              LabelledFormula formula = LtlParser.parse(testCase.formula);
              var dpa = SymbolicAutomaton.of(
                  Views.convertParity(SLM21.translation(
                          EnumSet.of(SIMPLIFY_FORMULA, USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS,
                              COMPLETE))
                      .apply(formula), ParityAcceptance.Parity.MIN_EVEN));
              ImmutableBitSet controllable = controllable(formula.atomicPropositions(),
                  testCase.controllable);
              assertSame(new DFISymbolicDPASolver().solve(dpa, controllable).winner(),
                  realizable ? CONTROLLER : ENVIRONMENT
              );
            })));
  }

  private static ImmutableBitSet controllable(List<String> aps, String[] controllable) {
    BitSet controllableBitSet = new BitSet();
    for (String ap : controllable) {
      controllableBitSet.set(aps.indexOf(ap));
    }
    return ImmutableBitSet.copyOf(controllableBitSet);
  }

  private static final class TestCase {

    String formula;
    String[] controllable;
    String[] uncontrollable;
  }
}
