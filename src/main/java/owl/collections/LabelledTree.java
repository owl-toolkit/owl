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

package owl.collections;

import java.util.List;
import java.util.stream.Stream;
import owl.util.annotation.CEntryPoint;

public abstract class LabelledTree<L1, L2> {
  private LabelledTree() {
  }

  public abstract Stream<L2> leavesStream();

  public static final class Leaf<L1, L2> extends LabelledTree<L1, L2> {
    private final L2 label;

    public Leaf(L2 label) {
      this.label = label;
    }

    @CEntryPoint
    public L2 getLabel() {
      return label;
    }

    @Override
    public Stream<L2> leavesStream() {
      return Stream.of(label);
    }
  }

  public static final class Node<L1, L2> extends LabelledTree<L1, L2> {
    private final L1 label;
    private final List<LabelledTree<L1, L2>> children;

    public Node(L1 label, List<LabelledTree<L1, L2>> children) {
      this.label = label;
      this.children = List.copyOf(children);
    }

    @CEntryPoint
    public L1 getLabel() {
      return label;
    }

    @CEntryPoint
    public List<LabelledTree<L1, L2>> getChildren() {
      return children;
    }

    @Override
    public Stream<L2> leavesStream() {
      return children.stream().flatMap(LabelledTree::leavesStream);
    }
  }
}
