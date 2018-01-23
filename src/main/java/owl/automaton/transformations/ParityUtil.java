/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.automaton.transformations;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Collections2;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.Views.ForwardingAutomaton;
import owl.automaton.Views.ForwardingMutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.minimizations.GenericMinimizations;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.OwlModuleParser.TransformerParser;

public final class ParityUtil {
  public static final TransformerParser COMPLEMENT_CLI = ImmutableTransformerParser.builder()
      .key("complement-parity")
      .description("Complements a parity automaton")
      .parser(settings -> environment -> (input, context) -> {
        Automaton<Object, ParityAcceptance> automaton = AutomatonUtil.cast(input,
          ParityAcceptance.class);
        return ParityUtil.complement(AutomatonUtil.asMutable(automaton),
          AutomatonUtil.defaultSinkSupplier());
      }).build();

  public static final TransformerParser CONVERSION_CLI = ImmutableTransformerParser.builder()
      .key("convert-parity")
      .optionsDirect(new Options()
        .addOptionGroup(new OptionGroup()
          .addOption(new Option(null, "max", false, null))
          .addOption(new Option(null, "min", false, null)))
        .addOptionGroup(new OptionGroup()
          .addOption(new Option(null, "even", false, null))
          .addOption(new Option(null, "odd", false, null))))
      .description("Converts a parity automaton into the desired type")
      .parser(settings -> {
        @Nullable
        Boolean toMax;
        if (settings.hasOption("max")) {
          toMax = true;
        } else if (settings.hasOption("min")) {
          toMax = false;
        } else {
          toMax = null;
        }

        @Nullable
        Boolean toEven;
        if (settings.hasOption("even")) {
          toEven = true;
        } else if (settings.hasOption("odd")) {
          toEven = false;
        } else {
          toEven = null;
        }

        return environment -> (input, context) -> {
          Automaton<Object, ParityAcceptance> automaton =
            AutomatonUtil.cast(input, ParityAcceptance.class);
          ParityAcceptance acceptance = automaton.getAcceptance();
          Parity target = acceptance.getParity();
          if (toEven != null) {
            target = target.setEven(toEven);
          }
          if (toMax != null) {
            target = target.setMax(toMax);
          }

          return ParityUtil.convert(automaton, target);
        };
      }).build();

  private ParityUtil() {
  }

  public static <S> MutableAutomaton<S, ParityAcceptance>
  complement(MutableAutomaton<S, ParityAcceptance> automaton, Supplier<S> sinkSupplier) {
    ParityAcceptance acceptance = automaton.getAcceptance();

    if (acceptance.getAcceptanceSets() == 0) {
      if (!acceptance.emptyIsAccepting()) {
        // Automaton currently accepts nothing
        automaton.free();
        acceptance.complement();
        return MutableAutomatonFactory.singleton(sinkSupplier.get(), automaton.getFactory(),
          acceptance);
      }

      Optional<S> completionState = AutomatonUtil.complete(automaton, sinkSupplier, () -> {
        BitSet bitSet = new BitSet(1);
        bitSet.set(0);
        return bitSet;
      });

      if (!completionState.isPresent()) {
        // Automaton accepted everything
        automaton.free();
        acceptance.complement();
        return MutableAutomatonFactory.singleton(sinkSupplier.get(), automaton.getFactory(),
          acceptance);
      }

      // Automaton accepted everything which does not end up in the completion state
      acceptance.setAcceptanceSets(1);
      acceptance.setParity(Parity.MAX_EVEN);
      return automaton;
    }

    int rejectingAcceptance = acceptance.getParity().even() ? 1 : 0;
    BitSet set = new BitSet(rejectingAcceptance + 1);
    set.set(rejectingAcceptance);

    Optional<S> completionState = AutomatonUtil.complete(automaton, sinkSupplier, () -> set);
    if (completionState.isPresent() && acceptance.getAcceptanceSets() <= rejectingAcceptance) {
      acceptance.setAcceptanceSets(rejectingAcceptance + 1);
    }

    acceptance.complement();
    return automaton;
  }

  public static <S> MutableAutomaton<S, ParityAcceptance> minimizePriorities(
    MutableAutomaton<S, ParityAcceptance> automaton) {
    GenericMinimizations.removeTransientAcceptance(automaton);
    return minimizePriorities(automaton, SccDecomposition.computeSccs(automaton, false));
  }

  private static <S> MutableAutomaton<S, ParityAcceptance> minimizePriorities(
    MutableAutomaton<S, ParityAcceptance> automaton, List<Set<S>> sccs) {
    if (automaton instanceof ForwardingMutableAutomaton) {
      return automaton;
    }

    /* This optimization simply determines all priorities used in each SCC and then tries to
     * eliminate "gaps". For example, when [0, 2, 4, 5] are used, we actually only need to consider
     * [0, 1]. Furthermore, edges between SCCs are set to an arbitrary priority. */

    ParityAcceptance acceptance = automaton.getAcceptance();
    int acceptanceSets = acceptance.getAcceptanceSets();
    // Gather the priorities used _after_ the reduction - cheap and can be used for verification
    BitSet globallyUsedPriorities = new BitSet(acceptanceSets);

    // Construct the mapping for the priorities in this map
    Int2IntMap reductionMapping = new Int2IntOpenHashMap();
    reductionMapping.defaultReturnValue(-1);
    // Priorities used in each SCC
    BitSet usedPriorities = new BitSet(acceptanceSets);
    int usedAcceptanceSets = 0;

    for (Set<S> scc : sccs) {
      reductionMapping.clear();
      usedPriorities.clear();

      // Determine the used priorities
      for (S state : scc) {
        for (Edge<S> edge : automaton.getEdges(state)) {
          if (scc.contains(edge.getSuccessor())) {
            PrimitiveIterator.OfInt acceptanceSetIterator = edge.acceptanceSetIterator();
            if (acceptanceSetIterator.hasNext()) {
              usedPriorities.set(acceptanceSetIterator.nextInt());
            }
          }
        }
      }

      // All priorities are used, can't collapse any
      if (usedPriorities.cardinality() == acceptanceSets) {
        usedAcceptanceSets = Math.max(usedAcceptanceSets, acceptanceSets);
        continue;
      }

      // Construct the mapping
      int currentPriority = usedPriorities.nextSetBit(0);
      int currentTarget = currentPriority % 2;

      while (currentPriority != -1) {
        if (currentTarget % 2 != currentPriority % 2) {
          currentTarget += 1;
        }

        reductionMapping.put(currentPriority, currentTarget);
        globallyUsedPriorities.set(currentTarget);
        usedAcceptanceSets = Math.max(usedAcceptanceSets, currentTarget + 1);
        currentPriority = usedPriorities.nextSetBit(currentPriority + 1);
      }

      // This remaps _all_ outgoing edges of the states in the SCC - including transient edges.
      // Since these are only taken finitely often by any run, their value does not matter.
      automaton.remapEdges(scc, (state, edge) -> edge.withAcceptance(reductionMapping));
    }

    automaton.getAcceptance().setAcceptanceSets(usedAcceptanceSets);
    return automaton;
  }

  public static <S> Automaton<S, ParityAcceptance> viewAsParity(
    Automaton<S, BuchiAcceptance> automaton) {
    return new WrappedBuchiAutomaton<>(automaton);
  }

  public static <S> Automaton<S, ParityAcceptance> convert(Automaton<S, ParityAcceptance> automaton,
    Parity toParity) {
    // TODO Check for "colored" property
    ParityAcceptance acceptance = automaton.getAcceptance();

    if (acceptance.getParity().equals(toParity)) {
      return automaton;
    }

    IntUnaryOperator mapping = getEdgeMapping(acceptance, toParity);

    MutableAutomaton<S, ParityAcceptance> mutable = AutomatonUtil.asMutable(automaton);
    AtomicInteger maximalNewAcceptance = new AtomicInteger(0);

    mutable.remapEdges((state, edge) -> {
      if (!edge.hasAcceptanceSets()) {
        return edge;
      }

      int newAcceptance = mapping.applyAsInt(edge.smallestAcceptanceSet());

      if (newAcceptance == -1) {
        return Edge.of(edge.getSuccessor());
      }

      if (maximalNewAcceptance.get() < newAcceptance) {
        maximalNewAcceptance.set(newAcceptance);
      }

      return Edge.of(edge.getSuccessor(), newAcceptance);
    });

    acceptance.setParity(toParity);
    acceptance.setAcceptanceSets(maximalNewAcceptance.get() + 1);
    return mutable;
  }

  private static IntUnaryOperator getEdgeMapping(ParityAcceptance fromAcceptance, Parity toParity) {
    Parity fromParity = fromAcceptance.getParity();

    if (fromParity.max() == toParity.max()) {
      assert fromParity.even() != toParity.even();
      return i -> i + 1;
    } else {
      int acceptanceSets = fromAcceptance.getAcceptanceSets();
      int leastImportantColor = fromParity.max() ? 0 : acceptanceSets - 1;
      int offset;

      if (fromParity.even() != toParity.even()) {
        // Delete the least important color
        offset = fromAcceptance.isAccepting(leastImportantColor) ? -1 : -2;
      } else {
        offset = fromAcceptance.isAccepting(leastImportantColor) ? 0 : 1;
      }

      int newAcceptanceSets = acceptanceSets + offset;
      return i -> newAcceptanceSets - i;
    }
  }

  private static final class WrappedBuchiAutomaton<S> extends
    ForwardingAutomaton<S, ParityAcceptance, BuchiAcceptance, Automaton<S, BuchiAcceptance>> {
    private final ParityAcceptance acceptance;

    WrappedBuchiAutomaton(Automaton<S, BuchiAcceptance> backingAutomaton) {
      super(backingAutomaton);
      acceptance = new ParityAcceptance(2, Parity.MIN_EVEN);
    }

    private Edge<S> convertBuchiToParity(Edge<S> edge) {
      return edge.inSet(0) ? edge : Edge.of(edge.getSuccessor(), 1);
    }

    @Override
    public ParityAcceptance getAcceptance() {
      return acceptance;
    }

    @Override
    public Collection<LabelledEdge<S>> getLabelledEdges(S state) {
      checkState(acceptance.getAcceptanceSets() == 2);
      return Collections2.transform(super.getLabelledEdges(state), labelledEdge ->
        LabelledEdge.of(convertBuchiToParity(labelledEdge.edge), labelledEdge.valuations));
    }

    @Nullable
    @Override
    public S getSuccessor(S state, BitSet valuation) {
      return automaton.getSuccessor(state, valuation);
    }

    @Override
    public Set<S> getSuccessors(S state) {
      return automaton.getSuccessors(state);
    }

    @Override
    public boolean is(@Nonnull Property property) {
      return property.equals(Property.COLOURED) || super.is(property);
    }
  }
}
