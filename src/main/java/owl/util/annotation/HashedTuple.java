package owl.util.annotation;

import org.immutables.value.Value;

@Value.Style(allParameters = true,
             visibility = Value.Style.ImplementationVisibility.PACKAGE,
             typeImmutable = "*Tuple",
             of = "create",
             defaults = @Value.Immutable(builder = false, copy = false, prehash = true))
public @interface HashedTuple {
}
