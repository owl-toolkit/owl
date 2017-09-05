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

package owl.translations.frequency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import owl.factories.Factories;
import owl.factories.ValuationSetFactory;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.UnaryModalOperator;
import owl.translations.frequency.ProductControllerSynthesis.State;

public final class DtgrmaFactory {
  private static final Logger logger = Logger.getLogger(DtgrmaFactory.class.getName());

  private final Factories factories;
  private final EnumSet<Optimisation> opts;
  private final Formula phi;
  private final ValuationSetFactory valuationSetFactory;
  private ProductControllerSynthesis product;

  public DtgrmaFactory(Formula phi, Factories factories, EnumSet<Optimisation> opts) {
    this.phi = phi;
    this.factories = factories;
    this.valuationSetFactory = factories.vsFactory;
    this.opts = opts;
  }

  private void constructAcceptance() {
    AccLocalControllerSynthesis accLocal = new AccLocalControllerSynthesis(product, factories,
      opts);

    Map<UnaryModalOperator, Map<Set<UnaryModalOperator>, Map<TranSet<State>, Integer>>>
      completeSlaveAcceptance = accLocal.getAllSlaveAcceptanceConditions();

    List<RabinPair2<TranSet<State>, List<TranSet<State>>>> genRabinCondition = new ArrayList<>();
    List<Collection<BoundAndReward>> mdpCondition = new ArrayList<>();
    Map<FrequencyG, BoundAndReward> mdpAcceptance = new HashMap<>();

    for (Entry<Set<UnaryModalOperator>, TranSet<State>> entry :
      accLocal.computeAccMasterOptions().entrySet()) {
      Set<UnaryModalOperator> gSet = entry.getKey();
      mdpAcceptance.clear();

      TranSet<State> fin = new TranSet<>(valuationSetFactory);
      fin.addAll(entry.getValue());

      List<TranSet<State>> infs = new ArrayList<>();
      for (UnaryModalOperator g : gSet) {
        Set<UnaryModalOperator> localGSet = new HashSet<>(gSet);
        localGSet.retainAll(accLocal.topmostSlaves.get(g));
        Map<TranSet<State>, Integer> singleAccCondition =
          completeSlaveAcceptance.get(g).get(localGSet);

        if (g instanceof FrequencyG) {
          FrequencyG freqg = (FrequencyG) g;
          BoundAndReward current = mdpAcceptance.get(freqg);
          if (current == null) {
            current = new BoundAndReward(freqg, valuationSetFactory);
          }
          current.increaseRewards(singleAccCondition);
          mdpAcceptance.put(freqg, current);
        } else if (g instanceof FOperator) {
          infs.addAll(singleAccCondition.keySet());
        } else if (g instanceof GOperator) {
          singleAccCondition.keySet().forEach(fin::addAll);
        } else {
          throw new AssertionError();
        }
      }

      genRabinCondition.add(new RabinPair2<>(fin, infs));
      mdpCondition.add(new HashSet<>(mdpAcceptance.values()));
    }

    GeneralisedRabinWithMeanPayoffAcceptance acc = new GeneralisedRabinWithMeanPayoffAcceptance(
      genRabinCondition, mdpCondition);
    product.setAcceptance(acc);
  }

  public ProductControllerSynthesis constructAutomaton() {
    logger.log(Level.FINE, "Generating primary automaton");
    MasterAutomaton master = new MasterAutomaton(phi, factories, opts);
    master.generate();
    logger.finest(() -> String.format("MasterAutomaton for %1$s:\n%2$s", phi,
      HoaUtil.toHoa(master)));

    Map<UnaryModalOperator, FrequencySelfProductSlave> slaves = constructSlaves();

    logger.log(Level.FINE, "Generating product");

    product = new ProductControllerSynthesis(master, slaves, factories);
    product.generate();

    logger.log(Level.FINE, "Generating acceptance condition");
    constructAcceptance();
    logger.finest(() -> String.format("Product Slave for %1$s:\n%2$s", phi,
      HoaUtil.toHoa(product)));

    logger.log(Level.FINE, "Remove some redundancy of Acceptance Condition");
    removeRedundancy();
    return product;
  }

  private Map<UnaryModalOperator, FrequencySelfProductSlave> constructSlaves() {
    Set<UnaryModalOperator> gSubformulas = phi.accept(new SlaveSubFormulaVisitor());
    Map<UnaryModalOperator, FrequencySelfProductSlave> slaves = new HashMap<>();

    for (UnaryModalOperator f : gSubformulas) {
      FrequencyMojmirSlaveAutomaton mSlave = new FrequencyMojmirSlaveAutomaton(f, factories, opts);
      mSlave.generate();

      logger.finest(() -> String.format("Mojmir slave for %1$s:\n%2$s", f,
        HoaUtil.toHoa(mSlave)));

      FrequencySelfProductSlave rSlave = new FrequencySelfProductSlave(mSlave, factories);
      rSlave.generate();

      slaves.put(f, rSlave);

      logger.finest(() -> String.format("Rabin slave for %1$s:\n%2$s", f,
        HoaUtil.toHoa(rSlave)));
    }
    return slaves;
  }

  private void removeRedundancy() {
    Set<Integer> toRemove = new HashSet<>();

    IntStream.range(0, product.getAcceptance().unmodifiableCopyOfAcceptanceCondition().size())
      .filter(i -> product.containsAllTransitions(
        product.getAcceptance().unmodifiableCopyOfAcceptanceCondition().get(i).left))
      .forEach(toRemove::add);
    product.getAcceptance().removeIndices(toRemove);

    toRemove.clear();
    product.getAcceptance().unmodifiableCopyOfAcceptanceCondition()
      .forEach(pair -> pair.right.forEach(inf -> inf.removeAll(pair.left)));

    IntStream.range(0, product.getAcceptance().unmodifiableCopyOfAcceptanceCondition().size())
      .filter(i -> product.getAcceptance().unmodifiableCopyOfAcceptanceCondition().get(i).right
        .stream().anyMatch(TranSet::isEmpty)).forEach(toRemove::add);
    product.getAcceptance().removeIndices(toRemove);
    toRemove.clear();

    product.getAcceptance().unmodifiableCopyOfAcceptanceCondition().forEach(
      pair -> pair.right.removeIf(i -> product.containsAllTransitions(i.union(pair.left))));

    for (RabinPair2<TranSet<State>, List<TranSet<State>>> pair :
      product.getAcceptance().unmodifiableCopyOfAcceptanceCondition()) {
      toRemove.clear();
      for (int i = 0; i < pair.right.size(); i++) {
        for (int j = 0; j < pair.right.size(); j++) {
          if (i != j && !toRemove.contains(j) && pair.right.get(i).containsAll(pair.right.get(j))) {
            toRemove.add(i);
          }
        }
      }
      toRemove.stream().sorted(Collections.reverseOrder())
        .forEachOrdered(i -> pair.right.remove(i.intValue()));
    }

    toRemove.clear();

    int acceptanceSize = product.getAcceptance().unmodifiableCopyOfAcceptanceCondition().size();
    for (int i = 0; i < acceptanceSize; i++) {
      for (int j = 0; j < acceptanceSize; j++) {
        if (i == j) {
          continue;
        }

        if (product.getAcceptance().implies(i, j) && !toRemove.contains(j)) {
          toRemove.add(i);
          break;
        }
      }
    }

    product.getAcceptance().removeIndices(toRemove);
  }
}
