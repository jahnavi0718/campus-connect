package campus;

/**
 * Post.java  (OOPJ class)
 * One Post object = one row of the "posts" table.
 */
public class Post {

    private int id;
    private int userId;          // who wrote the post
    private String content;
    private String imagePath;    // null when the post has no image
    private boolean doubt;       // true = this post is a doubt
    private boolean solved;      // true = the doubt is solved
    private String createdAt;

    public Post(int id, int userId, String content, String imagePath,
                boolean doubt, boolean solved, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.content = content;
        this.imagePath = imagePath;
        this.doubt = doubt;
        this.solved = solved;
        this.createdAt = createdAt;
    }

    public int getId()           { return id; }
    public int getUserId()       { return userId; }
    public String getContent()   { return content; }
    public String getImagePath() { return imagePath; }
    public boolean isDoubt()     { return doubt; }
    public boolean isSolved()    { return solved; }
    public String getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        String tag = doubt ? (solved ? "[Doubt - Solved] " : "[Doubt] ") : "";
        return tag + content + "  (" + createdAt + ")";
    }
}
