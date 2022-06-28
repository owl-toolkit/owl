/*
 * Copyright (C) 2020, 2022  (Salomon Sickert)
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

package owl.cinterface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static owl.cinterface.StateFeatures.Feature;
import static owl.cinterface.StateFeatures.TemporalOperatorsProfileNormalForm.CNF;
import static owl.cinterface.StateFeatures.TemporalOperatorsProfileNormalForm.DNF;
import static owl.cinterface.StateFeatures.extract;
import static owl.cinterface.StateFeatures.extractFeaturesFromEquivalenceClass;
import static owl.collections.Collections3.hasDistinctValues;
import static owl.translations.LtlTranslationRepository.LtlToDpaTranslation;
import static owl.translations.LtlTranslationRepository.Option;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.BASE;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.CHECK;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.DWYER;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.LIBEROUTER;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet.PARAMETRISED;

import com.google.common.collect.Comparators;
import com.google.common.collect.Iterables;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import owl.automaton.acceptance.ParityAcceptance;
import owl.cinterface.CAutomaton.AutomatonWrapper;
import owl.collections.BitSet2;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.UOperator;
import owl.ltl.parser.LtlParser;
import owl.ltl.visitors.PrintVisitor;
import owl.translations.canonical.DeterministicConstructionsPortfolio;

public class CAutomatonTest {

  private static final String BASE_PATH = "data/formulas/features";

  private static final Map<FormulaSet, Summary> TEST_DATA = Map.ofEntries(
      Map.entry(BASE, Summary.of(59, 59, 59)),
      Map.entry(LIBEROUTER, Summary.of(46, 45, 45)),
      Map.entry(CHECK, Summary.of(75, 74, 68)),
      Map.entry(PARAMETRISED, Summary.of(33, 33, 33)),
      Map.entry(DWYER, Summary.of(49, 49, 44))
  );

  private static Formula leftNestedU(int depth) {
    assert depth >= 0;
    return depth > 0
        ? UOperator.of(leftNestedU(depth - 1), Literal.of(depth))
        : Literal.of(0);
  }

  @Tag("performance")
  @Test
  void testStateFeaturesExtractionForUTiming() {
    var formula = LabelledFormula.of(leftNestedU(11), IntStream.rangeClosed(0, 11)
        .mapToObj(Character::toString)
        .toList());
    var automaton = DeterministicConstructionsPortfolio
        .coSafety(formula);
    var states = automaton.states();

    // Takes ~50 seconds on a MacBook Pro (16-inch, 2019) / 2,6 GHz 6-Core Intel Core i7.
    Assertions.assertTimeout(Duration.ofSeconds(90),
        () -> extractFeaturesFromEquivalenceClass(states));
  }

  @Test
  void testStateFeaturesExtractionForU() {
    var formula = LtlParser.parse("((a U b) U c) U d");
    var automaton = DeterministicConstructionsPortfolio
        .coSafety(formula);
    var states = automaton.states();

    var expectedDnfFeatures = new HashSet<Feature>();
    BitSet2.powerSet(4).forEach(x -> {
      var profile = (BitSet) x.clone();
      profile.set(0);
      expectedDnfFeatures.add(
          Feature.temporalOperatorsProfileFromBitset(profile));
    });

    var expectedCnfFeatures = new HashSet<Feature>();
    expectedCnfFeatures.add(
        Feature.temporalOperatorsProfileFromBitset(new BitSet()));
    expectedCnfFeatures.add(Feature
        .temporalOperatorsProfileFromBitset(BitSet.valueOf(new byte[]{3})));
    expectedCnfFeatures.add(Feature
        .temporalOperatorsProfileFromBitset(BitSet.valueOf(new byte[]{7})));
    expectedCnfFeatures.add(Feature
        .temporalOperatorsProfileFromBitset(BitSet.valueOf(new byte[]{15})));

    var dnfFeatures = extractFeaturesFromEquivalenceClass(states, DNF, false);
    assertEquals(8, dnfFeatures.size());
    assertEquals(expectedDnfFeatures, new HashSet<>(dnfFeatures.values()));
    assertEquals(states, dnfFeatures.keySet());
    assertFalse(dnfFeatures.containsValue(null));

    var cnfFeatures = extractFeaturesFromEquivalenceClass(states, CNF, false);
    assertEquals(8, cnfFeatures.size());
    assertEquals(expectedCnfFeatures, new HashSet<>(cnfFeatures.values()));
    assertEquals(states, cnfFeatures.keySet());
    assertFalse(dnfFeatures.containsValue(null));
  }

  @Test
  void testStateFeaturesExtractionForF() {
    var formula = LtlParser.parse("F(a & ((b) U (c)))", List.of("a", "b", "c"));
    var automaton = DeterministicConstructionsPortfolio
        .coSafety(formula);
    var states = automaton.states();

    {
      var expectedDnfFeatures = Set.of(
          Feature.temporalOperatorsProfileFromBitset(0),
          Feature.temporalOperatorsProfileFromBitset(0, 1, 2)
      );

      var dnfFeatures
          = extractFeaturesFromEquivalenceClass(states, DNF, false);

      assertEquals(3, dnfFeatures.size());
      assertEquals(expectedDnfFeatures, new HashSet<>(dnfFeatures.values()));
      assertEquals(states, dnfFeatures.keySet());
      assertFalse(dnfFeatures.containsValue(null));
    }

    {
      var expectedDnfFeatures = Set.of(
          Feature.temporalOperatorsProfileFromBitset(0),
          Feature.temporalOperatorsProfileFromBitset(1, 2, 4),
          Feature.temporalOperatorsProfileFromBitset(2, 3, 5)
      );

      var dnfFeatures
          = extractFeaturesFromEquivalenceClass(states, DNF, true);

      assertEquals(3, dnfFeatures.size());
      assertEquals(expectedDnfFeatures, new HashSet<>(dnfFeatures.values()));
      assertEquals(states, dnfFeatures.keySet());
      assertFalse(dnfFeatures.containsValue(null));
    }

    {
      var expectedCnfFeatures = Set.of(
          Feature.temporalOperatorsProfileFromBitset(),
          Feature.temporalOperatorsProfileFromBitset(0)
      );

      var cnfFeatures
          = extractFeaturesFromEquivalenceClass(states, CNF, false);

      assertEquals(3, cnfFeatures.size());
      assertEquals(expectedCnfFeatures, new HashSet<>(cnfFeatures.values()));
      assertEquals(states, cnfFeatures.keySet());
      assertFalse(cnfFeatures.containsValue(null));
    }

    {
      var expectedCnfFeatures = Set.of(
          Feature.temporalOperatorsProfileFromBitset(),
          Feature.temporalOperatorsProfileFromBitset(0, 1, 2),
          Feature.temporalOperatorsProfileFromBitset(1, 2)
      );

      var cnfFeatures
          = extractFeaturesFromEquivalenceClass(states, CNF, true);

      assertEquals(3, cnfFeatures.size());
      assertEquals(expectedCnfFeatures, new HashSet<>(cnfFeatures.values()));
      assertEquals(states, cnfFeatures.keySet());
      assertFalse(cnfFeatures.containsValue(null));
    }
  }

  @SuppressWarnings(
      {"PMD.EmptyCatchBlock", "PMD.AvoidCatchingGenericException", "PMD.AvoidCatchingNPE"})
  @Test
  void testStateFeaturesExtractionForFormulaDatabase() throws IOException {
    var translation = LtlToDpaTranslation.SEJK16_EKRS17.translation(
        ParityAcceptance.class,
        EnumSet.of(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS));

    for (var test : TEST_DATA.entrySet()) {
      List<TestCase> features = new ArrayList<>();

      int total = 0;
      int successful = 0;
      int unambiguous = 0;

      for (var formula : test.getKey().loadAndDeduplicateFormulaSet()) {
        var automaton = translation.apply(formula);
        total++;

        try {
          var featureMap = extract(automaton.states());
          assertEquals(automaton.states(), featureMap.keySet());

          try {
            assertFalse(featureMap.containsValue(null));
          } catch (NullPointerException ex) {
            // Some collections are null-hostile.
          }

          successful++;

          if (hasDistinctValues(featureMap)) {
            unambiguous++;
          }

          var set = new TreeSet<List<Feature>>(Comparators.lexicographical(
              Feature::compareTo));
          set.addAll(featureMap.values());
          features.add(new TestCase(PrintVisitor.toString(formula, false), set));
        } catch (IllegalArgumentException ex) {
          // We failed to extract features from the state type.
        }
      }

      var summary = test.getValue();
      assertEquals(summary, Summary.of(total, successful, unambiguous), test.getKey().toString());

      Path referenceFile = Paths.get(String.format("%s/%s.json", BASE_PATH, test.getKey().name()));

      if (Files.notExists(referenceFile)) {
        try (BufferedWriter writer = Files.newBufferedWriter(Files.createFile(referenceFile))) {
          var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
          gson.toJson(features, writer);
        }
      } else {
        try (BufferedReader reader = Files.newBufferedReader(referenceFile)) {
          var gson = new GsonBuilder()
              .registerTypeAdapter(Feature.class, new FeatureDeserializer())
              .create();
          Type typeOfT = new TypeToken<List<TestCase>>() {
          }.getType();
          List<TestCase> referenceFeatures = gson.fromJson(reader, typeOfT);
          assertEquals(referenceFeatures, features);
        }
      }
    }
  }

  @Test
  public void testScoringZielonkaConstruction() {
    var translationAcd = LtlToDpaTranslation.SLM21
        .translation(ParityAcceptance.class, EnumSet.noneOf(Option.class), OptionalInt.empty());

    var translationZlk = LtlToDpaTranslation.SLM21
        .translation(ParityAcceptance.class, EnumSet.noneOf(Option.class), OptionalInt.of(0));

    var automatonAcd1 = translationAcd.apply(LtlParser.parse("G F (a & X X a) | F G (b | X X b)"));
    var cAutomatonAcd1 = AutomatonWrapper.of(automatonAcd1, -1);

    assertEquals(
        Set.of(0.562_5d, 0.609_375d, 0.468_75d, 0.515_625d),
        Arrays.stream(cAutomatonAcd1.edgeTree(0, true).scores.toArray()).boxed()
            .collect(Collectors.toUnmodifiableSet()));

    var automatonZlk1 = translationZlk.apply(LtlParser.parse("G F (a & X X a) | F G (b | X X b)"));
    var cAutomatonZlk1 = AutomatonWrapper.of(automatonZlk1, -1);

    assertEquals(
        Set.of(0.562_5d, 0.609_375d, 0.468_75d, 0.515_625d),
        Arrays.stream(cAutomatonZlk1.edgeTree(0, true).scores.toArray()).boxed()
            .collect(Collectors.toUnmodifiableSet()));

    var automatonAcd2 = translationAcd.apply(LtlParser.parse("a | X b"));
    var cAutomatonAcd2 = AutomatonWrapper.of(automatonAcd2, -1);

    assertEquals(
        Set.of(0.0d, 1.0d),
        Arrays.stream(cAutomatonAcd2.edgeTree(0, true).scores.toArray()).boxed()
            .collect(Collectors.toUnmodifiableSet()));

    var automatonZlk2 = translationAcd.apply(LtlParser.parse("F (a & X a & X X a)"));
    var cAutomatonZlk2 = AutomatonWrapper.of(automatonZlk2, -1);

    assertEquals(
        Set.of(0.125d, 0.25d),
        Arrays.stream(cAutomatonZlk2.edgeTree(0, true).scores.toArray()).boxed()
            .collect(Collectors.toUnmodifiableSet()));

    assertEquals(
        Set.of(0.125d, 0.5d),
        Arrays.stream(cAutomatonZlk2.edgeTree(1, true).scores.toArray()).boxed()
            .collect(Collectors.toUnmodifiableSet()));

    var automatonZlk3 = translationAcd.apply(LtlParser.parse("F (a & X a & X X a) | G F b"));
    var cAutomatonZlk3 = AutomatonWrapper.of(automatonZlk3, -1);

    assertEquals(
        Set.of(0.445_312_5d, 0.453_125d),
        Arrays.stream(cAutomatonZlk3.edgeTree(0, true).scores.toArray()).boxed()
            .collect(Collectors.toUnmodifiableSet()));

    assertEquals(
        Set.of(0.445_312_5d, 0.468_75d),
        Arrays.stream(cAutomatonZlk3.edgeTree(1, true).scores.toArray()).boxed()
            .collect(Collectors.toUnmodifiableSet()));
  }

  private static class FeatureDeserializer implements JsonDeserializer<Feature> {

    @Override
    public Feature deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {

      if (!json.isJsonObject()) {
        throw new JsonParseException("expected json object");
      }

      JsonObject jsonObject = (JsonObject) json;
      var entry = Iterables.getOnlyElement(jsonObject.entrySet());

      switch (entry.getKey()) {
        case "roundRobinCounter":
          return Feature.roundRobinCounter(
              context.deserialize(entry.getValue(), Integer.class));

        case "temporalOperatorsProfile":
          return Feature.temporalOperatorsProfile(
              context.deserialize(entry.getValue(), new TypeToken<SortedSet<Integer>>() {
              }.getType()));

        case "permutation":
          return Feature.permutation(
              context.deserialize(entry.getValue(), new TypeToken<List<Integer>>() {
              }.getType()));

        default:
          throw new JsonParseException("malformed json object");
      }
    }
  }

  static class TestCase {

    final String formula;
    @Nullable
    final Set<List<Feature>> properties;

    public TestCase(String formula, @Nullable Set<List<Feature>> properties) {
      this.formula = formula;
      this.properties = properties;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TestCase testCase = (TestCase) o;
      return formula.equals(testCase.formula) && Objects.equals(properties, testCase.properties);
    }

    @Override
    public int hashCode() {
      return Objects.hash(formula, properties);
    }
  }

  private static class Summary {

    private final int total;
    private final int successful;
    private final int unambiguous;

    private Summary(int total, int successful, int unambiguous) {
      this.total = total;
      this.successful = successful;
      this.unambiguous = unambiguous;
    }

    private static Summary of(int total, int successful, int unambiguous) {
      return new Summary(total, successful, unambiguous);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Summary summary = (Summary) o;
      return total == summary.total
          && successful == summary.successful
          && unambiguous == summary.unambiguous;
    }

    @Override
    public int hashCode() {
      return Objects.hash(total, successful, unambiguous);
    }

    @Override
    public String toString() {
      return "Summary{total=" + total
          + ", successful=" + successful + ", unambiguous=" + unambiguous + '}';
    }
  }
}
