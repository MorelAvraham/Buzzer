# Contributing

The course grades the Git activity of each member. Please follow these rules.

## Commit from your own account

- Each member commits their own work from their own GitHub account.
- Set your name and the email of your GitHub account once on each computer:

```bash
git config --global user.name "Your Name"
```

```bash
git config --global user.email "you@example.com"
```

- If you worked with a teammate on a commit, add a line at the end of the message: `Co-authored-by: Name <email>`.
- Do not commit for someone else.

## Small steps

We work in small steps. Each step aims to answer one open question quickly. The course calls the stages skateboard, bike and car (see the roadmap in [README.md](README.md)). A good task takes one to three days and ends with something you can run or show.

## Branches

Never commit straight to `main`. Make a branch for each task:

| Prefix | For | Example |
|---|---|---|
| `feature/` | New code | `feature/ble-scan` |
| `fix/` | A bug fix | `fix/release-frame-off-by-one` |
| `exp/` | An experiment U1 to U6 | `exp/u1-release-height` |
| `docs/` | Docs only | `docs/consent-form` |

```bash
git switch -c feature/ble-scan
```

## Commit messages

Start with the folder, then say what changed, in the present tense:

```
firmware: read WHO_AM_I from the LSM6DSV16X
vision: add --draw option to pose_to_csv.py
docs: add U1 result to experiments.md
```

Make small commits. One commit, one change.

## Pull requests

1. Push your branch and open a pull request into `main`.
2. Fill in the template. Link the issue.
3. Ask one teammate to review. Merge after one approval.
4. Delete the branch after the merge.

## Issues

- Use the **Task** template for normal work and the **Experiment** template for U1 to U6.
- Assign yourself when you start, so two people do not do the same thing.
- Put the right label on it: `hardware`, `firmware`, `app`, `vision`, `analysis`, `experiment` or `docs`.

## Numbers in the docs

Every number must say what it is: a **target**, an **estimate** or a **measured** result. For measured results, write how many shots and players they come from. Do not write "works" or "tested" unless it was tested, and say how.

## Data

No videos, raw recordings or names in Git. See [data/README.md](data/README.md).
