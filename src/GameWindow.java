import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert.AlertType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

public class GameWindow {
    @FXML
    MenuBar menuBar;

    @FXML
    Label nameLabel;

    @FXML
    Label scoreLabel;

    @FXML
    Label levelLabel;

    @FXML
    Label comboLabel;

    @FXML
    Label moveCountLabel;

    @FXML
    Pane boardPane;

    @FXML
    Canvas canvas;

    @FXML
    Label messageLabel;

    Stage stage;
    AnimationTimer animationTimer;

    final String imagePath = "images/";
    final String[] symbols = {"bg", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "Joker"};
    final Image[] images = new Image[symbols.length];
    final GameEngine gameEngine = GameEngine.getInstance();

    private ScoreboardWindow scoreboardWindow;
    private final GameEngine clientEngine = new GameEngine();
    private Database database = null;

    // Support data communication with the network
    private Socket socket;
    private DataInputStream din;
    private DataOutputStream dout;

    private boolean isGameOver = false;

    public GameWindow(Stage stage, String serverIP, int serverPort) throws IOException, SQLException, ClassNotFoundException {
        connectToServer(serverIP, serverPort);
        loadImages();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("mainUI.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);

        this.stage = stage;

        stage.setScene(scene);
        stage.setTitle("Battle Joker");
        stage.setMinWidth(scene.getWidth());
        stage.setMinHeight(scene.getHeight());

        stage.widthProperty().addListener(w -> onWidthChangedWindow(((ReadOnlyDoubleProperty) w).getValue()));
        stage.heightProperty().addListener(h -> onHeightChangedWindow(((ReadOnlyDoubleProperty) h).getValue()));
        stage.setOnCloseRequest(event -> {
            event.consume(); // 阻止默认的关闭行为，执行自定义逻辑
            try {
                quit();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        stage.show();
        initCanvas();

        gameStart();
    }

    private void quit() throws IOException {
        // 弹出确认框，确保用户是要退出
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Exit Game");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to exit the game?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // 关闭网络连接
//            sendMessageToServer("closeWindow");
            // 关闭窗口
            System.out.println("Bye bye");
            Platform.exit();
            stage.close();
        }
        System.exit(0);
    }

    public void showDecisionDialog(String title, String message) throws IOException {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            ButtonType yesButton = new ButtonType("Wait");
            ButtonType noButton = new ButtonType("Start");
            alert.getButtonTypes().setAll(yesButton, noButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == yesButton) {
                try {
                    sendMessageToServer("wait_for_others");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    sendMessageToServer("start_game");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            alert.close();
        });
    }

    private void sendMessageToServer(String message) throws IOException {
        try {
            dout.writeUTF(message);
            dout.flush();
        } catch (SocketException e) {
            System.out.println("Socket has been closed, cannot send message: " + message);
            // 可以考虑在此重新连接或者向用户报告连接中断
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void gameStart() {
        animationTimer.start();
    }

    private void loadImages() throws IOException {
        for (int i = 0; i < symbols.length; i++)
            images[i] = new Image(Files.newInputStream(Paths.get(imagePath + symbols[i] + ".png")));
    }

    private void initCanvas() {
        canvas.setOnKeyPressed(event -> {
            if (isGameOver == true)
                return;
            try {
                String direction = event.getCode().toString();
                this.clientEngine.moveMerge(direction);
                dout.writeUTF(direction);
                dout.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
                if (isGameOver) {
                    System.out.println("Game Over!");
                    animationTimer.stop();

                    Platform.runLater(() -> {
                        System.out.println("Show Scoreboard");
                        try {
                            scoreboardWindow = new ScoreboardWindow(database);
                        } catch (SQLException | ClassNotFoundException | IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        };
        canvas.requestFocus();
    }

    private void render() {

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        double sceneSize = Math.min(w, h);
        double blockSize = sceneSize / GameEngine.SIZE;
        double padding = blockSize * .05;
        double startX = (w - sceneSize) / 2;
        double startY = (h - sceneSize) / 2;
        double cardSize = blockSize - (padding * 2);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double y = startY;
        int v;

        // Draw the background and cards from left to right, and top to bottom.
        for (int i = 0; i < GameEngine.SIZE; i++) {
            double x = startX;
            for (int j = 0; j < GameEngine.SIZE; j++) {
                gc.drawImage(images[0], x, y, blockSize, blockSize);  // Draw the background

                v = gameEngine.getValue(i, j);

                if (v > 0)  // if a card is in the place, draw it
                    gc.drawImage(images[v], x + padding, y + padding, cardSize, cardSize);

                x += blockSize;
            }
            y += blockSize;
        }
    }

    void onWidthChangedWindow(double w) {
        double width = w - boardPane.getBoundsInParent().getMinX();
        boardPane.setMinWidth(width);
        canvas.setWidth(width);
        render();
    }

    void onHeightChangedWindow(double h) {
        double height = h - boardPane.getBoundsInParent().getMinY() - menuBar.getHeight();
        boardPane.setMinHeight(height);
        canvas.setHeight(height);
        render();
    }

    public void setName(String name) throws IOException, SQLException, ClassNotFoundException {
        nameLabel.setText(name);
        sendMessageToServer("name:" + name);
        String url = "jdbc:sqlite:data/" + name + "_battleJoker.db";
        this.database = new Database(url);
        createDatase(url);
    }

    private void createDatase(String url) throws SQLException, IOException, ClassNotFoundException {
        // 连接数据库，如果数据库不存在则创建新文件
        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                System.out.println("A new database has been created.");

                // 创建一个示例表
                String sql = "CREATE TABLE IF NOT EXISTS scores (\n"
                        + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                        + "    name TEXT NOT NULL,\n"
                        + "    score INTEGER NOT NULL,\n"
                        + "    level INTEGER NOT NULL,\n"
                        + "    time TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n"
                        + ");";

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                    System.out.println("Table 'scores' has been created.");
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private void updateGameState(String gameState) {
        String[] parts = gameState.split(",");
        int index = 0;

        if (parts[0].equals("board")) {
            index++;
            for (int r = 0; r < GameEngine.SIZE; r++) {
                for (int c = 0; c < GameEngine.SIZE; c++) {
                    gameEngine.board[r * GameEngine.SIZE + c] = Integer.parseInt(parts[index++]);
                }
            }
        }
        render();
    }


    private void listenToServer() throws IOException {
        try {
            while (true) {
                String serverResponse = din.readUTF();
                System.out.println(serverResponse);
                if (serverResponse.startsWith("You are the first player")) {
                    showDecisionDialog("Start Game", "Do you want to start the game now, or wait for more players?");
                } else if (serverResponse.startsWith("The game is full")) {
                    showYesNoDialogForWaiting("Wait for Next Game", "The game is full. Would you like to wait for the next game or leave?");
                } else if (serverResponse.startsWith("It's your turn")) {
                    Platform.runLater(() -> {
                        messageLabel.setText("Your turn! Make 4 moves.");
                    });
                } else if (serverResponse.startsWith("Wait for")) {
                    Platform.runLater(() -> {
                        messageLabel.setText(serverResponse);
                    });
                } else if (serverResponse.equals("Game is starting now!")) {
                    showAlert("Game Start", "The game is starting with all players. Get ready!");
                } else if (serverResponse.startsWith("Currently, there are")) {
                    showDynamicYesNoDialog("Update on Players", serverResponse);
                } else if (serverResponse.equals("updateDatabase")) {
                    String order = din.readUTF();
                    Platform.runLater(() -> {
                        try {
                            updateDatabase(order);
                        } catch (IOException | SQLException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    });
                } else if (serverResponse.startsWith("PersonalScore")) {
                    Platform.runLater(() -> {
                        try {
                            updatePersonalScore();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } else if (serverResponse.startsWith("Game over")) {
                    Platform.runLater(() -> this.isGameOver = true);
                } else if (serverResponse.startsWith("board")) {
                    Platform.runLater(() -> updateGameState(serverResponse));
                } else {
                    continue;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateDatabase(String response) throws IOException, SQLException, ClassNotFoundException {
        this.database.connect();
        PlayerScore score = new PlayerScore("", 0, 1, "");
        System.out.println(response);
        score.initializationFromString(response);
        try {
            this.database.putScore(score.name, score.score, score.level, score.time);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updatePersonalScore() throws IOException {
        int score = this.clientEngine.getScore();
        int level = this.clientEngine.getLevel();
        int combo = this.clientEngine.getCombo();
        int totalMoves = this.clientEngine.getTotalMoveCount();
        scoreLabel.setText("Score: " + score);
        levelLabel.setText("Level: " + level);
        comboLabel.setText("Combo: " + combo);
        moveCountLabel.setText("# of Moves: " + totalMoves);
        sendMessageToServer("scoreboard," + score + "," + level + "," + combo + "," + totalMoves);
        render();
    }

    public void showDynamicYesNoDialog(String title, String message) throws IOException {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            ButtonType yesButton = new ButtonType("Yes");
            ButtonType noButton = new ButtonType("No");
            alert.getButtonTypes().setAll(yesButton, noButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == yesButton) {
                try {
                    sendMessageToServer("start_game");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void showYesNoDialogForWaiting(String title, String message) throws IOException {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            ButtonType yesButton = new ButtonType("Yes");
            ButtonType noButton = new ButtonType("No");
            alert.getButtonTypes().setAll(yesButton, noButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == yesButton) {
                try {
                    sendMessageToServer("wait_next_game");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    sendMessageToServer("leave");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void connectToServer(String serverIP, int serverPort) {
        int retries = 5; // The times that try to re-connect
        while (retries > 0) {
            try {
                socket = new Socket(serverIP, serverPort);
                dout = new DataOutputStream(socket.getOutputStream());
                din = new DataInputStream(socket.getInputStream());

                // A thread to listen to the server
                new Thread(() -> {
                    try {
                        listenToServer();
                    } catch (SocketException e) {
                        System.out.println("Connection has been closed by server.");
                        // 可以尝试重新连接或者给用户提示连接断开
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
                break; // If you succeed, break the loop
            } catch (IOException e) {
                System.out.println("Fail to connect to the sever, re-trying...");
                retries--;
                try {
                    Thread.sleep(2000); // Retry every 2 secs
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
        if (retries == 0) {
            throw new RuntimeException("Cannot connect to the server. Check your network settings");
        }
    }

    public void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}