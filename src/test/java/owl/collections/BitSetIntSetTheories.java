package owl.collections;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class BitSetIntSetTheories {
  @DataPoints
  public static Collection<DataPointHolder> dataPoints() {
    return ImmutableList.<DataPointHolder>builder()
      .add(new DataPointHolder(ImmutableList.of(), null))
      .add(new DataPointHolder(ImmutableList.of(1), new SubsetRange(1, 1)))
      .add(new DataPointHolder(ImmutableList.of(1), new SubsetRange(2, 4)))
      .add(new DataPointHolder(ImmutableList.of(1, 2), null))
      .add(new DataPointHolder(ImmutableList.of(2, 3), null))
      .add(new DataPointHolder(ImmutableList.of(1, 2), new SubsetRange(2, 3)))
      .add(new DataPointHolder(ImmutableList.of(2, 3), new SubsetRange(2, 3)))
      .add(new DataPointHolder(ImmutableList.of(1, 2, 3), null))
      .add(new DataPointHolder(ImmutableList.of(1, 2, 3), new SubsetRange(0, 2)))
      .add(new DataPointHolder(ImmutableList.of(4, 5, 6), null))
      .add(new DataPointHolder(ImmutableList.of(2, 3, 4, 5, 6), new SubsetRange(3, 5)))
      .add(new DataPointHolder(ImmutableList.of(2, 3, 4, 5, 6), new SubsetRange(4, 5)))
      .add(new DataPointHolder(ImmutableList.of(0, 8), null))
      .add(new DataPointHolder(ImmutableList.of(0, 2, 7), new SubsetRange(6, 10)))
      .add(new DataPointHolder(ImmutableList.of(0, 1, 2, 3, 4, 5, 6, 7), null))
      .build();
  }

  @Theory(nullsAccepted = false)
  public void testAddAll(DataPointHolder one, DataPointHolder other) {
    assumeThat(one.range.contains(other.range), is(true));

    one.set.addAll(other.set);
    one.reference.addAll(other.reference);
    assertThat(one.set, is(one.reference));

    one.checkConsistency();
    other.checkConsistency();
  }

  @Theory(nullsAccepted = false)
  public void testContainsAll(DataPointHolder one, DataPointHolder other) {
    assumeThat(one.range.contains(other.range), is(true));

    assertThat(one.set.containsAll(other.set), is(one.reference.containsAll(other.reference)));

    one.checkConsistency();
    other.checkConsistency();
  }

  @Theory(nullsAccepted = false)
  public void testRemoveAll(DataPointHolder one, DataPointHolder other) {
    assumeThat(one.range.contains(other.range), is(true));

    one.set.removeAll(other.set);
    one.reference.removeAll(other.reference);
    assertThat(one.set, is(one.reference));

    one.checkConsistency();
    other.checkConsistency();
  }

  @Theory(nullsAccepted = false)
  public void testRetainAll(DataPointHolder one, DataPointHolder other) {
    assumeThat(one.range.contains(other.range), is(true));

    one.set.retainAll(other.set);
    one.reference.retainAll(other.reference);
    assertThat(one.set, is(one.reference));

    one.checkConsistency();
    other.checkConsistency();
  }

  private static final class DataPointHolder {
    final IntSortedSet baseReference;
    final BitSetIntSet baseSet;
    final IntSortedSet reference;
    final BitSetIntSet set;
    final SubsetRange range;

    DataPointHolder(List<Integer> input, @Nullable SubsetRange range) {
      baseReference = new IntAVLTreeSet(input);
      baseSet = new BitSetIntSet(BitSets.toSet(input));

      if (range == null) {
        this.reference = baseReference;
        this.set = baseSet;
        this.range = new SubsetRange(0, Integer.MAX_VALUE);
      } else {
        this.reference = baseReference.subSet(range.low, range.high);
        this.set = baseSet.subSet(range.low, range.high);
        this.range = range;
      }
    }

    void checkConsistency() {
      assertThat(baseSet, is(baseReference));
    }

    @Override
    public String toString() {
      return set.toString();
    }
  }
}
