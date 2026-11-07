package icf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reference solution — Part B, all four levels.
 *
 * Shape of the design:
 *  - one flat name -> file index, so Level 1 and Level 2 stay O(1) / O(F log F);
 *  - ownership is a field on the file plus a per-user usage counter, so
 *    capacity never has to be recomputed by scanning;
 *  - the Level 4 snapshot is a plain name -> entry map held on the user, which
 *    is why deleting a user in mergeUser also drops its snapshot for free;
 *  - every create and every destroy goes through addEntry / removeEntry, so the
 *    invariant "a file's size is counted in exactly its owner's usage" cannot
 *    be broken by a caller. The nine public methods are thin wrappers.
 */
public class CloudStorage {

    private static final String ADMIN = "admin";

    private static final class FileEntry {
        final int size;
        String owner;

        FileEntry(int size, String owner) {
            this.size = size;
            this.owner = owner;
        }
    }

    private static final class User {
        final String id;
        final boolean unlimited;
        long capacity;
        long used;
        /** null means "no snapshot taken yet", which differs from an empty snapshot. */
        Map<String, Integer> snapshot;

        User(String id, long capacity, boolean unlimited) {
            this.id = id;
            this.capacity = capacity;
            this.unlimited = unlimited;
        }
    }

    private final Map<String, FileEntry> files = new HashMap<>();
    private final Map<String, User> users = new HashMap<>();

    public CloudStorage() {
        users.put(ADMIN, new User(ADMIN, Long.MAX_VALUE, true));
    }

    // ------------------------------------------------------------------
    // Level 1 — Files
    // ------------------------------------------------------------------

    public boolean addFile(String name, int size) {
        return addFileBy(ADMIN, name, size) != null;
    }

    public Integer getFileSize(String name) {
        FileEntry entry = files.get(name);
        return entry == null ? null : entry.size;
    }

    public Integer deleteFile(String name) {
        return removeEntry(name);
    }

    // ------------------------------------------------------------------
    // Level 2 — Queries
    // ------------------------------------------------------------------

    public String[] getNLargest(String prefix, int n) {
        if (n <= 0 || prefix == null) {
            return new String[0];
        }
        List<String> names = new ArrayList<>();
        for (String name : files.keySet()) {
            if (name.startsWith(prefix)) {
                names.add(name);
            }
        }
        names.sort((a, b) -> {
            int sizeA = files.get(a).size;
            int sizeB = files.get(b).size;
            if (sizeA != sizeB) {
                return Integer.compare(sizeB, sizeA); // size descending
            }
            return a.compareTo(b);                    // name ascending
        });

        int count = Math.min(n, names.size());
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            String name = names.get(i);
            result[i] = name + "(" + files.get(name).size + ")";
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Level 3 — Users and capacity
    // ------------------------------------------------------------------

    public boolean addUser(String userId, int capacity) {
        if (userId == null || users.containsKey(userId)) {
            return false;
        }
        users.put(userId, new User(userId, capacity, false));
        return true;
    }

    public Integer addFileBy(String userId, String name, int size) {
        return addEntry(userId, name, size);
    }

    public Integer mergeUser(String userId1, String userId2) {
        if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
            return null;
        }
        if (ADMIN.equals(userId2)) {
            return null;
        }
        User target = users.get(userId1);
        User source = users.get(userId2);
        if (target == null || source == null) {
            return null;
        }

        for (FileEntry entry : files.values()) {
            if (entry.owner.equals(userId2)) {
                entry.owner = userId1;
            }
        }
        if (!target.unlimited) {
            target.capacity += source.capacity;
        }
        target.used += source.used;
        users.remove(userId2); // drops the snapshot with the user
        return remaining(target);
    }

    // ------------------------------------------------------------------
    // Level 4 — Backup and restore
    // ------------------------------------------------------------------

    public Integer backupUser(String userId) {
        User user = users.get(userId);
        if (user == null) {
            return null;
        }
        Map<String, Integer> snapshot = new TreeMap<>();
        for (Map.Entry<String, FileEntry> e : files.entrySet()) {
            if (e.getValue().owner.equals(userId)) {
                snapshot.put(e.getKey(), e.getValue().size);
            }
        }
        user.snapshot = snapshot;
        return snapshot.size();
    }

    public Integer restoreUser(String userId) {
        User user = users.get(userId);
        if (user == null) {
            return null;
        }

        for (String name : ownedBy(userId)) {
            removeEntry(name);
        }

        if (user.snapshot == null) {
            return 0;
        }
        int restored = 0;
        for (Map.Entry<String, Integer> e : user.snapshot.entrySet()) {
            // A name claimed by somebody else in the meantime is skipped.
            if (addEntry(userId, e.getKey(), e.getValue()) != null) {
                restored++;
            }
        }
        return restored;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** The single place a file is created and its owner's usage charged. */
    private Integer addEntry(String userId, String name, int size) {
        if (userId == null || name == null) {
            return null;
        }
        User user = users.get(userId);
        if (user == null || files.containsKey(name)) {
            return null;
        }
        if (!user.unlimited && user.used + size > user.capacity) {
            return null;
        }
        files.put(name, new FileEntry(size, userId));
        user.used += size;
        return remaining(user);
    }

    /** The single place a file is destroyed and its owner's usage credited. */
    private Integer removeEntry(String name) {
        FileEntry entry = files.remove(name);
        if (entry == null) {
            return null;
        }
        User owner = users.get(entry.owner);
        if (owner != null) {
            owner.used -= entry.size;
        }
        return entry.size;
    }

    private List<String> ownedBy(String userId) {
        List<String> owned = new ArrayList<>();
        for (Map.Entry<String, FileEntry> e : files.entrySet()) {
            if (e.getValue().owner.equals(userId)) {
                owned.add(e.getKey());
            }
        }
        return owned;
    }

    private Integer remaining(User user) {
        if (user.unlimited) {
            return Integer.MAX_VALUE;
        }
        long left = user.capacity - user.used;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, left));
    }
}
