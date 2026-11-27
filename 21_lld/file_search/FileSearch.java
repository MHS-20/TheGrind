import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

public class FileSearch {

    // ---------- file tree (composite) ----------

    sealed interface FsNode permits File, Directory {
        String name();
    }

    record File(String name, long size, String owner, LocalDate modified) implements FsNode {
        File(String name, long size) { this(name, size, "root", LocalDate.of(2026, 1, 1)); }

        String extension() {
            int dot = name.lastIndexOf('.');
            return dot < 0 ? "" : name.substring(dot + 1);
        }
    }

    record Directory(String name, List<FsNode> children, boolean readable) implements FsNode {
        Directory(String name, FsNode... children) { this(name, new ArrayList<>(List.of(children)), true); }
    }

    // ---------- filters (composite + strategy) ----------

    /** A pure predicate on a file. New criteria are new implementations; the walker never changes. */
    @FunctionalInterface
    interface Filter {
        boolean matches(File f);

        default Filter and(Filter other) { return new And(this, other); }

        default Filter or(Filter other) { return new Or(this, other); }

        default Filter not() { return new Not(this); }
    }

    record SizeGreaterThan(long bytes) implements Filter {
        public boolean matches(File f) { return f.size() > bytes; }
    }

    record ExtensionIs(String ext) implements Filter {
        public boolean matches(File f) { return f.extension().equalsIgnoreCase(ext); }
    }

    /** Glob on the file name: {@code *} any run, {@code ?} one character. */
    record NameMatches(Pattern regex) implements Filter {
        static NameMatches glob(String glob) {
            StringBuilder re = new StringBuilder();
            for (char c : glob.toCharArray()) {
                re.append(switch (c) {
                    case '*' -> ".*";
                    case '?' -> ".";
                    default -> Pattern.quote(String.valueOf(c));
                });
            }
            return new NameMatches(Pattern.compile(re.toString()));
        }

        public boolean matches(File f) { return regex.matcher(f.name()).matches(); }
    }

    record OwnerIs(String owner) implements Filter {
        public boolean matches(File f) { return f.owner().equals(owner); }
    }

    record ModifiedAfter(LocalDate date) implements Filter {
        public boolean matches(File f) { return f.modified().isAfter(date); }
    }

    record And(Filter left, Filter right) implements Filter {
        public boolean matches(File f) { return left.matches(f) && right.matches(f); }
    }

    record Or(Filter left, Filter right) implements Filter {
        public boolean matches(File f) { return left.matches(f) || right.matches(f); }
    }

    record Not(Filter inner) implements Filter {
        public boolean matches(File f) { return !inner.matches(f); }
    }

    // ---------- search ----------

    static List<File> find(Directory root, Filter filter) { return find(root, filter, Integer.MAX_VALUE); }

    /** Iterative DFS with an explicit stack: no recursion depth limit; stops after {@code limit} results. */
    static List<File> find(Directory root, Filter filter, int limit) {
        List<File> out = new ArrayList<>();
        Deque<FsNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty() && out.size() < limit) {
            switch (stack.pop()) {
                case File f -> { if (filter.matches(f)) out.add(f); }
                case Directory d -> {
                    if (!d.readable()) continue;
                    for (int i = d.children().size() - 1; i >= 0; i--) stack.push(d.children().get(i)); // keep order
                }
            }
        }
        return out;
    }

    // ---------- checks ----------

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
        if (!ok) failures++;
    }

    static List<String> names(List<File> files) { return files.stream().map(File::name).toList(); }

    static final long MB = 1_000_000;

    static Directory sample() {
        return new Directory("/",
                new File("readme.md", 2_000),
                new Directory("src",
                        new File("Main.java", 40_000),
                        new File("config.xml", 6 * MB),
                        new Directory("data",
                                new File("big.json", 8 * MB),
                                new File("small.json", 1_000, "ann", LocalDate.of(2026, 6, 1)))),
                new Directory("logs", new File("app.log", 20 * MB)));
    }

    public static void main(String[] args) {
        System.out.println("Stage 1: core model");
        {
            Directory root = sample();
            check("size greater than 5MB",
                    names(find(root, new SizeGreaterThan(5 * MB))).equals(List.of("config.xml", "big.json", "app.log")));
            check("extension equals json",
                    names(find(root, new ExtensionIs("json"))).equals(List.of("big.json", "small.json")));
            check("name matches glob", names(find(root, NameMatches.glob("*.m?"))).equals(List.of("readme.md")));
            check("search from a subdirectory",
                    find((Directory) root.children().get(1), new ExtensionIs("log")).isEmpty());
        }

        System.out.println("Stage 2: combining filters");
        {
            Directory root = sample();
            Filter bigData = new SizeGreaterThan(5 * MB).and(new ExtensionIs("xml").or(new ExtensionIs("json")));
            check("size > 5MB AND (xml OR json)", names(find(root, bigData)).equals(List.of("config.xml", "big.json")));
            check("NOT json", find(root, new ExtensionIs("json").not()).size() == 4);
            check("new filter types: owner and modified date",
                    names(find(root, new OwnerIs("ann").and(new ModifiedAfter(LocalDate.of(2026, 3, 1)))))
                            .equals(List.of("small.json")));
            Filter nested = new Not(new Or(new ExtensionIs("log"), new And(new ExtensionIs("json"), new SizeGreaterThan(MB))));
            check("nested to any depth", find(root, nested).size() == 4);
        }

        System.out.println("Stage 3: large trees");
        {
            Directory root = sample();
            check("stops after k results", find(root, f -> true, 2).size() == 2);
            Directory secret = new Directory("secret", List.of(new File("key.pem", 10)), false);
            root.children().add(secret);
            check("unreadable directory skipped", find(root, new ExtensionIs("pem")).isEmpty());

            Directory deep = new Directory("d0");
            Directory cur = deep;
            for (int i = 1; i < 200_000; i++) {
                Directory next = new Directory("d" + i);
                cur.children().add(next);
                cur = next;
            }
            cur.children().add(new File("bottom.txt", 1));
            check("200k-deep tree does not overflow the stack",
                    names(find(deep, new ExtensionIs("txt"))).equals(List.of("bottom.txt")));
        }

        System.out.println(failures == 0 ? "All checks passed" : failures + " check(s) failed");
        if (failures > 0) System.exit(1);
    }
}
