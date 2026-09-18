# Compile-only Compose / Android stubs

These files exist so `./tools/check-ui.sh` can type-check `composeApp/` on a machine with no
Maven Central and no Android SDK. They are **not** part of the Gradle build, are not on any
runtime classpath, and contain no behaviour — every body is `{}` or a `throw`.

## What this check can and cannot tell you

**Can:** the app's own code is well-formed Kotlin against the real API *shapes* — parameter names,
default arguments, receiver scopes, generics, imports, exhaustiveness, smart-cast legality. It
caught, on its first run, four genuine bugs: a `Modifier.align` used outside a `BoxScope`,
`clickable(onLongClick = …)` (that parameter does not exist; it is `combinedClickable`), a smart
cast on a delegated property, and a named `selector` argument to a `vararg`.

**Cannot:** anything about behaviour or rendering. The stubs never run. A stub is also only as
faithful as the signature that was copied, so a parameter that exists in real Compose but is
missing here produces a false "unresolved reference", and one that is mis-typed here can hide a
real bug. Treat a failure in this check as a lead to verify against the real API, not as proof.

## Fidelity rules for adding to these files

- Copy the real signature, including parameter names, order and defaults, from the Compose
  Multiplatform version pinned in `gradle/libs.versions.toml` (1.8.2).
- Model distinctions the compiler enforces, not just the types. `Alignment`, `Alignment.Horizontal`
  and `Alignment.Vertical` are three separate types here for that reason — collapsing them would
  let `Modifier.align(Alignment.CenterVertically)` compile inside a `Column`, which is the exact
  class of mistake this check exists to catch.
- Do not add a convenience overload to make an error go away. If the app does not compile, either
  the app or the stub is wrong; fix the one that is wrong.
- When a stub is simplified in a way that loses information (a `Color` that ignores its argument,
  a `Dp` that drops its value), say so in a comment.
