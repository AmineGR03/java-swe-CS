package gca;

/** Reference solution — Task 1. O(n) time, O(n) space. */
public class Task1 {

    public static String compress(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            int j = i;
            while (j < s.length() && s.charAt(j) == ch) {
                j++;
            }
            out.append(ch);
            int run = j - i;
            if (run > 1) {
                out.append(run);
            }
            i = j;
        }
        return out.toString();
    }
}
