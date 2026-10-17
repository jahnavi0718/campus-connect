package campus;

import java.util.List;

/**
 * Main.java
 * Step 3 test: try follow, post, like, comment, helpful and delete.
 * Student ids: 1 = Jahnavi, 2 = Ravi, 3 = Ananya
 * (To reset the data any time, run:  python database\create_db.py)
 */
public class Main {

    public static void main(String[] args) {
        try {
            Database db = new Database();

            // ---------- 1. FOLLOW / UNFOLLOW ----------
            System.out.println("=== 1. Follow / Unfollow ===");
            System.out.println("Jahnavi follows Ananya? " + db.isFollowing(1, 3));
            db.follow(1, 3);
            System.out.println("After follow   : " + db.isFollowing(1, 3));
            db.unfollow(1, 3);
            System.out.println("After unfollow : " + db.isFollowing(1, 3));
            System.out.println("Ravi's followers: " + db.countFollowers(2));

            // ---------- 2. NEW POST ----------
            System.out.println("\n=== 2. New post ===");
            int postId = db.addPost(1, "Testing my first post from Java!", null, false);
            System.out.println("Created post with id " + postId);

            // ---------- 3. LIKE ----------
            System.out.println("\n=== 3. Like / Unlike ===");
            System.out.println("Ravi likes it   -> liked? " + db.toggleLike(postId, 2));
            System.out.println("Ananya likes it -> liked? " + db.toggleLike(postId, 3));
            System.out.println("Total likes: " + db.getLikeCount(postId));
            System.out.println("Ravi clicks again -> liked? " + db.toggleLike(postId, 2));
            System.out.println("Total likes: " + db.getLikeCount(postId));

            // ---------- 4. COMMENT ----------
            System.out.println("\n=== 4. Comment ===");
            db.addComment(postId, 3, "Looks great, Jahnavi!");
            for (Comment c : db.getComments(postId)) {
                System.out.println("  " + db.getUser(c.getUserId()).getName() + ": " + c);
            }

            // ---------- 5. DOUBT + HELPFUL ----------
            System.out.println("\n=== 5. Doubt solved with Helpful ===");
            // Jahnavi's doubt is post 9. Find Ravi's comment on it.
            for (Comment c : db.getComments(9)) {
                if (c.getUserId() == 2) {
                    System.out.println("Ravi tries to mark his own comment: " + db.markHelpful(c.getId(), 2));
                    System.out.println("Jahnavi marks it helpful          : " + db.markHelpful(c.getId(), 1));
                }
            }
            System.out.println("Ravi's helpful answers (badge): " + db.countHelpful(2));

            // ---------- 6. DELETE ----------
            System.out.println("\n=== 6. Delete ===");
            System.out.println("Ravi deletes Jahnavi's post -> " + db.deletePost(postId, 2));
            System.out.println("Jahnavi deletes her post    -> " + db.deletePost(postId, 1));

            // ---------- 7. HOME FEED ----------
            System.out.println("\n=== 7. Jahnavi's home feed (top 5) ===");
            List<Post> feed = db.getFeed(1);
            for (int i = 0; i < Math.min(5, feed.size()); i++) {
                Post p = feed.get(i);
                System.out.println("  " + db.getUser(p.getUserId()).getName() + ": " + p
                    + "  | likes " + db.getLikeCount(p.getId())
                    + ", comments " + db.getCommentCount(p.getId()));
            }

            db.close();
        } catch (Exception e) {
            System.out.println("Something went wrong: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
