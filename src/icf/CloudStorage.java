package icf;

/**
 * Part B — Industry Coding Framework: in-memory cloud storage.
 *
 * Implement the levels in order. Signatures are fixed; the grader calls them
 * exactly as declared. See ASSESSMENT.md for the full statement.
 */
public class CloudStorage {

    public CloudStorage() {
        // Level 3 note: the built-in "admin" user with unlimited capacity
        // must exist from construction.
    }

    // ------------------------------------------------------------------
    // Level 1 — Files
    // ------------------------------------------------------------------

    /** @return true on success, false if the name is already taken. */
    public boolean addFile(String name, int size) {
        throw new UnsupportedOperationException("Level 1: addFile not implemented");
    }

    /** @return the file's size, or null if there is no such file. */
    public Integer getFileSize(String name) {
        throw new UnsupportedOperationException("Level 1: getFileSize not implemented");
    }

    /** @return the deleted file's size, or null if there is no such file. */
    public Integer deleteFile(String name) {
        throw new UnsupportedOperationException("Level 1: deleteFile not implemented");
    }

    // ------------------------------------------------------------------
    // Level 2 — Queries
    // ------------------------------------------------------------------

    /**
     * At most n files whose name starts with prefix, formatted "name(size)",
     * ordered by size descending then name ascending.
     *
     * @return the matches, or an empty array (never null)
     */
    public String[] getNLargest(String prefix, int n) {
        throw new UnsupportedOperationException("Level 2: getNLargest not implemented");
    }

    // ------------------------------------------------------------------
    // Level 3 — Users and capacity
    // ------------------------------------------------------------------

    /** @return true on success, false if userId already exists. */
    public boolean addUser(String userId, int capacity) {
        throw new UnsupportedOperationException("Level 3: addUser not implemented");
    }

    /** @return the user's remaining capacity after the add, or null on failure. */
    public Integer addFileBy(String userId, String name, int size) {
        throw new UnsupportedOperationException("Level 3: addFileBy not implemented");
    }

    /** @return userId1's remaining capacity after the merge, or null on failure. */
    public Integer mergeUser(String userId1, String userId2) {
        throw new UnsupportedOperationException("Level 3: mergeUser not implemented");
    }

    // ------------------------------------------------------------------
    // Level 4 — Backup and restore
    // ------------------------------------------------------------------

    /** @return the number of files snapshotted, or null if the user does not exist. */
    public Integer backupUser(String userId) {
        throw new UnsupportedOperationException("Level 4: backupUser not implemented");
    }

    /** @return the number of files restored, or null if the user does not exist. */
    public Integer restoreUser(String userId) {
        throw new UnsupportedOperationException("Level 4: restoreUser not implemented");
    }
}
