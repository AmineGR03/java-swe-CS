# Java SWE Assessment — CodeSignal-style practice

A timed Java coding assessment with a proctored-feel exam environment, an
offline auto-grader, and a 60-problem revision book.

Built as preparation for a **BCG X–style screening round**: four problems in
seventy minutes on CodeSignal, at final-year engineering level. No dynamic
programming, no shortest paths, no exotic data structures — fluent Java
collections, careful reading, and edge cases handled without being reminded.

| | |
|---|---|
| **Part A** — General Coding | 4 independent tasks · 70 min · 50 pts |
| **Part B** — Industry Coding | 1 problem, 4 progressive levels · 90 min · 50 pts |
| **Grader** | 117 checks, zero dependencies beyond a JDK |
| **Revision book** | 60 LeetCode-style problems with full Java solutions |

---

## Requirements

- **JDK 17 or newer** on your `PATH` (`java -version`). That is the only hard
  requirement — no Maven, no Gradle, no libraries, no internet.
- Python 3 + `reportlab`, and a LaTeX install, **only** if you want to rebuild
  the PDFs. The PDFs are committed, so you do not need either to use them.

---

## Launching the test

```bash
.\exam.ps1
```

Then open **http://localhost:8711** and pick a part. On macOS or Linux use
`./exam.sh`.

That is the whole setup. The server compiles itself from source on first run.

### What the environment does

It behaves like a proctored assessment rather than a practice page:

- **The clock runs on the server, not in the page.** Reloading, closing the
  tab, stopping the page's JavaScript, even restarting the server, does not
  give you a second back — the attempt resumes with the time it had left.
- **At zero you are submitted automatically.** A scheduled task grades your
  last autosave whether or not a browser is connected. Your work is saved a
  second or so after you stop typing, and at least every fifteen seconds.
- **No correctness feedback while the clock runs.** The only button is
  *Compile check*, which returns `javac` diagnostics and nothing else. Scores
  exist only after you submit.
- **One submission per attempt.** Submitting ends the part.

The UI gives you the problem statement on the left, an editor with line
numbers, auto-indent and bracket pairing on the right, task tabs that mark
which files you have touched, and a countdown that turns amber at ten minutes
and red at two.

### Trying the environment without burning 70 minutes

```bash
.\exam.ps1 -DemoSeconds 120
```

Two minutes per part, every other behaviour identical. On macOS/Linux:
`./exam.sh --demo-seconds 120`.

### Where your work goes

Each attempt writes `attempts/<timestamp>-part<A|B>/` containing the files you
submitted, `report.json`, and a readable `summary.txt`. `src/` is never
modified, so the starter files stay clean for your next attempt.

Other options: `-Port 9000` to serve elsewhere. `Ctrl+C` stops the server.

---

## Using the PDFs

Three documents, meant to be used in this order.

### 1. `Revision-Book.pdf` — start here

**41 pages, 60 problems, graded easy to hard.** Every problem has a complete
Java solution, its complexity, and a **Trap** note naming the specific mistake
that breaks submissions.

| Chapter | Content |
|---|---|
| 0 | Java toolkit — which collection for which job, idioms, the ten gotchas |
| 1–3 | Strings · Arrays and two pointers · Hashing and counting |
| 4–5 | Sorting, intervals and comparators · Matrices |
| 6–7 | Recursion, BFS and DFS · Stacks and queues |
| 8 | **Data processing** — closest to what a consulting-tech screen asks |
| 9 | Stretch: above the bar, for when the rest is comfortable |

**How to drill it.** Give yourself the budget printed on each problem — Easy 8
minutes, Medium 15, Hard 25. Write the solution without looking. Then read the
Trap note: it is worth more than the solution, because it is the thing that
actually costs points.

Read Chapter 0 once before anything else. Chapters 1–8 are the exam; Chapter 9
is deliberately beyond it.

### 2. `Assessment-Questions.pdf` — the exam paper

The same statements the exam environment shows, as a 10-page printable paper,
one task or level per page. Use it to read the problems away from a screen, or
to sit the assessment on paper. You do not need it to take the test — the
environment shows everything.

### 3. `Assessment-Solutions.pdf` — the debrief

**Spoilers.** Page 1 is nothing but a warning for that reason. Read it only
after your timer has stopped.

For each task: the approach, the complexity, and a list of the ways people lose
the points. The Part B chapter covers the data model that survives all four
levels and why each design decision pays off later.

> Reading the solutions before you have attempted the problems throws away the
> only thing a practice assessment measures.

---

## Grading from the command line

If you want to work at your own pace instead of against a clock, edit `src/`
in your own editor and grade directly:

```bash
.\run.ps1
```

```bash
.\run.ps1 task3
```

```bash
.\run.ps1 -Quiet
```

```bash
.\run.ps1 -Reference
```

The last one grades the reference solutions against exactly the same checks
your code faces — it should score 117/117. On macOS/Linux: `./run.sh`,
`./run.sh task3`, `./run.sh --quiet`, `./run.sh --reference`.

Output is `PASS`/`FAIL` per check with expected and actual values, points per
section, and a total. Exit code is 0 only when everything passes.

---

## What is being tested

| Section | Skill | Checks | Pts |
|---|---|---:|---:|
| Task 1 — Run-Length Compression | string scanning, `StringBuilder` | 11 | 10 |
| Task 2 — Department Averages | parse, group, average, sort with tie-break | 21 | 12 |
| Task 3 — Minesweeper Reveal | iterative flood fill, 8-neighbour counting | 12 | 14 |
| Task 4 — Longest Balanced Segment | prefix balance + hash map | 15 | 14 |
| Level 1 — Files | the data model you will live with | 10 | 10 |
| Level 2 — Queries | prefix filter, sort with tie-break | 11 | 10 |
| Level 3 — Users and Capacity | ownership, quotas, merging | 21 | 15 |
| Level 4 — Backup and Restore | snapshots, consistent accounting | 16 | 15 |
| **Total** | | **117** | **100** |

Points within a section are proportional to checks passed. Several checks are
timed, so a correct but quadratic solution fails with `correct, but too slow` —
the way the real platform behaves.

Part B's later levels are meant to make you refactor the model you chose at
Level 1. Budget for that; design so it is a small refactor.

---

## Repository layout

```
ASSESSMENT.md             problem statements (source of truth)
SOLUTIONS.md              the debrief
Revision-Book.pdf         60 drills with solutions
Assessment-Questions.pdf  the statements, printable
Assessment-Solutions.pdf  the debrief, printable

exam.ps1 / exam.sh        start the timed exam environment
exam/ExamServer.java      the exam server: clock, autosave, grading
exam/web/                 its UI — no dependencies, no CDN

run.ps1 / run.sh          build and grade from the command line
src/                      >>> YOUR CODE GOES HERE <<<
tests/harness/            the grader and all 117 checks
solutions/                reference implementations — spoilers
revision/                 LaTeX source for the revision book
tools/md2pdf.py           rebuilds the two assessment PDFs
```

`out/` and `attempts/` are build and run artefacts and are not tracked.

### Rebuilding the PDFs

```bash
python tools/md2pdf.py ASSESSMENT.md Assessment-Questions.pdf
```

```bash
cd revision && pdflatex main.tex && pdflatex main.tex
```

Run `pdflatex` twice so the table of contents resolves.

---

## Honest notes on calibration

The format of Part A — CodeSignal, four problems, seventy minutes — matches
what candidates report from BCG X Casablanca. The difficulty is set from those
same self-reported accounts, which describe tasks in the range of *array
manipulation* and *data processing: calculating averages, filtering data*.
Those are third-party reports, not company material, and they vary by office
and by year.

Two things this repository does not cover. A coding screen is one gate among
several — reports also mention a one-way video interview, a live coding round
and a fit element. And if you are applying to an AI/data track rather than
software engineering, the reported screen there is a **Data Science**
assessment in Python, not Java, and this is the wrong instrument for it.

The exam environment enforces the clock. It does not police your filesystem:
`tests/` and `solutions/` are sitting right there, and the honest constraint is
you.
