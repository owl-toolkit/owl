# Workarounds

This file documents workarounds used within Owl to side-step issues with underlying Java platform.

## native-image

- https://github.com/oracle/graal/issues/3973: Provide hand-written `equals` and `hashCode` for
  Records until the linked issue is resolved.
- https://github.com/oracle/graal/issues/3398: Spin off a new thread for `main` in order to avoid a
  `StackOverflow` due to Musl's smaller default stack size.
