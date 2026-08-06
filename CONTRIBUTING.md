# Contributing to BlueHour

## Branch workflow

`main` and `develop` are protected integration branches. All changes must be made on a task branch and merged through a pull request.

```text
feature/*, fix/*, chore/*, docs/*, refactor/*, test/*
                         ↓ pull request
                      develop
                         ↓ release pull request
                        main
```

1. Fetch the latest remote branches.
2. Create a task branch from `origin/develop`.
3. Keep the change focused and add or update relevant tests.
4. Open a pull request targeting `develop`.
5. Merge `develop` into `main` only through a release pull request after CI and release QA pass.

Use `hotfix/*` from `main` only for an explicitly approved emergency production fix. After a hotfix is released, merge it back into `develop` so the branches do not diverge.

Direct pushes, force pushes, and branch deletion are not allowed for `main` or `develop`.

## Validation

Run the checks relevant to the changed area before requesting review.

### Backend

```bash
cd backend
./gradlew test
./gradlew build
```

### Frontend

```bash
cd frontend
npm run lint
npm test -- --run
npm run build
```

## Pull request checklist

- The pull request targets `develop`, unless it is a release from `develop` to `main`.
- The diff contains no unrelated refactoring or generated artifacts.
- Behavior changes include appropriate tests.
- Documentation and environment-variable requirements are current.
- Required CI checks pass before merge.
