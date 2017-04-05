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

package owl.translations.tlsf2arena;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Iterators;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.collections.ints.BitSets;

final class Any2BitArena<S> {

  int colors = 0;
  int edges = 0;
  @Nullable
  Object2IntMap<S> ids = null;
  int primaryNodes = 0;
  int secondaryNodes = 0;
  @Nullable
  private Player firstPlayer = null;
  @Nullable
  private BitSet fstAlphabet = null;
  @Nullable
  private BitSet sndAlphabet = null;

  void readBinary(File nodeFile, File edgeFile) throws IOException {
    try (DataInputStream nodeStream =
           new DataInputStream(new BufferedInputStream(new FileInputStream(nodeFile)));
         DataInputStream edgeStream =
           new DataInputStream(new BufferedInputStream(new FileInputStream(edgeFile)))) {
      System.out.println("Number of Nodes: " + nodeStream.readInt());
      System.out.println("Number of Edges: " + edgeStream.readInt());
      System.out.println("Initial State: " + nodeStream.readInt());
      System.out.println(
        "First Player: " + (nodeStream.readInt() == Player.ENVIRONMENT.ordinal() ? "env" : "sys"));
      int maxColor = edgeStream.readInt();
      System.out.println("Max Color: " + maxColor);

      int currentEdgePos = nodeStream.readInt();
      checkState(currentEdgePos == 0);
      int currentNode = nodeStream.readInt();

      //noinspection NestedTryStatement
      try {
        // TODO This might need an overhaul
        //noinspection InfiniteLoopStatement - Stopped by EOFException
        while (true) {
          int nextNode = nodeStream.readInt();
          int nextEdgePos = nodeStream.readInt();

          System.out.println("Node: " + currentNode);

          for (; currentEdgePos < nextEdgePos; currentEdgePos++) {
            int destination = edgeStream.readInt();
            int color = edgeStream.readInt();

            System.out.println(currentNode + " -(" + color + ")-> " + destination);

            checkState(destination * currentNode <= 0, "Edge corrupt");
            checkState(color >= 0 && color <= maxColor, "Color corrupt");
          }
          currentNode = nextNode;
        }
      } catch (EOFException ignored) {
        // Ignored
      }
    }

  }

  private void setUp(Automaton<? extends S, ?> automaton, Player firstPlayer, BitSet envAlphabet) {
    checkArgument(automaton.getAcceptance() instanceof ParityAcceptance
        || automaton.getAcceptance() instanceof BuchiAcceptance,
      "Unsupported acceptance: %s", automaton.getAcceptance());

    this.firstPlayer = firstPlayer;
    @SuppressWarnings("UseOfClone")
    BitSet sysAlphabet = (BitSet) envAlphabet.clone();
    sysAlphabet.flip(0, automaton.getFactory().getSize());

    this.fstAlphabet = (firstPlayer == Player.ENVIRONMENT) ? envAlphabet : sysAlphabet;
    this.sndAlphabet = (firstPlayer == Player.SYSTEM) ? envAlphabet : sysAlphabet;

    primaryNodes = 0;
    secondaryNodes = 0;
    edges = 0;

    ids = new Object2IntOpenHashMap<>(automaton.stateCount());

    for (S state : automaton.getStates()) {
      ids.put(state, primaryNodes);
      primaryNodes += 1;
    }

    if (automaton.getAcceptance() instanceof BuchiAcceptance) {
      colors = 2;
    } else {
      colors = automaton.getAcceptance().getAcceptanceSets();
    }
  }

  void writeBinary(Automaton<S, ParityAcceptance> automaton, Player firstPlayer,
    BitSet envAlphabet, File nodeFile, File edgeFile) throws IOException {
    assert ids != null;
    setUp(automaton, firstPlayer, envAlphabet);

    try (DataOutputStream nodeStream = new DataOutputStream(
      new BufferedOutputStream(new FileOutputStream(nodeFile)));
         DataOutputStream edgeStream = new DataOutputStream(
           new BufferedOutputStream(new FileOutputStream(edgeFile)))) {

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

  private void writeHeader(File nodeFile, File edgeFile) throws IOException {
    try (RandomAccessFile nodeStream = new RandomAccessFile(nodeFile, "rwd");
         RandomAccessFile edgeStream = new RandomAccessFile(edgeFile, "rwd")) {
      nodeStream.writeInt(secondaryNodes + primaryNodes);
      edgeStream.writeInt(edges);
    }
  }

  private void writeState(DataOutputStream nodeStream, DataOutputStream edgeStream,
    @Nullable Writer labelStream, Automaton<S, ?> automaton, S state) throws IOException {
    assert ids != null;

    BitSet fstAlpha = new BitSet();
    fstAlpha.set(0, automaton.getFactory().getSize());
    //noinspection UseOfClone
    BitSet sndAlpha = (BitSet) fstAlpha.clone();

    fstAlpha.and(fstAlphabet);
    sndAlpha.and(sndAlphabet);

    // Compute intermediate states and transitions.
    int beforeSecondaryNodes = secondaryNodes;

    //noinspection LabeledStatement
    fstChoice:
    for (BitSet fstChoice : BitSets.powerSet(fstAlpha)) {
      Map<S, Int2ObjectMap<ValuationSet>> intermediateStates = new HashMap<>();

      for (BitSet sndChoice : BitSets.powerSet(sndAlpha)) {
        sndChoice.or(fstChoice);
        Edge<S> edge = automaton.getEdge(state, sndChoice);

        if (edge == null) {
          if (firstPlayer == Player.SYSTEM) {
            continue fstChoice;
          } else {
            continue;
          }
        }

        @SuppressWarnings("ConstantConditions")
        int color = Iterators.getNext(edge.acceptanceSetIterator(), this.colors);
        Int2ObjectMap<ValuationSet> colorMap = intermediateStates
          .computeIfAbsent(edge.getSuccessor(), k -> new Int2ObjectArrayMap<>());

        ValuationSet vs = colorMap.get(color);

        if (vs == null) {
          ValuationSet newVs = automaton.getFactory().createValuationSet(sndChoice, sndAlpha);
          colorMap.put(color, newVs);
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

  enum Player {
    SYSTEM, ENVIRONMENT
  }
}
