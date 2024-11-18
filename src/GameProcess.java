import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GameProcess {
    private int gameID = -1;
    private final List<ClientHandler> clients = new ArrayList<>();
    private GameEngine gameEngine;
    private int currentPlayer = 0;

    public GameProcess(ArrayList<ClientHandler> clients, int gameID) {
        clients.addAll(clients);
        this.gameID = gameID;
        this.gameEngine = new GameEngine();
    }

    public GameProcess(int gameID) {
        this.gameID = gameID;
    }

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

    public void recordGameScore(String playerName, int score, int level) throws SQLException, ClassNotFoundException {
        Database.connect();
        try {
            Database.putScore(playerName, score, level);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Database.disconnect();
    }

    public int endGame() throws IOException {
        System.out.println("Engine Status: " + gameEngine.isGameOver());
        System.out.println("Game:" + this.gameID + " has ended."); //
        for (ClientHandler client : clients) {
            client.sendMessage("Game Over!"); //
        }
        clients.clear();
        return 0;
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
            clientHandler.sendMessage("<" + this.gameID + ">It is not your turn yet. Please wait.");
        }
    }

    private void resetPlayerMoves(ClientHandler clientHandler) {
        clientHandler.setRemainingMoves(4);
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

    protected void startGame() throws IOException{
        System.out.println("Game is starting now!");
        for (ClientHandler clientHandler : clients) {
            clientHandler.sendMessage("Game starts!");
        }
        notifyCurrentPlayer();
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
        clients.get(currentPlayer).sendPersonalScore();
    }
}
