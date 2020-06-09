package owl.util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class CombinationGenerator {
  private CombinationGenerator() {
  }

  public static <S> List<S> get(List<S> elements, int mask) {
    return IntStream.range(0, elements.size())
      .filter(i -> (mask & (1 << i)) > 0)
      .mapToObj(i -> elements.get(i))
      .collect(Collectors.toList());
  }

  public static int count(int mask) {
    int n;
    int m = mask;
    for (n = 0; m > 0; ++n) {
      m &= (m - 1);
    }
    return n;
  }

  public static <R> List<List<R>> comb(List<R> input, int k) {
    LinkedList<List<R>> out = new LinkedList<>();
    for (int i = 1; i <= k; i++) {
      for (int mask = 0; mask < (1 << input.size()); mask++) {
        if (count(mask) == i) {
          out.push(get(input, mask));
        }
      }
    }
    return out;
  }

  public static <R> Set<List<R>> combFast(Set<R> input, int k) {
    if (input.isEmpty()) {
      return Set.of();
    }
    assert k > 0;

    int i = 1;
    Set<List<R>> out = new HashSet<>();

    input.forEach(in -> {
      out.add(List.of(in));
    });

    while (i < k) {
      Set<List<R>> toAdd = new HashSet<>();
      out.forEach(e -> {
        input.forEach(in -> {
          var tmp = new LinkedList<>(e);
          tmp.add(in);
          toAdd.add(tmp);
        });
      });
      out.addAll(toAdd);
      i++;
    }

    return out;
  }

  public static <R> List<List<R>> combFast(List<R> input, int k) {
    if (input.isEmpty()) {
      return List.of();
    }
    assert k > 0;

    int i = 1;
    List<List<R>> out = new LinkedList<>();

    input.forEach(in -> {
      out.add(List.of(in));
    });

    while (i < k) {
      List<List<R>> toAdd = new LinkedList<>();
      out.forEach(e -> {
        input.forEach(in -> {
          var tmp = new LinkedList<>(e);
          tmp.add(in);
          toAdd.add(tmp);
        });
      });
      out.addAll(toAdd);
      i++;
    }

    return out;
  }

  public static <S> List<List<S>> kComb(List<S> input, int k) {
    LinkedList<List<S>> out = new LinkedList<>();
    for (int i = 1; i <= k; i++) {
      out.addAll(comb(input, i));
    }
    return out;
  }
}
