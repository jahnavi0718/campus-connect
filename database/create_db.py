# create_db.py
# Builds the database file "campus.db" from schema.sql and seed.sql.
# Run it any time you want a fresh database:   python database\create_db.py
import sqlite3   # sqlite3 comes built-in with Python, nothing to install
import os

# folder where this file is kept
folder = os.path.dirname(os.path.abspath(__file__))
db_path = os.path.join(folder, "campus.db")

# delete the old database so we always start fresh
if os.path.exists(db_path):
    os.remove(db_path)

conn = sqlite3.connect(db_path)          # creates the new campus.db file

# run the two SQL files one after another:
# 1) schema.sql creates the empty tables, 2) seed.sql fills sample data
for file_name in ["schema.sql", "seed.sql"]:
    with open(os.path.join(folder, file_name), encoding="utf-8") as f:
        conn.executescript(f.read())

conn.commit()
conn.close()
print("Database created:", db_path)
