# Use Conventional Commits and Branch Naming

## Status

Accepted

## Context and Problem Statement

With multiple contributors working on Neon WES, we need consistent Git
practices for clear, scannable commit history, automated changelog potential,
and consistent branch naming for CI/CD integration.

## Decision Outcome

Chosen option: "Conventional Commits with Conventional Branch naming", because
it provides machine-readable commit history, clear communication of intent, and
integrates well with tooling.

## Commit Message Format

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

### Examples

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

## Branch Naming Format

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

### Tips

1. **Use imperative mood**: "add feature" not "added feature"
2. **Don't capitalize first letter**: "fix bug" not "Fix bug"
3. **No period at the end**: "add feature" not "add feature."
4. **Keep subject under 72 characters**
5. **Separate subject from body with blank line**
6. **Use body to explain what and why, not how**
