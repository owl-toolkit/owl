/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.translations.nbadet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.BiMap;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import owl.collections.BitSet2;

/**
 * these sets reflect the different determinisation components to be used in the DetState
 * i.e. rsccStates + asccsStates + dsccsStates + msccsStates form a labelled partition of
 * the state space of the input Büchi automaton, where each partition is handled separately
 * corresponding to its type (this refers to the different SCC-based optimizations)
 */
@AutoValue
@SuppressWarnings("PMD.LooseCoupling")
public abstract class NbaDetConfSets {
  /* Properties:
   * nscc_states + ascc_states + dscc_states + mscc_states = aut_states
   * nscc_states , ascc_states , dscc_states , mscc_states all pw. disj.
   * union of asccs_states = ascc_states
   * asccs_states pw. disj.
   * msccs_states pw. disj.
   */

  public abstract BitSet rsccStates();

  public abstract BitSet asccStates();

  //those are being cycled through if accSepCyc enabled
  public abstract ArrayList<BitSet> asccsStates();

  public abstract ArrayList<BitSet> dsccsStates();

  public abstract ArrayList<BitSet> msccsStates();

  public static <S> NbaDetConfSets of(NbaDetArgs args,
                                      NbaSccInfo<S> scci, BiMap<S, Integer> stateMap) {
    var handled = new HashSet<Integer>(); //keep track of already handled SCCs

    var rejStatesBS = new BitSet();
    if (args.sepRej()) {
      //collect all rejecting SCCs into a single buffer set
      Set<S> rejStates = scci.rejSccs().stream()
        .flatMap(i -> scci.sccDecomposition().sccs().get(i).stream())
        .collect(Collectors.toUnmodifiableSet());
      rejStatesBS = BitSet2.copyOf(rejStates, stateMap::get);

      handled.addAll(scci.rejSccs());
    }

    var accStatesBS = new BitSet();
    var asccs = new ArrayList<BitSet>();
    if (args.sepAcc()) {
      //collect the accepting SCCs
      var accSccs = scci.accSccs().stream().map(i -> scci.sccDecomposition().sccs().get(i))
                                         .collect(Collectors.toUnmodifiableList());
      //and also their union
      var accStates = accSccs.stream().flatMap(Collection::stream)
                                      .collect(Collectors.toUnmodifiableSet());

      accStatesBS = BitSet2.copyOf(accStates, stateMap::get);
      accSccs.forEach(s -> asccs.add(BitSet2.copyOf(s, stateMap::get)));

      if (!args.sepAccCyc()) {
        //if not requested to separate, put all ASCCs in single determinisation tuple component
        final var merged = asccs.stream().reduce(BitSet2::union).orElse(new BitSet());
        asccs.clear();
        asccs.add(merged);
      }

      handled.addAll(scci.accSccs());
    }

    var dsccs = new ArrayList<BitSet>();
    if (args.sepDet()) {
      //collect deterministic SCCs (that are not already handled as acc./rej.)
      var unhDetSccs = scci.detSccs().stream()
          .filter(i -> !handled.contains(i))
          .collect(Collectors.toUnmodifiableList());

      var expDetSccs = unhDetSccs.stream().map(i -> scci.sccDecomposition().sccs().get(i))
                                       .collect(Collectors.toUnmodifiableList());


      //those must always be separate to work correctly
      expDetSccs.forEach(s -> dsccs.add(BitSet2.copyOf(s, stateMap::get)));

      handled.addAll(unhDetSccs);
    }

    //SCCs which are not handled specially yet are treated as mixed (generic determinization)
    var msccs = new ArrayList<BitSet>();
    scci.ids().filter(i -> !handled.contains(i)).forEach(
      i -> msccs.add(BitSet2.copyOf(scci.sccDecomposition().sccs().get(i), stateMap::get)));

    if (!args.sepMix()) {
      //if not requested to separate, put all states in single determinisation tuple component
      final var merged = msccs.stream().reduce(BitSet2::union).orElse(new BitSet());
      msccs.clear();
      if (!merged.isEmpty()) {
        msccs.add(merged);
      }
    }

    return new AutoValue_NbaDetConfSets(rejStatesBS, accStatesBS, asccs, dsccs, msccs);
  }
}