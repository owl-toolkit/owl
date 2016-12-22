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

package translations.tlsf2arena;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.BuchiAcceptance;
import omega_automaton.acceptance.ParityAcceptance;
import omega_automaton.collections.Collections3;
import omega_automaton.collections.valuationset.ValuationSet;
import owl.automaton.edge.Edge;
import translations.ltl2parity.ParityAutomaton;

import java.io.*;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

class Any2BitArena {

    private <S extends AutomatonState<S>> void writeState(final DataOutputStream nodeStream, final DataOutputStream edgeStream, final Writer labelStream, final ParityAutomaton<S> automaton, final S state) throws IOException {
        BitSet fstAlpha = state.getSensitiveAlphabet();
        BitSet sndAlpha = (BitSet) fstAlpha.clone();

        fstAlpha.and(fstAlphabet);
        sndAlpha.and(sndAlphabet);

        // Compute intermediate states and transitions.
        int beforeSecondaryNodes = secondaryNodes;

        fstChoice:
        for (BitSet fstChoice : Collections3.powerSet(fstAlpha)) {
            Map<S, Int2ObjectMap<ValuationSet>> intermediateStates = new HashMap<>();

            for (BitSet sndChoice : Collections3.powerSet(sndAlpha)) {
                sndChoice.or(fstChoice);
                Edge<S> edge = automaton.getSuccessor(state, sndChoice);

                if (edge == null) {
                    if (firstPlayer == Player.System) {
                        continue fstChoice;
                    } else {
                        continue;
                    }
                }

                final int color = edge.acceptanceSetStream().findFirst().orElse(this.colors);
                Int2ObjectMap<ValuationSet> colorMap = intermediateStates.get(edge);

                if (colorMap == null) {
                    colorMap = new Int2ObjectArrayMap<>();
                    intermediateStates.put(edge.getSuccessor(), colorMap);
                }

                ValuationSet vs = colorMap.get(color);

                if (vs == null) {
                    vs = automaton.getFactory().createValuationSet(sndChoice, sndAlpha);
                    colorMap.put(color, vs);
                } else {
                    vs.add(sndChoice);
                }
            }

            // Write Node
            secondaryNodes++;
            nodeStream.writeInt(-secondaryNodes);
            nodeStream.writeInt(edges);

            for (Map.Entry<S, Int2ObjectMap<ValuationSet>> entry : intermediateStates.entrySet()) {
                for (Int2ObjectMap.Entry<ValuationSet> entry2 : entry.getValue().int2ObjectEntrySet()) {
                    edgeStream.writeInt(ids.get(entry.getKey()));
                    edgeStream.writeInt(entry2.getIntKey());
                    edges++;

                    if (labelStream != null) {
                        labelStream.write(entry2.getValue().toExpression().toString());
                        labelStream.write('\n');
                    }

                    entry2.getValue().free();
                }
            }
        }

        nodeStream.writeInt(ids.get(state));
        nodeStream.writeInt(edges);

        for (int i = beforeSecondaryNodes + 1; i <= secondaryNodes; i++) {
            edgeStream.writeInt(-i);
            edgeStream.writeInt(colors);
            edges++;
        }
    }

    enum Player {System, Environment}

    private Player firstPlayer;
    private BitSet fstAlphabet;
    private BitSet sndAlphabet;

    int primaryNodes;
    int secondaryNodes;
    int edges;

    Object2IntMap<AutomatonState<?>> ids;
    int colors;

    private <S extends AutomatonState<S>> void setUp(Automaton<S, ?> automaton, Player firstPlayer, BitSet envAlphabet) {
        if (!(automaton.getAcceptance() instanceof ParityAcceptance) && !(automaton.getAcceptance() instanceof BuchiAcceptance)) {
            throw new RuntimeException("Unsupported acceptance: " + automaton.getAcceptance());
        }

        this.firstPlayer = firstPlayer;
        BitSet sysAlphabet = (BitSet) envAlphabet.clone();
        sysAlphabet.flip(0, automaton.getFactory().getSize());

        this.fstAlphabet = (firstPlayer == Player.Environment) ? envAlphabet : sysAlphabet;
        this.sndAlphabet = (firstPlayer == Player.System) ? envAlphabet : sysAlphabet;

        primaryNodes = 0;
        secondaryNodes = 0;
        edges = 0;

        ids = new Object2IntOpenHashMap<>(automaton.size());

        for (AutomatonState<?> state : automaton.getStates()) {
            ids.put(state, primaryNodes);
            primaryNodes += 1;
        }

        if (automaton.getAcceptance() instanceof BuchiAcceptance) {
            colors = 2;
        } else {
            colors = automaton.getAcceptance().getAcceptanceSets();
        }
    }

    private void writeHeader(File nodeFile, File edgeFile) throws IOException {
        try (RandomAccessFile nodeStream = new RandomAccessFile(nodeFile, "rwd");
             RandomAccessFile edgeStream = new RandomAccessFile(edgeFile, "rwd")) {
            nodeStream.writeInt(secondaryNodes + primaryNodes);
            edgeStream.writeInt(edges);
        }
    }

    <S extends AutomatonState<S>> void writeBinary(ParityAutomaton<S> automaton, Player firstPlayer, BitSet envAlphabet, File nodeFile, File edgeFile) throws IOException {
        setUp(automaton, firstPlayer, envAlphabet);

        try (DataOutputStream nodeStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(nodeFile)));
             DataOutputStream edgeStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(edgeFile)))) {

            nodeStream.writeInt(0);
            nodeStream.writeInt(ids.get(automaton.getInitialState()));
            nodeStream.writeInt(firstPlayer.ordinal());

            // Write empty header.
            edgeStream.writeInt(0);
            edgeStream.writeInt(colors);

            for (S state : automaton.getStates()) {
                writeState(nodeStream, edgeStream, null, automaton, state);
            }
        }

        writeHeader(nodeFile, edgeFile);
    }

    void readBinary(File nodeFile, File edgeFile) throws IOException {
        try (DataInputStream nodeStream = new DataInputStream(new BufferedInputStream(new FileInputStream(nodeFile)));
             DataInputStream edgeStream = new DataInputStream(new BufferedInputStream(new FileInputStream(edgeFile)))) {
            System.out.println("Number of Nodes: " + nodeStream.readInt());
            System.out.println("Number of Edges: " + edgeStream.readInt());
            System.out.println("Initial State: " + nodeStream.readInt());
            System.out.println("First Player: " + (nodeStream.readInt() == Player.Environment.ordinal() ? "env" : "sys"));
            int maxColor = edgeStream.readInt();
            System.out.println("Max Color: " + maxColor);

            int currentNode = nodeStream.readInt();
            int currentEdgePos = nodeStream.readInt();

            if (currentEdgePos != 0) {
                throw new RuntimeException();
            }

            int nextNode;
            int nextEdgePos;

            try {
                while (true) {
                    nextNode = nodeStream.readInt();
                    nextEdgePos = nodeStream.readInt();

                    System.out.println("Node: " + currentNode);

                    for (; currentEdgePos < nextEdgePos; currentEdgePos++) {
                        int desti = edgeStream.readInt();
                        int color = edgeStream.readInt();

                        System.out.println(currentNode + " -(" + color + ")-> " + desti);

                        if (desti * currentNode > 0) {
                            throw new RuntimeException("Edge corrupt");
                        }

                        if (color < 0 || color > maxColor) {
                            throw new RuntimeException("Color corrupt");
                        }
                    }

                    currentNode = nextNode;
                }
            } catch (EOFException ex) {

            }
        }

    }
}
