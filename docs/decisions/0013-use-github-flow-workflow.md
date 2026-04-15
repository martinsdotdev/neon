# Use GitHub Flow Workflow

## Status

Accepted

## Context and Problem Statement

The team needs a branching strategy that supports continuous delivery while
remaining simple enough for a small team. The workflow must integrate with our
Conventional Commits and branch naming conventions.

## Decision Outcome

Chosen option: "GitHub Flow", because it provides the right balance of
simplicity and safety for a small team with continuous deployment, while still
requiring code review via pull requests.

### Rules

- Only one long-lived branch: **master**
- All features developed on short-lived branches
- All merges to master go through pull requests
- **Linear history required** (no merge commits)

## Workflow

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
