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

package owl.automaton.hoa;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Variable;

import com.google.common.collect.Iterables;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedCoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;
import owl.thirdparty.jhoafparser.ast.AtomLabel;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerException;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerStore;
import owl.thirdparty.jhoafparser.owl.extensions.HOAFParserFixed;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;
import owl.thirdparty.jhoafparser.storage.StoredAutomaton;
import owl.thirdparty.jhoafparser.storage.StoredEdgeWithLabel;
import owl.thirdparty.jhoafparser.storage.StoredHeader;
import owl.thirdparty.jhoafparser.transformations.ToExplicitLabels;
import owl.thirdparty.jhoafparser.transformations.ToTransitionAcceptance;

public final class HoaReader {

  private HoaReader() {}

  public static void readStream(
    Reader reader,
    Supplier<BddSetFactory> factorySupplier,
    @Nullable List<String> predefinedAtomicPropositions,
    Consumer<? super Automaton<Integer, ?>> consumer) throws ParseException {

    var copiedPredefinedAtomicPropositions = predefinedAtomicPropositions == null
      ? null
      : List.copyOf(predefinedAtomicPropositions);

    final class HoaConsumerAutomatonSupplier extends HOAConsumerStore {
      @Override
      public void notifyEnd() throws HOAConsumerException {
        super.notifyEnd();
        consumer.accept(transform(
          getStoredAutomaton(), factorySupplier, copiedPredefinedAtomicPropositions));
      }
    }

    HOAFParserFixed.parseHOA(reader,
      () -> new ToTransitionAcceptance(new HoaConsumerAutomatonSupplier()));
  }

  public static Automaton<Integer, ?> read(String string) throws ParseException {
    return read(string, FactorySupplier.defaultSupplier()::getBddSetFactory, null);
  }

  public static Automaton<Integer, ?> read(
    String string,
    Supplier<BddSetFactory> factorySupplier,
    @Nullable List<String> predefinedAtomicPropositions) throws ParseException {

    return read(new StringReader(string), factorySupplier, predefinedAtomicPropositions);
  }

  public static Automaton<Integer, ?> read(
    Reader reader,
    Supplier<BddSetFactory> factorySupplier,
    @Nullable List<String> predefinedAtomicPropositions) throws ParseException {

    AtomicReference<Automaton<Integer, ?>> reference = new AtomicReference<>();

    readStream(reader, factorySupplier, predefinedAtomicPropositions, automaton -> {
      var oldValue = reference.getAndSet(automaton);
      if (oldValue != null) {
        throw new IllegalArgumentException(
          String.format("Stream contained at least two automata: %s, %s", automaton, oldValue));
      }
    });

    var automaton = reference.get();

    if (automaton == null) {
      throw new NoSuchElementException("Stream did not contain an automata.");
    }

    return automaton;
  }

  private static EmersonLeiAcceptance acceptance(StoredHeader header) throws HOAConsumerException {

    var name = Iterables.getOnlyElement(header.getAcceptanceNames(), null);
    var formula = header.getAcceptanceCondition();
    int sets = header.getNumberOfAcceptanceSets();

    switch (name == null ? "default" : name.name().toLowerCase(Locale.ENGLISH)) {
      case "all":
        return AllAcceptance.ofPartial(formula).orElseThrow();

      case "buchi":
        return BuchiAcceptance.ofPartial(formula).orElseThrow();

      case "parity":
        check(name.extra().size() == 3, "Malformed parity condition.");

        String stringPriority = name.extra().get(0).toString();
        boolean max = switch (stringPriority) {
          case "max" -> true;
          case "min" -> false;
          default -> throw new HOAConsumerException("Unknown priority " + stringPriority);
        };

        String stringParity = name.extra().get(1).toString();
        boolean even = switch (stringParity) {
          case "even" -> true;
          case "odd" -> false;
          default -> throw new HOAConsumerException("Unknown parity " + stringParity);
        };

        String stringColours = name.extra().get(2).toString();
        int colours;
        try {
          colours = Integer.parseInt(stringColours);
        } catch (NumberFormatException e) {
          throw (HOAConsumerException) new HOAConsumerException(
            "Failed to parse colours " + stringColours).initCause(e);
        }
        check(colours >= 0, "Negative colours");
        check(colours == sets, String.format("Mismatch between colours (%d) and acceptance"
          + " set count (%d)", colours, sets));

        return new ParityAcceptance(sets, Parity.of(max, even));

      case "co-buchi":
        // acc-name: co-Buchi
        // Acceptance: 1 Fin(0)
        return CoBuchiAcceptance.ofPartial(formula).orElseThrow();

      case "generalized-buchi":
        // acc-name: generalized-Buchi 3
        // Acceptance: 3 Inf(0)&Inf(1)&Inf(2)
        int size1 = Integer.parseInt(name.extra().get(0).toString());
        var generalizedBuchiAcceptance
          = GeneralizedBuchiAcceptance.ofPartial(formula).orElseThrow();
        check(generalizedBuchiAcceptance.acceptanceSets() == size1, "Mismatch.");
        return generalizedBuchiAcceptance;

      case "generalized-co-buchi":
        // acc-name: generalized-co-Buchi 3
        // Acceptance: 3 Fin(0)|Fin(1)|Fin(2)
        int size2 = Integer.parseInt(name.extra().get(0).toString());
        var generalizedCoBuchiAcceptance
          = GeneralizedCoBuchiAcceptance.ofPartial(formula).orElseThrow();
        check(generalizedCoBuchiAcceptance.acceptanceSets() == size2, "Mismatch.");
        return generalizedCoBuchiAcceptance;

      case "rabin":
        // acc-name: Rabin 3
        // Acceptance: 6 (Fin(0)&Inf(1))|(Fin(2)&Inf(3))|(Fin(4)&Inf(5))
        return RabinAcceptance.ofPartial(formula).orElseThrow(
          () -> new IllegalArgumentException(
            String.format("Rabin Acceptance (%s) not well-formed.", formula)));

      case "generalized-rabin":
        // acc-name: generalized-Rabin 2 3 2
        // Acceptance: 7 (Fin(0)&Inf(1)&Inf(2)&Inf(3))|(Fin(4)&Inf(5)&Inf(6))
        return GeneralizedRabinAcceptance.ofPartial(formula).orElseThrow(
          () -> new IllegalArgumentException(String.format(
            "Generalized-Rabin Acceptance (%s) not well-formed.", formula)));

      case "streett":
        // acc-name: Streett 3
        // Acceptance: 6 (Fin(0)|Inf(1))&(Fin(2)|Inf(3))&(Fin(4)|Inf(5))

      default:
        return EmersonLeiAcceptance.of(formula);
    }
  }

  private static void check(boolean condition, String message)
    throws HOAConsumerException {
    if (!condition) {
      throw new HOAConsumerException(message);
    }
  }

  private static AbstractMemoizingAutomaton<Integer, ?> transform(
    StoredAutomaton storedAutomaton,
    Supplier<BddSetFactory> factorySupplier,
    @Nullable List<String> predefinedAtomicPropositions) throws HOAConsumerException {

    var storedHeader = storedAutomaton.getStoredHeader();
    var vsFactory = factorySupplier.get();

    // Remove implicit edges and replace them with explicit edges.
    if (storedAutomaton.hasEdgesImplicit()) {
      new ToExplicitLabels().manipulate(storedAutomaton);
    }

    assert !storedAutomaton.hasEdgesImplicit();
    check(!storedAutomaton.hasUniversalBranching(), "Universal branching not supported.");

    List<String> atomicPropositions = List.copyOf(storedHeader.getAPs());

    int[] remapping;

    if (predefinedAtomicPropositions == null
      || predefinedAtomicPropositions.equals(atomicPropositions)) {
      remapping = null;
    } else {
      remapping = new int[atomicPropositions.size()];
      ListIterator<String> variableIterator = atomicPropositions.listIterator();
      while (variableIterator.hasNext()) {
        int variableIndex = predefinedAtomicPropositions.indexOf(variableIterator.next());
        checkArgument(variableIndex >= 0,
          "Failed to map to predefined atomic propositions.");
        remapping[variableIterator.previousIndex()] = variableIndex;
      }
    }

    Set<Integer> initialStates = new HashSet<>();

    for (List<Integer> startState : storedHeader.getStartStates()) {
      check(startState.size() == 1, "Universal initial states not supported");
      initialStates.add(Iterables.getOnlyElement(startState));
    }

    var automaton = new StoredAutomatonConverter(remapping,
      remapping == null ? atomicPropositions : predefinedAtomicPropositions,
      vsFactory, initialStates, acceptance(storedHeader), storedAutomaton);

    automaton.states();
    assert automaton.storedAutomaton == null;
    return automaton;
  }

  private static final class StoredAutomatonConverter
    extends AbstractMemoizingAutomaton.EdgeTreeImplementation<Integer, EmersonLeiAcceptance> {

    @Nullable
    private StoredAutomaton storedAutomaton;
    @Nullable
    private int[] mapping;
    @Nullable
    private Map<String, PropositionalFormula<AtomLabel>> aliases;

    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    private StoredAutomatonConverter(
      @Nullable int[] remapping,
      List<String> atomicPropositions,
      BddSetFactory vsFactory,
      Set<Integer> initialStates,
      EmersonLeiAcceptance acceptance,
      StoredAutomaton storedAutomaton) {

      super(atomicPropositions, vsFactory, initialStates, acceptance);

      this.storedAutomaton = storedAutomaton;
      this.mapping = remapping;
      this.aliases = new HashMap<>();
      storedAutomaton.getStoredHeader().getAliases().forEach(
        x -> this.aliases.put(x.name(), x.extra()));
    }

    @Override
    protected MtBdd<Edge<Integer>> edgeTreeImpl(Integer state) {
      var storedState = storedAutomaton.getStoredState(state);

      assert state == storedState.getStateId();
      assert storedState.getAccSignature() == null || storedState.getAccSignature().isEmpty();
      assert !storedAutomaton.hasEdgesImplicit(state);

      Map<Edge<Integer>, PropositionalFormula<Integer>> edgeMap = new HashMap<>();

      Iterable<StoredEdgeWithLabel> edges = storedAutomaton.getEdgesWithLabel(state);

      if (edges == null) {
        return MtBdd.of();
      }

      int acceptanceSets = acceptance.acceptanceSets();
      for (StoredEdgeWithLabel edgeWithLabel : storedAutomaton.getEdgesWithLabel(state)) {
        Edge<Integer> edge = Edge.of(Iterables.getOnlyElement(edgeWithLabel.getConjSuccessors()),
          edgeWithLabel.getAccSignature() == null
            ? ImmutableBitSet.of()
            : ImmutableBitSet.copyOf(edgeWithLabel.getAccSignature()));
        checkArgument(edge.colours().last().orElse(-1) < acceptanceSets,
          "The number of colours on the edge (%s) exceeds the number of colours "
            + "allowed by the acceptance condition (%s).", edge, acceptance());
        edgeMap.compute(edge, (key, value) -> value == null
          ? resolveAndRemap(edgeWithLabel.getLabelExpr())
          : Disjunction.of(value, resolveAndRemap(edgeWithLabel.getLabelExpr())));
      }

      return MtBdd.of(edgeMap);
    }

    @Override
    protected void explorationCompleted() {
      storedAutomaton = null;
      mapping = null;
      aliases = null;
    }

    private PropositionalFormula<Integer> resolveAndRemap(
      PropositionalFormula<? extends AtomLabel> expression) {

      return expression.substitute(atom -> {
        if (atom.isAlias()) {
          String alias = atom.aliasName();
          checkArgument(
            aliases != null && aliases.containsKey(alias), "Alias " + alias + " undefined");
          return resolveAndRemap(aliases.get(alias));
        } else {
          int apIndex = atom.apIndex();
          return Variable.of(mapping == null ? apIndex : mapping[apIndex]);
        }
      });
    }
  }
}
