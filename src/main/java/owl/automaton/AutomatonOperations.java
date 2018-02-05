package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.Automaton.Property.COMPLETE;
import static owl.automaton.Automaton.Property.DETERMINISTIC;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.factories.ValuationSetFactory;

public final class AutomatonOperations {

  private AutomatonOperations() {}

  private static <S> boolean isSublist(List<S> subList, List<S> list) {
    return subList.size() <= list.size() && subList.equals(list.subList(0, subList.size()));
  }

  private static ValuationSetFactory sharedAlphabet(Stream<ValuationSetFactory> factories) {
    Iterator<ValuationSetFactory> iterator = factories.iterator();
    ValuationSetFactory factory = iterator.next();

    while (iterator.hasNext()) {
      ValuationSetFactory otherFactory = iterator.next();

      if (isSublist(factory.alphabet(), otherFactory.alphabet())) {
        factory = otherFactory;
      } else if (!isSublist(otherFactory.alphabet(), factory.alphabet())) {
        throw new IllegalArgumentException("Could not find shared alphabet.");
      }
    }

    return factory;
  }

  /**
   * Constructs an automaton recognizing the intersection of languages of the given automata.
   *
   * @param automata
   *     A list of automata over the same alphabet ({@link ValuationSetFactory}). The only supported
   *     acceptance are AllAcceptance, CoBuchiAcceptance and GeneralisedBuchiAcceptance. The given
   *     automata need to be deterministic.
   * @param <S>
   *     The type of the states. Can be set to Object.
   *
   * @return An automaton that is constructed on-the-fly.
   */
  public static <S> Automaton<List<S>, OmegaAcceptance> intersection(
    List<Automaton<S, ? extends OmegaAcceptance>> automata) {
    checkArgument(!automata.isEmpty(), "No automaton was passed.");

    ListAutomatonBuilder<S> builder = new ListAutomatonBuilder<>(false);
    ValuationSetFactory factory = sharedAlphabet(automata.stream().map(Automaton::getFactory));
    int offset = 1;

    for (Automaton<S, ? extends OmegaAcceptance> automaton : automata) {
      checkArgument(automaton.is(DETERMINISTIC), "Only deterministic automata supported");

      if (automaton.getAcceptance() instanceof AllAcceptance) {
        builder.all.add(AutomatonUtil.cast(automaton, AllAcceptance.class));
      } else if (automaton.getAcceptance() instanceof CoBuchiAcceptance) {
        builder.coBuchi.add(AutomatonUtil.cast(automaton, CoBuchiAcceptance.class));
      } else if (automaton.getAcceptance() instanceof GeneralizedBuchiAcceptance) {
        builder.buchi.add(AutomatonUtil.cast(automaton, GeneralizedBuchiAcceptance.class));
        builder.acceptanceRemapping.add(offset);
        offset = offset + automaton.getAcceptance().getAcceptanceSets();
      } else {
        throw new IllegalArgumentException(
          "Unsupported Acceptance Type " + automaton.getAcceptance());
      }
    }

    OmegaAcceptance acceptance;

    if (builder.buchi.isEmpty()) {
      acceptance = CoBuchiAcceptance.INSTANCE;
    } else if (builder.coBuchi.isEmpty()) {
      acceptance = new GeneralizedBuchiAcceptance(offset - 1);
    } else if (offset < 2) {
      acceptance = new RabinAcceptance(1);
    } else {
      GeneralizedRabinAcceptance rabinAcceptance = new GeneralizedRabinAcceptance();
      rabinAcceptance.createPair(offset - 1);
      acceptance = rabinAcceptance;
    }

    return AutomatonFactory.createStreamingAutomaton(acceptance, builder.init(), factory,
      builder::successor);
  }

  public static <S> Automaton<List<S>, BuchiAcceptance> union(
    List<Automaton<S, BuchiAcceptance>> automata) {
    checkArgument(!automata.isEmpty(), "No automaton was passed.");
    assert automata.stream().allMatch(x -> x.is(COMPLETE) && x.is(DETERMINISTIC));

    ListAutomatonBuilder<S> builder = new ListAutomatonBuilder<>(true);
    ValuationSetFactory factory = sharedAlphabet(automata.stream().map(Automaton::getFactory));
    builder.buchi.addAll(automata);

    return AutomatonFactory.createStreamingAutomaton(BuchiAcceptance.INSTANCE, builder.init(),
      factory, builder::successor);
  }

  private static class ListAutomatonBuilder<S> {
    final IntList acceptanceRemapping = new IntArrayList();
    final List<Automaton<S, ? extends AllAcceptance>> all = new ArrayList<>();
    final List<Automaton<S, ? extends GeneralizedBuchiAcceptance>> buchi = new ArrayList<>();
    final List<Automaton<S, ? extends CoBuchiAcceptance>> coBuchi = new ArrayList<>();
    final boolean collapseBuchi;

    private ListAutomatonBuilder(boolean collapseBuchi) {
      this.collapseBuchi = collapseBuchi;
    }

    Automaton<S, ?> getAutomaton(int i) {
      if (i < all.size()) {
        return all.get(i);
      }

      i -= all.size();

      if (i < coBuchi.size()) {
        return coBuchi.get(i);
      }

      i -= coBuchi.size();
      return buchi.get(i);
    }

    int getOffset(int i) {
      if (i < all.size()) {
        return -1;
      }

      i -= all.size();

      if (i < coBuchi.size()) {
        return 0;
      }

      i -= coBuchi.size();
      return acceptanceRemapping.getInt(i);
    }

    List<S> init() {
      return Stream.of(all, coBuchi, buchi)
        .flatMap(List::stream)
        .map(Automaton::getInitialState)
        .collect(ImmutableList.toImmutableList());
    }

    @Nullable
    Edge<List<S>> successor(List<S> list, BitSet valuation) {
      ImmutableList.Builder<S> successor = ImmutableList.builderWithExpectedSize(list.size());
      BitSet acceptanceSets = new BitSet();

      for (int i = 0; i < list.size(); i++) {
        Edge<S> edge = getAutomaton(i).getEdge(list.get(i), valuation);

        if (edge == null) {
          return null;
        }

        successor.add(edge.getSuccessor());

        if (collapseBuchi) {
          assert all.isEmpty();
          assert coBuchi.isEmpty();
          assert edge.largestAcceptanceSet() <= 0;

          if (edge.hasAcceptanceSets()) {
            acceptanceSets.set(0);
          }
        } else {
          int offset = getOffset(i);

          if (offset > 0) {
            OfInt iterator = edge.acceptanceSetIterator();

            while (iterator.hasNext()) {
              acceptanceSets.set(iterator.nextInt() + offset);
            }
          } else if (offset == 0 && edge.hasAcceptanceSets()) {
            acceptanceSets.set(0);
          }
        }
      }

      return Edge.of(successor.build(), acceptanceSets);
    }
  }
}
