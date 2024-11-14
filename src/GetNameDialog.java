// import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;


import java.io.IOException;

public class GetNameDialog {
    @FXML
    TextField nameField;

    @FXML
    Button goButton;

    @FXML
    TextField serverIPField, serverPortField;

    Stage stage;
    String playername;
    protected String serverIP;
    protected int serverPort;

    public GetNameDialog() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("getNameUI.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);

        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("Battle Joker");
        stage.setMinWidth(scene.getWidth());
        stage.setMinHeight(scene.getHeight());

        goButton.setOnMouseClicked(this::OnButtonClick);

        stage.showAndWait();
    }

    private static int getIntegerFromString(String str) {
        int len = str.length(), x = 0;
        for (int i = 0; i < len; ++ i) {
            char c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                x = x * 10 + c - '0';
            }
        }
        return x;
    }

    @FXML
    void OnButtonClick(Event event) {
        playername = nameField.getText().trim();
        serverIP = serverIPField.getText().trim();
        serverPort = getIntegerFromString(serverPortField.getText());
        System.out.println(playername + " " + serverIP + " " + serverPort);
        if (!playername.isEmpty() && !serverIP.isEmpty() && serverPort > 0) {
            stage.close();
        }

    }

    public String getServerIP() {
        return this.serverIP;
    }

    public int getServerPort() {
        return this.serverPort;
    }

    public String getPlayername() {
        return playername;
    }
}
