# Neon WES — Context

The ubiquitous language of the Warehouse Execution System. This file is the
home for terms whose precise meaning has been decided deliberately; the
encyclopedic warehouse and technical vocabulary lives in
[`docs/book/06-appendices/appendix-f-glossary.md`](docs/book/06-appendices/appendix-f-glossary.md).

## Language

**Completion cascade**:
The chain of outcomes that a single task completion sets in motion — stock
consumption, a shortpick replacement, handling-unit routing, wave completion,
and consolidation-group picking completion. It is decided as one unit
(`TaskCompletionCascade`) over pre-loaded state, not a sequence of side effects.
_Avoid_: completion flow, post-completion steps, completion pipeline.

**Shortpick replacement**:
The new `Planned` task created for the unfulfilled remainder when a pick
completes with less than the requested quantity. It inherits the original
task's wave and order and references it as its parent, so an open remainder
blocks wave and picking completion until it too reaches a terminal state.
_Avoid_: remainder task, follow-up task, re-pick.

**Stock position**:
The on-hand quantity of one SKU/lot in one warehouse area, tracked as four
buckets — available, allocated, reserved, blocked — whose sum is the on-hand
total. Allocation moves available → allocated; completing a pick consumes
allocated and deallocates any shortpicked remainder back to available.
_Avoid_: stock record, inventory position, stock level.

**Verified mirror**:
A duplicated encoding of the same knowledge (the sync/async completion
cascade, the TS/Scala permission list, a projection table's SELECT and INSERT)
that is kept honest by an adapter at the seam — a shell over a shared decision,
a contract test, or a single schema owner — so the copies cannot silently
drift apart.
_Avoid_: sync'd copy, parallel definition.
