package campus;

/**
 * Comment.java  (OOPJ class)
 * One Comment object = one row of the "comments" table.
 */
public class Comment {

    private int id;
    private int postId;        // which post this comment is on
    private int userId;        // who wrote the comment
    private String text;
    private boolean helpful;   // true = the doubt owner marked it as helpful
    private String createdAt;

    public Comment(int id, int postId, int userId, String text, boolean helpful, String createdAt) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.text = text;
        this.helpful = helpful;
        this.createdAt = createdAt;
    }

    public int getId()          { return id; }
    public int getPostId()      { return postId; }
    public int getUserId()      { return userId; }
    public String getText()     { return text; }
    public boolean isHelpful()  { return helpful; }
    public String getCreatedAt(){ return createdAt; }

    @Override
    public String toString() {
        return text + (helpful ? "  [Helpful]" : "");
    }
}
