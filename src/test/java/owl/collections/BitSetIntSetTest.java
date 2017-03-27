package owl.collections;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BitSetIntSetTest {
  private final IntSortedSet baseReference;
  private final BitSetIntSet baseSet;
  private final IntSortedSet reference;
  private final BitSetIntSet set;
  private final IntSortedSet valuesInRange;

  public BitSetIntSetTest(List<Integer> input, @Nullable SubsetRange range) {
    baseReference = new IntAVLTreeSet(input);
    baseSet = new BitSetIntSet(BitSets.toSet(input));

    if (range == null) {
      this.reference = baseReference;
      this.set = baseSet;
      valuesInRange = new IntAVLTreeSet(Arrays.asList(1, 2, 6));
    } else {
      this.reference = baseReference.subSet(range.low, range.high);
      this.set = baseSet.subSet(range.low, range.high);
      valuesInRange = new IntAVLTreeSet();
      IntListIterator iterator = IntIterators.fromTo(range.low, range.high);
      int addEveryStep = 0;
      int step = 0;
      while (iterator.hasNext()) {
        int nextValue = iterator.nextInt();
        if (step == addEveryStep) {
          valuesInRange.add(nextValue);
          addEveryStep += 1;
          step = 0;
        } else {
          step += 1;
        }
      }
    }
  }

  @Parameters(name = "{0}: {1}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][] {
      {ImmutableList.of(), null},
      {ImmutableList.of(1), null},
      {ImmutableList.of(1, 2), null},
      {ImmutableList.of(1, 2), new SubsetRange(0, 2)},
      {ImmutableList.of(1, 1), null},
      {ImmutableList.of(1, 4, 5, 8), null},
      {ImmutableList.of(1, 4, 5, 8), new SubsetRange(3, 5)},
      {ImmutableList.of(1, 4, 8, 123, 1, 34, 54, 23, 650, 34), null},
      {ImmutableList.copyOf(IntStream.range(0, 30).boxed().collect(Collectors.toList())),
        new SubsetRange(10, 20)},
    });
  }

  @After
  public void checkBaseSet() {
    assertThat(baseSet, equalTo(baseReference));
  }

  @Test
  public void subSet() {
  }

  @Test
  public void testAdd() {
    assertThat(set.add(1), is(reference.add(1)));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testAddAll() {
    assertThat(set.addAll(valuesInRange), is(reference.addAll(valuesInRange)));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testAddAllOptimized() {
    BitIntSet testSet = BitSets.createBitSet();
    testSet.addAll(valuesInRange);

    assertThat(set.addAll(testSet), is(reference.addAll(valuesInRange)));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testAddAllSelf() {
    assertThat(set.addAll(reference), is(false));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testAddAllSelfOptimized() {
    //noinspection CollectionAddedToSelf
    assertThat(set.addAll(set), is(false));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testClear() {
    reference.rem(1);
    set.clear(1);
    assertThat(set, is(reference));
  }

  @Test
  public void testClearRange() {
    IntSet rangeSet = new IntArraySet();
    IntIterators.fromTo(valuesInRange.firstInt(), valuesInRange.lastInt())
      .forEachRemaining(rangeSet::add);

    reference.removeAll(rangeSet);
    set.clear(valuesInRange.firstInt(), valuesInRange.lastInt());
    assertThat(set, is(reference));
  }

  @Test(expected = CloneNotSupportedException.class)
  public void testCloneNotSupported() throws CloneNotSupportedException {
    //noinspection UseOfClone - We test it
    set.clone();
  }

  @Test
  public void testComparatorIsNull() {
    assertThat(set.comparator(), nullValue());
  }

  @Test
  public void testContains() {
    for (int i : valuesInRange) {
      assertThat(set.contains(i), is(reference.contains(i)));
    }
  }

  @Test
  public void testContainsAll() {
    assertThat(set.containsAll(reference), is(true));

    assertThat(set.containsAll(valuesInRange), is(reference.containsAll(valuesInRange)));
  }

  @Test
  public void testContainsAllOptimized() {
    //noinspection CollectionAddedToSelf
    assertThat(set.containsAll(set), is(true));

    BitIntSet testSet = BitSets.createBitSet(valuesInRange);

    assertThat(set.containsAll(testSet), is(reference.containsAll(valuesInRange)));
  }

  @Test
  public void testContainsAny() {
    assertThat(set.containsAny(reference), is(not(reference.isEmpty())));

    boolean referenceContainsAny = reference.stream().anyMatch(valuesInRange::contains);
    assertThat(set.containsAny(valuesInRange), is(referenceContainsAny));
  }

  @Test
  public void testContainsAnyOptimized() {
    //noinspection CollectionAddedToSelf
    assertThat(set.containsAny(set), is(not(reference.isEmpty())));

    BitIntSet testSet = BitSets.createBitSet(valuesInRange);

    boolean referenceContainsAny = reference.stream().anyMatch(valuesInRange::contains);
    assertThat(set.containsAny(testSet), is(referenceContainsAny));
  }

  @Test
  public void testEquals() {
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testFirstInt() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.firstInt(), is(reference.firstInt()));
  }

  @Test(expected = NoSuchElementException.class)
  public void testFirstIntEmpty() {
    assumeThat(set.isEmpty(), is(true));

    set.firstInt();
  }

  @Test
  public void testForEach() {
    IntArraySet test = new IntArraySet();
    set.forEach((IntConsumer) test::add);

    IntArraySet testReference = new IntArraySet();
    reference.forEach(testReference::add);

    assertThat(test, is(testReference));
  }

  @Test
  public void testHeadSet() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.headSet(set.lastInt()), is(reference.headSet(reference.lastInt())));
  }

  @Test
  public void testHeadSetEmpty() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.headSet(set.firstInt()).isEmpty(), is(true));
  }

  @Test
  public void testHeadSetFull() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.headSet(set.lastInt() + 1), is(set));
  }

  @Test
  public void testIsEmpty() {
    assertThat(set.isEmpty(), is(reference.isEmpty()));
  }

  @Test
  public void testIteratorEmpty() {
    assumeThat(set.isEmpty(), is(true));
    assumeThat(set.iterator().hasNext(), is(false));
    assumeThat(set.iterator().hasPrevious(), is(false));
  }

  @Test
  public void testIteratorFirst() {
    IntBidirectionalIterator iterator = set.iterator();
    assertThat(iterator.hasPrevious(), is(false));

    IntSet elements = new IntArraySet();
    while (iterator.hasNext()) {
      assertThat(elements.add(iterator.nextInt()), is(true));
    }
    assertThat(elements, is(reference));
    while (iterator.hasPrevious()) {
      assertThat(elements.add(iterator.previousInt()), is(false));
    }
  }

  @Test
  public void testIteratorLast() {
    assumeThat(set.isEmpty(), is(false));

    IntBidirectionalIterator iterator = set.iterator(set.lastInt());
    assertThat(iterator.hasNext(), is(false));

    IntSet elements = new IntArraySet();
    while (iterator.hasPrevious()) {
      assertThat(elements.add(iterator.previousInt()), is(true));
    }
    assertThat(elements, is(reference));
    while (iterator.hasNext()) {
      assertThat(elements.add(iterator.nextInt()), is(false));
    }
  }

  @Test
  public void testLastInt() {
    assumeThat(set.isEmpty(), is(false));
    assertThat(set.lastInt(), is(reference.lastInt()));
  }


  @Test(expected = NoSuchElementException.class)
  public void testLastIntEmpty() {
    assumeThat(set.isEmpty(), is(true));

    set.lastInt();
  }

  @Test
  public void testRemove() {
    assertThat(set.rem(1), is(reference.rem(1)));
  }

  @Test
  public void testRemoveAll() {
    assertThat(set.removeAll(valuesInRange), is(reference.removeAll(valuesInRange)));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testRemoveAllEmpty() {
    assertThat(set.removeAll(reference), is(not(reference.isEmpty())));
    assertThat(set.isEmpty(), is(true));
  }

  @Test
  public void testRemoveAllOptimized() {
    BitIntSet testSet = BitSets.createBitSet(valuesInRange);

    assertThat(set.removeAll(testSet), is(reference.removeAll(valuesInRange)));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testRemoveElement() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.rem(set.lastInt()), is(true));
    reference.rem(reference.lastInt());
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testRetainAll() {
    IntCollection test = new IntArraySet(Arrays.asList(1, 2, 8));

    assertThat(set.retainAll(test), is(reference.retainAll(test)));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testRetainAllEmpty() {
    assertThat(set.retainAll(IntLists.EMPTY_LIST), is(not(reference.isEmpty())));
    assertThat(set.isEmpty(), is(true));
  }

  @Test
  public void testRetainAllOptimized() {
    BitIntSet testSet = BitSets.createBitSet(valuesInRange);

    assertThat(set.retainAll(testSet), is(reference.retainAll(valuesInRange)));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testRetainAllSelf() {
    //noinspection CollectionAddedToSelf
    assertThat(set.retainAll(set), is(false));
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testSet() {
    set.set(1);
    reference.add(1);
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testSetRange() {
    IntSet rangeSet = new IntArraySet();
    IntIterators.fromTo(valuesInRange.firstInt(), valuesInRange.lastInt())
      .forEachRemaining(rangeSet::add);

    reference.addAll(rangeSet);
    set.set(valuesInRange.firstInt(), valuesInRange.lastInt());
    assertThat(set, equalTo(reference));
  }

  @Test
  public void testSize() {
    assertThat(set.size(), is(reference.size()));
  }

  @Test
  public void testSubset() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.subSet(valuesInRange.firstInt(), valuesInRange.lastInt()),
      is(reference.subSet(valuesInRange.firstInt(), valuesInRange.lastInt())));
  }

  @Test
  public void testSubsetEmpty() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.subSet(set.firstInt(), set.firstInt()).isEmpty(), is(true));
  }

  @Test
  public void testSubsetFull() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.subSet(set.firstInt(), set.lastInt() + 1), equalTo(set));
  }

  @Test
  public void testTailSet() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.tailSet(set.firstInt() + 1), is(reference.tailSet(reference.firstInt() + 1)));
  }

  @Test
  public void testTailSetEmpty() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.tailSet(set.lastInt() + 1).isEmpty(), is(true));
  }

  @Test
  public void testTailSetFull() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.tailSet(set.firstInt()), is(set));
  }

  @Test
  public void testTailSetSingle() {
    assumeThat(set.isEmpty(), is(false));

    assertThat(set.tailSet(set.lastInt()).size(), is(1));
  }

  @Test
  public void testToStringNoError() {
    //noinspection ResultOfMethodCallIgnored
    set.toString();
  }

  private static final class SubsetRange {
    public final int high;
    public final int low;

    public SubsetRange(int low, int high) {
      this.low = low;
      this.high = high;
    }

    @Override
    public String toString() {
      return String.format("[%d, %d)", low, high);
    }
  }
}
