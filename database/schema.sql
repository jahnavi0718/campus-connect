-- ============================================================
-- schema.sql  |  CampusConnect database design (DBMS)
-- Each CREATE TABLE below = one table in our database.
-- PRIMARY KEY  = unique id of a row
-- FOREIGN KEY  = a link to a row in another table
-- ============================================================
PRAGMA foreign_keys = ON;

-- 1. USERS: one row per student
CREATE TABLE users (
    user_id     INTEGER PRIMARY KEY,
    name        TEXT NOT NULL,
    department  TEXT NOT NULL,          -- CSE, IT, ECE, EEE
    year        INTEGER NOT NULL,       -- 1 to 4
    bio         TEXT
);

-- 2. USER_SKILLS: a student can have many skills (one row per skill)
CREATE TABLE user_skills (
    user_id  INTEGER NOT NULL,
    skill    TEXT NOT NULL,
    PRIMARY KEY (user_id, skill),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 3. USER_CLUBS: a student can be in many clubs (one row per club)
CREATE TABLE user_clubs (
    user_id  INTEGER NOT NULL,
    club     TEXT NOT NULL,
    PRIMARY KEY (user_id, club),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 4. FOLLOWS: "follower follows following"  (DMGT: a relation = set of pairs)
-- Row (1, 2) means: user 1 follows user 2
CREATE TABLE follows (
    follower_id   INTEGER NOT NULL,
    following_id  INTEGER NOT NULL,
    PRIMARY KEY (follower_id, following_id),
    FOREIGN KEY (follower_id)  REFERENCES users(user_id),
    FOREIGN KEY (following_id) REFERENCES users(user_id),
    CHECK (follower_id <> following_id)   -- nobody can follow themselves
);

-- 5. POSTS: text post, optional image, can be marked as a doubt
CREATE TABLE posts (
    post_id     INTEGER PRIMARY KEY,
    user_id     INTEGER NOT NULL,        -- who wrote it
    content     TEXT,
    image_path  TEXT,                    -- NULL if no image
    is_doubt    INTEGER NOT NULL DEFAULT 0,   -- 1 = doubt post
    is_solved   INTEGER NOT NULL DEFAULT 0,   -- 1 = doubt solved
    created_at  TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 6. COMMENTS: interaction type 1 (comment on a post)
CREATE TABLE comments (
    comment_id  INTEGER PRIMARY KEY,
    post_id     INTEGER NOT NULL,
    user_id     INTEGER NOT NULL,        -- who commented
    text        TEXT NOT NULL,
    is_helpful  INTEGER NOT NULL DEFAULT 0,   -- 1 = marked helpful by doubt owner
    created_at  TEXT NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(post_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 7. LIKES: interaction type 2 (like on a post). One like per user per post.
CREATE TABLE likes (
    post_id  INTEGER NOT NULL,
    user_id  INTEGER NOT NULL,
    PRIMARY KEY (post_id, user_id),
    FOREIGN KEY (post_id) REFERENCES posts(post_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 8. EVENTS: hackathons, fests, workshops
CREATE TABLE events (
    event_id      INTEGER PRIMARY KEY,
    title         TEXT NOT NULL,
    club          TEXT NOT NULL,
    when_where    TEXT NOT NULL,         -- e.g. "Sat, 9 AM, Lab 3"
    organizer_id  INTEGER NOT NULL,
    image_path    TEXT,
    FOREIGN KEY (organizer_id) REFERENCES users(user_id)
);

-- 9. EVENT_RSVPS: who clicked "I'm going"
CREATE TABLE event_rsvps (
    event_id  INTEGER NOT NULL,
    user_id   INTEGER NOT NULL,
    PRIMARY KEY (event_id, user_id),
    FOREIGN KEY (event_id) REFERENCES events(event_id),
    FOREIGN KEY (user_id)  REFERENCES users(user_id)
);

-- 10. SUGGESTED_FRIENDS: filled by Python (similarity score), read by Java
CREATE TABLE suggested_friends (
    user_id           INTEGER NOT NULL,   -- who gets the suggestion
    suggested_id      INTEGER NOT NULL,   -- who is suggested
    mutual_count      INTEGER NOT NULL,   -- number of mutual friends
    similarity_score  REAL NOT NULL,      -- 0 to 100
    PRIMARY KEY (user_id, suggested_id),
    FOREIGN KEY (user_id)      REFERENCES users(user_id),
    FOREIGN KEY (suggested_id) REFERENCES users(user_id)
);
