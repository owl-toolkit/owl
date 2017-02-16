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

package owl.collections;

import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * it is designed to be a stack that can handle push, pop, peek, contains and
 * isEmpty in constant time under the assumption, that the hash function of the
 * elements is distributed reasonably well. However, as the TrivialTarjanStack,
 * some subclasses may take longer in theory, but they might be faster, because
 * we are using only rather small automata.
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!Important!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * This class always assumes the Objects to be unique, i.e. no Object can be in
 * the stack twice
 */
public class TarjanStack<E> extends AbstractCollection<E> implements Deque<E> {
  private final Set<E> elementsInTheStack;
  private final Deque<E> stack;

  public TarjanStack() {
    stack = new ArrayDeque<>();
    elementsInTheStack = new HashSet<>();
  }

  @Override
  public boolean add(E element) {
    elementsInTheStack.add(element);
    return stack.add(element);
  }

  @Override
  public void addFirst(E element) {
    stack.addFirst(element);
    elementsInTheStack.add(element);
  }

  @Override
  public void addLast(E element) {
    stack.addLast(element);
    elementsInTheStack.add(element);
  }

  @Override
  public void clear() {
    elementsInTheStack.clear();
    stack.clear();
  }

  @Override
  public boolean contains(Object element) {
    return elementsInTheStack.contains(element);
  }

  @Nonnull
  @Override
  public Iterator<E> descendingIterator() {
    return stack.descendingIterator();
  }

  @Override
  public E element() {
    return stack.element();
  }

  @Override
  public E getFirst() {
    return stack.getFirst();
  }

  @Override
  public E getLast() {
    return stack.getLast();
  }

  @Override
  public boolean isEmpty() {
    return elementsInTheStack.isEmpty();
  }

  @Nonnull
  @Override
  public Iterator<E> iterator() {
    return stack.iterator();
  }

  @Override
  public boolean offer(E element) {
    elementsInTheStack.add(element);
    return stack.offer(element);
  }

  @Override
  public boolean offerFirst(E element) {
    elementsInTheStack.add(element);
    return stack.offerFirst(element);
  }

  @Override
  public boolean offerLast(E element) {
    elementsInTheStack.add(element);
    return stack.offerLast(element);
  }

  @Override
  public E peek() {
    return stack.peek();
  }

  @Override
  public E peekFirst() {
    return stack.peekFirst();
  }

  @Override
  public E peekLast() {
    return stack.peekLast();
  }

  @Override
  public E poll() {
    E element = stack.poll();
    elementsInTheStack.remove(element);
    return element;
  }

  @Override
  public E pollFirst() {
    E element = stack.pollFirst();
    elementsInTheStack.remove(element);
    return element;
  }

  @Override
  public E pollLast() {
    E element = stack.pollLast();
    elementsInTheStack.remove(element);
    return element;
  }

  @Override
  public E pop() {
    E element = stack.pop();
    elementsInTheStack.remove(element);
    return element;
  }

  @Override
  public void push(E element) {
    stack.push(element);
    elementsInTheStack.add(element);
  }

  @Override
  public E remove() {
    E element = stack.remove();
    elementsInTheStack.remove(element);
    return element;
  }

  @Override
  public boolean remove(Object element) {
    elementsInTheStack.remove(element);
    return stack.remove(element);
  }

  @Override
  public E removeFirst() {
    E element = stack.removeFirst();
    elementsInTheStack.remove(element);
    return element;
  }

  @Override
  public boolean removeFirstOccurrence(Object element) {
    if (this.contains(element)) {
      this.remove(element);
      return true;
    }

    return false;
  }

  @Override
  public E removeLast() {
    E element = stack.removeLast();
    elementsInTheStack.remove(element);
    return element;
  }

  @Override
  public boolean removeLastOccurrence(Object element) {
    return removeFirstOccurrence(element);
  }

  @Override
  public int size() {
    return stack.size();
  }

  @Nonnull
  @Override
  public Object[] toArray() {
    return stack.toArray();
  }

  @Nonnull
  @Override
  public <T> T[] toArray(@Nonnull T[] array) {
    return stack.toArray(array);
  }
}
