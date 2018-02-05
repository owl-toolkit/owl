package owl.game.output.aig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import org.junit.Test;
import owl.game.output.AigConsumer;
import owl.game.output.AigFactory;
import owl.game.output.AigPrintable;
import owl.game.output.AigerPrinter;
import owl.game.output.LabelledAig;

public class AigPrintableTest {
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
  public void testPrinting() {
    SampleAiger sample = new SampleAiger();
    AigerPrinter consumer = new AigerPrinter(false);
    sample.feedTo(consumer);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    consumer.print(baos);
    assertThat(baos.toString(), is(equalTo(sampleAigerOutput)));
  }

  @Test
  public void testBinaryPrinting() {
    SampleAiger sample = new SampleAiger();
    AigerPrinter consumer = new AigerPrinter(true);
    sample.feedTo(consumer);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    consumer.print(baos);
    assertThat(baos.toString(), is(equalTo(sampleBinaryAigerOutput)));
  }
}
