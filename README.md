# wust3

Run
```
sbt dev
```

Then point your browser to <http://localhost:12345/>



## Core design principles
- Everything is a Topic (same id space).
- No in-database traversals needed, e.g. schemas can be validated locally.

## Data Structure
- Every Topic (subject) can have a key-value store (bindings), where key (predicate) and value (object) are also topics.
- Content is stored in Literal topics, encoded as a string.
- TODO: mime types for literals?
- Topics and its bindings form a generalized hypergraph (n-ary edges, no distinction between node and edge).

## Versioning
- Topics have a version history, allows to define snapshots of whole subgraphs at a specific point in time.
- Topics can be deleted, which means the latest version becomes a tombstone. Restore is a version after a tombstone.
- TODO: current version (HEAD) marker

## Schemas
- Literal topics can be validated against schemas, which are literal topics themselves.
- A schema's connected component can be interpreted as an ER diagram.
- A schema can be validated against other schemas, thereby modeling (multiple) inheritance.
- When a Binding is interpreted as schema, it can have a cardinality for the object side.
- TODO: Attach preferred visualization to schema?

## Change Proposals
- Changes to a topic can be proposed in the form of a new version that has an older version as a parent.
- When a Proposal is accepted, the current version marker of the topic points to the proposed version
- TODO: Can literals be created without limit?
- TODO: Threshold for bindings depends on thresholds of subject and object?



# Open Problems
- VersionId = Content adressable hash that includes parent?
- access read/write Permissions
- Domain system
- External ids, like messages from slack or WikiData, PURL
