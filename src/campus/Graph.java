package campus;

import java.sql.SQLException;
import java.util.*;

/**
 * Graph.java  |  ADSA part: the follow table seen as a GRAPH.
 * Node = student, Edge = "A follows B".
 * BFS (Breadth First Search) visits nodes level by level using a QUEUE:
 *   level 1 = people I follow, level 2 = people they follow, ...
 */
public class Graph {
    private final Database db;

    public Graph(Database db) {
        this.db = db;
    }

    // neighbours of a student = the people he/she follows
    private List<Integer> neighbours(int u) throws SQLException {
        return db.getFollowingIds(u);
    }

    // BFS from "start": returns map  student -> distance (how many hops away)
    public Map<Integer, Integer> bfsLevels(int start, int maxDepth) throws SQLException {
        Map<Integer, Integer> dist = new LinkedHashMap<>();
        Queue<Integer> queue = new LinkedList<>();
        dist.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            if (dist.get(cur) == maxDepth) continue;     // do not go deeper
            for (int next : neighbours(cur)) {
                if (!dist.containsKey(next)) {           // not visited yet
                    dist.put(next, dist.get(cur) + 1);
                    queue.add(next);
                }
            }
        }
        return dist;
    }

    // Mutual friends between a and c = people m such that  a -> m -> c
    public List<Integer> mutualFriends(int a, int c) throws SQLException {
        List<Integer> result = new ArrayList<>();
        for (int m : neighbours(a)) {
            if (db.isFollowing(m, c)) result.add(m);
        }
        return result;
    }

    // Shortest connection path from start to target (BFS + parent map)
    public List<Integer> shortestPath(int start, int target) throws SQLException {
        Map<Integer, Integer> parent = new HashMap<>();
        Queue<Integer> queue = new LinkedList<>();
        parent.put(start, -1);
        queue.add(start);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            if (cur == target) break;
            for (int next : neighbours(cur)) {
                if (!parent.containsKey(next)) {
                    parent.put(next, cur);
                    queue.add(next);
                }
            }
        }
        List<Integer> path = new ArrayList<>();
        if (!parent.containsKey(target)) return path;    // no connection
        for (int at = target; at != -1; at = parent.get(at)) path.add(at);
        Collections.reverse(path);
        return path;
    }
}
