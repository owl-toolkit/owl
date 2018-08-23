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

package owl.game.output;

public interface AigPrintable {
  void feedTo(AigConsumer consumer);

  /*
  @Override
  default void feedTo(AigConsumer consumer) {
    List<String> inputNames = variables(Owner.ENVIRONMENT);
    List<String> outputNames = variables(Owner.SYSTEM);

    AigFactory factory = new AigFactory();
    inputNames.forEach(consumer::addInput);

    // how many latches will we need?
    int nStates = Sets.filter(states(), x1 -> owner(x1) == Owner.SYSTEM).size();
    int nLatches = (int) Math.ceil(Math.log(nStates) / Math.log(2));

    // create mapping from states to bitsets of latches + inputs
    // where the input bits are always set to 0
    Map<S, BitSet> encoding = new HashMap<>();
    int iState = inputNames.size() + 1;

    for (S state : Sets.filter(states(), x -> owner(x) == Owner.SYSTEM)) {
      int value = iState;
      int index = inputNames.size();
      BitSet b = new BitSet(inputNames.size() + nLatches);
      while (value != 0) {
        if (value % 2 != 0) {
          b.set(index);
        }
        index++;
        value >>>= 1;
      }
      encoding.put(state, b);
      iState += 1;
    }

    // create a list of LabelledAig for the latches and outputs
    List<LabelledAig> latches = Lists.newArrayList(
      Collections.nCopies(nLatches, factory.getFalse()));
    List<LabelledAig> outputs = Lists.newArrayList(
      Collections.nCopies(outputNames.size(), factory.getFalse()));

    // iterate through labelled edges to create latch and output formulas
    for (S player2State : Sets.filter(states(), x -> owner(x) == Owner.SYSTEM)) {
      BitSet stateAndInput = BitSets.copyOf(encoding.get(player2State));
      stateAndInput.or(choice(player2State, Owner.ENVIRONMENT));
      LabelledAig stateAndInputAig = factory.cube(stateAndInput);

      // for all set indices in the output valuation
      // we update their transition function
      choice(player2State, Owner.SYSTEM).stream().forEach(
        i -> outputs.set(i, factory.disjunction(outputs.get(i), stateAndInputAig)));

      // we do the same for all set indices in the representation
      // of the successor state
      encoding.get(Iterables.getOnlyElement(successors(player2State))).stream().forEach(
        i -> latches.set(i, factory.disjunction(latches.get(i), stateAndInputAig)));
    }

    // we finish adding the information to the consumer
    latches.forEach(a -> consumer.addLatch("", a));
    Collections3.zip(outputNames, outputs, consumer::addOutput);
  } */
}
