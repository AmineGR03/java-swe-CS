package harness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Tiny zero-dependency check framework used by the grader. */
public final class Harness {

    private Harness() {}

    /** A single check. Returns null when it passes, or a failure detail. */
    public interface Check {
        String run() throws Exception;
    }

    public static final class Case {
        public final String name;
        public final Check check;

        Case(String name, Check check) {
            this.name = name;
            this.check = check;
        }
    }

    public static final class Section {
        public final String id;
        public final String title;
        public final double weight;
        public final List<Case> cases = new ArrayList<>();

        public Section(String id, String title, double weight) {
            this.id = id;
            this.title = title;
            this.weight = weight;
        }

        public Section add(String name, Check check) {
            cases.add(new Case(name, check));
            return this;
        }
    }

    // ------------------------------------------------------------------
    // Assertions
    // ------------------------------------------------------------------

    public static String eq(Object expected, Object actual) {
        if (Objects.deepEquals(expected, actual)) {
            return null;
        }
        return "expected " + fmt(expected) + ", got " + fmt(actual);
    }

    public static String isTrue(boolean condition, String whatFailed) {
        return condition ? null : whatFailed;
    }

    /** Runs a check and additionally fails it if it took longer than the limit. */
    public static String within(long limitMillis, Check check) throws Exception {
        long start = System.nanoTime();
        String failure = check.run();
        long ms = (System.nanoTime() - start) / 1_000_000L;
        if (failure != null) {
            return failure;
        }
        if (ms > limitMillis) {
            return "correct, but too slow: " + ms + " ms (limit " + limitMillis + " ms)";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public static char[][] grid(String... rows) {
        char[][] g = new char[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            g[i] = rows[i].toCharArray();
        }
        return g;
    }

    public static String fmt(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof char[][] g) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < g.length; i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                sb.append(new String(g[i]));
            }
            return sb.append(']').toString();
        }
        if (o instanceof Object[] a) {
            return Arrays.deepToString(a);
        }
        if (o instanceof int[] a) {
            return Arrays.toString(a);
        }
        if (o instanceof char[] a) {
            return '"' + new String(a) + '"';
        }
        if (o instanceof String s) {
            return '"' + s + '"';
        }
        return String.valueOf(o);
    }
}
