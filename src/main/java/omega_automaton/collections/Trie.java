/*
 * Copyright (C) 2016  (See AUTHORS)
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

package omega_automaton.collections;

import com.google.common.collect.Lists;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Trie<T> {

    private final Node<T> root;

    public Trie() {
        root = new Node<>();
    }

    /**
     * Adds the list to the Trie.
     * @param element
     * @return true if the element was not present before the operation.
     */
    public boolean add(List<T> element) {
        Node<T> node = get(element);
        boolean present = node.terminal;
        node.terminal = true;
        return !present;
    }

    public Optional<List<T>> suffix(List<T> prefix, Set<T> elements) {
        return suffix(prefix, elements::contains, elements.size());
    }

    private Optional<List<T>> suffix(List<T> prefix, Predicate<T> predicate, int depth) {
        Optional<List<T>> suffix = Optional.ofNullable(get(prefix).findFirst(predicate, depth));
        return suffix.map(Lists::reverse);
    }

    public List<List<T>> suffixes(List<T> prefix, Set<T> elements) {
        return suffixes(prefix, elements::contains, elements.size());
    }

    private List<List<T>> suffixes(List<T> prefix, Predicate<T> predicate, int depth) {
        return get(prefix).find(predicate, depth).stream().map(Lists::reverse).collect(Collectors.toList());
    }

    private Node<T> get(List<T> element) {
        Node<T> current = root;

        for (T t : element) {
            current = current.getSuccessor(t);
        }

        return current;
    }

    private static class Node<T> {
        private Map<T, Node<T>> successors = new HashMap<>();
        private boolean terminal;

        private Node<T> getSuccessor(T node) {
            return successors.computeIfAbsent(node, k -> new Node<>());
        }

        @Nullable
        private List<T> findFirst(Predicate<T> continueWithBranch, @Nonnegative int depth) {
            if (depth == 0) {
                return terminal ? new ArrayList<>() : null;
            }

            for (Map.Entry<T, Node<T>> entry : successors.entrySet()) {
                T label = entry.getKey();
                Node<T> successor = entry.getValue();

                if (continueWithBranch.test(label)) {
                    List<T> suffix = successor.findFirst(continueWithBranch, depth - 1);

                    if (suffix != null) {
                        suffix.add(label);
                        return suffix;
                    }
                }
            }

            return null;
        }

        private List<List<T>> find(Predicate<T> continueWithBranch, @Nonnegative int depth) {
            List<List<T>> suffixes = new ArrayList<>();

            if (terminal && depth == 0) {
                suffixes.add(new ArrayList<>());
            }

            for (Map.Entry<T, Node<T>> entry : successors.entrySet()) {
                T label = entry.getKey();
                Node<T> successor = entry.getValue();

                if (continueWithBranch.test(label) && depth > 0) {
                    List<List<T>> suffixes2 = successor.find(continueWithBranch, depth - 1);
                    suffixes2.forEach(list -> list.add(label));
                    suffixes.addAll(suffixes2);
                }
            }

            return suffixes;
        }
    }
}
