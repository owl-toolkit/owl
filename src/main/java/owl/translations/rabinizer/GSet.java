package owl.translations.rabinizer;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.GOperator;

final class GSet extends AbstractSet<GOperator> {
  @Nullable
  private final EquivalenceClass conjunction;
  private final Set<GOperator> elements;
  private final int hashCode;

  GSet(Iterable<GOperator> elements, EquivalenceClassFactory factory) {
    this.elements = ImmutableSet.copyOf(elements);
    this.conjunction =  factory.createEquivalenceClass(elements);
    hashCode = this.elements.hashCode();
  }

  @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
  GSet(Set<GOperator> elements) {
    // Special constructor for intersections
    this.elements = elements;
    this.conjunction = null;
    hashCode = this.elements.hashCode();
  }

  public EquivalenceClass conjunction() {
    if (conjunction == null) {
      throw new IllegalStateException();
    }
    return conjunction;
  }

  @Override
  public boolean contains(Object o) {
    return elements.contains(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return elements.containsAll(c);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof GSet) {
      GSet other = (GSet) o;
      return conjunction == null || other.conjunction == null
        ? elements.equals(other.elements)
        : conjunction.equals(other.conjunction);
    }
    return super.equals(o);
  }

  public void free() {
    if (conjunction != null) {
      conjunction.free();
    }
  }

  @Override
  public int hashCode() {
    assert hashCode == elements.hashCode();
    return hashCode;
  }

  public GSet intersection(GSet other) {
    return new GSet(Sets.intersection(elements, other.elements));
  }

  @Override
  public boolean isEmpty() {
    return elements.isEmpty();
  }

  @Override
  public Iterator<GOperator> iterator() {
    if (elements instanceof ImmutableSet) {
      return elements.iterator();
    }
    return Iterators.unmodifiableIterator(elements.iterator());
  }

  @Override
  public int size() {
    return elements.size();
  }

  @Override
  public String toString() {
    return elements.toString();
  }
}
