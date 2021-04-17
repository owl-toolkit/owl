package owl.automaton.symbolic;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;
import static owl.automaton.symbolic.SymbolicDPASolver.Solution.Winner.CONTROLLER;
import static owl.automaton.symbolic.SymbolicDPASolver.Solution.Winner.ENVIRONMENT;
import static owl.translations.LtlTranslationRepository.LtlToDpaTranslation.UNPUBLISHED_ZIELONKA;
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
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.collections.ImmutableBitSet;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;

public class SymbolicDPASolverTest {

  @TestFactory
  Stream<DynamicTest> syntCompSelectionRealizable() {
    return tests("synt-comp-selection-realizable.json", true);
  }

  @TestFactory
  Stream<DynamicTest> syntCompSelectionUnrealizable() {
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

  private static Stream<DynamicTest> tests(String file, boolean realizable) {
    try {
      return Arrays.stream(testCases(file)).map(
        testCase -> DynamicTest.dynamicTest(testCase.formula,
          () -> assertTimeoutPreemptively(Duration.of(30, ChronoUnit.SECONDS), () -> {
            LabelledFormula formula = LtlParser.parse(testCase.formula);
            var dpa = SymbolicAutomaton.of(
              (Automaton<?, ParityAcceptance>) OmegaAcceptanceCast.cast(
                Views.complete(UNPUBLISHED_ZIELONKA.translation(
                  EnumSet.of(SIMPLIFY_FORMULA, USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS))
                  .apply(formula)), ParityAcceptance.class));
            ImmutableBitSet controllable = controllable(formula.atomicPropositions(),
              testCase.controllable);
            assertSame(new DFISymbolicDPASolver().solve(dpa, controllable).winner(),
              realizable ? CONTROLLER : ENVIRONMENT
            );
          })));
    } catch (IOException e) {
      fail(e);
    }
    throw new AssertionError();
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
