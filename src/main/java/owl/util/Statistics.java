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

package owl.util;

import com.google.common.math.Stats;
import java.math.BigInteger;
import java.util.Arrays;

public final class Statistics {

  private Statistics() {}

  public static double arithmeticMean(int... values) {
    return Arrays.stream(values).summaryStatistics().getAverage();
  }

  public static double geometricMean(int... values) {
    if (values.length == 0) {
      return Double.NaN;
    }

    var product = BigInteger.ONE;

    for (int value : values) {
      product = product.multiply(BigInteger.valueOf(value));
    }

    return Math.pow(Math.E, Math.log(product.doubleValue()) / (double) values.length);
  }

  public static double median(int... values) {
    if (values.length == 0) {
      return Double.NaN;
    }

    var valuesCopy = values.clone();

    Arrays.sort(valuesCopy);
    int middle = valuesCopy.length / 2;

    if (valuesCopy.length % 2 == 1) {
      return valuesCopy[middle];
    } else {
      return (valuesCopy[middle - 1] + valuesCopy[middle]) / 2.0;
    }
  }

  public static double standardDeviation(int... values) {
    if (values.length == 0) {
      return Double.NaN;
    }

    return Stats.of(values).populationStandardDeviation();
  }
}
