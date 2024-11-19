import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Database {
    private String url = "";
    private Connection conn = null;

    public Database(String url) {
        this.url = url;
    }

    public void connect() throws SQLException, ClassNotFoundException {
        conn = DriverManager.getConnection(url);
        System.out.println("Connected to the database:" + url);
    }

    public void disconnect() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
            System.out.println("Database connection closed.");
        }
    }

    public ArrayList<HashMap<String, String>> getScores() throws SQLException {
        String sql = "SELECT * FROM scores ORDER BY score DESC LIMIT 10";
        ArrayList<HashMap<String, String>> data = new ArrayList<>();
        Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        while (resultSet.next()) {
            HashMap<String, String> m = new HashMap<>();
            m.put("name", resultSet.getString("name"));
            m.put("score", resultSet.getString("score"));
            m.put("level", resultSet.getString("level"));
            m.put("time", resultSet.getString("time"));
            data.add(m);
        }
        return data;
    }

    public void putScore(String name, int score, int level) {
        String checkSql = "SELECT COUNT(*) FROM scores WHERE name = ? AND score = ? AND level = ?";
        String insertSql = "INSERT INTO scores ('name', 'score', 'level', 'time') VALUES (?, ?, ?, datetime('now'))";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            // 检查是否已经存在相同的记录
            checkStmt.setString(1, name);
            checkStmt.setInt(2, score);
            checkStmt.setInt(3, level);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                // 如果没有相同记录，则插入
                insertStmt.setString(1, name);
                insertStmt.setInt(2, score);
                insertStmt.setInt(3, level);
                insertStmt.executeUpdate();
                System.out.println("Data successfully inserted for: " + name);
            } else {
                System.out.println("Duplicate record found. Insertion skipped for: " + name);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void putScore(String name, int score, int level, String time) {
        String checkSql = "SELECT COUNT(*) FROM scores WHERE name = ? AND score = ? AND level = ? AND time = ?";
        String insertSql = "INSERT INTO scores ('name', 'score', 'level', 'time') VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            // 检查是否已经存在相同的记录
            checkStmt.setString(1, name);
            checkStmt.setInt(2, score);
            checkStmt.setInt(3, level);
            checkStmt.setString(4, time);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                // 如果没有相同记录，则插入
                insertStmt.setString(1, name);
                insertStmt.setInt(2, score);
                insertStmt.setInt(3, level);
                insertStmt.setString(4, time);
                insertStmt.executeUpdate();
                System.out.println("Data successfully inserted for: " + name);
            } else {
                System.out.println("Duplicate record found. Insertion skipped for: " + name);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        Database db = new Database("jdbc:sqlite:data/battleJoker.db");
        db.connect();
        db.putScore("Bob", 1000, 13);
        db.putScore("Bob", 1000, 13); // 这次应该跳过
        db.getScores().forEach(map -> {
            System.out.println(map.get("name"));
        });
        db.disconnect();
    }
}
