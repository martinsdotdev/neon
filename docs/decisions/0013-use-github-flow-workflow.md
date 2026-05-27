---
status: "accepted"
date: 2026-04-14
decision-makers: project owner
consulted:
informed: future contributors
---

# Use GitHub Flow workflow

## Context and Problem Statement

The team needs a branching strategy that supports continuous delivery while
remaining simple enough for a small team. The workflow must integrate with our
Conventional Commits and branch naming conventions. Which branching model do we
adopt?

## Decision Drivers

- Simple enough for a small team to follow without ceremony.
- Supports continuous delivery from a single production-ready branch.
- Requires code review via pull requests before merge.
- Integrates with the Conventional Commits and branch-naming conventions ([ADR-0012](0012-use-conventional-commits-and-branches.md)).

## Considered Options

- GitHub Flow (one long-lived branch, short-lived feature branches, PRs)
- Git Flow (develop / release / hotfix branch model)
- Trunk-based development (commit to main behind feature flags)

## Decision Outcome

Chosen option: "GitHub Flow", because it provides the right balance of
simplicity and safety for a small team with continuous deployment, while still
requiring code review via pull requests.

### Rules

- Only one long-lived branch: **master**
- All features developed on short-lived branches
- All merges to master go through pull requests
- **Linear history required** (no merge commits)

### Consequences

- **Good**, because a single long-lived branch (master) keeps the mental model simple.
- **Good**, because every change lands through a reviewed PR.
- **Good**, because linear history (no merge commits) keeps the log readable and bisectable.
- **Good**, because it pairs naturally with continuous deployment from master.
- **Neutral**, because keeping branches current means rebasing onto master rather
  than merging — a habit to maintain.
- **Bad**, because it has less ceremony than Git Flow for coordinating long-lived
  release branches, which larger release-train teams may need.

### Confirmation

Confirmed by the protected-branch rules on master (PR required, CI required,
linear history enforced) and by the absence of merge commits in the history.

## Pros and Cons of the Options

### GitHub Flow

- **Good**, because minimal branches and rules; fast to learn and apply.
- **Good**, because PR-per-change enforces review and CI gating.
- **Bad**, because it has no built-in story for parallel maintenance releases.

### Git Flow

- **Good**, because it formalizes release and hotfix branches for versioned releases.
- **Bad**, because the develop/release/hotfix machinery is heavy for a small team
  doing continuous delivery.

### Trunk-based development

- **Good**, because it minimizes branch divergence and merge pain.
- **Bad**, because committing to main behind feature flags adds flag infrastructure
  this project does not yet need.
- **Bad**, because it weakens the PR-review gate unless paired with strict
  pre-merge checks.

## More Information

### Workflow

```
master (production-ready)
  |
  +-- feat/wave-dashboard ------ PR --+
  |                                    |
  +-- fix/task-completion ------ PR --+-- master
  |                                    |
  +-- docs/architecture-book --- PR --+
```

### Step-by-Step

```bash
# 1. Start from up-to-date master
git checkout master
git pull origin master

# 2. Create feature branch
git checkout -b feat/wave-dashboard

# 3. Make changes with conventional commits
git commit -m "feat(web): add wave summary cards"
git commit -m "feat(web): add task progress table"

# 4. Push and create PR
git push -u origin feat/wave-dashboard
gh pr create --title "feat(web): add wave dashboard"

# 5. After approval and merge, clean up
git checkout master
git pull origin master
git branch -d feat/wave-dashboard
```

### Merge Strategy

**Squash merge** (preferred): combines all branch commits into one.

```bash
gh pr merge --squash
```

**Rebase merge** (for clean multi-commit PRs): replays each commit.

```bash
gh pr merge --rebase
```

### Keeping Branches Up to Date

Rebase your branch (never merge master into it):

```bash
git checkout feat/my-feature
git rebase master
git push --force-with-lease
```

### Protected Branch Rules

- [x] Require pull request before merging
- [x] Require status checks to pass (CI)
- [x] Require conversation resolution
- [x] Require linear history (no merge commits)
- [x] Do not allow bypassing the above settings

### Related

- The commit and branch naming conventions this workflow assumes — see [ADR-0012](0012-use-conventional-commits-and-branches.md).
