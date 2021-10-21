/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.collections;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nullable;
import owl.util.ArraysSupport;

/**
 * Bucket-based implementation of an upward-closed set.
 */
public final class UpwardClosedSet {
  private static final long[] EMPTY_ARRAY = {};
  private static final long[][] EMPTY_BUCKETS = {};

  private static final UpwardClosedSet EMPTY = new UpwardClosedSet();
  private static final UpwardClosedSet UNIVERSE = new UpwardClosedSet(0);

  private final long[][] buckets;

  private UpwardClosedSet() {
    this.buckets = EMPTY_BUCKETS;
    assert checkInvariants();
  }

  private UpwardClosedSet(long element) {
    int i = Long.bitCount(element);
    this.buckets = new long[i + 1][];
    Arrays.fill(buckets, EMPTY_ARRAY);
    this.buckets[i] = new long[] {element};
    assert checkInvariants();
  }

  private UpwardClosedSet(long[][] buckets) {
    int size = buckets.length;

    while (0 < size && buckets[size - 1] == null) {
      size--;
    }

    this.buckets = new long[size][];
    Arrays.setAll(this.buckets, i -> {
      var bucket = buckets[i];
      return bucket == null ? EMPTY_ARRAY : bucket.clone();
    });

    assert checkInvariants();
  }

  public static UpwardClosedSet of() {
    return EMPTY;
  }

  public static UpwardClosedSet of(BitSet bitSet) {
    Preconditions.checkArgument(bitSet.length() <= Long.SIZE);

    if (bitSet.isEmpty()) {
      return UNIVERSE;
    }

    return new UpwardClosedSet(bitSet.toLongArray()[0]);
  }

  public boolean contains(BitSet bitSet) {
    Preconditions.checkArgument(bitSet.length() <= Long.SIZE);
    long element = bitSet.isEmpty() ? 0 : bitSet.toLongArray()[0];

    for (long[] bucket : buckets) {
      for (long set : bucket) {
        if ((element | set) == element) {
          return true;
        }
      }
    }

    return false;
  }

  public UpwardClosedSet intersection(UpwardClosedSet otherSet) {
    long[][] newBuckets = new long[this.buckets.length * otherSet.buckets.length][];
    long[] seenElements = new long[newBuckets.length >> 1];
    Arrays.fill(seenElements, Long.MAX_VALUE);

    for (long[] bucket1 : buckets) {
      for (long[] bucket2 : otherSet.buckets) {
        for (long element1 : bucket1) {
          if (element1 == Long.MAX_VALUE) {
            break;
          }

          for (long element2 : bucket2) {
            if (element2 == Long.MAX_VALUE) {
              break;
            }

            long intersection = element1 | element2;

            if (Arrays.binarySearch(seenElements, intersection) < 0) {
              insert(seenElements, intersection);
              insert(newBuckets, intersection);
            }
          }
        }
      }
    }

    return new UpwardClosedSet(newBuckets);
  }

  public List<BitSet> representatives() {
    return Arrays.stream(buckets)
      .flatMapToLong(Arrays::stream)
      .filter(x -> x != Long.MAX_VALUE)
      .mapToObj(x -> BitSet.valueOf(new long[] {x}))
      .toList();
  }

  public UpwardClosedSet union(UpwardClosedSet otherSet) {
    if (otherSet.buckets.length > this.buckets.length) {
      return otherSet.union(this);
    }

    long[][] newBuckets = new long[this.buckets.length][];
    long[] seenElements = new long[this.buckets.length >> 1];
    Arrays.fill(seenElements, Long.MAX_VALUE);

    Arrays.setAll(newBuckets, i -> {
      long[] bucket = this.buckets[i].clone();

      for (long element : bucket) {
        if (element == Long.MAX_VALUE) {
          break;
        }

        insert(seenElements, element);
      }

      return bucket;
    });

    for (long[] bucket : otherSet.buckets) {
      for (long element : bucket) {
        // the element is new.
        if (Arrays.binarySearch(seenElements, element) < 0) {
          insert(newBuckets, element);
        }
      }
    }

    return new UpwardClosedSet(newBuckets);
  }

  private boolean checkInvariants() {
    for (int i = 0; i < buckets.length; i++) {
      long[] bucket = buckets[i];

      for (int j = 0; j < bucket.length; j++) {
        long element = bucket[j];
        long nextElement = j < bucket.length - 1 ? bucket[j + 1] : Long.MAX_VALUE;
        assert element < nextElement
          || (element == Long.MAX_VALUE && nextElement == Long.MAX_VALUE);
        assert element == Long.MAX_VALUE || Long.bitCount(element) == i;
      }
    }

    return true;
  }

  private static void insert(long[][] buckets, long newElement) {
    int bitCount = Long.bitCount(newElement);

    for (int i = 0; i < bitCount; i++) {
      var bucket = buckets[i];

      if (bucket != null) {
        for (long oldElement : bucket) {
          if ((oldElement & newElement) == oldElement) {
            return;
          }
        }
      }
    }

    for (int i = bitCount + 1; i < buckets.length; i++) {
      var bucket = buckets[i];

      if (bucket != null) {
        for (int j = 0; j < bucket.length; j++) {
          long oldElement = bucket[j];

          if ((oldElement & newElement) == newElement) {
            bucket[j] = Long.MAX_VALUE;
          }
        }

        // Move free-space to the end of array.
        Arrays.sort(bucket);
      }
    }

    buckets[bitCount] = insert(buckets[bitCount], newElement);
  }

  private static long[] insert(@Nullable long[] bucket, long element) {
    Preconditions.checkArgument(element != Long.MAX_VALUE);

    if (bucket == null || bucket.length == 0) {
      return new long[]{
        element,        Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
        Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
    }

    if (bucket[bucket.length - 1] == Long.MAX_VALUE) {
      int insertionPoint = -(Arrays.binarySearch(bucket, element) + 1);

      if (insertionPoint < 0) {
        return bucket;
      }

      System.arraycopy(
        bucket, insertionPoint, // from
        bucket, insertionPoint + 1, // to
        bucket.length - 1 - insertionPoint);
      bucket[insertionPoint] = element;
      return bucket;
    }

    long[] newArray = Arrays.copyOf(bucket, ArraysSupport.newLength(bucket.length,
      1, /* minimum growth */
      bucket.length >> 1));
    Arrays.fill(newArray, bucket.length, newArray.length, Long.MAX_VALUE);
    return insert(newArray, element);
  }
}

