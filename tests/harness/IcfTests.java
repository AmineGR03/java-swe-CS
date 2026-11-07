package harness;

import static harness.Harness.eq;

import harness.Harness.Section;
import icf.CloudStorage;
import java.util.ArrayList;
import java.util.List;

/** Part B test cases, one section per level. */
public final class IcfTests {

    private IcfTests() {}

    public static List<Section> sections() {
        List<Section> all = new ArrayList<>();
        all.add(level1());
        all.add(level2());
        all.add(level3());
        all.add(level4());
        return all;
    }

    // ------------------------------------------------------------------

    private static Section level1() {
        Section s = new Section("level1", "Level 1 - Files", 10);

        s.add("addFile on a fresh name -> true", () -> {
            CloudStorage cs = new CloudStorage();
            return eq(true, cs.addFile("/dir/file.txt", 10));
        });
        s.add("addFile on a taken name -> false", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/dir/file.txt", 10);
            return eq(false, cs.addFile("/dir/file.txt", 20));
        });
        s.add("a rejected addFile does not change the stored size", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/dir/file.txt", 10);
            cs.addFile("/dir/file.txt", 20);
            return eq(10, cs.getFileSize("/dir/file.txt"));
        });
        s.add("getFileSize on a missing file -> null", () -> {
            CloudStorage cs = new CloudStorage();
            return eq(null, cs.getFileSize("/nope"));
        });
        s.add("deleteFile returns the deleted size", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/a", 42);
            return eq(42, cs.deleteFile("/a"));
        });
        s.add("deleting twice -> null the second time", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/a", 42);
            cs.deleteFile("/a");
            return eq(null, cs.deleteFile("/a"));
        });
        s.add("a deleted file is gone", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/a", 42);
            cs.deleteFile("/a");
            return eq(null, cs.getFileSize("/a"));
        });
        s.add("the name is reusable after deletion", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/a", 42);
            cs.deleteFile("/a");
            return eq(true, cs.addFile("/a", 7));
        });
        s.add("size 0 is a valid file", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/empty", 0);
            return eq(0, cs.getFileSize("/empty"));
        });
        s.add("many files stay independent", () -> {
            CloudStorage cs = new CloudStorage();
            for (int i = 0; i < 500; i++) {
                if (!cs.addFile("/f" + i, i)) {
                    return "addFile(\"/f" + i + "\") returned false";
                }
            }
            return eq(499, cs.getFileSize("/f499"));
        });
        return s;
    }

    // ------------------------------------------------------------------

    private static Section level2() {
        Section s = new Section("level2", "Level 2 - Queries", 10);

        s.add("sample: getNLargest(\"/dir\", 2)", () -> eq(
                new String[] {"/dir/deeper/f3(30)", "/dir/f2(20)"},
                sample().getNLargest("/dir", 2)));
        s.add("sample: getNLargest(\"/dir/\", 10) returns all three", () -> eq(
                new String[] {"/dir/deeper/f3(30)", "/dir/f2(20)", "/dir/f1(10)"},
                sample().getNLargest("/dir/", 10)));
        s.add("prefix matches nothing -> empty array", () -> eq(
                new String[0], sample().getNLargest("/nope", 5)));
        s.add("empty prefix matches everything", () -> eq(
                new String[] {"/other/f4(40)", "/dir/deeper/f3(30)",
                        "/dir/f2(20)", "/dir/f1(10)"},
                sample().getNLargest("", 10)));
        s.add("ties broken by name ascending", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/t/b", 5);
            cs.addFile("/t/a", 5);
            cs.addFile("/t/c", 5);
            return eq(new String[] {"/t/a(5)", "/t/b(5)"}, cs.getNLargest("/t", 2));
        });
        s.add("n = 0 -> empty array", () -> eq(new String[0], sample().getNLargest("/dir", 0)));
        s.add("negative n -> empty array", () -> eq(new String[0], sample().getNLargest("/dir", -3)));
        s.add("n = 1 returns only the largest", () -> eq(
                new String[] {"/other/f4(40)"}, sample().getNLargest("/other", 1)));
        s.add("deleted files drop out of results", () -> {
            CloudStorage cs = sample();
            cs.deleteFile("/dir/deeper/f3");
            return eq(new String[] {"/dir/f2(20)", "/dir/f1(10)"}, cs.getNLargest("/dir", 5));
        });
        s.add("an exact file name works as a prefix", () -> eq(
                new String[] {"/dir/f1(10)"}, sample().getNLargest("/dir/f1", 5)));
        s.add("ordering holds across 200 files", () -> {
            CloudStorage cs = new CloudStorage();
            for (int i = 0; i < 200; i++) {
                cs.addFile("/p/" + i, i);
            }
            String[] top = cs.getNLargest("/p/", 3);
            return eq(new String[] {"/p/199(199)", "/p/198(198)", "/p/197(197)"}, top);
        });
        return s;
    }

    private static CloudStorage sample() {
        CloudStorage cs = new CloudStorage();
        cs.addFile("/dir/f1", 10);
        cs.addFile("/dir/f2", 20);
        cs.addFile("/dir/deeper/f3", 30);
        cs.addFile("/other/f4", 40);
        return cs;
    }

    // ------------------------------------------------------------------

    private static Section level3() {
        Section s = new Section("level3", "Level 3 - Users and Capacity", 15);

        s.add("addUser on a fresh id -> true", () -> {
            CloudStorage cs = new CloudStorage();
            return eq(true, cs.addUser("u1", 100));
        });
        s.add("addUser on a taken id -> false", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addUser("u1", 100);
            return eq(false, cs.addUser("u1", 50));
        });
        s.add("\"admin\" already exists", () -> {
            CloudStorage cs = new CloudStorage();
            return eq(false, cs.addUser("admin", 10));
        });
        s.add("addFileBy returns the remaining capacity", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addUser("u1", 100);
            return eq(50, cs.addFileBy("u1", "/u1/f", 50));
        });
        s.add("addFileBy beyond capacity -> null", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addUser("u1", 100);
            cs.addFileBy("u1", "/u1/f", 50);
            return eq(null, cs.addFileBy("u1", "/u1/g", 60));
        });
        s.add("a rejected addFileBy stores nothing", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addUser("u1", 100);
            cs.addFileBy("u1", "/u1/f", 50);
            cs.addFileBy("u1", "/u1/g", 60);
            return eq(null, cs.getFileSize("/u1/g"));
        });
        s.add("a file filling capacity exactly is accepted", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addUser("u1", 100);
            return eq(0, cs.addFileBy("u1", "/u1/f", 100));
        });
        s.add("addFileBy for an unknown user -> null", () -> {
            CloudStorage cs = new CloudStorage();
            return eq(null, cs.addFileBy("ghost", "/x", 1));
        });
        s.add("addFileBy on a taken name -> null", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addUser("u1", 100);
            cs.addFileBy("u1", "/u1/f", 10);
            return eq(null, cs.addFileBy("u1", "/u1/f", 1));
        });
        s.add("admin has unlimited capacity", () -> {
            CloudStorage cs = new CloudStorage();
            return eq(Integer.MAX_VALUE, cs.addFileBy("admin", "/a", 1_000_000));
        });
        s.add("addFile is equivalent to addFileBy(\"admin\", ...)", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/a", 5);
            return eq(null, cs.addFileBy("admin", "/a", 5));
        });
        s.add("deleteFile frees the owner's capacity", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addUser("u1", 100);
            cs.addFileBy("u1", "/u1/f", 50);
            cs.deleteFile("/u1/f");
            return eq(40, cs.addFileBy("u1", "/u1/g", 60));
        });
        s.add("mergeUser adds capacities and usage", () -> eq(80, mergeFixture().mergeUser("u1", "u2")));
        s.add("merged files survive and are queryable", () -> {
            CloudStorage cs = mergeFixture();
            cs.mergeUser("u1", "u2");
            return eq(20, cs.getFileSize("/u2/h"));
        });
        s.add("the merged-away user is gone", () -> {
            CloudStorage cs = mergeFixture();
            cs.mergeUser("u1", "u2");
            return eq(null, cs.addFileBy("u2", "/u2/new", 1));
        });
        s.add("merging twice -> null", () -> {
            CloudStorage cs = mergeFixture();
            cs.mergeUser("u1", "u2");
            return eq(null, cs.mergeUser("u1", "u2"));
        });
        s.add("mergeUser with itself -> null", () -> eq(null, mergeFixture().mergeUser("u1", "u1")));
        s.add("mergeUser with an unknown user -> null",
                () -> eq(null, mergeFixture().mergeUser("u1", "ghost")));
        s.add("mergeUser cannot consume \"admin\"",
                () -> eq(null, mergeFixture().mergeUser("u1", "admin")));
        s.add("capacity after a merge is enforced on the merged files", () -> {
            CloudStorage cs = mergeFixture();
            cs.mergeUser("u1", "u2");
            if (cs.addFileBy("u1", "/u1/big", 81) != null) {
                return "a file of 81 should not fit in the remaining 80";
            }
            return eq(0, cs.addFileBy("u1", "/u1/big", 80));
        });
        s.add("deleting a merged file frees the new owner's capacity", () -> {
            CloudStorage cs = mergeFixture();
            cs.mergeUser("u1", "u2");
            cs.deleteFile("/u2/h");
            return eq(0, cs.addFileBy("u1", "/u1/big", 100));
        });
        return s;
    }

    /** u1: capacity 100, 50 used. u2: capacity 50, 20 used. */
    private static CloudStorage mergeFixture() {
        CloudStorage cs = new CloudStorage();
        cs.addUser("u1", 100);
        cs.addFileBy("u1", "/u1/f", 50);
        cs.addUser("u2", 50);
        cs.addFileBy("u2", "/u2/h", 20);
        return cs;
    }

    // ------------------------------------------------------------------

    private static Section level4() {
        Section s = new Section("level4", "Level 4 - Backup and Restore", 15);

        s.add("backupUser returns the file count", () -> eq(2, backupFixture().backupUser("u1")));
        s.add("backupUser for an unknown user -> null",
                () -> eq(null, new CloudStorage().backupUser("ghost")));
        s.add("restoreUser for an unknown user -> null",
                () -> eq(null, new CloudStorage().restoreUser("ghost")));
        s.add("backupUser with no files -> 0", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addUser("u1", 100);
            return eq(0, cs.backupUser("u1"));
        });
        s.add("restoreUser returns the count restored", () -> {
            CloudStorage cs = backupFixture();
            cs.backupUser("u1");
            cs.addFileBy("u1", "/c", 30);
            cs.deleteFile("/a");
            return eq(2, cs.restoreUser("u1"));
        });
        s.add("a deleted file comes back", () -> {
            CloudStorage cs = backupFixture();
            cs.backupUser("u1");
            cs.deleteFile("/a");
            cs.restoreUser("u1");
            return eq(10, cs.getFileSize("/a"));
        });
        s.add("a file added after the backup is dropped", () -> {
            CloudStorage cs = backupFixture();
            cs.backupUser("u1");
            cs.addFileBy("u1", "/c", 30);
            cs.restoreUser("u1");
            return eq(null, cs.getFileSize("/c"));
        });
        s.add("capacity is consistent after a restore", () -> {
            CloudStorage cs = backupFixture();
            cs.backupUser("u1");
            cs.addFileBy("u1", "/c", 30);
            cs.deleteFile("/a");
            cs.restoreUser("u1");
            // u1 has capacity 100 and 30 restored bytes, so 70 is left.
            if (cs.addFileBy("u1", "/d", 71) != null) {
                return "a file of 71 should not fit in the remaining 70";
            }
            return eq(0, cs.addFileBy("u1", "/d", 70));
        });
        s.add("restoring twice is idempotent", () -> {
            CloudStorage cs = backupFixture();
            cs.backupUser("u1");
            cs.restoreUser("u1");
            return eq(2, cs.restoreUser("u1"));
        });
        s.add("a name taken by another user is skipped", () -> {
            CloudStorage cs = backupFixture();
            cs.backupUser("u1");
            cs.deleteFile("/a");
            cs.deleteFile("/b");
            cs.addUser("u2", 100);
            cs.addFileBy("u2", "/a", 5);
            if (!eqInt(1, cs.restoreUser("u1"))) {
                return "expected 1 file restored (\"/a\" is taken by u2), got "
                        + cs.getFileSize("/a");
            }
            return eq(5, cs.getFileSize("/a"));
        });
        s.add("the skipped file keeps its other owner", () -> {
            CloudStorage cs = backupFixture();
            cs.backupUser("u1");
            cs.deleteFile("/a");
            cs.addUser("u2", 100);
            cs.addFileBy("u2", "/a", 5);
            cs.restoreUser("u1");
            cs.deleteFile("/a");
            // Freeing "/a" must give the 5 bytes back to u2, not to u1.
            return eq(100, cs.addFileBy("u2", "/a2", 0));
        });
        s.add("restore with no backup clears the user's files", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addUser("u3", 10);
            cs.addFileBy("u3", "/z", 5);
            if (!eqInt(0, cs.restoreUser("u3"))) {
                return "expected 0 restored for a user with no snapshot";
            }
            if (cs.getFileSize("/z") != null) {
                return "\"/z\" should have been deleted";
            }
            return eq(0, cs.addFileBy("u3", "/z2", 10));
        });
        s.add("a second backup replaces the first", () -> {
            CloudStorage cs = backupFixture();
            cs.backupUser("u1");
            cs.addFileBy("u1", "/c", 30);
            if (!eqInt(3, cs.backupUser("u1"))) {
                return "the second backupUser should report 3 files";
            }
            cs.deleteFile("/c");
            return eq(3, cs.restoreUser("u1"));
        });
        s.add("merging a user discards its snapshot", () -> {
            CloudStorage cs = backupFixture();
            cs.addUser("u2", 100);
            cs.addFileBy("u2", "/x", 10);
            cs.backupUser("u2");
            cs.mergeUser("u1", "u2");
            return eq(null, cs.restoreUser("u2"));
        });
        s.add("restore does not touch other users' files", () -> {
            CloudStorage cs = backupFixture();
            cs.addUser("u2", 100);
            cs.addFileBy("u2", "/u2/keep", 10);
            cs.backupUser("u1");
            cs.restoreUser("u1");
            return eq(10, cs.getFileSize("/u2/keep"));
        });
        s.add("admin can be backed up and restored", () -> {
            CloudStorage cs = new CloudStorage();
            cs.addFile("/adm1", 1);
            cs.addFile("/adm2", 2);
            cs.backupUser("admin");
            cs.deleteFile("/adm1");
            cs.addFile("/adm3", 3);
            if (!eqInt(2, cs.restoreUser("admin"))) {
                return "expected 2 files restored for admin";
            }
            if (cs.getFileSize("/adm3") != null) {
                return "\"/adm3\" was added after the backup and should be gone";
            }
            return eq(1, cs.getFileSize("/adm1"));
        });
        return s;
    }

    /** u1: capacity 100, files "/a" (10) and "/b" (20). */
    private static CloudStorage backupFixture() {
        CloudStorage cs = new CloudStorage();
        cs.addUser("u1", 100);
        cs.addFileBy("u1", "/a", 10);
        cs.addFileBy("u1", "/b", 20);
        return cs;
    }

    private static boolean eqInt(int expected, Integer actual) {
        return actual != null && actual == expected;
    }
}
