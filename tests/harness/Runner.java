package harness;

import harness.Harness.Case;
import harness.Harness.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grader entry point.
 *
 * Usage: java -cp out harness.Runner [sectionId ...] [--quiet] [--json]
 *
 *   java -cp out harness.Runner                 grade everything
 *   java -cp out harness.Runner task3           grade one task
 *   java -cp out harness.Runner level1 level2   grade several
 *   java -cp out harness.Runner --quiet         failures and totals only
 *   java -cp out harness.Runner --json          machine-readable report only
 *
 * --json prints nothing but one JSON object, which is what the exam server
 * consumes; keep stdout clean in that mode.
 */
public final class Runner {

    private static final int WIDTH = 74;

    private static final class CheckResult {
        final String name;
        final String failure; // null when the check passed

        CheckResult(String name, String failure) {
            this.name = name;
            this.failure = failure;
        }

        boolean passed() {
            return failure == null;
        }
    }

    private static final class SectionResult {
        final Section section;
        final List<CheckResult> checks = new ArrayList<>();
        long millis;

        SectionResult(Section section) {
            this.section = section;
        }

        int passed() {
            int n = 0;
            for (CheckResult c : checks) {
                if (c.passed()) {
                    n++;
                }
            }
            return n;
        }

        double points() {
            return checks.isEmpty() ? 0 : section.weight * passed() / checks.size();
        }
    }

    public static void main(String[] args) {
        List<String> wanted = new ArrayList<>();
        boolean quiet = false;
        boolean json = false;
        for (String arg : args) {
            if (arg.equals("--quiet") || arg.equals("-q")) {
                quiet = true;
            } else if (arg.equals("--json")) {
                json = true;
            } else {
                wanted.add(arg.toLowerCase(Locale.ROOT));
            }
        }

        List<Section> sections = new ArrayList<>();
        sections.addAll(GcaTests.sections());
        sections.addAll(IcfTests.sections());

        List<Section> selected = new ArrayList<>();
        for (Section section : sections) {
            if (wanted.isEmpty() || wanted.contains(section.id)) {
                selected.add(section);
            }
        }
        if (selected.isEmpty()) {
            if (json) {
                System.out.println("{\"error\":\"no section matched\"}");
            } else {
                System.out.println("No section matched " + wanted
                        + ". Known ids: task1 task2 task3 task4 level1 level2 level3 level4 level5");
            }
            System.exit(2);
        }

        if (!json) {
            rule('=');
            System.out.println("  Java Software Engineering Assessment - grader");
            rule('=');
        }

        List<SectionResult> results = new ArrayList<>();
        for (Section section : selected) {
            if (!json) {
                System.out.println();
                System.out.println(section.title + "  (" + trim(section.weight) + " pts)");
                System.out.println("-".repeat(WIDTH));
            }

            SectionResult result = new SectionResult(section);
            long start = System.nanoTime();
            for (Case testCase : section.cases) {
                String failure = run(testCase);
                result.checks.add(new CheckResult(testCase.name, failure));
                if (json) {
                    continue;
                }
                if (failure == null) {
                    if (!quiet) {
                        System.out.println("  PASS  " + testCase.name);
                    }
                } else {
                    System.out.println("  FAIL  " + testCase.name);
                    System.out.println("        " + failure);
                }
            }
            result.millis = (System.nanoTime() - start) / 1_000_000L;
            results.add(result);

            if (!json) {
                System.out.printf("  -> %d/%d checks, %s/%s pts, %d ms%n",
                        result.passed(), result.checks.size(),
                        trim(result.points()), trim(section.weight), result.millis);
            }
        }

        int totalPassed = 0;
        int totalCases = 0;
        double earned = 0;
        double available = 0;
        for (SectionResult r : results) {
            totalPassed += r.passed();
            totalCases += r.checks.size();
            earned += r.points();
            available += r.section.weight;
        }
        double ratio = available == 0 ? 0 : earned / available;

        if (json) {
            System.out.println(toJson(results, totalPassed, totalCases, earned, available, ratio));
        } else {
            System.out.println();
            rule('=');
            System.out.println("  SUMMARY");
            rule('=');
            for (SectionResult r : results) {
                System.out.println(String.format("  %-46s %2d/%-2d  %5s/%-3s",
                        r.section.title, r.passed(), r.checks.size(),
                        trim(r.points()), trim(r.section.weight)));
            }
            System.out.println("-".repeat(WIDTH));
            System.out.printf("  checks passed: %d/%d%n", totalPassed, totalCases);
            System.out.printf("  score:         %s / %s%n", trim(earned), trim(available));
            System.out.println("  band:          " + band(ratio));
            rule('=');
        }

        System.exit(totalPassed == totalCases ? 0 : 1);
    }

    private static String run(Case testCase) {
        try {
            return testCase.check.run();
        } catch (UnsupportedOperationException e) {
            return "not implemented";
        } catch (StackOverflowError e) {
            return "StackOverflowError - recursion too deep for the largest inputs";
        } catch (Throwable t) {
            String message = t.getMessage();
            return "threw " + t.getClass().getSimpleName()
                    + (message == null ? "" : ": " + message);
        }
    }

    // ------------------------------------------------------------------

    private static String toJson(List<SectionResult> results, int passed, int total,
                                 double earned, double available, double ratio) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"sections\":[");
        for (int i = 0; i < results.size(); i++) {
            SectionResult r = results.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(quote(r.section.id))
              .append(",\"title\":").append(quote(r.section.title))
              .append(",\"weight\":").append(trim(r.section.weight))
              .append(",\"points\":").append(round1(r.points()))
              .append(",\"passed\":").append(r.passed())
              .append(",\"total\":").append(r.checks.size())
              .append(",\"millis\":").append(r.millis)
              .append(",\"checks\":[");
            for (int j = 0; j < r.checks.size(); j++) {
                CheckResult c = r.checks.get(j);
                if (j > 0) {
                    sb.append(',');
                }
                sb.append("{\"name\":").append(quote(c.name))
                  .append(",\"pass\":").append(c.passed())
                  .append(",\"detail\":").append(c.failure == null ? "null" : quote(c.failure))
                  .append('}');
            }
            sb.append("]}");
        }
        sb.append("],\"passed\":").append(passed)
          .append(",\"total\":").append(total)
          .append(",\"earned\":").append(round1(earned))
          .append(",\"available\":").append(round1(available))
          .append(",\"band\":").append(quote(band(ratio)))
          .append('}');
        return sb.toString();
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String round1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String band(double ratio) {
        if (ratio >= 0.95) {
            return "excellent - hiring-bar pass at nearly any company";
        }
        if (ratio >= 0.80) {
            return "strong - comfortably above a typical senior-SWE bar";
        }
        if (ratio >= 0.60) {
            return "solid - passes many screens; the hard task is where points are left";
        }
        if (ratio >= 0.35) {
            return "developing - the fundamentals are there, the harder levels are not";
        }
        return "keep going - work the tasks in order and re-run";
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void rule(char ch) {
        System.out.println(String.valueOf(ch).repeat(WIDTH));
    }
}
