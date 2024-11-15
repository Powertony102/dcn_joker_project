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



    private final Queue<ClientHandler> waitingClients = new LinkedList<>();
    private int currentPlayer = 0;

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
                System.out.println("New client connected from " + clientSocket.getInetAddress().getHostAddress());

                if (clients.size() < 4) {
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();

                    if (clients.size() == 1) {
                        clientHandler.sendMessage("You are the first player. Do you want to start now or wait for others?");
                    } else if (clients.size() >= 2 && clients.size() < 4) {
                        notifyFirstPlayerOfCurrentPlayers();
                    }

                    if (clients.size() == 4) {
                        startGame();
                    }
                } else {
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    new Thread(clientHandler).start();
                    clientHandler.sendMessage("The game is full. Would you like to wait for the next game or leave?");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void notifyFirstPlayerOfCurrentPlayers() throws IOException {
        if (!clients.isEmpty()) {
            ClientHandler firstPlayer = clients.getFirst(); // 第一个加入的玩家
            int playerCount = clients.size();
            firstPlayer.sendMessage("Currently, there are " + playerCount + " players in the game.");
        }
    }

    protected void startGame() throws IOException{
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
        for (ClientHandler client : clients) {
            client.sendGameState(gameState);
        }

        // 因为只有当前操作的玩家需要更新分数板，所以只在 client 中找到 currentPlayer
        clients.get(currentPlayer).sendPersonalScore(gameState);
    }


    public void endGame() throws IOException {
        System.out.println("Engine Status: " + gameEngine.isGameOver());
        System.out.println("Current game has ended.");
        for (ClientHandler client : clients) {
            client.sendMessage("Game Over!");
        }

        clients.clear();

        int counter = 0;
        while (!waitingClients.isEmpty() && counter < 4) {
            ClientHandler newPlayer = waitingClients.poll();
            ++ counter;
            clients.add(newPlayer);
            newPlayer.sendMessage("You have been moved to the next game. Get ready!");
        }
    }

    public void addToWaitingQueue(ClientHandler clientHandler) throws IOException {
        waitingClients.add(clientHandler);
        clientHandler.sendMessage("You have been added to the waiting queue for the next game.");
    }
}