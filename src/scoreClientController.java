import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleStringProperty;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class scoreClientController {
    @FXML
    private TableView<ScoreData> scoreTable;

    @FXML
    private TableColumn<ScoreData, String> nameColumn;

    @FXML
    private TableColumn<ScoreData, String> scoreColumn;

    @FXML
    private TableColumn<ScoreData, String> levelColumn;

    @FXML
    private TableColumn<ScoreData, String> timeColumn;

    @FXML
    private Button connectBtn;

    @FXML
    private Button disconnectBtn;

    private MulticastSocket multicastSocket;
    private InetAddress group;
    private boolean running = false;

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        scoreColumn.setCellValueFactory(new PropertyValueFactory<>("score"));
        levelColumn.setCellValueFactory(new PropertyValueFactory<>("level"));
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("time"));
    }

    public void handleConnect() {
        try {
            group = InetAddress.getByName("224.0.0.1");
            multicastSocket = new MulticastSocket(10086);
            multicastSocket.joinGroup(group);
            running = true;

            new Thread(() -> {
                while (running) {
                    try {
                        byte[] buffer = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        multicastSocket.receive(packet);
                        String received = new String(packet.getData(), 0, packet.getLength());

                        // 清空表格中的现有数据
                        Platform.runLater(() -> scoreTable.getItems().clear());

                        // 解析接收到的分数信息
                        String[] lines = received.split("\n");
                        for (String line : lines) {
                            if (line.startsWith("Name:")) {
                                String[] parts = line.split(", ");
                                String name = parts[0].split(": ")[1];
                                String score = parts[1].split(": ")[1];
                                String level = parts[2].split(": ")[1];
                                String time = parts[3].split(": ")[1];

                                ScoreData scoreData = new ScoreData(name, score, level, time);
                                Platform.runLater(() -> scoreTable.getItems().add(scoreData));
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            showAlert("Error", "Could not connect to the multicast group: " + e.getMessage());
        }
    }

    public void handleDisconnect() {
        try {
            running = false;
            if (multicastSocket != null) {
                multicastSocket.leaveGroup(group);
                multicastSocket.close();
            }
            Platform.runLater(() -> scoreTable.getItems().clear());
        } catch (IOException e) {
            showAlert("Error", "Could not disconnect: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // 定义 ScoreData 类来保存每一行数据
    public static class ScoreData {
        private final SimpleStringProperty name;
        private final SimpleStringProperty score;
        private final SimpleStringProperty level;
        private final SimpleStringProperty time;

        public ScoreData(String name, String score, String level, String time) {
            this.name = new SimpleStringProperty(name);
            this.score = new SimpleStringProperty(score);
            this.level = new SimpleStringProperty(level);
            this.time = new SimpleStringProperty(time);
        }

        public String getName() {
            return name.get();
        }

        public String getScore() {
            return score.get();
        }

        public String getLevel() {
            return level.get();
        }

        public String getTime() {
            return time.get();
        }
    }
}