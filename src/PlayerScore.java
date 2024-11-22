import java.util.HashMap;

public class PlayerScore {
    public String name, time;
    public int score, level;

    public PlayerScore(String name, int score, int level, String time) {
        this.name = name;
        this.score = score;
        this.level = level;
        this.time = time;
    }

    public PlayerScore(String name, int score, int level) {
        this.name = name;
        this.score = score;
        this.level = level;
        this.time = "null";
    }

    public String toString() {
        return name + "," + score + "," + level + "," + time;
    }

    public void initializationFromString(String str) {
        String[] parts = str.split(",");
        this.name = parts[0];
        this.score = Integer.parseInt(parts[1]);
        this.level = Integer.parseInt(parts[2]);
        this.time = parts[3];
    }

    public String initializationFromDatabase(HashMap<String, String> data) {
        this.name = data.get("name");
        this.score = Integer.parseInt(data.get("score"));
        this.level = Integer.parseInt(data.get("level"));
        this.time = data.get("time");
        return this.toString();
    }
}
