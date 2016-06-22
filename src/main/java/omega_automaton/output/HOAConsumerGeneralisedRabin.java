
package omega_automaton.output;

import java.util.*;

import com.google.common.collect.BiMap;

import jhoafparser.consumer.HOAConsumer;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedRabinAcceptance;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;

public class HOAConsumerGeneralisedRabin extends HOAConsumerExtended {

    private GeneralisedRabinAcceptance<AutomatonState<?>> acceptance;

    public HOAConsumerGeneralisedRabin(HOAConsumer hoa, ValuationSetFactory valuationSetFactory, BiMap<String,Integer> aliases, AutomatonState<?> initialState, GeneralisedRabinAcceptance<AutomatonState<?>> accCond, int size) {
        super(hoa, valuationSetFactory, aliases, accCond, initialState, size);
        this.acceptance = accCond;
    }

    @Override
    public void addEdge(ValuationSet key, AutomatonState<?> end) {
        List<Integer> accSets = new ArrayList<>();
        Set<ValuationSet> realEdges = acceptance.getMaximallyMergedEdgesOfEdge(currentState, key);
        for (ValuationSet edgeKey : realEdges) {
            accSets.clear();
            accSets = acceptance.getInvolvedAcceptanceNumbers(currentState, edgeKey);
            addEdgeBackend(edgeKey, end, accSets);
        }
    }
}

