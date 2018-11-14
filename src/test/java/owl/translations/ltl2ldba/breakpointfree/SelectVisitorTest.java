/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.translations.ltl2ldba.breakpointfree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import owl.collections.Collections3;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.UnaryModalOperator;
import owl.ltl.parser.LtlParser;
import owl.translations.mastertheorem.Fixpoints;
import owl.translations.mastertheorem.Selector;

class SelectVisitorTest {

  private static Set<Fixpoints> getFScoped(Formula formula) {
    return Selector.selectSymmetric(formula, false);
  }

  private static Set<Fixpoints> getGScoped(Formula formula) {
    return Selector.selectSymmetric(formula, false);
  }

  private static Set<Fixpoints> getToplevel(Formula formula) {
    return Selector.selectSymmetric(formula, false);
  }

  @Disabled
  @Test
  void testFScopedSelectorM() {
    FOperator fOperator = (FOperator) LtlParser.parse("F (a M b)").formula();
    assertEquals(Set.of(Set.<UnaryModalOperator>of(fOperator)), getGScoped(fOperator));
  }

  @Disabled
  @Test
  void testFScopedSelectorR() {
    FOperator fOperator = (FOperator) LtlParser.parse("F (a R b)").formula();
    GOperator gOperator = new GOperator(Literal.of(1));

    Set<UnaryModalOperator> choice1 = Set.of(fOperator);
    Set<UnaryModalOperator> choice2 = Set.of(fOperator, gOperator);
    assertEquals(Set.of(choice1, choice2), getGScoped(fOperator));
  }

  @Disabled
  @Test
  void testFScopedSelectorU() {
    FOperator fOperator = (FOperator) LtlParser.parse("F (a U b)").formula();

    Set<UnaryModalOperator> choice = Set.of(fOperator);
    assertEquals(Set.of(choice), getGScoped(fOperator));
  }

  @Disabled
  @Test
  void testGScopedSelectorR() {
    GOperator gOperator = (GOperator) LtlParser.parse("G (a R b)").formula();

    Set<UnaryModalOperator> choice = Set.of(gOperator);
    assertEquals(Set.of(choice), getFScoped(gOperator));
  }

  @Disabled
  @Test
  void testGScopedSelectorU() {
    GOperator gOperator = (GOperator) LtlParser.parse("G (a U b)").formula();
    FOperator fOperator = new FOperator(Literal.of(1));

    Set<UnaryModalOperator> choice = Set.of(gOperator, fOperator);
    assertEquals(Set.of(choice), getFScoped(gOperator));
  }

  @Disabled
  @Test
  void testGScopedSelectorW() {
    GOperator gOperator = (GOperator) LtlParser.parse("G (a W b)").formula();

    Set<UnaryModalOperator> choice = Set.of(gOperator);
    assertEquals(Set.of(choice), getFScoped(gOperator));
  }

  @Disabled
  @Test
  void testSelectorSkippedUpwardClosure() {
    Conjunction conjunction = (Conjunction) LtlParser.parse("G (a | F b) & G (b | F a)").formula();
    Disjunction disjunction = (Disjunction) LtlParser.parse("G (F b) | G (F a)").formula();

    Set<GOperator> gOperators = (Set<GOperator>) ((Set) conjunction.children);

    FOperator fOperatorA = new FOperator(Literal.of(0));
    FOperator fOperatorB = new FOperator(Literal.of(1));

    Set<? extends UnaryModalOperator> baseChoiceConj = gOperators;
    Set<UnaryModalOperator> choiceA = Set.of(fOperatorA);
    Set<UnaryModalOperator> choiceB = Set.of(fOperatorB);
    Set<UnaryModalOperator> choiceAandB = Set.of(fOperatorA, fOperatorB);

    assertEquals(Set.of(baseChoiceConj,
      Sets.union(baseChoiceConj, choiceA),
      Sets.union(baseChoiceConj, choiceB),
      Sets.union(baseChoiceConj, choiceAandB)),
      getToplevel(conjunction).stream().map(x -> x.leastFixpoints()).collect(Collectors.toSet()));

    assertEquals(getToplevel(disjunction), Collections3.transformSet(disjunction.children,
      formula -> Sets.union(Set.of(formula), formula.subformulas(FOperator.class))));
  }

  @Disabled
  @Test
  void testSelectorUpwardClosure() {
    GOperator gOperatorConj =
      (GOperator) LtlParser.parse("G (((a | F b) & (b | F a)) | c)").formula();
    GOperator gOperatorDisj = (GOperator) LtlParser.parse("G ((a & F b) | (b & F a))").formula();
    FOperator fOperatorA = new FOperator(Literal.of(0));
    FOperator fOperatorB = new FOperator(Literal.of(1));

    Set<UnaryModalOperator> baseChoiceConj = Set.of(gOperatorConj);
    Set<UnaryModalOperator> baseChoiceDisj = Set.of(gOperatorDisj);
    Set<UnaryModalOperator> choiceA = Set.of(fOperatorA);
    Set<UnaryModalOperator> choiceB = Set.of(fOperatorB);
    Set<UnaryModalOperator> choiceAandB = Set.of(fOperatorA, fOperatorB);

    assertEquals(Set.copyOf(getToplevel(gOperatorConj)),
      Set.of(baseChoiceConj, Sets.union(baseChoiceConj, choiceA),
        Sets.union(baseChoiceConj, choiceB), Sets.union(baseChoiceConj, choiceAandB)));
    assertEquals(getToplevel(gOperatorDisj),
      Set.of(Sets.union(baseChoiceDisj, choiceA), Sets.union(baseChoiceDisj, choiceB),
        Sets.union(baseChoiceDisj, choiceAandB)));
  }
}