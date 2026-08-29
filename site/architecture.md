# Architecture

agent-kit is organised in three layers. The only thing crossing into your project is the `ChatModel` interface.

![agent-kit layered architecture](/architecture-en.svg)

## Layer 1 — Your project

You own the lifecycle: when agents run, how state is stored, what the domain types are. You provide a `ChatModel` adapter and, optionally, your own extension implementations.

## Layer 2 — Capability components

Three groups, each independently usable:

- **Execution** — `toolcalling`, `planning`, `session`, `struct`, `mcp`
- **Quality and governance** — `eval`, `checkpoint`, `obs`, `hitl`, `router`
- **Foundation** — `extension`, `security`

Nothing in one group depends on another group's internals, so adopting one capability does not drag in the rest.

## Layer 3 — Infrastructure

Only `jackson` for JSON and `slf4j` for logging. No framework, no reactive library, no server.

## Design rules

These constraints are why the library stays embeddable:

1. **One model boundary.** Only `ChatModel` talks to an LLM. Every component uses it and nothing else.
2. **No lifecycle ownership.** Components are created and called by you; none of them start threads or register shutdown hooks behind your back.
3. **Degrade, never break.** An enhancement that fails falls back to the behaviour that would have happened without it.
4. **Validate at construction.** A `TaskPlan` with a cycle cannot exist; you find out when you build it, not when you run it.
5. **Domain types stay yours.** Generic parameters like `LlmJudge<F extends FindingLike>` mean you never wrap your types in library-specific ones.
