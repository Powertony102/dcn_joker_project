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
        String sql = "INSERT INTO scores ('name', 'score', 'level', 'time') VALUES (?, ?, ?, datetime('now'))";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, score);
            pstmt.setInt(3, level);
            pstmt.executeUpdate();
            System.out.println("Data successfully inserted for: " + name);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void putScore(String name, int score, int level, String time) {
        String sql = "INSERT INTO scores ('name', 'score', 'level', 'time') VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, score);
            pstmt.setInt(3, level);
            pstmt.setString(4, time);
            pstmt.executeUpdate();
            System.out.println("Data successfully inserted for: " + name);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void main(String[] args) throws SQLException, ClassNotFoundException {
        connect();
        putScore("Bob", 1000, 13);
        getScores().forEach(map->{
            System.out.println(map.get("name"));
        });
    }
}
