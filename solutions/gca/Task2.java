package gca;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference solution — Task 2.
 *
 * One pass to accumulate a running total and a count per department, then one
 * sort. Keeping the total and the count (rather than a running average) is what
 * makes the integer division come out right: the average is computed once, at
 * the end, from exact numbers.
 *
 * O(R + D log D) for R records and D departments.
 */
public class Task2 {

    private static final class Tally {
        long total;
        int count;
    }

    public static String[] topDepartments(String[] records, int n) {
        if (records == null || n <= 0) {
            return new String[0];
        }

        Map<String, Tally> byDepartment = new HashMap<>();
        for (String record : records) {
            if (record == null) {
                continue;
            }
            String[] parts = record.split(",", -1);
            if (parts.length != 3) {
                continue;
            }
            String department = parts[1].trim();
            if (department.isEmpty()) {
                continue;
            }
            long salary;
            try {
                salary = Long.parseLong(parts[2].trim());
            } catch (NumberFormatException e) {
                continue; // not a number
            }
            if (salary < 0) {
                continue;
            }
            Tally tally = byDepartment.computeIfAbsent(department, k -> new Tally());
            tally.total += salary;
            tally.count++;
        }
        if (byDepartment.isEmpty()) {
            return new String[0];
        }

        List<String> names = new ArrayList<>(byDepartment.keySet());
        names.sort((a, b) -> {
            long avgA = average(byDepartment.get(a));
            long avgB = average(byDepartment.get(b));
            if (avgA != avgB) {
                return Long.compare(avgB, avgA); // average descending
            }
            return a.compareTo(b);               // name ascending
        });

        int size = Math.min(n, names.size());
        String[] result = new String[size];
        for (int i = 0; i < size; i++) {
            result[i] = names.get(i) + "(" + average(byDepartment.get(names.get(i))) + ")";
        }
        return result;
    }

    private static long average(Tally tally) {
        return tally.total / tally.count;
    }
}
