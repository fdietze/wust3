# wust3

Run

```bash
sbt dev
```

and 

```bash
yarn install
npx firebase emulators:start
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

# Datastructure draft

```scala
type TopicId = String
type VersionId = String
type LiteralId = TopicId

sealed trait Topic {
  def id: TopicId
  def version: VersionId // primary key
  def parent: Option[VersionId] // Parent version, the root has no parent
  def timestamp: DateTime
}

object Topic {
  // A topic that can contain arbitrary content encoded as a string.
  case class Literal(
    value: String,
    // The literal can be validated against multiple schemas.
    // A schema is a literal itself. It's connected component
    // can be interpreted as an ER diagram.
    // A schema can be validated against other schemas itself,
    // modeling (multiple) inheritance.
    schema: Set[LiteralId],
  ) extends Topic

  // represents a key-value (name-target) store for a topic
  case class Binding(
    subject: TopicId,
    predicate: LiteralId,
    obj: TopicId,
    cardinality: Option[Int], // only relevant when the subject is a schema
    // min/max cardinality
  ) extends Topic

  // For referencing a specific version as a topic
  case class Version(
    value: VersionId,
  ) extends Topic

  // Represents a deleted topic
  case class Tombstone(
  ) extends Topic

  // no versionid for proposals?
  // ordered versionids
  // TRAVERSEALS needed here
  // "this proposal responds to another proposal"
  case class Proposal(
    user: UserId,
    versions: Seq[(TopicId, VersionId)],
  ) extends Topic
}
```
