"""
similarity.py  |  Python part: computes a similarity score (0 to 100)
for every "friend of a friend" and saves it in the suggested_friends table.

Score = 50 (mutual friends) + 25 (shared skills) + 15 (shared clubs)
        + 5 (same department) + 5 (same year)
Java reads this table later and shows the suggestions.
"""
import os
import sqlite3

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "campus.db")


def jaccard(a, b):
    """Overlap of two sets: common / total. 0 if both are empty."""
    if not a and not b:
        return 0.0
    return len(a & b) / len(a | b)


def main():
    con = sqlite3.connect(DB_PATH)

    # 1. load everything from the database into Python dictionaries
    users = {r[0]: (r[1], r[2]) for r in
             con.execute("SELECT user_id, department, year FROM users")}
    following = {u: set() for u in users}
    for a, b in con.execute("SELECT follower_id, following_id FROM follows"):
        following[a].add(b)
    skills = {u: set() for u in users}
    for u, s in con.execute("SELECT user_id, skill FROM user_skills"):
        skills[u].add(s.lower())
    clubs = {u: set() for u in users}
    for u, c in con.execute("SELECT user_id, club FROM user_clubs"):
        clubs[u].add(c.lower())

    # 2. clear old suggestions, then compute fresh ones
    con.execute("DELETE FROM suggested_friends")
    total = 0
    for u in users:
        # candidates = people my friends follow, minus me, minus people I already follow
        candidates = set()
        for f in following[u]:
            candidates |= following[f]
        candidates.discard(u)
        candidates -= following[u]

        for c in candidates:
            mutual = sum(1 for f in following[u] if c in following[f])
            score = 50 * (min(mutual, 3) / 3)            # 3+ mutual = full marks
            score += 25 * jaccard(skills[u], skills[c])
            score += 15 * jaccard(clubs[u], clubs[c])
            score += 5 if users[u][0] == users[c][0] else 0   # same department
            score += 5 if users[u][1] == users[c][1] else 0   # same year
            con.execute(
                "INSERT INTO suggested_friends VALUES (?, ?, ?, ?)",
                (u, c, mutual, round(score, 1)))
            total += 1

    con.commit()
    con.close()
    print("Done. " + str(total) + " suggestions saved in suggested_friends.")


if __name__ == "__main__":
    main()
