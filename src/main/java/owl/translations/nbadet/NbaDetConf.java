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

package owl.translations.nbadet;

import com.google.auto.value.AutoValue;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.collections.BitSet2;
import owl.collections.Numbering;
import owl.collections.Pair;
import owl.command.AutomatonConversionCommands;

/**
 * This is the structure containing all required information that is used in the
 * determinization process and is obtained based on an NbaDetArgs instance.
 */
@AutoValue
public abstract class NbaDetConf<S> {
  public enum UpdateMode { MUELLER_SCHUPP, SAFRA, MAX_MERGE }

  private static final Logger logger = Logger.getLogger(NbaDet.class.getName());

  // mandatory infos that are used with various heuristics
  public abstract AutomatonConversionCommands.Nba2DpaCommand args();  //contains various flags

  public abstract NbaAdjMat<S> aut();          //computed adj matrix of NBA

  //heuristics and optimisations:
  public abstract BitSet accSinks();           //if non-empty, will be used to stop early

  public abstract SubsumedStatesMap extMask();  //stores inclusions used for powersets

  public abstract SubsumedStatesMap intMask();  //stores inclusions used in detstates

  //precomputed BitSets for various SCCs and their unions used for optimizations
  public abstract NbaDetConfSets sets();

  //modded version with different update mode
  public NbaDetConf<S> withUpdateMode(UpdateMode mode) {
    return new AutoValue_NbaDetConf<>(
      new AutomatonConversionCommands.Nba2DpaCommand(args(), mode),
        aut(), accSinks(), extMask(), intMask(), sets());
  }


  public static <S> Set<Pair<S,S>> filterInternalIncl(
    Set<? extends Pair<S, S>> incl, SccDecomposition<? super S> scci) {

    return incl.stream()
      .filter(p -> !p.fst().equals(p.snd()) // not equal, but in same SCC
            && scci.index(p.fst()) == scci.index(p.snd()))
      .collect(Collectors.toUnmodifiableSet());

    //NOTE: in principle, this can be made slightly more general, by keeping pairs p <= q if
    //both same SCC || (different SCCs in same comp. && every path from q to p stays in that comp.)
    //where second option is the same as:
    // every SCC between scc(p) and scc(q) is also included in same determinization component
    //the between-SCCs can be computed using the SCC graph,
    //and also the DetConfSet partition is required for the check
  }

  public static <S> Set<Pair<S, S>> filterExternalIncl(
    Set<? extends Pair<S, S>> incl,
    SccDecomposition<? super S> scci,
    Numbering<S> stm) {

    //can only use a < b if b -/> a
    var inclFilt = incl.stream()
      .filter(p -> !scci.pathExists(p.snd(), p.fst()))
      .collect(Collectors.toSet());

    //need to collapse remaining usable incl. to strict partial order and break symmetry inside
    //eq. classes for this, compute SCCs in graph induced by relation, each SCC is an eq. class
    var leq = new HashMap<S, Set<S>>();
    for (var p : inclFilt) {
      if (!leq.containsKey(p.fst())) {
        leq.put(p.fst(), new HashSet<>());
      }
      leq.get(p.fst()).add(p.snd());
    }
    var eqClass = new HashMap<S, Integer>();
    int i = 0;
    for (var eqcl : SccDecomposition.of(leq.keySet(), x -> leq.getOrDefault(x, Set.of())).sccs()) {
      for (var st : eqcl) {
        eqClass.put(st, i);
      }
      i++;
    }

    //exclude equivalences (keep one-sided relation, and without reflexive pairs)
    return inclFilt.stream()
      .filter(p -> !eqClass.get(p.fst()).equals(eqClass.get(p.snd()))
                   || stm.lookup(p.fst()) < stm.lookup(p.snd()))
      .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * construct the structure containing all required information and that will be
   * passed around as information store a lot.
   * @param <S> automaton state type
   * @param aut input NBA
   * @param incl known language inclusions in input NBA
   * @param args the configuration
   */
  public static <S> NbaDetConf<S> prepare(
      Automaton<S, ? extends BuchiAcceptance> aut,
      Set<Pair<S,S>> incl,
      AutomatonConversionCommands.Nba2DpaCommand args) {

    //compute SCCs
    var scci = SccDecomposition.of(aut);

    //fix a bijection between bit indices <-> original NBA states:
    var states = aut.states();
    //var states = new ArrayList<>(aut.states());
    //states.sort(Comparator.comparing(Object::toString)); //more predictable for debugging
    Numbering<S> stateMap = new Numbering<>(states.size());
    states.forEach(stateMap::lookup);

    var sccSets = NbaDetConfSets.of(args, scci, stateMap);

    //accepting (pseudo-)sinks
    var aSinks = NbaLangInclusions.getNbaAccPseudoSinks(aut);

    //get restricted inclusions for two kinds of optimizations
    var extIncl = filterExternalIncl(incl, scci, stateMap);
    var intIncl = filterInternalIncl(incl, scci);

    BitSet aSinksBS = BitSet2.copyOf(aSinks, stateMap::lookup);
    SubsumedStatesMap extMask = args.simExt()
        ? SubsumedStatesMap.of(stateMap, extIncl) : SubsumedStatesMap.empty();
    SubsumedStatesMap intMask = args.simInt()
        ? SubsumedStatesMap.of(stateMap, intIncl) : SubsumedStatesMap.empty();

    NbaAdjMat<S> adjMat = new NbaAdjMat<>(aut, stateMap, aSinks, extMask);

    NbaDetConf<S> ret = new AutoValue_NbaDetConf<>(
      args, adjMat, aSinksBS, extMask, intMask, sccSets);

    if (logger.getLevel() != null) {
      if (logger.getLevel().equals(Level.FINER)) {
        logger.log(Level.FINER, ret.toString());
      } else if (logger.getLevel().equals(Level.FINEST)) {
        logger.log(Level.FINEST, "NBA adj. mat. - state -> (accSucc, rejSucc)\n"
          + adjMat);
      }
    }

    return ret;
  }

  @Override
  public String toString() {
    IntFunction<S> inv = aut().stateMap()::lookup;
    var sb = new StringBuilder(200);
    sb.append("assembled NBA determinization configuration:")
      .append("\nstate to bit mapping: ")
      .append(aut().stateMap().asMap())
      .append("\ndetected accepting pseudo-sinks: ")
      .append(BitSet2.asSet(accSinks(), inv))
      .append("\nused external language inclusions:\n").append(extMask().toString(inv))
      .append("used internal language inclusions:\n").append(intMask().toString(inv))
      .append("determinization components:")
      .append("\nrScc(s): ").append(BitSet2.asSet(sets().rsccStates(), inv));
    sets().asccsStates().forEach(s -> sb.append("\naScc: ").append(BitSet2.asSet(s, inv)));
    sets().dsccsStates().forEach(s -> sb.append("\ndScc: ").append(BitSet2.asSet(s, inv)));
    sets().msccsStates().forEach(s -> sb.append("\nmScc: ").append(BitSet2.asSet(s, inv)));
    return sb.toString();
  }

}
