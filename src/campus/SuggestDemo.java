package campus;

import java.sql.*;
import java.util.*;

/**
 * SuggestDemo.java  |  Shows BFS results + the suggestions Python saved.
 * Run:  java -cp "bin;lib/*" campus.SuggestDemo <myId> <otherId>
 */
public class SuggestDemo {
    static Connection con;

    static String name(int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT name FROM users WHERE user_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "?";
            }
        }
    }

    static String names(List<Integer> ids) throws SQLException {
        List<String> out = new ArrayList<>();
        for (int id : ids) out.add(name(id));
        return out.isEmpty() ? "(none)" : String.join(", ", out);
    }

    public static void main(String[] args) throws Exception {
        int me = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        int other = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        con = DriverManager.getConnection("jdbc:sqlite:database/campus.db");
        Database db = new Database();
        Graph g = new Graph(db);

        System.out.println("=== 8. BFS levels from " + name(me) + " (2 hops) ===");
        for (Map.Entry<Integer, Integer> e : g.bfsLevels(me, 2).entrySet()) {
            if (e.getValue() > 0) {
                System.out.println("  level " + e.getValue() + ": " + name(e.getKey()));
            }
        }

        System.out.println();
        System.out.println("=== 9. Mutual friends: " + name(me) + " and " + name(other) + " ===");
        System.out.println("  " + names(g.mutualFriends(me, other)));

        System.out.println();
        System.out.println("=== 10. Connection path ===");
        List<Integer> path = g.shortestPath(me, other);
        System.out.println(path.isEmpty() ? "  no connection" : "  " + names(path).replace(", ", " -> "));

        System.out.println();
        System.out.println("=== 11. People you may know for " + name(me) + " (from Python score) ===");
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT suggested_id, mutual_count, similarity_score FROM suggested_friends "
                + "WHERE user_id = ? ORDER BY similarity_score DESC LIMIT 5")) {
            ps.setInt(1, me);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    int sid = rs.getInt(1);
                    System.out.println("  " + name(sid) + "  | score " + rs.getDouble(3)
                            + " | " + rs.getInt(2) + " mutual: " + names(g.mutualFriends(me, sid)));
                }
                if (!any) System.out.println("  (no suggestions - run similarity.py first)");
            }
        }

        db.close();
        con.close();
    }
}
