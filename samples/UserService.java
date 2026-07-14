import java.util.*;
import java.sql.*;

/**
 * UserService — a deliberately flawed "God class" that handles
 * users, database access, email, and validation all in one place.
 */
public class UserService {

    // mutable static state — not thread-safe
    private static List users = new ArrayList(); // raw type, no generics
    private static Map cache = new HashMap();     // raw type, no generics
    public static String dbUrl = "jdbc:mysql://localhost:3306/mydb";
    public static String dbUser = "root";
    public static String dbPass = "password123"; // hardcoded credentials!

    // God method — does way too much
    public Object processUser(String name, String email, String action) {
        // no input validation
        if (action == "CREATE") { // == instead of .equals() for String comparison
            Map user = new HashMap();
            user.put("name", name);
            user.put("email", email);
            user.put("created", new Date()); // java.util.Date deprecated patterns
            user.put("id", users.size() + 1);
            users.add(user);
            cache.put(email, user);

            // SQL injection vulnerability
            try {
                Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                Statement stmt = conn.createStatement();
                stmt.executeUpdate("INSERT INTO users (name, email) VALUES ('" + name + "', '" + email + "')");
                // resource leak — never closes connection or statement
            } catch (SQLException e) {
                System.out.println("DB error: " + e.getMessage()); // logging via println
                return null; // returns null on error — caller must null-check
            }

            // inline email logic — no separation of concerns
            System.out.println("Sending welcome email to " + email);
            sendEmail(email, "Welcome!", "Hello " + name + ", welcome!");

            return user;

        } else if (action == "DELETE") { // == instead of .equals()
            Iterator it = users.iterator(); // raw iterator
            while (it.hasNext()) {
                Map u = (Map) it.next(); // unchecked cast
                if (u.get("email").equals(email)) {
                    it.remove();
                    cache.remove(email);

                    try {
                        Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
                        Statement stmt = conn.createStatement();
                        stmt.executeUpdate("DELETE FROM users WHERE email = '" + email + "'");
                        // another resource leak + SQL injection
                    } catch (SQLException e) {
                        e.printStackTrace(); // stack trace to stdout
                    }

                    return "deleted";
                }
            }
            return null; // returns null if not found — no Optional

        } else if (action == "FIND") {
            // linear search — could use map lookup
            for (int i = 0; i < users.size(); i++) {
                Map u = (Map) users.get(i);
                if (u.get("email").equals(email)) {
                    return u;
                }
            }
            return null;

        } else if (action == "COUNT") {
            return users.size(); // autoboxing
        } else {
            return -1; // magic return value for "unknown action"
        }
    }

    // duplicated database logic
    public List getAllUsers() {
        List result = new ArrayList();
        try {
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM users");
            while (rs.next()) {
                Map user = new HashMap();
                user.put("name", rs.getString("name"));
                user.put("email", rs.getString("email"));
                result.add(user);
            }
            // never closes rs, stmt, conn
        } catch (Exception e) {
            // swallowed exception — empty catch
        }
        return result;
    }

    // trivial method that should be in a utility class
    public boolean validateEmail(String email) {
        return email.contains("@"); // oversimplified validation
    }

    // side-effect-laden method with no abstraction
    private void sendEmail(String to, String subject, String body) {
        System.out.println("=== EMAIL ===");
        System.out.println("To: " + to);
        System.out.println("Subject: " + subject);
        System.out.println("Body: " + body);
        System.out.println("=============");
        // no actual email sending — just println
    }

    // exposing mutable internal state
    public static List getUsers() {
        return users; // returns mutable reference — violates encapsulation
    }

    // method with inconsistent return type (Object)
    public Object findByNameOrEmail(String query) {
        for (Object u : users) {
            Map m = (Map) u;
            if (m.get("name").equals(query) || m.get("email").equals(query)) {
                return m;
            }
        }
        return "not found"; // returns String on failure, Map on success — inconsistent
    }
}
