import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class ScoreboardWindow {
    Stage stage;

    @FXML
    ListView<String> scoreList;

    protected Database database;

    public ScoreboardWindow(Database database) throws IOException, SQLException, ClassNotFoundException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("scoreUI.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);
        this.database = database;

        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("Battle Joker");
        stage.setMinWidth(scene.getWidth());
        stage.setMinHeight(scene.getHeight());

        this.stage.show();

        setFont(14);
        updateList();
    }

    private void setFont(int fontSize) {
        scoreList.setCellFactory(param -> {
            TextFieldListCell<String> cell = new TextFieldListCell<>();
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                cell.setFont(Font.font("Courier New", fontSize));
            } else if (osName.contains("mac")) {
                cell.setFont(Font.font("Menlo", fontSize));
            } else {
                cell.setFont(Font.font("Monospaced", fontSize));
            }
            return cell;
        });
    }

    private void updateList() throws SQLException, ClassNotFoundException {
        this.database.connect();
        try {
            ObservableList<String> items = FXCollections.observableArrayList();
            this.database.getScores().forEach(data->{
                String scoreStr = String.format("%s (%s)", data.get("score"), data.get("level"));
                items.add(String.format("%10s | %10s | %s", data.get("name"), scoreStr, data.get("time").substring(0, 16)));
            });
            scoreList.setItems(items);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        this.database.disconnect();
    }
}
