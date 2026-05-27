---
status: "accepted"
date: 2026-04-14
decision-makers: project owner
consulted:
informed: future contributors
---

# Use Conventional Commits and branch naming

## Context and Problem Statement

With multiple contributors working on Neon WES, we need consistent Git
practices for clear, scannable commit history, automated changelog potential,
and consistent branch naming for CI/CD integration. Which commit and branch
conventions should the project adopt?

## Decision Drivers

- Commit history should be scannable and communicate intent at a glance.
- History should be machine-readable, enabling automated changelogs and semver inference.
- Branch names should be consistent for CI/CD integration and discoverability.
- Low onboarding cost: a convention contributors already know or can learn quickly.

## Considered Options

- Conventional Commits with Conventional Branch naming
- Free-form commit messages and branch names
- An emoji-based convention (e.g., gitmoji)

## Decision Outcome

Chosen option: "Conventional Commits with Conventional Branch naming", because
it provides machine-readable commit history, clear communication of intent, and
integrates well with tooling.

### Consequences

- **Good**, because `<type>(<scope>)` prefixes make history scannable and
  communicate intent at a glance.
- **Good**, because the structured format is machine-readable, enabling automated
  changelogs and semver inference.
- **Good**, because consistent `<type>/<description>` branch names integrate
  cleanly with CI/CD and are easy to discover.
- **Good**, because the convention is widely known, so onboarding cost is low.
- **Neutral**, because contributors must learn the type vocabulary (`feat`,
  `fix`, ...) — a small, one-time cost.
- **Bad**, because without commit linting the convention relies on discipline and
  review to stay consistent.

### Confirmation

Confirmed in code review and by the existing history: commits follow
`<type>(<scope>): <description>` and branches follow `<type>/<description>`.

## Pros and Cons of the Options

### Conventional Commits + Conventional Branch naming

- **Good**, because it is a widely adopted, documented standard with ecosystem
  tooling (commitlint, semantic-release).
- **Good**, because commit type and branch type align, keeping the two conventions coherent.
- **Bad**, because it is more rigid than free-form; even trivial commits need a type prefix.

### Free-form messages and branch names

- **Good**, because there is nothing to learn and no friction at commit time.
- **Bad**, because history is inconsistent and not machine-readable; no automated changelog.
- **Bad**, because branch names vary per contributor, complicating CI rules and discovery.

### Emoji-based convention (gitmoji)

- **Good**, because emoji prefixes are visually scannable.
- **Bad**, because emoji are less greppable and less tooling-friendly than text types.
- **Bad**, because the emoji-to-intent mapping is not standardized across teams.

## More Information

### Commit Message Format

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**Types:**

| Type       | Description                                     |
| ---------- | ----------------------------------------------- |
| `feat`     | New feature                                     |
| `fix`      | Bug fix                                         |
| `docs`     | Documentation only                              |
| `style`    | Formatting, no code change                      |
| `refactor` | Code change that neither fixes nor adds feature |
| `perf`     | Performance improvement                         |
| `test`     | Adding or correcting tests                      |
| `build`    | Build system or external dependencies           |
| `ci`       | CI configuration                                |
| `chore`    | Other changes that don't modify src or test     |

**Scope** (optional): Module or area affected (e.g., `web`, `core`, `wave`,
`task`, `app`, `common`)

**Breaking Changes:** Add `!` before the colon

```
feat(web)!: redesign wave dashboard layout
```

#### Examples

```bash
# Feature
feat(web): add wave release dashboard

# Bug fix
fix(core): prevent double allocation on shortpick

# Documentation
docs: update architecture decision records

# Breaking change
feat(web)!: change task list API response shape

# With body and footer
fix(web): handle FEFO allocation edge case

When two lots have the same expiration date, fall back to
production date for tiebreaking.

Closes #42
```

### Branch Naming Format

```
<type>/<description>
```

Use kebab-case. Branch type should match commit type.

**Examples:**

```bash
feat/wave-dashboard
fix/task-completion
docs/architecture-book
refactor/fsd-structure
hotfix/auth-session
chore/upgrade-dependencies
```

#### Tips

1. **Use imperative mood**: "add feature" not "added feature"
2. **Don't capitalize first letter**: "fix bug" not "Fix bug"
3. **No period at the end**: "add feature" not "add feature."
4. **Keep subject under 72 characters**
5. **Separate subject from body with blank line**
6. **Use body to explain what and why, not how**

### Related

- The branching workflow that uses these conventions — see [ADR-0013](0013-use-github-flow-workflow.md).
