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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Optional;

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

    private final GameEngine clientEngine = new GameEngine();

    // Support data communication with the network
    private Socket socket;
    private DataInputStream din;
    private DataOutputStream dout;

    private boolean isGameOver = false;

    public GameWindow(Stage stage, String serverIP, int serverPort) throws IOException {
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
            sendMessageToServer("closeWindow");
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
//                System.out.println(gameEngine.isGameOver());
                if (isGameOver) {
//                if (gameEngine.isGameOver()) {
                    System.out.println("Game Over!");
                    animationTimer.stop();

                    Platform.runLater(() -> {
                        try {
                            System.out.println("Show Scoreboard");
                            new ScoreboardWindow();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        } catch (ClassNotFoundException e) {
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

    public void setName(String name) throws IOException {
        nameLabel.setText(name);
        sendMessageToServer("name:" + name);
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
//                System.out.println(serverResponse);
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
                } else if (serverResponse.startsWith("PersonalScore")) {
                    Platform.runLater(() -> {
                        try {
                            updatePersonalScore();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } else if (serverResponse.startsWith("Game Over")) {
                    this.isGameOver = true;
                } else {
                    Platform.runLater(() -> updateGameState(serverResponse));
                }
            }
        } catch (IOException e) {
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