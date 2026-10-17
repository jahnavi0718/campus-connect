package campus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Database.java
 * The ONLY class that talks to the database (campus.db).
 * Other classes ask Database for data and never write SQL themselves.
 *
 * JDBC = the Java way to run SQL. Steps for every query:
 *   1. write the SQL            2. run it           3. read the result rows
 */
public class Database {

    // Where the database file is. We run the program from the CampusConnect folder.
    private static final String URL = "jdbc:sqlite:database/campus.db";

    private Connection conn;   // the open connection to the database

    // Constructor: opens the connection
    public Database() throws SQLException {
        conn = DriverManager.getConnection(URL);
        // Tell SQLite to enforce FOREIGN KEY rules (e.g. you cannot like a post that does not exist)
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
    }

    // Current date and time as text, e.g. "2026-10-02 14:30" (saved in created_at)
    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    // Close the connection when we are finished
    public void close() throws SQLException {
        conn.close();
    }

    // ---------- USERS ----------

    // Returns every student in the database
    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT user_id, name, department, year, bio FROM users ORDER BY user_id";

        // "?" is a placeholder, here we have none. ResultSet = the result rows.
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {                       // go through the rows one by one
                User u = new User(
                    rs.getInt("user_id"),
                    rs.getString("name"),
                    rs.getString("department"),
                    rs.getInt("year"),
                    rs.getString("bio"));
                loadSkillsAndClubs(u);                // fill skills and clubs lists
                users.add(u);
            }
        }
        return users;
    }

    // Returns one student by id (or null if not found)
    public User getUser(int userId) throws SQLException {
        String sql = "SELECT user_id, name, department, year, bio FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);                     // put userId in place of the "?"
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = new User(
                        rs.getInt("user_id"),
                        rs.getString("name"),
                        rs.getString("department"),
                        rs.getInt("year"),
                        rs.getString("bio"));
                    loadSkillsAndClubs(u);
                    return u;
                }
            }
        }
        return null;
    }

    // Reads this student's skills and clubs from their tables and adds them to the User
    private void loadSkillsAndClubs(User u) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT skill FROM user_skills WHERE user_id = ?")) {
            ps.setInt(1, u.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) u.addSkill(rs.getString("skill"));
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT club FROM user_clubs WHERE user_id = ?")) {
            ps.setInt(1, u.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) u.addClub(rs.getString("club"));
            }
        }
    }

    // ---------- POSTS ----------

    // Returns all posts written by one student, newest first
    public List<Post> getPostsByUser(int userId) throws SQLException {
        List<Post> posts = new ArrayList<>();
        String sql = "SELECT post_id, user_id, content, image_path, is_doubt, is_solved, created_at "
                   + "FROM posts WHERE user_id = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(new Post(
                        rs.getInt("post_id"),
                        rs.getInt("user_id"),
                        rs.getString("content"),
                        rs.getString("image_path"),
                        rs.getInt("is_doubt") == 1,   // 1 in the database = true in Java
                        rs.getInt("is_solved") == 1,
                        rs.getString("created_at")));
                }
            }
        }
        return posts;
    }

    // ======================================================================
    // STEP 3 : ACTIONS (follow, post, like, comment, delete)
    // INSERT / UPDATE / DELETE use executeUpdate() and return how many rows changed.
    // ======================================================================

    // ---------- FOLLOW ----------

    // a starts following b. Returns true if a new follow was added.
    public boolean follow(int a, int b) throws SQLException {
        if (a == b) return false;                       // nobody can follow themselves
        // INSERT OR IGNORE: if the pair already exists, do nothing (no error)
        String sql = "INSERT OR IGNORE INTO follows (follower_id, following_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            return ps.executeUpdate() > 0;
        }
    }

    // a stops following b. Returns true if a follow was removed.
    public boolean unfollow(int a, int b) throws SQLException {
        String sql = "DELETE FROM follows WHERE follower_id = ? AND following_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            return ps.executeUpdate() > 0;
        }
    }

    // Does a follow b ?
    public boolean isFollowing(int a, int b) throws SQLException {
        String sql = "SELECT 1 FROM follows WHERE follower_id = ? AND following_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();                       // a row exists = true
            }
        }
    }

    // Ids of all people that this user follows
    public List<Integer> getFollowingIds(int userId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT following_id FROM follows WHERE follower_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt("following_id"));
            }
        }
        return ids;
    }

    // How many people follow this user
    public int countFollowers(int userId) throws SQLException {
        return countOne("SELECT COUNT(*) FROM follows WHERE following_id = ?", userId);
    }

    // ---------- POSTS ----------

    // Creates a new post and returns its new id. imagePath can be null.
    public int addPost(int userId, String content, String imagePath, boolean isDoubt) throws SQLException {
        String sql = "INSERT INTO posts (user_id, content, image_path, is_doubt, is_solved, created_at) "
                   + "VALUES (?, ?, ?, ?, 0, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, content);
            ps.setString(3, imagePath);
            ps.setInt(4, isDoubt ? 1 : 0);              // true -> 1, false -> 0
            ps.setString(5, now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {   // the id the database gave
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    // Deletes a post. Only the owner can delete. Returns true if deleted.
    public boolean deletePost(int postId, int userId) throws SQLException {
        // 1. check that this user owns the post
        try (PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM posts WHERE post_id = ?")) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt("user_id") != userId) return false;
            }
        }
        // 2. delete likes and comments of the post first, then the post itself
        //    (they point to the post, so they must go first)
        runUpdate("DELETE FROM likes WHERE post_id = ?", postId);
        runUpdate("DELETE FROM comments WHERE post_id = ?", postId);
        runUpdate("DELETE FROM posts WHERE post_id = ?", postId);
        return true;
    }

    // The home feed: my posts + posts of people I follow, newest first
    public List<Post> getFeed(int userId) throws SQLException {
        List<Post> posts = new ArrayList<>();
        String sql = "SELECT post_id, user_id, content, image_path, is_doubt, is_solved, created_at "
                   + "FROM posts "
                   + "WHERE user_id = ? OR user_id IN (SELECT following_id FROM follows WHERE follower_id = ?) "
                   + "ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    posts.add(new Post(
                        rs.getInt("post_id"), rs.getInt("user_id"),
                        rs.getString("content"), rs.getString("image_path"),
                        rs.getInt("is_doubt") == 1, rs.getInt("is_solved") == 1,
                        rs.getString("created_at")));
                }
            }
        }
        return posts;
    }

    // ---------- LIKES ----------

    // Like if not liked yet, unlike if already liked. Returns true if the post is now liked.
    public boolean toggleLike(int postId, int userId) throws SQLException {
        String check = "SELECT 1 FROM likes WHERE post_id = ? AND user_id = ?";
        boolean alreadyLiked;
        try (PreparedStatement ps = conn.prepareStatement(check)) {
            ps.setInt(1, postId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                alreadyLiked = rs.next();
            }
        }
        String sql = alreadyLiked
            ? "DELETE FROM likes WHERE post_id = ? AND user_id = ?"
            : "INSERT INTO likes (post_id, user_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, postId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        return !alreadyLiked;
    }

    public int getLikeCount(int postId) throws SQLException {
        return countOne("SELECT COUNT(*) FROM likes WHERE post_id = ?", postId);
    }

    // ---------- COMMENTS ----------

    // Adds a comment and returns its id
    public int addComment(int postId, int userId, String text) throws SQLException {
        String sql = "INSERT INTO comments (post_id, user_id, text, is_helpful, created_at) VALUES (?, ?, ?, 0, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, postId);
            ps.setInt(2, userId);
            ps.setString(3, text);
            ps.setString(4, now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    // All comments of a post, oldest first
    public List<Comment> getComments(int postId) throws SQLException {
        List<Comment> list = new ArrayList<>();
        String sql = "SELECT comment_id, post_id, user_id, text, is_helpful, created_at "
                   + "FROM comments WHERE post_id = ? ORDER BY comment_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Comment(
                        rs.getInt("comment_id"), rs.getInt("post_id"), rs.getInt("user_id"),
                        rs.getString("text"), rs.getInt("is_helpful") == 1,
                        rs.getString("created_at")));
                }
            }
        }
        return list;
    }

    public int getCommentCount(int postId) throws SQLException {
        return countOne("SELECT COUNT(*) FROM comments WHERE post_id = ?", postId);
    }

    // Deletes a comment. Only the person who wrote it can delete it.
    public boolean deleteComment(int commentId, int userId) throws SQLException {
        String sql = "DELETE FROM comments WHERE comment_id = ? AND user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commentId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // The owner of a DOUBT post marks someone's comment as helpful.
    // Then the comment becomes "helpful" and the doubt becomes "solved".
    public boolean markHelpful(int commentId, int byUserId) throws SQLException {
        // find the post of this comment and its owner
        String find = "SELECT p.post_id, p.user_id AS owner, p.is_doubt, c.user_id AS commenter "
                    + "FROM comments c JOIN posts p ON p.post_id = c.post_id WHERE c.comment_id = ?";
        int postId;
        try (PreparedStatement ps = conn.prepareStatement(find)) {
            ps.setInt(1, commentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;                         // comment not found
                boolean ownerIsMe = rs.getInt("owner") == byUserId;
                boolean isDoubt = rs.getInt("is_doubt") == 1;
                boolean notMyOwnComment = rs.getInt("commenter") != byUserId;
                if (!(ownerIsMe && isDoubt && notMyOwnComment)) return false;   // rules not met
                postId = rs.getInt("post_id");
            }
        }
        runUpdate("UPDATE comments SET is_helpful = 1 WHERE comment_id = ?", commentId);
        runUpdate("UPDATE posts SET is_solved = 1 WHERE post_id = ?", postId);
        return true;
    }

    // How many of this student's comments were marked helpful (for the Helper badge)
    public int countHelpful(int userId) throws SQLException {
        return countOne("SELECT COUNT(*) FROM comments WHERE user_id = ? AND is_helpful = 1", userId);
    }

    // ---------- SMALL HELPERS (used by the methods above) ----------

    // Runs "SELECT COUNT(*) ... WHERE something = ?" and returns the number
    private int countOne(String sql, int value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Runs an INSERT / UPDATE / DELETE that has one "?" number
    private void runUpdate(String sql, int value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, value);
            ps.executeUpdate();
        }
    }
}
