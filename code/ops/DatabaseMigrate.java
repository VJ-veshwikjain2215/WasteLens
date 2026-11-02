import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

/**
 * Executes migration SQL files safely via JDBC without logging passwords or secrets.
 */
public class DatabaseMigrate {

  public static void main(String[] args) {
    String url = System.getenv("DB_JDBC_URL");
    String user = System.getenv("DB_USER");
    String password = System.getenv("DB_PASSWORD");

    if (url == null || user == null || password == null) {
      System.err.println("Error: Missing database credentials in environment.");
      System.exit(1);
    }

    Path sqlPath = Path.of("../backend/src/main/resources/db/migration/V1__init_schema.sql");
    if (!Files.exists(sqlPath)) {
      sqlPath = Path.of("backend/src/main/resources/db/migration/V1__init_schema.sql");
    }

    if (!Files.exists(sqlPath)) {
      System.err.println("Error: Migration SQL file not found at " + sqlPath);
      System.exit(1);
    }

    try {
      Class.forName("org.postgresql.Driver");
      String sql = Files.readString(sqlPath);

      Properties properties = new Properties();
      properties.setProperty("user", user);
      properties.setProperty("password", password);
      DriverManager.setLoginTimeout(10);

      try (Connection connection = DriverManager.getConnection(url, properties);
           Statement statement = connection.createStatement()) {
        System.out.println("Applying schema migration V1__init_schema.sql...");
        statement.execute(sql);
        System.out.println("PASS: Migration applied successfully.");
      }
    } catch (Exception e) {
      System.err.println("FAIL: Migration failed: " + e.getMessage());
      System.exit(1);
    }
  }
}
