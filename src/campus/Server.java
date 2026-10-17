package campus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server.java  |  The WEB part of CampusConnect.
 *
 * It does two jobs:
 *   1. Serves the website files from the "web" folder (index.html)
 *   2. Answers "API" requests like /api/feed?me=1 with JSON data.
 *
 * The website (browser) never touches the database directly.
 * Browser --(HTTP request)--> Server.java --(SQL)--> campus.db
 * Browser <--(JSON answer)--- Server.java <--------- campus.db
 *
 * Actions that change data (post, like, comment, delete, follow)
 * reuse the methods we already wrote in Database.java.
 * Run:  java -cp "bin;lib/*" campus.Server   then open http://localhost:8080
 */
public class Server {
    static Connection con;     // our own connection, used for reading data
    static Database db;        // Step 3 class, used for actions
    static Graph graph;        // Step 4 class, used for BFS paths
    static final Path WEB = Paths.get("web").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        con = DriverManager.getConnection("jdbc:sqlite:database/campus.db");
        try (Statement st = con.createStatement()) {
            st.execute("PRAGMA busy_timeout = 5000");
            st.execute("PRAGMA foreign_keys = ON");
        }
        db = new Database();
        graph = new Graph(db);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", Server::handle);
        // several threads, so one idle browser connection cannot block the others
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("CampusConnect is running. Open http://localhost:8080 in your browser.");
        System.out.println("(Press Ctrl+C in this terminal to stop.)");
    }

    // ---------------------------------------------------------------
    // Every browser request comes here first
    // ---------------------------------------------------------------
    static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                Map<String, String> q = params(ex.getRequestURI().getRawQuery());
                String json = api(path, q);
                send(ex, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
            } else {
                serveFile(ex, path);
            }
        } catch (Exception e) {
            e.printStackTrace();
            String json = "{\"error\":" + quote(String.valueOf(e.getMessage())) + "}";
            send(ex, 500, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------------------------------------------------------------
    // The API: one "case" per button/page of the website
    // ---------------------------------------------------------------
    static final String POST_COLS =
        "SELECT p.post_id, p.user_id, u.name, p.content, p.image_path, p.is_doubt, p.is_solved, p.created_at, "
        + "(SELECT COUNT(*) FROM likes l WHERE l.post_id = p.post_id) AS likes, "
        + "(SELECT COUNT(*) FROM comments c WHERE c.post_id = p.post_id) AS comments, "
        + "(SELECT COUNT(*) FROM likes l WHERE l.post_id = p.post_id AND l.user_id = ?) AS liked "
        + "FROM posts p JOIN users u ON u.user_id = p.user_id ";

    static synchronized String api(String path, Map<String, String> q) throws Exception {
        int me = num(q, "me");
        switch (path) {

            case "/api/users":
                return rows("SELECT user_id, name, department, year FROM users ORDER BY name");

            // ---------- home feed: my posts + posts of people I follow ----------
            case "/api/feed":
                return rows(POST_COLS
                    + "WHERE p.user_id = ? OR p.user_id IN (SELECT following_id FROM follows WHERE follower_id = ?) "
                    + "ORDER BY p.created_at DESC", me, me, me);

            case "/api/comments":
                return rows("SELECT c.comment_id, c.user_id, u.name, c.text, c.is_helpful, c.created_at "
                    + "FROM comments c JOIN users u ON u.user_id = c.user_id "
                    + "WHERE c.post_id = ? ORDER BY c.created_at, c.comment_id", num(q, "post"));

            // ---------- posts, likes, comments ----------
            case "/api/post":
                db.addPost(me, q.get("content"), null, num(q, "doubt") == 1);
                return ok(true);
            case "/api/like":
                db.toggleLike(num(q, "post"), me);
                return ok(true);
            case "/api/comment":
                db.addComment(num(q, "post"), me, q.get("text"));
                return ok(true);
            case "/api/delete-post":
                return ok(db.deletePost(num(q, "post"), me));
            case "/api/delete-comment":
                return ok(db.deleteComment(num(q, "comment"), me));
            case "/api/helpful":
                return ok(db.markHelpful(num(q, "comment"), me));

            // ---------- people you may know (filled by similarity.py) ----------
            case "/api/suggestions":
                return rows("SELECT s.suggested_id AS user_id, u.name, u.department, u.year, "
                    + "s.mutual_count, s.similarity_score, "
                    + "(SELECT group_concat(m.name, ', ') FROM follows a "
                    + "   JOIN follows b ON a.following_id = b.follower_id "
                    + "   JOIN users m ON m.user_id = a.following_id "
                    + "   WHERE a.follower_id = s.user_id AND b.following_id = s.suggested_id) AS mutual_names "
                    + "FROM suggested_friends s JOIN users u ON u.user_id = s.suggested_id "
                    + "WHERE s.user_id = ? ORDER BY s.similarity_score DESC LIMIT 8", me);

            // ---------- follow / unfollow, then recompute suggestions ----------
            case "/api/follow": {
                int target = num(q, "target");
                boolean nowFollowing;
                if (db.isFollowing(me, target)) {
                    db.unfollow(me, target);
                    nowFollowing = false;
                } else {
                    db.follow(me, target);
                    nowFollowing = true;
                }
                refreshSuggestions();
                return "{\"following\":" + nowFollowing + "}";
            }

            // ---------- profile page ----------
            case "/api/profile": {
                int id = num(q, "id");
                String pathJson = "[]";
                String mutual = "[]";
                if (id != me) {
                    pathJson = pathNames(me, id);
                    mutual = rows("SELECT u.name FROM follows a JOIN follows b ON a.following_id = b.follower_id "
                        + "JOIN users u ON u.user_id = a.following_id "
                        + "WHERE a.follower_id = ? AND b.following_id = ?", me, id);
                }
                return "{\"user\":" + rows("SELECT user_id, name, department, year, bio FROM users WHERE user_id = ?", id)
                    + ",\"skills\":" + rows("SELECT skill FROM user_skills WHERE user_id = ?", id)
                    + ",\"clubs\":" + rows("SELECT club FROM user_clubs WHERE user_id = ?", id)
                    + ",\"followers\":" + db.countFollowers(id)
                    + ",\"following\":" + db.getFollowingIds(id).size()
                    + ",\"isFollowing\":" + db.isFollowing(me, id)
                    + ",\"helpful\":" + db.countHelpful(id)
                    + ",\"mutual\":" + mutual
                    + ",\"path\":" + pathJson
                    + ",\"posts\":" + rows(POST_COLS + "WHERE p.user_id = ? ORDER BY p.created_at DESC", me, id)
                    + "}";
            }

            case "/api/bio":
                update("UPDATE users SET bio = ? WHERE user_id = ?", q.get("bio"), me);
                return ok(true);

            // ---------- events board with RSVP ----------
            case "/api/events":
                return rows("SELECT e.event_id, e.title, e.club, e.when_where, e.image_path, u.name AS organizer, "
                    + "(SELECT group_concat(g.name, ', ') FROM event_rsvps r2 JOIN users g ON g.user_id = r2.user_id WHERE r2.event_id = e.event_id) AS going_names, "
                    + "(SELECT COUNT(*) FROM event_rsvps r WHERE r.event_id = e.event_id) AS going, "
                    + "(SELECT COUNT(*) FROM event_rsvps r WHERE r.event_id = e.event_id AND r.user_id = ?) AS mine "
                    + "FROM events e JOIN users u ON u.user_id = e.organizer_id ORDER BY e.event_id DESC", me);
            case "/api/rsvp": {
                int ev = num(q, "event");
                int removed = update("DELETE FROM event_rsvps WHERE event_id = ? AND user_id = ?", ev, me);
                if (removed == 0) {
                    update("INSERT INTO event_rsvps (event_id, user_id) VALUES (?, ?)", ev, me);
                }
                return ok(true);
            }
            case "/api/event":
                update("INSERT INTO events (title, club, when_where, organizer_id) VALUES (?, ?, ?, ?)",
                    q.get("title"), q.get("club"), q.get("when"), me);
                return ok(true);

            // ---------- skill search / teammate finder ----------
            case "/api/search": {
                String skill = "%" + q.getOrDefault("skill", "").trim().toLowerCase() + "%";
                return rows("SELECT u.user_id, u.name, u.department, u.year, "
                    + "(SELECT group_concat(skill, ', ') FROM user_skills WHERE user_id = u.user_id) AS skills "
                    + "FROM users u WHERE u.user_id <> ? AND u.user_id IN "
                    + "(SELECT user_id FROM user_skills WHERE lower(skill) LIKE ?)", me, skill);
            }

            // ---------- mentor suggestions: seniors who share my skills ----------
            case "/api/mentors":
                return rows("SELECT u.user_id, u.name, u.department, u.year, "
                    + "(SELECT group_concat(s.skill, ', ') FROM user_skills s WHERE s.user_id = u.user_id "
                    + "   AND s.skill IN (SELECT skill FROM user_skills WHERE user_id = ?)) AS shared_skills "
                    + "FROM users u WHERE u.year > (SELECT year FROM users WHERE user_id = ?) "
                    + "AND EXISTS (SELECT 1 FROM user_skills a JOIN user_skills b ON a.skill = b.skill "
                    + "            WHERE a.user_id = u.user_id AND b.user_id = ?) "
                    + "ORDER BY u.year DESC", me, me, me);

            default:
                return "{\"error\":\"unknown api\"}";
        }
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    // BFS shortest path (Graph.java) turned into a JSON list of names
    static String pathNames(int from, int to) throws SQLException {
        List<Integer> ids = graph.shortestPath(from, to);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(quote(one("SELECT name FROM users WHERE user_id = ?", ids.get(i))));
        }
        return sb.append("]").toString();
    }

    // run similarity.py again so suggestions stay fresh after a follow/unfollow
    static void refreshSuggestions() {
        try {
            Process p = new ProcessBuilder("python", "database/similarity.py")
                .redirectErrorStream(true).start();
            while (p.getInputStream().read() != -1) { /* drain output */ }
            p.waitFor();
        } catch (Exception e) {
            System.out.println("Could not run similarity.py: " + e);
        }
    }

    // SELECT -> JSON array of objects, e.g. [{"name":"Ravi","year":2}, ...]
    static String rows(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                StringBuilder sb = new StringBuilder("[");
                boolean firstRow = true;
                while (rs.next()) {
                    if (!firstRow) sb.append(",");
                    firstRow = false;
                    sb.append("{");
                    for (int c = 1; c <= md.getColumnCount(); c++) {
                        if (c > 1) sb.append(",");
                        sb.append(quote(md.getColumnLabel(c))).append(":");
                        Object v = rs.getObject(c);
                        if (v == null) sb.append("null");
                        else if (v instanceof Number) sb.append(v);
                        else sb.append(quote(v.toString()));
                    }
                    sb.append("}");
                }
                return sb.append("]").toString();
            }
        }
    }

    // SELECT that returns just one text value
    static String one(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    // INSERT / UPDATE / DELETE -> returns how many rows changed
    static int update(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps.executeUpdate();
        }
    }

    static String ok(boolean b) {
        return "{\"ok\":" + b + "}";
    }

    static int num(Map<String, String> q, String key) {
        String v = q.get(key);
        return (v == null || v.trim().isEmpty()) ? 0 : Integer.parseInt(v.trim());
    }

    // "me=1&post=5" -> {me=1, post=5}
    static Map<String, String> params(String raw) throws Exception {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) continue;
            m.put(URLDecoder.decode(pair.substring(0, i), "UTF-8"),
                  URLDecoder.decode(pair.substring(i + 1), "UTF-8"));
        }
        return m;
    }

    // make a Java string safe to put inside JSON
    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // serve index.html and other files from the "web" folder
    static void serveFile(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        Path f = WEB.resolve(path.substring(1)).normalize();
        if (!f.startsWith(WEB) || !Files.isRegularFile(f)) {
            send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String n = f.getFileName().toString().toLowerCase();
        String type = n.endsWith(".html") ? "text/html; charset=utf-8"
            : n.endsWith(".css") ? "text/css; charset=utf-8"
            : n.endsWith(".js") ? "text/javascript; charset=utf-8"
            : n.endsWith(".png") ? "image/png"
            : (n.endsWith(".jpg") || n.endsWith(".jpeg")) ? "image/jpeg"
            : "application/octet-stream";
        send(ex, 200, type, Files.readAllBytes(f));
    }

    static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
