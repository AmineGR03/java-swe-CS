package gca;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Reference solution — Task 3.
 *
 * Iterative flood fill (BFS) so a 300x300 empty board cannot overflow the
 * stack. O(rows * cols) time and space.
 */
public class Task3 {

    public static char[][] reveal(char[][] board, int r, int c) {
        if (board == null || board.length == 0 || board[0].length == 0) {
            return board;
        }
        int rows = board.length;
        int cols = board[0].length;
        if (r < 0 || r >= rows || c < 0 || c >= cols) {
            return board;
        }
        if (board[r][c] == 'M') {
            board[r][c] = 'X';
            return board;
        }
        if (board[r][c] != '.') {
            return board; // already revealed
        }

        boolean[][] queued = new boolean[rows][cols];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {r, c});
        queued[r][c] = true;

        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int i = cell[0];
            int j = cell[1];

            int mines = 0;
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    if (di == 0 && dj == 0) {
                        continue;
                    }
                    int ni = i + di;
                    int nj = j + dj;
                    if (ni < 0 || ni >= rows || nj < 0 || nj >= cols) {
                        continue;
                    }
                    if (board[ni][nj] == 'M' || board[ni][nj] == 'X') {
                        mines++;
                    }
                }
            }

            if (mines > 0) {
                board[i][j] = (char) ('0' + mines);
                continue; // numbered cells do not cascade
            }

            board[i][j] = 'B';
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    if (di == 0 && dj == 0) {
                        continue;
                    }
                    int ni = i + di;
                    int nj = j + dj;
                    if (ni < 0 || ni >= rows || nj < 0 || nj >= cols) {
                        continue;
                    }
                    if (board[ni][nj] == '.' && !queued[ni][nj]) {
                        queued[ni][nj] = true;
                        queue.add(new int[] {ni, nj});
                    }
                }
            }
        }
        return board;
    }
}
