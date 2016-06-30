
package omega_automaton.output;

import java.util.*;
import java.util.Map.Entry;

import com.google.common.collect.BiMap;

import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.AllAcceptance;
import omega_automaton.acceptance.GeneralisedRabinAcceptance;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.collections.valuationset.ValuationSetFactory;

public class HOAConsumerGeneralisedRabin<St extends AutomatonState<?>> extends HOAConsumerExtended {

    private GeneralisedRabinAcceptance<St> acceptance;

    public HOAConsumerGeneralisedRabin(HOAConsumer hoa, ValuationSetFactory valuationSetFactory, BiMap<String, Integer> aliases, St initialState,
            GeneralisedRabinAcceptance<St> accCond, int size) {
        super(hoa, valuationSetFactory, aliases, (accCond==null ? new AllAcceptance(): accCond), initialState, size);
        this.acceptance = (accCond==null ? new GeneralisedRabinAcceptance<St>(Collections.emptyList()): accCond);

        Map<String, List<Object>> map = acceptance.miscellaneousAnnotations();

        try {
            for (Entry<String, List<Object>> entry : map.entrySet()) {
                hoa.addMiscHeader(entry.getKey(), entry.getValue());
            }
        } catch (HOAConsumerException ex) {
            // We wrap HOAConsumerException into an unchecked exception in order
            // to keep the interfaces clean and tidy.
            throw new RuntimeException(ex);
        }

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

