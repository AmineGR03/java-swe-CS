import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The exam environment: a local, loopback-only web app that runs a timed
 * attempt at this assessment.
 *
 *     java exam/ExamServer.java [--port 8711] [--demo-seconds 120]
 *
 * Why a server rather than a static page: the clock has to be something the
 * candidate cannot edit. The deadline lives here, every mutating request is
 * checked against it, and a scheduled task submits the last autosave when it
 * expires -- so closing the tab, reloading, or stopping your own JavaScript
 * does not buy you time.
 *
 * The one thing it deliberately does not do is tell you whether your code is
 * correct. During an attempt the only feedback is from javac. Test results
 * exist only after you submit, or after the clock runs out.
 *
 * It compiles and runs the Java you type, so it binds to 127.0.0.1 only.
 */
public class ExamServer {

    static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneId.systemDefault());

    static Path root;
    static Path webRoot;
    static Path attemptsRoot;
    static long demoSeconds = 0;

    // ------------------------------------------------------------------
    // Exam definition, read out of ASSESSMENT.md so the two cannot drift
    // ------------------------------------------------------------------

    record TaskSpec(String id, String label, String title, String file, String statement,
                    String starter) {}

    record PartSpec(String id, String name, String blurb, int minutes, int points,
                    String intro, List<TaskSpec> tasks, List<String> sections) {}

    static final Map<String, PartSpec> PARTS = new LinkedHashMap<>();

    static void loadExam() throws IOException {
        String text = Files.readString(root.resolve("ASSESSMENT.md"));
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);

        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        Map<String, StringBuilder> intros = new LinkedHashMap<>();
        String part = null;
        String section = null;

        Pattern h2 = Pattern.compile("^## (Task|Level) (\\d+) [\\u2014-] (.*?)(?: \\((\\d+) pts\\))?$");
        for (String line : lines) {
            if (line.startsWith("# Part A")) {
                part = "A";
                section = null;
                intros.put(part, new StringBuilder());
                continue;
            }
            if (line.startsWith("# Part B")) {
                part = "B";
                section = null;
                intros.put(part, new StringBuilder());
                continue;
            }
            Matcher m = h2.matcher(line);
            if (m.matches()) {
                String kind = m.group(1);
                String number = m.group(2);
                section = (kind.equals("Task") ? "task" : "level") + number;
                titles.put(section, kind + " " + number + " — " + m.group(3));
                bodies.put(section, new StringBuilder());
                continue;
            }
            if (line.startsWith("## ") || line.startsWith("# ")) {
                section = null; // Scoring, and anything else we do not show
                continue;
            }
            if (section != null) {
                bodies.get(section).append(line).append('\n');
            } else if (part != null && intros.containsKey(part)) {
                intros.get(part).append(line).append('\n');
            }
        }

        int minutesA = findInt(text, "General Coding", "(\\d+) min", 70);
        int pointsA = findInt(text, "General Coding", "(\\d+) pts", 50);
        int minutesB = findInt(text, "Industry Coding", "(\\d+) min", 110);
        int pointsB = findInt(text, "Industry Coding", "(\\d+) pts", 50);

        List<TaskSpec> aTasks = new ArrayList<>();
        List<String> aSections = new ArrayList<>();
        for (int n = 1; n <= 4; n++) {
            String id = "task" + n;
            if (!bodies.containsKey(id)) {
                continue;
            }
            String file = "src/gca/Task" + n + ".java";
            aTasks.add(new TaskSpec(id, "Task " + n, titles.get(id), file,
                    tidy(bodies.get(id)), Files.readString(root.resolve(file))));
            aSections.add(id);
        }
        List<TaskSpec> bTasks = new ArrayList<>();
        List<String> bSections = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            String id = "level" + n;
            if (!bodies.containsKey(id)) {
                continue;
            }
            String file = "src/icf/CloudStorage.java";
            bTasks.add(new TaskSpec(id, "Level " + n, titles.get(id), file,
                    tidy(bodies.get(id)), Files.readString(root.resolve(file))));
            bSections.add(id);
        }

        PARTS.put("A", new PartSpec("A", "Part A — General Coding Assessment",
                aTasks.size() + " independent tasks", minutesA, pointsA,
                tidy(intros.get("A")), aTasks, aSections));
        PARTS.put("B", new PartSpec("B", "Part B — Industry Coding Framework",
                bTasks.size() + " progressive levels, one file", minutesB, pointsB,
                tidy(intros.get("B")), bTasks, bSections));
    }

    static int findInt(String text, String near, String regex, int fallback) {
        for (String line : text.split("\n")) {
            if (line.contains(near)) {
                Matcher m = Pattern.compile(regex).matcher(line);
                if (m.find()) {
                    return Integer.parseInt(m.group(1));
                }
            }
        }
        return fallback;
    }

    static String tidy(StringBuilder sb) {
        if (sb == null) {
            return "";
        }
        String s = sb.toString().replaceAll("(?m)^---\\s*$", "").strip();
        return s.replaceAll("\n{3,}", "\n\n");
    }

    // ------------------------------------------------------------------
    // Attempt state
    // ------------------------------------------------------------------

    static final class Attempt {
        String id;
        String part;
        long startedAt;
        long durationMs;
        boolean submitted;
        boolean autoSubmitted;
        long submittedAt;
        final Map<String, String> files = new LinkedHashMap<>();
        String reportJson;
        ScheduledFuture<?> alarm;

        long deadline() {
            return startedAt + durationMs;
        }

        long remaining() {
            return Math.max(0L, deadline() - System.currentTimeMillis());
        }

        boolean expired() {
            return System.currentTimeMillis() >= deadline();
        }

        Path dir() {
            return attemptsRoot.resolve(id);
        }
    }

    static final Object LOCK = new Object();
    static Attempt current;

    static final ScheduledExecutorService CLOCK = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "exam-clock");
        t.setDaemon(true);
        return t;
    });

    // ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        int port = 8711;
        Path where = Paths.get("").toAbsolutePath();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--demo-seconds" -> demoSeconds = Long.parseLong(args[++i]);
                case "--root" -> where = Paths.get(args[++i]).toAbsolutePath();
                default -> {
                    System.err.println("unknown option: " + args[i]);
                    System.exit(2);
                }
            }
        }
        root = where.normalize();
        webRoot = root.resolve("exam").resolve("web");
        attemptsRoot = root.resolve("attempts");

        if (!Files.exists(root.resolve("ASSESSMENT.md")) || !Files.isDirectory(webRoot)) {
            System.err.println("Run this from the assessment folder (the one holding "
                    + "ASSESSMENT.md and exam/web). Looked in: " + root);
            System.exit(2);
        }
        Files.createDirectories(attemptsRoot);
        loadExam();
        resumeIfAny();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", ExamServer::serveStatic);
        server.createContext("/api/manifest", ex -> json(ex, 200, manifestJson()));
        server.createContext("/api/state", ex -> json(ex, 200, stateJson()));
        server.createContext("/api/start", ExamServer::handleStart);
        server.createContext("/api/resume", ExamServer::handleResume);
        server.createContext("/api/save", ExamServer::handleSave);
        server.createContext("/api/compile", ExamServer::handleCompile);
        server.createContext("/api/submit", ExamServer::handleSubmit);
        server.createContext("/api/report", ExamServer::handleReport);
        server.createContext("/api/abandon", ExamServer::handleAbandon);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println();
        System.out.println("  Exam environment ready:  http://localhost:" + port);
        System.out.println("  Part A: " + PARTS.get("A").minutes() + " min   "
                + "Part B: " + PARTS.get("B").minutes() + " min");
        if (demoSeconds > 0) {
            System.out.println("  !! DEMO CLOCK: both parts last " + demoSeconds
                    + " seconds. Not a real attempt.");
        }
        System.out.println("  Attempts are saved under attempts/. Ctrl+C to stop.");
        System.out.println();
    }

    /** A server restart mid-attempt must not void the attempt. */
    static void resumeIfAny() {
        try (Stream<Path> dirs = Files.list(attemptsRoot)) {
            List<Path> candidates = dirs.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("state.json")))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            for (Path dir : candidates) {
                Map<String, Object> state = Json.object(Json.parse(
                        Files.readString(dir.resolve("state.json"))));
                if (Json.bool(state.get("submitted"))
                        || Files.exists(dir.resolve("ABANDONED.txt"))) {
                    continue;
                }
                Attempt a = new Attempt();
                a.id = dir.getFileName().toString();
                a.part = Json.string(state.get("part"));
                a.startedAt = (long) Json.number(state.get("startedAt"));
                a.durationMs = (long) Json.number(state.get("durationMs"));
                PartSpec part = PARTS.get(a.part);
                if (part == null) {
                    continue;
                }
                for (TaskSpec task : part.tasks()) {
                    Path f = dir.resolve(task.file());
                    if (Files.exists(f) && !a.files.containsKey(task.file())) {
                        a.files.put(task.file(), Files.readString(f));
                    }
                }
                current = a;
                System.out.println("  Resumed attempt " + a.id + " (part " + a.part + "), "
                        + (a.remaining() / 1000) + "s left.");
                arm(a);
                return;
            }
        } catch (Exception e) {
            // Nothing resumable; start clean.
        }
    }

    static void arm(Attempt a) {
        long delay = Math.max(0L, a.deadline() - System.currentTimeMillis()) + 400L;
        a.alarm = CLOCK.schedule(() -> autoSubmit(a), delay, TimeUnit.MILLISECONDS);
    }

    static void autoSubmit(Attempt a) {
        synchronized (LOCK) {
            if (a.submitted || current != a) {
                return;
            }
            System.out.println("  Time is up for attempt " + a.id + "; grading the last autosave.");
            try {
                finish(a, true);
            } catch (Exception e) {
                a.submitted = true;
                a.autoSubmitted = true;
                a.reportJson = "{\"error\":" + Json.quote(String.valueOf(e.getMessage())) + "}";
            }
        }
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    static void serveStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.equals("/index.html")) {
            path = "index.html";
        } else {
            path = path.substring(1);
        }
        if (!path.matches("[A-Za-z0-9._-]+")) {
            send(ex, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        Path file = webRoot.resolve(path);
        if (!Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String type = path.endsWith(".html") ? "text/html; charset=utf-8"
                : path.endsWith(".css") ? "text/css; charset=utf-8"
                : path.endsWith(".js") ? "application/javascript; charset=utf-8"
                : "application/octet-stream";
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        send(ex, 200, type, Files.readAllBytes(file));
    }

    static void handleStart(HttpExchange ex) throws IOException {
        Map<String, Object> body = Json.object(Json.parse(read(ex)));
        String partId = String.valueOf(body.get("part"));
        PartSpec part = PARTS.get(partId);
        if (part == null) {
            json(ex, 400, "{\"error\":\"unknown part\"}");
            return;
        }
        synchronized (LOCK) {
            if (current != null && !current.submitted && !current.expired()) {
                json(ex, 409, "{\"error\":\"an attempt is already running\"}");
                return;
            }
            Attempt a = new Attempt();
            a.id = STAMP.format(Instant.now()) + "-part" + partId;
            a.part = partId;
            a.startedAt = System.currentTimeMillis();
            a.durationMs = (demoSeconds > 0 ? demoSeconds * 1000L : part.minutes() * 60_000L);
            for (TaskSpec task : part.tasks()) {
                if (!a.files.containsKey(task.file())) {
                    a.files.put(task.file(), Files.readString(root.resolve(task.file())));
                }
            }
            current = a;
            persist(a);
            arm(a);
            System.out.println("  Started attempt " + a.id + " ("
                    + (a.durationMs / 1000) + "s).");
            json(ex, 200, stateJson());
        }
    }

    static void handleResume(HttpExchange ex) throws IOException {
        synchronized (LOCK) {
            if (current == null) {
                json(ex, 404, "{\"error\":\"no attempt\"}");
                return;
            }
            StringBuilder sb = new StringBuilder("{\"files\":{");
            boolean first = true;
            for (Map.Entry<String, String> e : current.files.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(Json.quote(e.getKey())).append(':').append(Json.quote(e.getValue()));
            }
            sb.append("}}");
            json(ex, 200, sb.toString());
        }
    }

    static void handleSave(HttpExchange ex) throws IOException {
        Map<String, Object> body = Json.object(Json.parse(read(ex)));
        synchronized (LOCK) {
            Attempt a = current;
            if (a == null || a.submitted) {
                json(ex, 409, "{\"error\":\"no attempt in progress\"}");
                return;
            }
            if (a.expired()) {
                json(ex, 410, "{\"error\":\"time is up\"}");
                return;
            }
            ingest(a, body.get("files"));
            persist(a);
            json(ex, 200, "{\"savedAt\":" + System.currentTimeMillis() + "}");
        }
    }

    static void handleCompile(HttpExchange ex) throws IOException {
        Map<String, Object> body = Json.object(Json.parse(read(ex)));
        Attempt a;
        synchronized (LOCK) {
            a = current;
            if (a == null || a.submitted) {
                json(ex, 409, "{\"error\":\"no attempt in progress\"}");
                return;
            }
            if (a.expired()) {
                json(ex, 410, "{\"error\":\"time is up\"}");
                return;
            }
            ingest(a, body.get("files"));
            persist(a);
        }
        try {
            Path work = stage(a, "compile");
            Shell result = run(work, 90, "javac", "-d", work.resolve("out").toString(),
                    "-encoding", "UTF-8", "-nowarn", javaSources(work));
            json(ex, 200, "{\"ok\":" + (result.code() == 0)
                    + ",\"output\":" + Json.quote(tidyDiagnostics(result.output(), work).strip())
                    + "}");
        } catch (Exception e) {
            json(ex, 500, "{\"ok\":false,\"output\":" + Json.quote(describe(e)) + "}");
        }
    }

    static void handleSubmit(HttpExchange ex) throws IOException {
        Map<String, Object> body = Json.object(Json.parse(read(ex)));
        synchronized (LOCK) {
            Attempt a = current;
            if (a == null) {
                json(ex, 409, "{\"error\":\"no attempt\"}");
                return;
            }
            if (a.submitted) {
                json(ex, 200, a.reportJson);
                return;
            }
            boolean late = a.expired();
            if (!late) {
                ingest(a, body.get("files"));
            }
            try {
                finish(a, late);
                json(ex, 200, a.reportJson);
            } catch (Exception e) {
                json(ex, 500, "{\"error\":" + Json.quote(describe(e)) + "}");
            }
        }
    }

    static void handleReport(HttpExchange ex) throws IOException {
        synchronized (LOCK) {
            if (current == null || !current.submitted || current.reportJson == null) {
                json(ex, 404, "{\"error\":\"no report\"}");
                return;
            }
            json(ex, 200, current.reportJson);
        }
    }

    static void handleAbandon(HttpExchange ex) throws IOException {
        synchronized (LOCK) {
            if (current != null && current.alarm != null) {
                current.alarm.cancel(false);
            }
            if (current != null && !current.submitted) {
                try {
                    Files.writeString(current.dir().resolve("ABANDONED.txt"),
                            "Abandoned at " + Instant.now() + "\n");
                } catch (IOException ignored) {
                    // best effort
                }
            }
            current = null;
            json(ex, 200, "{\"ok\":true}");
        }
    }

    // ------------------------------------------------------------------
    // Grading
    // ------------------------------------------------------------------

    /** Grade, mark the attempt closed, and keep everything on disk. */
    static void finish(Attempt a, boolean auto) throws Exception {
        PartSpec part = PARTS.get(a.part);
        Path work = stage(a, "final");
        Path out = work.resolve("out");

        Shell compile = run(work, 120, "javac", "-d", out.toString(),
                "-encoding", "UTF-8", "-nowarn", javaSources(work));

        String report;
        if (compile.code() != 0) {
            report = "{\"compiled\":false,\"compileOutput\":"
                    + Json.quote(tidyDiagnostics(compile.output(), work).strip())
                    + ",\"sections\":[],\"passed\":0,\"total\":0,\"earned\":0.0,\"available\":"
                    + part.points() + ".0,\"band\":"
                    + Json.quote("did not compile - every check counts as failed") + "}";
        } else {
            List<String> cmd = new ArrayList<>(List.of("java", "-cp", out.toString(),
                    "harness.Runner", "--json"));
            cmd.addAll(part.sections());
            // Cast so the array arrives as one varargs element, not spread.
            Shell graded = run(work, 180, (Object) cmd.toArray(new String[0]));
            String body = graded.output().strip();
            int brace = body.indexOf('{');
            if (brace < 0) {
                throw new IOException("grader produced no report: " + body);
            }
            report = body.substring(brace);
            report = report.substring(0, report.lastIndexOf('}') + 1);
            report = "{\"compiled\":true,\"compileOutput\":"
                    + Json.quote(tidyDiagnostics(compile.output(), work).strip())
                    + "," + report.substring(1);
        }

        a.submitted = true;
        a.autoSubmitted = auto;
        a.submittedAt = System.currentTimeMillis();
        if (a.alarm != null) {
            a.alarm.cancel(false);
        }

        long usedMs = Math.min(a.submittedAt - a.startedAt, a.durationMs);
        a.reportJson = "{\"part\":" + Json.quote(a.part)
                + ",\"partName\":" + Json.quote(part.name())
                + ",\"attempt\":" + Json.quote(a.id)
                + ",\"autoSubmitted\":" + auto
                + ",\"usedMs\":" + usedMs
                + ",\"durationMs\":" + a.durationMs
                + ",\"result\":" + report + "}";

        Files.writeString(a.dir().resolve("report.json"), a.reportJson);
        Files.writeString(a.dir().resolve("summary.txt"), humanSummary(a, part, usedMs));
        persist(a);

        // The staging copies have done their job; keep only the submission.
        try {
            deleteTree(a.dir().resolve("compile"));
            deleteTree(a.dir().resolve("final"));
        } catch (IOException ignored) {
            // Leaving them behind is untidy, not wrong.
        }
    }

    static String humanSummary(Attempt a, PartSpec part, long usedMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Attempt ").append(a.id).append('\n');
        sb.append(part.name()).append('\n');
        sb.append("Time used: ").append(clockText(usedMs)).append(" of ")
          .append(clockText(a.durationMs)).append('\n');
        sb.append(a.autoSubmitted ? "Submitted automatically when the clock ran out.\n"
                : "Submitted by the candidate.\n");
        sb.append("\nFull machine-readable report: report.json\n");
        sb.append("Submitted files: ").append(String.join(", ", a.files.keySet())).append('\n');
        return sb.toString();
    }

    static String clockText(long ms) {
        long total = ms / 1000;
        return String.format("%d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60);
    }

    /** Builds a throwaway copy of the project with the candidate's files in it. */
    static Path stage(Attempt a, String name) throws IOException {
        Path work = a.dir().resolve(name);
        deleteTree(work);
        Files.createDirectories(work);
        copyTree(root.resolve("tests"), work.resolve("tests"));
        copyTree(root.resolve("src"), work.resolve("src"));
        for (Map.Entry<String, String> e : a.files.entrySet()) {
            Path target = work.resolve(e.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, e.getValue());
        }
        return work;
    }

    static String[] javaSources(Path work) throws IOException {
        try (Stream<Path> files = Files.walk(work)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toString).sorted().toArray(String[]::new);
        }
    }

    record Shell(int code, String output) {}

    static Shell run(Path cwd, int timeoutSeconds, Object... parts) throws Exception {
        List<String> cmd = new ArrayList<>();
        for (Object p : parts) {
            if (p instanceof String[] many) {
                cmd.addAll(List.of(many));
            } else {
                cmd.add(String.valueOf(p));
            }
        }
        Process proc = new ProcessBuilder(cmd)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        StringBuilder sb = new StringBuilder();
        try (InputStream in = proc.getInputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
        }
        if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            return new Shell(-1, sb + "\n[timed out after " + timeoutSeconds + "s]");
        }
        return new Shell(proc.exitValue(), sb.toString());
    }

    // ------------------------------------------------------------------
    // State plumbing
    // ------------------------------------------------------------------

    /** Accepts only the files this part actually owns. */
    static void ingest(Attempt a, Object filesNode) {
        if (!(filesNode instanceof Map<?, ?> map)) {
            return;
        }
        PartSpec part = PARTS.get(a.part);
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey());
            boolean owned = part.tasks().stream().anyMatch(t -> t.file().equals(key));
            if (owned && e.getValue() instanceof String code) {
                a.files.put(key, code);
            }
        }
    }

    static void persist(Attempt a) {
        try {
            Files.createDirectories(a.dir());
            for (Map.Entry<String, String> e : a.files.entrySet()) {
                Path target = a.dir().resolve(e.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, e.getValue());
            }
            Files.writeString(a.dir().resolve("state.json"),
                    "{\"attempt\":" + Json.quote(a.id)
                            + ",\"part\":" + Json.quote(a.part)
                            + ",\"startedAt\":" + a.startedAt
                            + ",\"durationMs\":" + a.durationMs
                            + ",\"submitted\":" + a.submitted
                            + ",\"autoSubmitted\":" + a.autoSubmitted
                            + ",\"submittedAt\":" + a.submittedAt + "}");
        } catch (IOException e) {
            System.err.println("  could not persist attempt: " + e.getMessage());
        }
    }

    static String stateJson() {
        synchronized (LOCK) {
            if (current == null) {
                return "{\"phase\":\"idle\"}";
            }
            Attempt a = current;
            String phase = a.submitted ? "submitted" : (a.expired() ? "grading" : "running");
            return "{\"phase\":" + Json.quote(phase)
                    + ",\"attempt\":" + Json.quote(a.id)
                    + ",\"part\":" + Json.quote(a.part)
                    + ",\"remainingMs\":" + a.remaining()
                    + ",\"durationMs\":" + a.durationMs
                    + ",\"autoSubmitted\":" + a.autoSubmitted
                    + ",\"serverNow\":" + System.currentTimeMillis() + "}";
        }
    }

    static String manifestJson() {
        StringBuilder sb = new StringBuilder("{\"parts\":[");
        boolean firstPart = true;
        for (PartSpec part : PARTS.values()) {
            if (!firstPart) {
                sb.append(',');
            }
            firstPart = false;
            sb.append("{\"id\":").append(Json.quote(part.id()))
              .append(",\"name\":").append(Json.quote(part.name()))
              .append(",\"blurb\":").append(Json.quote(part.blurb()))
              .append(",\"minutes\":").append(part.minutes())
              .append(",\"points\":").append(part.points())
              .append(",\"intro\":").append(Json.quote(part.intro()))
              .append(",\"tasks\":[");
            boolean firstTask = true;
            for (TaskSpec task : part.tasks()) {
                if (!firstTask) {
                    sb.append(',');
                }
                firstTask = false;
                sb.append("{\"id\":").append(Json.quote(task.id()))
                  .append(",\"label\":").append(Json.quote(task.label()))
                  .append(",\"title\":").append(Json.quote(task.title()))
                  .append(",\"file\":").append(Json.quote(task.file()))
                  .append(",\"statement\":").append(Json.quote(task.statement()))
                  .append(",\"starter\":").append(Json.quote(task.starter()))
                  .append('}');
            }
            sb.append("]}");
        }
        sb.append("],\"demoSeconds\":").append(demoSeconds).append('}');
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Tiny HTTP + JSON helpers
    // ------------------------------------------------------------------

    static String read(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void json(HttpExchange ex, int status, String body) throws IOException {
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        send(ex, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    /** javac prints absolute paths into the staging copy; show project paths. */
    static String tidyDiagnostics(String output, Path work) {
        String prefix = work.toAbsolutePath().toString();
        return output.replace(prefix + java.io.File.separator, "")
                     .replace(prefix, "")
                     .replace("\\", "/");
    }

    static String describe(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    static void copyTree(Path from, Path to) throws IOException {
        if (!Files.exists(from)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    /** Just enough JSON for request bodies and the resumed state file. */
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            Json p = new Json(text == null ? "" : text);
            p.ws();
            if (p.i >= p.s.length()) {
                return new LinkedHashMap<String, Object>();
            }
            return p.value();
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> object(Object o) {
            return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
        }

        static String string(Object o) {
            return o == null ? null : String.valueOf(o);
        }

        static double number(Object o) {
            return o instanceof Number n ? n.doubleValue() : 0;
        }

        static boolean bool(Object o) {
            return Boolean.TRUE.equals(o);
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private Object value() {
            ws();
            if (i >= s.length()) {
                return null;
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> obj();
                case '[' -> arr();
                case '"' -> str();
                case 't' -> lit("true", Boolean.TRUE);
                case 'f' -> lit("false", Boolean.FALSE);
                case 'n' -> lit("null", null);
                default -> num();
            };
        }

        private Object lit(String word, Object val) {
            i += word.length();
            return val;
        }

        private Map<String, Object> obj() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            ws();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return map;
            }
            while (i < s.length()) {
                ws();
                String key = str();
                ws();
                if (i < s.length() && s.charAt(i) == ':') {
                    i++;
                }
                map.put(key, value());
                ws();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                }
                break;
            }
            return map;
        }

        private List<Object> arr() {
            List<Object> list = new ArrayList<>();
            i++; // [
            ws();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (i < s.length()) {
                list.add(value());
                ws();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                }
                break;
            }
            return list;
        }

        private String str() {
            StringBuilder sb = new StringBuilder();
            i++; // opening quote
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    break;
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char esc = s.charAt(i++);
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> sb.append(esc);
                }
            }
            return sb.toString();
        }

        private Object num() {
            int start = i;
            while (i < s.length() && "+-.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            try {
                return Double.parseDouble(s.substring(start, i));
            } catch (RuntimeException e) {
                return null;
            }
        }

        static String quote(String s) {
            if (s == null) {
                return "null";
            }
            StringBuilder sb = new StringBuilder("\"");
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.append('"').toString();
        }
    }
}
