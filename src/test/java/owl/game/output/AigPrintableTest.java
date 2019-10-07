/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AigPrintableTest {
  private static final String sampleAigerOutput =
    "aag 7 2 1 2 4\n"
      + "2\n"
      + "4\n"
      + "6 14\n"
      + "6\n"
      + "7\n"
      + "8 2 6\n"
      + "10 3 7\n"
      + "12 9 11\n"
      + "14 4 12\n"
      + "i0 enable\n"
      + "i1 reset\n"
      + "l0 latch\n"
      + "o0 Q\n"
      + "c\n"
      + "This is a\n"
      + "two-line comment\n";

  @SuppressWarnings("StringConcatenationMissingWhitespace")
  private static final String sampleBinaryAigerOutput =
    "aig 7 2 1 2 4\n"
      + "14\n"
      + "6\n"
      + "7\n"
      + ((char) 0x04)
      + ((char) 0x04)
      + ((char) 0x02)
      + ((char) 0x08)
      + '\n'
      + "i0 enable\n"
      + "i1 reset\n"
      + "l0 latch\n"
      + "o0 Q\n"
      + "c\n"
      + "This is a\n"
      + "two-line comment\n";

  @Test
  void testPrinting() {
    SampleAiger sample = new SampleAiger();
    AigerPrinter consumer = new AigerPrinter(false);
    sample.feedTo(consumer);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    consumer.print(baos);
    assertEquals(sampleAigerOutput, baos.toString(StandardCharsets.UTF_8));
  }

  @Test
  void testBinaryPrinting() {
    SampleAiger sample = new SampleAiger();
    AigerPrinter consumer = new AigerPrinter(true);
    sample.feedTo(consumer);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    consumer.print(baos);
    assertEquals(baos.toString(), sampleBinaryAigerOutput);
  }

  private static final class SampleAiger implements AigPrintable {
    @Override
    public void feedTo(AigConsumer consumer) {
      AigFactory factory = new AigFactory();
      consumer.addInput("enable");
      consumer.addInput("reset");
      LabelledAig enable = factory.getNode(1);
      LabelledAig reset = factory.getNode(2);
      LabelledAig latch = factory.getNode(3);
      LabelledAig gate3 = factory.conjunction(factory.not(enable), factory.not(latch));
      LabelledAig gate2 = factory.conjunction(enable, latch);
      LabelledAig gate1 = factory.conjunction(factory.not(gate2), factory.not(gate3));
      LabelledAig gate0 = factory.conjunction(reset, gate1);
      consumer.addLatch("latch", gate0);
      consumer.addOutput("Q", latch);
      consumer.addOutput("", factory.not(latch));
      consumer.addComment("This is a");
      consumer.addComment("two-line comment");
    }
  }
}
