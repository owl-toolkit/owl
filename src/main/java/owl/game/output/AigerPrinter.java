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

package owl.game.output;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import owl.collections.Collections3;

public class AigerPrinter implements AigConsumer {

  private final boolean binaryOutput;
  private List<String> inputNames;
  private List<LabelledAig> latches;
  private List<String> latchNames;
  private List<LabelledAig> outputs;
  private List<String> outputNames;
  private List<String> comments;

  public AigerPrinter(boolean binaryOutput) {
    this.binaryOutput = binaryOutput;
    this.inputNames = new ArrayList<>();
    this.latches = new ArrayList<>();
    this.latchNames = new ArrayList<>();
    this.outputs = new ArrayList<>();
    this.outputNames = new ArrayList<>();
    this.comments = new ArrayList<>();
  }

  @Override
  public int addInput(String name) {
    inputNames.add(name);
    return inputNames.size();
  }

  @Override
  public int addLatch(String names, LabelledAig circuit) {
    latchNames.add(names);
    latches.add(circuit);
    return inputNames.size() + latchNames.size();
  }

  @Override
  public void addOutput(String name, LabelledAig circuit) {
    outputNames.add(name);
    outputs.add(circuit);
  }

  @Override
  public void addComment(String comment) {
    comments.add(comment);
  }

  private int visitAig(BiMap<Aig, Integer> index, int startIndex, Aig root) {
    assert root != null : "You must provide a valid aig to start from.";
    int maxIndex = startIndex;

    // we maintain the invariant: everything in the stack is not in index
    Stack<Aig> toVisit = new Stack<>();
    if (index.get(root) == null) {
      assert root != null;
      toVisit.push(root);
    }

    while (!toVisit.isEmpty()) {
      assert (toVisit.peek() != null);
      if (toVisit.peek().isVariable()
          || toVisit.peek().isConstant()) {  // just pop
        toVisit.pop();
      } else if (index.get(toVisit.peek().left) == null) {
        assert toVisit.peek().left != null
          : "All internal nodes should have exactly two children";
        toVisit.push(toVisit.peek().left);
      } else if (index.get(toVisit.peek().right) == null) {
        assert toVisit.peek().right != null
          : "All internal nodes should have exactly two children";
        toVisit.push(toVisit.peek().right);
      } else {
        // not a variable, constant, or internal node
        // with children not yet visited => just add it
        index.put(toVisit.pop(), maxIndex);
        maxIndex += 2;
      }
    }

    return maxIndex;
  }

  private int aig2lit(BiMap<Aig, Integer> index, LabelledAig aig) {
    Integer i = index.get(aig.aig);
    assert i != null : "Make sure the element is in the computed map!";
    return aig.isNegated ? i + 1 : i;
  }

  private void encode(PrintWriter writer, int x) {
    int ch;
    while ((x & ~0x7f) != 0) {
      ch = (x & 0x7f) | 0x80;
      writer.write((char) ch);
      x >>= 7;
    }
    ch = x;
    writer.write((char) ch);
  }

  private int aig2lit(BiMap<Aig, Integer> index, Aig aig, boolean negated) {
    Integer i = index.get(aig);
    assert i != null : "Make sure the element is in the computed map!";
    return negated ? i + 1 : i;
  }

  public void print(OutputStream ostream) {
    PrintWriter writer = new PrintWriter(ostream);
    print(writer);
    writer.flush();
  }

  public void print(PrintWriter writer) {
    // compute variable indices
    BiMap<Aig, Integer> index = HashBiMap.create();
    // add the false and true constants to the map
    index.put(Aig.FALSE, 0);
    // add the other variables and gates
    int maxIndex = (inputNames.size() + latches.size() + 1) * 2;
    int varIndex = 2;

    for (int i = 1; i <= inputNames.size(); i++) {
      index.put(new Aig(i), varIndex);
      varIndex += 2;
    }

    for (int i = 1; i <= latchNames.size(); i++) {
      index.put(new Aig(i + inputNames.size()), varIndex);
      maxIndex = visitAig(index, maxIndex, latches.get(i - 1).aig);
      varIndex += 2;
    }

    // add the outputs too
    for (LabelledAig output : outputs) {
      maxIndex = visitAig(index, maxIndex, output.aig);
    }

    /*
     * Printing is different for binary and nonbinary formats
     */
    if (binaryOutput) {
      writer.println("aig "
        + ((maxIndex - 2) / 2) + ' '
        + inputNames.size() + ' '
        + latches.size() + ' '
        + outputs.size() + ' '
        + ((maxIndex - varIndex) / 2));
      // print latches
      for (LabelledAig latch : latches) {
        writer.println(aig2lit(index, latch));
      }
      // print all outputs
      for (LabelledAig output : outputs) {
        writer.println(aig2lit(index, output));
      }
      // print all and-gate deltas
      for (int j = (inputNames.size() + latches.size() + 1) * 2; j < maxIndex; j += 2) {
        Aig gate = index.inverse().get(j);
        assert gate != null : "All indices should have been assigned";
        int leftLit = aig2lit(index, gate.left, gate.leftIsNegated);
        int rightLit = aig2lit(index, gate.right, gate.rightIsNegated);
        assert rightLit >= leftLit : "Visiting sequence must be wrong!";
        encode(writer, rightLit - leftLit);
      }

      writer.println();
    } else {
      // print header
      writer.println("aag "
        + ((maxIndex - 2) / 2) + ' '
        + inputNames.size() + ' '
        + latches.size() + ' '
        + outputs.size() + ' '
        + ((maxIndex - varIndex) / 2));
      // print inputs
      for (int i = 1; i <= inputNames.size(); i++) {
        writer.println((i * 2));
      }
      // print latches
      int i = inputNames.size() + 1;
      for (LabelledAig latch : latches) {
        writer.println((i * 2) + " " + aig2lit(index, latch));
        i++;
      }
      // print all outputs
      for (LabelledAig output : outputs) {
        writer.println(aig2lit(index, output));
      }
      // print all and-gates
      for (int j = (inputNames.size() + latches.size() + 1) * 2;
           j < maxIndex; j += 2) {
        Aig gate = index.inverse().get(j);
        assert gate != null : "All indices should have been assigned";
        writer.println(j + " " + aig2lit(index, gate.left, gate.leftIsNegated)
                       + ' ' + aig2lit(index, gate.right, gate.rightIsNegated));
      }
    }

    /*
     * From here onwards, both formats coincide
     */

    // print symbol table
    Collections3.forEachIndexed(inputNames, (j, name) -> {
      if (!name.isEmpty()) {
        writer.println("i" + j + ' ' + name);
      }
    });

    Collections3.forEachIndexed(latchNames, (j, name) -> {
      if (!name.isEmpty()) {
        writer.println("l" + j + ' ' + name);
      }
    });

    Collections3.forEachIndexed(outputNames, (j, name) -> {
      if (!name.isEmpty()) {
        writer.println("o" + j + ' ' + name);
      }
    });

    // print comments
    if (!comments.isEmpty()) {
      writer.println("c");
      for (String comment : comments) {
        writer.println(comment);
      }
    }
  }
}
