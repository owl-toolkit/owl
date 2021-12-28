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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import owl.Bibliography;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.algorithm.simulations.BuchiSimulation;
import owl.automaton.edge.Edge;
import owl.collections.BitSet2;
import owl.collections.Pair;
import owl.command.AutomatonConversionCommands;

/**
 * This class provides the entry-point for the translation from non-deterministic Büchi automata to
 * deterministic parity automata described in {@link Bibliography#ICALP_19_1} with
 * optimisations from {@link Bibliography#ATVA_19}.
 */
public final class NbaDet {
  private static final Logger logger = Logger.getLogger(NbaDet.class.getName());

  /** make PMD silent. */
  private NbaDet() {}

  public static Map<Handler, Level> overrideLogLevel(Level verbosity) {
    final var rootLogger = LogManager.getLogManager().getLogger("");
    final var oldLogLevels = Arrays.stream(rootLogger.getHandlers())
      .collect(Collectors.toMap(Function.identity(), Handler::getLevel));
    for (final var h : rootLogger.getHandlers()) {
      h.setLevel(verbosity);
    }
    logger.setLevel(verbosity);
    return oldLogLevels;
  }

  public static void restoreLogLevel(Map<Handler, Level> oldLogLevels) {
    oldLogLevels.forEach(Handler::setLevel);
  }

  /**
   * Compute selected simulations. if possible, shrink automaton using computed equivalences.
   * Returns (possibly quotiented) automaton and known language inclusions.
   */
  public static <S> Pair<Automaton<Set<S>, BuchiAcceptance>, Set<Pair<Set<S>,Set<S>>>> preprocess(
    Automaton<S, BuchiAcceptance> aut, AutomatonConversionCommands.Nba2DpaCommand args) {
    //compute language inclusions (a <= b) between states, using selected simulations
    var incl = NbaLangInclusions.computeLangInclusions(aut, args.computeSims());
    logger.log(Level.FINE, "calculated language inclusions: " + incl);

    if (incl.isEmpty() || !NbaLangInclusions.getQuotientable().containsAll(args.computeSims())) {
      //trivial pass-through "quotient" to makes types consistent
      logger.log(Level.FINE, "no (quotientable) inclusions, pass through automaton.");
      var quotAut = Views.quotientAutomaton(aut, Set::of);
      var remainingIncl = incl.stream()
        .map(p -> Pair.of(Set.of(p.fst()), Set.of(p.snd())))
        .collect(Collectors.toSet());
      return Pair.of(quotAut, remainingIncl);
    }

    //if all computed simulations in the list are safe for naive quotienting, do it
    var equivRel = BuchiSimulation.computeEquivalence(incl);
    var classMap = new HashMap<S, Set<S>>();
    for (S state : aut.states()) {
      classMap.put(state, new HashSet<>());
      equivRel.stream()
        .filter(p -> state.equals(p.fst()))
        .forEach(q -> classMap.get(state).add(q.snd()));
    }

    var quotAut = Views.quotientAutomaton(aut, classMap::get);
    if (quotAut.states().size() < aut.states().size()) {
      logger.log(Level.FINE, "Quotienting reduced automaton from "
        + aut.states().size() + " states to " + quotAut.states().size() + " states.");
    }
    var remainingIncl = incl.stream()
      .map(p -> Pair.of(classMap.get(p.fst()), classMap.get(p.snd())))
      .collect(Collectors.toSet());

    logger.log(Level.FINE, "remaining language inclusions: " + remainingIncl);
    return Pair.of(quotAut, remainingIncl);
  }

  /**
   * Main method of module.
   */
  public static <S> Automaton<?, ParityAcceptance> determinize(
    Automaton<S, ? extends BuchiAcceptance> aut,
    AutomatonConversionCommands.Nba2DpaCommand args) {

    if (aut.atomicPropositions().size() > 30) {
      throw new UnsupportedOperationException(
        "ERROR: Too many atomic propositions! Only up to 30 are supported.");
    }
    var oldLogLevels = overrideLogLevel(Level.parse(args.verbosity()));
    // --------

    logger.log(Level.CONFIG, "selected nbadet configuration:\n" + args);

    //compute simulations and quotient NBA
    Pair<Automaton<Set<S>, BuchiAcceptance>, Set<Pair<Set<S>, Set<S>>>> prepAut
      = preprocess((Automaton<S, BuchiAcceptance>) aut, args);

    //take the (possibly quotiented) automaton, known language inclusions from simulations
    //and provided user arguments, and assemble a configuration for the determinization procedure.
    NbaDetConf<Set<S>> conf = NbaDetConf.prepare(prepAut.fst(), prepAut.snd(), args);

    //var emptyDpa = SingletonAutomaton.of(aut.factory(),NbaDetState.empty(conf),
    //                                     NbaDetState.getAcceptance(conf));
    var resultDpa = args.usePowersets() ? determinizeNbaTopo(conf) : determinizeNba(conf);

    // --------
    restoreLogLevel(oldLogLevels);
    return resultDpa;
  }

  public static <S> Automaton<NbaDetState<S>, ParityAcceptance> determinizeNba(NbaDetConf<S> conf) {
    logger.log(Level.FINE, "Start naive exploration of DPA.");
    var succHelper = new SmartSucc<>(conf);
    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
      conf.aut().original().atomicPropositions(),
      conf.aut().original().factory(),
      Set.of(NbaDetState.of(conf, conf.aut().original().initialStates())),
      NbaDetState.getAcceptance(conf)) {
      private boolean logOverridden;

      @Override
      public Edge<NbaDetState<S>> edgeImpl(NbaDetState<S> state, BitSet val) {
        if (!logOverridden) {
          overrideLogLevel(Level.parse(conf.args().verbosity()));
          logOverridden = true;
        }
        return succHelper.successor(state, val);
      }
    };
  }

  /**
   * Determinize automaton, utilizing "topological" optimization, i.e. determinize each SCC of
   * the powerset structure of the NBA separately and prune the partial DPAs (keep one bottom SCC
   * of the partial DPAs).
   * @param conf prepared determinization config (which includes the input NBA)
   * @return resulting DPA
   */
  public static <S> Automaton<Pair<Integer,NbaDetState<S>>, ParityAcceptance> determinizeNbaTopo(
      NbaDetConf<S> conf) {
    //construct powerset automaton and get its SCCs
    logger.log(Level.FINE, "Computation of powerset structure of NBA");
    var psAut = createPowerSetAutomaton(conf.aut());
    var psInitial = psAut.initialState(); //Q_0

    var psScci = SccDecomposition.of(psAut);
    //compute quick lookup from states to SCC ids
    var sccOf = new HashMap<BitSet, Integer>();
    int i = 0;
    for (var psScc : psScci.sccs()) {
      for (var st : psScc) {
        sccOf.put(st, i);
      }
      i++;
    }

    //partial DPAs
    var sccDpa = new HashMap<Integer, Automaton<NbaDetState<S>, ParityAcceptance>>();
    //map between Powerset states and suitable DPA representatives
    var repMap = new HashMap<BitSet, NbaDetState<S>>();

    i = 0; //index of SCC
    for (var psScc : psScci.sccs()) {
      //start exploration from any NBA set in PS SCC, unless it is the actual set Q_0.
      var sccInit = psScc.contains(psInitial) ? psInitial : psScc.iterator().next();

      //partial determinization only while staying inside powerset SCC
      var psSccAut
        = Views.filtered(psAut, Views.Filter.of(Set.of(sccInit), psScc::contains));

      logger.log(Level.FINE, "Partial exploration of DPA for SCC " + i);
      var ret = determinizeNbaAlongScc(psSccAut, conf);
      logger.log(Level.FINE, "resulting partial DPA " + i + " of size "
        + ret.fst().states().size());

      //store partial DPA and collect representative mappings
      sccDpa.put(i, ret.fst());
      repMap.putAll(ret.snd());

      i++;
    }

    logger.log(Level.FINE, "Combination of partial DPAs");

    //init. DPA state (of Q_0 set)
    //NOTE: we must tag the states with the SCC/partial DPA ID, as due to optimizations
    //we might get two "equal" states for two partial automata, but that _MUST_ stay separated
    var dpaInitial = Pair.of(sccOf.get(psInitial), repMap.get(psInitial));
    var toPS = new HashMap<Pair<Integer,NbaDetState<S>>, BitSet>();
    toPS.put(dpaInitial, psInitial);

    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
      conf.aut().original().atomicPropositions(),
      conf.aut().original().factory(),
      Set.of(dpaInitial),
      NbaDetState.getAcceptance(conf)) {

      @Override
      public Edge<Pair<Integer,NbaDetState<S>>> edgeImpl(
          Pair<Integer,NbaDetState<S>> state, BitSet val) {
        var pSet = toPS.get(state);   //associated powerset
        int psScc = sccOf.get(pSet); //and its SCC

        var refSuc = psAut.successors(pSet, val);
        assert refSuc.size() == 1; //powerset automaton is complete
        var sucPSet = refSuc.iterator().next(); // get the unique powerset successor
        int sucScc = sccOf.get(sucPSet);        // and its SCC

        //if same SCC -> return existing edge of partial automaton
        //if between SCCs -> edge was missing in partial -> insert new edge to a representative
        var retSt = psScc == sucScc
          ? Objects.requireNonNull(sccDpa.get(psScc).edge(state.snd(), val))
          : Edge.of(Objects.requireNonNull(repMap.get(sucPSet)));

        var ret = retSt.mapSuccessor(x -> Pair.of(sucScc, x));

        if (!toPS.containsKey(ret.successor())) { // map from detstate to pset state, if new state
          toPS.put(ret.successor(), sucPSet);
        }

        return ret;
      }
    };
  }

  /**
   * Determinize provided NBA partially, while not leaving some automaton refScc.
   * Returns partial DPA and a mapping from all reference states to some representative DPA state.
   * refScc should be some kind of powerset SCC (i.e. deterministic structure) of the NBA.
   * The exploration starts from the unique initial state of that powerset SCC automaton.
   */
  public static <S> Pair<Automaton<NbaDetState<S>, ParityAcceptance>, Map<BitSet,NbaDetState<S>>>
      determinizeNbaAlongScc(Automaton<BitSet,?> refScc, NbaDetConf<S> conf) {

    BitSet psInit = refScc.initialState(); //initial set to start exploration
    NbaDetState<S> initDpa = NbaDetState.of(conf, psInit);
    var toPS = new HashMap<NbaDetState<S>, BitSet>(); //det state -> some PS state map
    toPS.put(initDpa, psInit);

    //explore deterministic automaton partially (in the background we also explore the
    //"reference" automaton that acts as a filter, i.e. explore only as long as there is
    //a successor in the reference automaton. This should be a powerset automaton SCC!
    var succHelper = new SmartSucc<>(conf);
    //converting to mutable required to force "realizing" the automaton,
    //preventing a bug or interaction of topo + smart succ. optimizations
    //otherwise some weird bug happens when using succHelper with the "lazy" construction,
    //where the SCC decomp. claims that there are no bottom SCCs, which is obviously impossible

    //associated powerset
    //successor of powerset leaves SCC -> abort exploration here (for now)
    // should be exactly one successor
    // get the unique powerset successor
    // compute determinization successor
    // map from detstate to pset state, if new state
    // computed successor + priority
    var sccAut = new AbstractMemoizingAutomaton.EdgeImplementation<>(
        conf.aut().original().atomicPropositions(),
        conf.aut().original().factory(),
        Set.of(initDpa),
        NbaDetState.getAcceptance(conf)) {

        @Override
        public Edge<NbaDetState<S>> edgeImpl(NbaDetState<S> state, BitSet val) {
          var pSet = toPS.get(state); //associated powerset
          var refSuc = refScc.successors(pSet, val);
          if (refSuc.isEmpty()) {
            return null; //successor of powerset leaves SCC -> abort exploration here (for now)
          }
          assert refSuc.size() == 1;                  // should be exactly one successor
          var sucPSet = refSuc.iterator().next();     // get the unique powerset successor

          var suc1 = succHelper.successor(state, val); // compute determinization successor
          if (!toPS.containsKey(
            suc1.successor())) { // map from detstate to pset state, if new state
            toPS.put(suc1.successor(), sucPSet);
          }
          return suc1; // computed successor + priority
        }
      };

    //compute SCCs in partial DPA, get smallest bottom SCC
    var compScci = SccDecomposition.of(sccAut);
    int minBScc = compScci.bottomSccs().stream()
      .map(sccId -> Pair.of(sccId, compScci.sccs().get(sccId).size())) //index+size
      .reduce((a,b) ->  a.snd() <= b.snd() ? a : b).orElse(Pair.of(-1,-1)).fst();
    assert minBScc != -1;
    var repScc = compScci.sccs().get(minBScc);

    //keep only the bottom SCC
    var onlyTheBotScc = Views.Filter.of(repScc, repScc::contains);
    var trimmedPartialDpa = Views.filtered(sccAut, onlyTheBotScc);

    //get mapping between PS SCC states and a representative in bottom SCC:
    //pick any DPA state, get its PS-SCC state, explore PS-SCC from there while exploring
    //the partial DPA at the same time, assigning each PS-SCC state a DPA state
    var anyState = trimmedPartialDpa.initialStates().iterator().next();    //any entry point
    var repMap = new HashMap<BitSet, NbaDetState<S>>();
    repMap.put(toPS.get(anyState), anyState);
    var toVisit = new ArrayDeque<BitSet>();
    toVisit.push(toPS.get(anyState));
    while (!toVisit.isEmpty()) {
      var curr = toVisit.pop();
      for (var e : refScc.edgeMap(curr).entrySet()) {
        var suc = e.getKey().successor();
        var sym = e.getValue().iterator(refScc.atomicPropositions().size()).next(); // some sym
        if (!repMap.containsKey(suc)) {
          repMap.put(suc, trimmedPartialDpa.successor(repMap.get(curr), sym));
          toVisit.push(suc);
        }
      }
    }

    return Pair.of(trimmedPartialDpa, repMap);
  }

  /**
   * Create a powerset automaton of NBA where state sets are represented by BitSets.
   * @param <S> state type of underlying NBA
   * @param adjMat BitSet-based transitions for underlying NBA
   * @return the resulting powerset automaton
   */
  public static <S> Automaton<BitSet, AllAcceptance> createPowerSetAutomaton(NbaAdjMat<S> adjMat) {
    return new AbstractMemoizingAutomaton.EdgeImplementation<>(
      adjMat.original().atomicPropositions(),
      adjMat.original().factory(),
      Set.of(BitSet2.copyOf(adjMat.original().initialStates(), adjMat.stateMap()::lookup)),
      AllAcceptance.INSTANCE) {

      @Override
      public Edge<BitSet> edgeImpl(BitSet state, BitSet val) {
        return Edge.of(adjMat.powerSucc(state, val).fst());
      }
    };
  }

}
