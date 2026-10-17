package campus;

import java.util.ArrayList;
import java.util.List;

/**
 * User.java  (OOPJ: a CLASS = a blueprint for one student)
 * One User object holds the details of one row of the "users" table,
 * plus that student's skills and clubs.
 */
public class User {

    // private = other classes cannot change these directly (encapsulation)
    private int id;
    private String name;
    private String department;
    private int year;
    private String bio;
    private List<String> skills = new ArrayList<>();   // e.g. [Java, Python]
    private List<String> clubs = new ArrayList<>();    // e.g. [Coding Club]

    // Constructor: runs when we create a new User(...)
    public User(int id, String name, String department, int year, String bio) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.year = year;
        this.bio = bio;
    }

    // Getters: the only way other classes can read the private fields
    public int getId()            { return id; }
    public String getName()       { return name; }
    public String getDepartment() { return department; }
    public int getYear()          { return year; }
    public String getBio()        { return bio; }
    public List<String> getSkills() { return skills; }
    public List<String> getClubs()  { return clubs; }

    // Used by Database.java to fill the lists after reading them from the database
    public void addSkill(String skill) { skills.add(skill); }
    public void addClub(String club)   { clubs.add(club); }

    // What we see when we print a User
    @Override
    public String toString() {
        return name + " (" + department + ", year " + year + ")";
    }
}
