package harness;

import static harness.Harness.eq;
import static harness.Harness.grid;
import static harness.Harness.isTrue;
import static harness.Harness.within;

import gca.Task1;
import gca.Task2;
import gca.Task3;
import gca.Task4;
import harness.Harness.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Part A test cases. */
public final class GcaTests {

    private GcaTests() {}

    public static List<Section> sections() {
        List<Section> all = new ArrayList<>();
        all.add(task1());
        all.add(task2());
        all.add(task3());
        all.add(task4());
        return all;
    }

    // ------------------------------------------------------------------

    private static Section task1() {
        Section s = new Section("task1", "Task 1 - Run-Length Compression", 10);

        s.add("\"aaabccddd\" -> \"a3bc2d3\"", () -> eq("a3bc2d3", Task1.compress("aaabccddd")));
        s.add("\"abc\" -> \"abc\" (no run markers)", () -> eq("abc", Task1.compress("abc")));
        s.add("\"a\" -> \"a\"", () -> eq("a", Task1.compress("a")));
        s.add("\"aa\" -> \"a2\"", () -> eq("a2", Task1.compress("aa")));
        s.add("\"aabbaa\" -> \"a2b2a2\" (runs repeat)",
                () -> eq("a2b2a2", Task1.compress("aabbaa")));
        s.add("12 x 'z' -> \"z12\" (multi-digit count)",
                () -> eq("z12", Task1.compress("zzzzzzzzzzzz")));
        s.add("\"\" -> \"\"", () -> eq("", Task1.compress("")));
        s.add("null -> \"\"", () -> eq("", Task1.compress(null)));
        s.add("\"abbcccddddeeeee\" -> \"ab2c3d4e5\"",
                () -> eq("ab2c3d4e5", Task1.compress("abbcccddddeeeee")));
        s.add("100k identical chars (performance)", () -> within(1000, () -> {
            String input = "a".repeat(100_000);
            return eq("a100000", Task1.compress(input));
        }));
        s.add("100k alternating chars stay uncompressed (performance)", () -> within(1000, () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100_000; i++) {
                sb.append(i % 2 == 0 ? 'a' : 'b');
            }
            String input = sb.toString();
            return eq(input, Task1.compress(input));
        }));
        return s;
    }

    // ------------------------------------------------------------------

    private static Section task2() {
        Section s = new Section("task2", "Task 2 - Department Averages", 12);

        String[] sample = {
            "ana,eng,100", "bo,eng,200", "cy,sales,150", "dee,sales,150", "bad record"
        };

        s.add("sample, n=2: tie broken by name", () -> eq(
                new String[] {"eng(150)", "sales(150)"}, Task2.topDepartments(sample.clone(), 2)));
        s.add("sample, n=1: only the first", () -> eq(
                new String[] {"eng(150)"}, Task2.topDepartments(sample.clone(), 1)));
        s.add("higher average comes first", () -> eq(
                new String[] {"y(20)", "x(10)"},
                Task2.topDepartments(new String[] {"a,x,10", "b,y,20"}, 5)));
        s.add("the average rounds down", () -> eq(
                new String[] {"x(10)"},
                Task2.topDepartments(new String[] {"a,x,10", "b,x,11"}, 5)));
        s.add("a record with too few fields is ignored", () -> eq(
                new String[] {"x(10)"},
                Task2.topDepartments(new String[] {"a,x,10", "b,y"}, 5)));
        s.add("a record with too many fields is ignored", () -> eq(
                new String[] {"x(10)"},
                Task2.topDepartments(new String[] {"a,x,10", "b,y,20,extra"}, 5)));
        s.add("a non-numeric salary is ignored", () -> eq(
                new String[] {"x(10)"},
                Task2.topDepartments(new String[] {"a,x,10", "b,y,abc"}, 5)));
        s.add("a negative salary is ignored", () -> eq(
                new String[] {"x(10)"},
                Task2.topDepartments(new String[] {"a,x,10", "b,y,-5"}, 5)));
        s.add("an empty department is ignored", () -> eq(
                new String[] {"x(10)"},
                Task2.topDepartments(new String[] {"a,x,10", "b,,20"}, 5)));
        s.add("salary 0 is a real salary", () -> eq(
                new String[] {"x(0)"}, Task2.topDepartments(new String[] {"a,x,0"}, 5)));
        s.add("fields are trimmed", () -> eq(
                new String[] {"eng(100)"},
                Task2.topDepartments(new String[] {"a, eng , 100 "}, 5)));
        s.add("null entries are skipped", () -> eq(
                new String[] {"x(10)"},
                Task2.topDepartments(new String[] {null, "a,x,10", null}, 5)));
        s.add("null array -> empty", () -> eq(new String[0], Task2.topDepartments(null, 5)));
        s.add("no valid records -> empty", () -> eq(new String[0],
                Task2.topDepartments(new String[] {"nope", "also,bad"}, 5)));
        s.add("n = 0 -> empty", () -> eq(new String[0], Task2.topDepartments(sample.clone(), 0)));
        s.add("negative n -> empty", () -> eq(new String[0], Task2.topDepartments(sample.clone(), -2)));
        s.add("n larger than the number of departments", () -> eq(
                new String[] {"eng(150)", "sales(150)"}, Task2.topDepartments(sample.clone(), 99)));
        s.add("three departments ordered correctly", () -> eq(
                new String[] {"c(300)", "a(200)", "b(100)"},
                Task2.topDepartments(new String[] {
                        "p,a,150", "q,a,250", "r,b,100", "s,c,300"}, 3)));
        s.add("totals do not overflow int", () -> {
            String[] big = new String[1000];
            for (int i = 0; i < big.length; i++) {
                big[i] = "e" + i + ",huge,2000000000";
            }
            return eq(new String[] {"huge(2000000000)"}, Task2.topDepartments(big, 1));
        });
        s.add("200k records, one department (performance)", () -> within(2000, () -> {
            String[] many = new String[200_000];
            for (int i = 0; i < many.length; i++) {
                many[i] = "e" + i + ",d,100";
            }
            return eq(new String[] {"d(100)"}, Task2.topDepartments(many, 3));
        }));
        s.add("200k records, 500 departments (performance)", () -> within(2000, () -> {
            String[] many = new String[200_000];
            for (int i = 0; i < many.length; i++) {
                many[i] = "e" + i + ",d" + (i % 500) + "," + (i % 97);
            }
            String[] top = Task2.topDepartments(many, 10);
            return isTrue(top.length == 10, "expected 10 departments, got " + top.length);
        }));
        return s;
    }

    // ------------------------------------------------------------------

    private static Section task3() {
        Section s = new Section("task3", "Task 3 - Minesweeper Reveal", 14);

        s.add("cascade stops at the numbered ring", () -> eq(
                grid("B1.1B",
                     "B1M1B",
                     "B111B",
                     "BBBBB"),
                Task3.reveal(grid(".....",
                                  "..M..",
                                  ".....",
                                  "....."), 3, 0)));
        s.add("clicking a mine only marks that mine 'X'", () -> eq(
                grid(".....",
                     "..X..",
                     ".....",
                     "....."),
                Task3.reveal(grid(".....",
                                  "..M..",
                                  ".....",
                                  "....."), 1, 2)));
        s.add("board with no mines reveals entirely", () -> eq(
                grid("BBB", "BBB", "BBB"),
                Task3.reveal(grid("...", "...", "..."), 1, 1)));
        s.add("clicking a numbered cell does not cascade", () -> eq(
                grid("1.", "M."),
                Task3.reveal(grid("..", "M."), 0, 0)));
        s.add("1x1 empty board -> 'B'", () -> eq(grid("B"), Task3.reveal(grid("."), 0, 0)));
        s.add("1x1 mine -> 'X'", () -> eq(grid("X"), Task3.reveal(grid("M"), 0, 0)));
        s.add("out-of-bounds click leaves the board untouched", () -> eq(
                grid("..M", "..."),
                Task3.reveal(grid("..M", "..."), 7, 1)));
        s.add("negative coordinates leave the board untouched", () -> eq(
                grid("..M", "..."),
                Task3.reveal(grid("..M", "..."), -1, 0)));
        s.add("re-clicking a revealed cell is a no-op", () -> {
            char[][] board = grid(".....", "..M..", ".....", ".....");
            Task3.reveal(board, 3, 0);
            char[][] snapshot = grid(new String(board[0]), new String(board[1]),
                    new String(board[2]), new String(board[3]));
            Task3.reveal(board, 3, 0);
            return eq(snapshot, board);
        });
        s.add("returns the same array instance (in-place)", () -> {
            char[][] board = grid("...", "...");
            char[][] returned = Task3.reveal(board, 0, 0);
            return isTrue(returned == board, "reveal must mutate and return the same array");
        });
        s.add("eight digits around a fully surrounded cell", () -> eq(
                grid("MMM", "M8M", "MMM"),
                Task3.reveal(grid("MMM", "M.M", "MMM"), 1, 1)));
        s.add("300x300 board, no stack overflow (performance)", () -> within(2000, () -> {
            int n = 300;
            char[][] board = new char[n][n];
            for (char[] row : board) {
                java.util.Arrays.fill(row, '.');
            }
            board[0][0] = 'M';
            char[][] result = Task3.reveal(board, n - 1, n - 1);
            int blanks = 0;
            int digits = 0;
            int hidden = 0;
            for (char[] row : result) {
                for (char ch : row) {
                    if (ch == 'B') {
                        blanks++;
                    } else if (ch >= '1' && ch <= '8') {
                        digits++;
                    } else if (ch == '.') {
                        hidden++;
                    }
                }
            }
            String detail = "blanks=" + blanks + " digits=" + digits + " hidden=" + hidden;
            return isTrue(blanks == n * n - 4 && digits == 3 && hidden == 0,
                    "expected blanks=89996 digits=3 hidden=0, got " + detail);
        }));
        return s;
    }

    // ------------------------------------------------------------------

    private static Section task4() {
        Section s = new Section("task4", "Task 4 - Longest Balanced Segment", 14);

        s.add("[1,2,3,4,5] -> 4",
                () -> eq(4, Task4.longestBalancedSegment(new int[] {1, 2, 3, 4, 5})));
        s.add("[2,4,6] -> 0 (all even)",
                () -> eq(0, Task4.longestBalancedSegment(new int[] {2, 4, 6})));
        s.add("[1,3,5] -> 0 (all odd)",
                () -> eq(0, Task4.longestBalancedSegment(new int[] {1, 3, 5})));
        s.add("[1,2] -> 2", () -> eq(2, Task4.longestBalancedSegment(new int[] {1, 2})));
        s.add("[2,1] -> 2", () -> eq(2, Task4.longestBalancedSegment(new int[] {2, 1})));
        s.add("[1,1,2,2,1] -> 4",
                () -> eq(4, Task4.longestBalancedSegment(new int[] {1, 1, 2, 2, 1})));
        s.add("[-1,-2,3,4] -> 4 (negatives)",
                () -> eq(4, Task4.longestBalancedSegment(new int[] {-1, -2, 3, 4})));
        s.add("[0,1] -> 2 (zero is even)",
                () -> eq(2, Task4.longestBalancedSegment(new int[] {0, 1})));
        s.add("[] -> 0", () -> eq(0, Task4.longestBalancedSegment(new int[0])));
        s.add("null -> 0", () -> eq(0, Task4.longestBalancedSegment(null)));
        s.add("[7] -> 0 (single element)",
                () -> eq(0, Task4.longestBalancedSegment(new int[] {7})));
        s.add("[2,2,2,1,2,1,2,2] -> 4 (best segment is interior)",
                () -> eq(4, Task4.longestBalancedSegment(new int[] {2, 2, 2, 1, 2, 1, 2, 2})));
        s.add("matches brute force on 300 random arrays", () -> {
            Random rnd = new Random(20261105L);
            for (int trial = 0; trial < 300; trial++) {
                int len = rnd.nextInt(14);
                int[] a = new int[len];
                for (int i = 0; i < len; i++) {
                    a[i] = rnd.nextInt(21) - 10;
                }
                int expected = bruteForceBalanced(a);
                int actual = Task4.longestBalancedSegment(a.clone());
                if (expected != actual) {
                    return "on " + Harness.fmt(a) + ": expected " + expected + ", got " + actual;
                }
            }
            return null;
        });
        s.add("200k alternating values -> 200000 (performance)", () -> within(2000, () -> {
            int[] a = new int[200_000];
            for (int i = 0; i < a.length; i++) {
                a[i] = i % 2;
            }
            return eq(200_000, Task4.longestBalancedSegment(a));
        }));
        s.add("200k values, answer zero (performance)", () -> within(2000, () -> {
            int[] a = new int[200_000];
            for (int i = 0; i < a.length; i++) {
                a[i] = 2; // all even, worst case for a naive scan
            }
            return eq(0, Task4.longestBalancedSegment(a));
        }));
        return s;
    }

    private static int bruteForceBalanced(int[] a) {
        int best = 0;
        for (int i = 0; i < a.length; i++) {
            int odd = 0;
            int even = 0;
            for (int j = i; j < a.length; j++) {
                if (a[j] % 2 != 0) {
                    odd++;
                } else {
                    even++;
                }
                if (odd == even) {
                    best = Math.max(best, j - i + 1);
                }
            }
        }
        return best;
    }
}
