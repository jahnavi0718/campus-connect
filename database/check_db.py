# check_db.py
# Prints what is inside campus.db so you can see that everything worked.
# Run:   python database\check_db.py
import sqlite3
import os

folder = os.path.dirname(os.path.abspath(__file__))
conn = sqlite3.connect(os.path.join(folder, "campus.db"))

print("=== 1. Rows in each table ===")
tables = ["users", "user_skills", "user_clubs", "follows", "posts",
          "comments", "likes", "events", "event_rsvps", "suggested_friends"]
for t in tables:
    count = conn.execute("SELECT COUNT(*) FROM " + t).fetchone()[0]
    print(f"{t:18} {count}")

print("\n=== 2. Whom does Jahnavi (user 1) follow? ===")
rows = conn.execute("""
    SELECT u.name FROM follows f
    JOIN users u ON u.user_id = f.following_id
    WHERE f.follower_id = 1""").fetchall()
print([r[0] for r in rows])

print("\n=== 3. Preview: friends of friends of Jahnavi (plain SQL) ===")
# f1 = people Jahnavi follows, f2 = people THEY follow.
# We skip Jahnavi herself and people she already follows.
rows = conn.execute("""
    SELECT u.name, COUNT(*) AS mutual
    FROM follows f1
    JOIN follows f2 ON f1.following_id = f2.follower_id
    JOIN users u ON u.user_id = f2.following_id
    WHERE f1.follower_id = 1
      AND f2.following_id <> 1
      AND f2.following_id NOT IN (SELECT following_id FROM follows WHERE follower_id = 1)
    GROUP BY f2.following_id
    ORDER BY mutual DESC""").fetchall()
for name, mutual in rows:
    print(f"{name:10} mutual friends: {mutual}")

conn.close()
