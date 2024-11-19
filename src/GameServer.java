import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


public class GameServer {
    private static final int PORT = 39995;
    private final List<ClientHandler> clients = new ArrayList<>();
    private final Queue<ClientHandler> waitingQueue = new LinkedList<>();
    private int currentPlayer = 0;
    private final int MAX_PLAYERS = 4;
    private boolean gameStatus = false;

    public static void main(String[] args) {
        new GameServer().startServer();
    }

    protected final GameEngine gameEngine = GameEngine.getInstance();

    private void recordResultInDataBase() throws IOException, SQLException, ClassNotFoundException {
        String winnerName = "";
        int winnerScore = 0, winnerLevel = 1;
        for (ClientHandler clientHandler: clients) {
            ArrayList<Integer> playerResult = clientHandler.getResult();
            if (playerResult.get(0) > winnerScore) {
                winnerScore = playerResult.get(0);
                winnerLevel = playerResult.get(1);
                winnerName = clientHandler.getPlayerName();
            }
        }
        recordGameScore(winnerName, winnerScore, winnerLevel);
        System.out.println(winnerName + " " + winnerScore + " " + winnerLevel + "\n");
        endGame();
    }

    public synchronized void handleClientMove(String move, ClientHandler clientHandler) throws IOException, SQLException, ClassNotFoundException {
        if (clients.get(currentPlayer) == clientHandler) {
            boolean handleClientMoveResult = gameEngine.moveMerge(move);
            if (!handleClientMoveResult) {
                recordResultInDataBase();
                return;
            }
            broadcastGameState();

            clientHandler.decreaseMoves();
            if (clientHandler.getRemainingMoves() <= 0) {
                currentPlayer = (currentPlayer + 1) % clients.size();
                resetPlayerMoves(clientHandler);
                notifyCurrentPlayer();
            }
        } else {
            clientHandler.sendMessage("It is not your turn yet. Please wait.");
        }
    }

    private void resetPlayerMoves(ClientHandler clientHandler) {
        clientHandler.setRemainingMoves(4);
    }

    public String getGameState() {
        StringBuilder stateBuilder = new StringBuilder();
        stateBuilder.append("board,");
        for (int r = 0; r < GameEngine.SIZE; r++) {
            for (int c = 0; c < GameEngine.SIZE; c++) {
                stateBuilder.append(gameEngine.getValue(r, c)).append(",");
            }
        }

        return stateBuilder.toString();
    }

    public void recordGameScore(String playerName, int score, int level) throws SQLException, ClassNotFoundException {
        Database.connect();
        try {
            Database.putScore(playerName, score, level);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Database.disconnect();
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                new Thread(clientHandler).start();
                handleNewPlayer(clientHandler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void firstPlayerDecision(ClientHandler clientHandler) throws IOException {
        clientHandler.sendMessage("You are the first player. Do you want to start now or wait for others?");
    }

    private void handleNewPlayer(ClientHandler clientHandler) throws IOException {
        if (clients.size() < MAX_PLAYERS || !this.gameStatus) {
            clients.add(clientHandler);

            // 如果是第一个玩家，赋予是否立即开始的选择权
            if (clients.size() == 1) {
                firstPlayerDecision(clientHandler);
            } else if (clients.size() >= 2 && clients.size() < MAX_PLAYERS) {
                notifyFirstPlayerOfCurrentPlayers();
            }

            // 如果玩家人数达到 4 人，自动开始游戏
            if (clients.size() == MAX_PLAYERS) {
                startGame();
            }
        } else {
            // 第 5 名及以后玩家的处理逻辑
            clientHandler.sendMessage("The game is full. Would you like to wait for the next game or leave?");
            int wantsToWait = -1;
            while (clientHandler.isWaitDecision() == -1) {
                wantsToWait = clientHandler.isWaitDecision();
            }

            if (wantsToWait == 1) {
                waitingQueue.add(clientHandler);
                clientHandler.sendMessage("You have been added to the waiting queue for the next game.");
            } else if (wantsToWait == 0) {
                clientHandler.closeConnection();
            }
        }
    }

    private void notifyFirstPlayerOfCurrentPlayers() throws IOException {
        if (!clients.isEmpty()) {
            ClientHandler firstPlayer = clients.get(0); // 第一个加入的玩家
            int playerCount = clients.size();
            firstPlayer.sendMessage("Currently, there are " + playerCount + " players in the game.");
        }
    }

    protected void startGame() throws IOException{
        this.gameStatus = true;
        System.out.println("Game is starting now!");
        for (ClientHandler clientHandler : clients) {
            clientHandler.sendMessage("Game starts!");
        }
        notifyCurrentPlayer();
    }

    public void notifyCurrentPlayer() throws IOException{
        ClientHandler currentPlayerClient = clients.get(currentPlayer);
        currentPlayerClient.sendMessage("It's your turn! You can make 4 moves.");
        for (int i = 0; i < clients.size(); i++) {
            if (i != currentPlayer) {
                clients.get(i).sendMessage("Wait for " + (currentPlayer + 1) + ".");
            }
        }
    }

    public String generateGameState() {
        StringBuilder stateBuilder = new StringBuilder();
        stateBuilder.append("board,");

        for (int r = 0; r < GameEngine.SIZE; r++) {
            for (int c = 0; c < GameEngine.SIZE; c++) {
                stateBuilder.append(gameEngine.getValue(r, c)).append(",");
            }
        }

        return stateBuilder.toString();
    }

    public void broadcastGameState() throws IOException {
        String gameState = generateGameState();
        System.out.println("Player number:" + clients.size());
        for (ClientHandler client : clients) {
            client.sendGameState(gameState);
        }
        // 因为只有当前操作的玩家需要更新分数板，所以只在 client 中找到 currentPlayer
        clients.get(currentPlayer).sendPersonalScore();
    }

    public void endGame() throws IOException {
        this.gameStatus = false;
        System.out.println("Engine Status: " + gameEngine.isGameOver());
        System.out.println("Current game has ended.");
        for (ClientHandler client : clients) {
            client.sendMessage("Game Over!");
        }

        clients.clear();
        for (int i = 0; i < MAX_PLAYERS && !waitingQueue.isEmpty(); ++ i) {
            clients.add(waitingQueue.poll());
        }

        if (!clients.isEmpty()) {
            this.gameStatus = true;
            startGame();
        }
    }
}