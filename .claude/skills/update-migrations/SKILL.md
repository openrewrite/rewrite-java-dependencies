---
name: update-migrations
description: Refresh src/main/resources/migrations.csv from jonathanlermitage/oga-maven-plugin and open a PR. Use when asked to update the relocated or old groupId migrations, or the RelocatedDependencyCheck data; this replaces the retired migrations.yml workflow until a scheduler takes over.
---

# Update the relocated dependency migrations

The weekly `migrations.yml` workflow that refreshed `src/main/resources/migrations.csv` was retired when CGP took over CI, and the required `cgp` check rejects direct pushes to `main`. Until scheduling has a permanent home, run this by hand, about once a week, and land the result through a PR.

## 1. Branch from a fresh `main`

Run everything from the root of rewrite-java-dependencies. If the working tree has uncommitted changes, stop and ask the user what to do with them instead of stashing them.

```bash
git fetch origin main
git switch -c "migrations/$(date -u +%Y-%m-%d)" origin/main
```

## 2. Regenerate the data

```bash
./gradlew updateMigrations 2>&1 | tee build/update-migrations.log
```

`updateMigrations` creates or refreshes a shallow clone of `jonathanlermitage/oga-maven-plugin` under `build/oga-maven-plugin`, then overwrites `migrations.csv` from its official and unofficial definitions. `migrations-denylist.csv` is applied at runtime, so it survives regeneration.

If Gradle can't resolve the `rewrite` snapshots (for example, `... has no target nodes`), add `-Preleasing` to build against the latest `rewrite` release instead. The parser doesn't need snapshot APIs.

Maven Central's `429 Too Many Requests` can fail Gradle's dependency resolution. Maven Central rate-limits per address, and Java prefers IPv4. If `curl -4 -sI https://repo1.maven.org/maven2/` gets a 429 but `curl -6` doesn't, rerun with `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Addresses=true` exported. Otherwise, wait and retry.

## 3. Check the output before committing

- The task must end with `BUILD SUCCESSFUL`.
- `grep '^Parsing ' build/update-migrations.log` names the oga-maven-plugin commit that was parsed.
- If `git diff --quiet -- src/main/resources/migrations.csv` succeeds, there is nothing to update: skip the commit and PR, and say so.
- Row counts should only grow or stay about the same. Compare `wc -l src/main/resources/migrations.csv` against `git show origin/main:src/main/resources/migrations.csv | wc -l`. If the CSV lost more than a few rows, or came out empty, stop and report it.
- `grep -c '^Skipping ' build/update-migrations.log` counts rows the parser left out: targets that aren't a coordinate, unofficial rows with several proposals and old coordinates with conflicting targets. As of October 2026 that's 33. A sharp rise means the upstream format changed, so stop and report it.
- Read the added and changed rows in `git diff`. `RelocatedDependencyCheck` only changes a dependency when the target publishes the version in use, but a new row whose target isn't published at all, or which names a successor with a different API rather than a relocation, belongs in `migrations-denylist.csv`. Don't edit the denylist yourself; list such rows in the PR description for a reviewer to decide.

## 4. Open the PR

Name the parsed oga-maven-plugin commit in the commit and PR, so the data can be traced back to its source.

```bash
oga=$(git -C build/oga-maven-plugin log -1 --format='%h')

git add src/main/resources/migrations.csv
git commit -m "Relocated dependency migrations as of oga-maven-plugin $oga"
git push -u origin HEAD
gh pr create --base main --title "Relocated dependency migrations as of oga-maven-plugin $oga" --body-file <(
  printf 'Regenerated `migrations.csv` with `./gradlew updateMigrations` from jonathanlermitage/oga-maven-plugin@%s.\n' "$oga")
gh pr merge --auto --squash
```

Add the rows to consider for the denylist from step 3 to the PR description, and leave out `gh pr merge --auto` in that case so a reviewer sees them before the PR merges. Otherwise, the PR merges once its `cgp` check passes.

Finish by switching back to the branch you started on, and report the PR URL.
