package owl.util;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;

public final class CombinationGenerator {

  private CombinationGenerator() {}

  public static int count(int mask) {
    int n;
    int m = mask;
    for (n = 0; m > 0; ++n) {
      m &= (m - 1);
    }
    return n;
  }

  public static <R> List<List<R>> comb(List<R> input, int k) {
    List<List<R>> out = new ArrayList<>();

    for (int i = 1; i <= k; i++) {
      for (int mask = 0; mask < (1 << input.size()); mask++) {
        if (count(mask) == i) {
          List<R> list = new ArrayList<>();

          int bound = input.size();

          for (int i1 = 0; i1 < bound; i1++) {
            if ((mask & (1 << i1)) > 0) {
              list.add(input.get(i1));
            }
          }

          out.add(list);
        }
      }
    }

    return Lists.reverse(out);
  }
}
