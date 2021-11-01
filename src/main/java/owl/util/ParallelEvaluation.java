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

package owl.util;

import com.google.common.util.concurrent.Uninterruptibles;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;

/**
 * This class provides static methods that evaluate the list of suppliers in parallel.
 */
public final class ParallelEvaluation {

  private ParallelEvaluation() {}

  public static <T> List<T> evaluate(List<? extends Supplier<Optional<T>>> suppliers) {
    ExecutorService executorService = Executors.newCachedThreadPool();
    List<Future<Optional<? extends T>>> futures = new ArrayList<>();

    // All suppliers, except the first one, are executed by the executor, i.e., on another thread.
    for (int i = 1, s = suppliers.size(); i < s; i++) {
      futures.add(executorService.submit(suppliers.get(i)::get));
    }

    List<T> results = new ArrayList<>();
    suppliers.get(0).get().ifPresent(results::add);

    // Retrieve results.
    for (Future<Optional<? extends T>> future : futures) {
      try {
        Uninterruptibles.getUninterruptibly(future).ifPresent(results::add);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }

    executorService.shutdownNow();
    return results;
  }

  public static <A extends EmersonLeiAcceptance> Automaton<?, A> takeSmallest(
    List<? extends Automaton<?, A>> automata) {

    if (automata.isEmpty()) {
      throw new NoSuchElementException();
    }

    var smallestAutomaton = automata.get(0);
    int smallestSize = smallestAutomaton.states().size();
    int smallestAcceptanceSets = smallestAutomaton.acceptance().acceptanceSets();

    for (var automaton : automata.subList(1, automata.size())) {
      int automatonSize = automaton.states().size();
      int automatonAcceptanceSize = automaton.acceptance().acceptanceSets();

      if (smallestSize < automatonSize) {
        continue;
      }

      if (smallestSize == automatonSize && smallestAcceptanceSets <= automatonAcceptanceSize) {
        continue;
      }

      // Current automaton is smaller than the so-far found smallest automaton.
      smallestAutomaton = automaton;
      smallestSize = automatonSize;
      smallestAcceptanceSets = automatonAcceptanceSize;
    }

    return smallestAutomaton;
  }

  @SuppressWarnings({"unchecked", "raw"})
  public static <A extends EmersonLeiAcceptance> Automaton<?, ? extends A>
    takeSmallestWildcardStateType(
    List<? extends Automaton<?, ? extends A>> automata) {
    return takeSmallest((List) automata);
  }
}
