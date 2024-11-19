import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.application.Platform;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

public class scoreServerController {
    @FXML
    private Button startServerBtn;

    @FXML
    private Button stopServerBtn;

    private ServerSocket serverSocket;
    private boolean running = false;
    private Database database;

    private static final String MULTICAST_ADDRESS = "224.0.0.1"; // 多播组地址
    private static final int MULTICAST_PORT = 10086; // 多播端口
    private MulticastSocket multicastSocket;

    public scoreServerController() {
        // 初始化数据库连接
        try {
            database = new Database("jdbc:sqlite:data/battleJoker.db");
            database.connect();
        } catch (SQLException | ClassNotFoundException e) {
            showAlert("Error", "Could not connect to database: " + e.getMessage());
        }
    }

    public void handleStartServer() {
        try {
            serverSocket = new ServerSocket(39995);
            multicastSocket = new MulticastSocket(); // 初始化多播套接字

            running = true;
            startServerBtn.setDisable(true);
            stopServerBtn.setDisable(false);

            // 开始监听客户端连接
            new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        sendTopScores(clientSocket);
                    } catch (IOException e) {
                        if (running) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();

            // 开始向多播组广播前10名分数
            startMulticastBroadcast();

        } catch (IOException e) {
            showAlert("Error", "Could not start server: " + e.getMessage());
        }
    }

    @FXML
    private void handleStopServer() {
        try {
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (multicastSocket != null && !multicastSocket.isClosed()) {
                multicastSocket.close();
            }
            Platform.runLater(() -> {
                startServerBtn.setDisable(false);
                stopServerBtn.setDisable(true);
            });
        } catch (IOException e) {
            showAlert("Error", "Could not stop server: " + e.getMessage());
        }
    }

    private void sendTopScores(Socket clientSocket) {
        new Thread(() -> {
            try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
                ArrayList<HashMap<String, String>> scores = database.getScores();
                out.println("Top 10 Scores:");
                for (HashMap<String, String> score : scores) {
                    String scoreStr = String.format("Name: %s, Score: %s, Level: %s, Time: %s",
                            score.get("name"),
                            score.get("score"),
                            score.get("level"),
                            score.get("time"));
                    out.println(scoreStr);
                }
            } catch (IOException | SQLException e) {
                showAlert("Error", "Could not send top scores: " + e.getMessage());
            }
        }).start();
    }

    private void startMulticastBroadcast() {
        new Thread(() -> {
            try {
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                while (running) {
                    ArrayList<HashMap<String, String>> scores = database.getScores();
                    StringBuilder message = new StringBuilder("Top 10 Scores:\n");
                    for (HashMap<String, String> score : scores) {
                        String scoreStr = String.format("Name: %s, Score: %s, Level: %s, Time: %s",
                                score.get("name"),
                                score.get("score"),
                                score.get("level"),
                                score.get("time"));
                        message.append(scoreStr).append("\n");
                    }

                    byte[] buffer = message.toString().getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, MULTICAST_PORT);

                    multicastSocket.send(packet); // 向多播组发送数据包
                    System.out.println("Multicast message sent to clients");

                    Thread.sleep(5000); // 每隔5秒发送一次多播消息
                }
            } catch (IOException | SQLException | InterruptedException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}