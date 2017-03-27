package owl.collections;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.function.IntConsumer;

public final class IntIterators2 {
  private IntIterators2() {
  }

  public static void forEach(IntSet intSet, IntConsumer consumer) {
    forEachRemaining(intSet.iterator(), consumer);
  }

  public static void forEachRemaining(IntIterator iterator, IntConsumer consumer) {
    while (iterator.hasNext()) {
      consumer.accept(iterator.nextInt());
    }
  }
}
