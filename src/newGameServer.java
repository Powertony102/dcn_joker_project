import javax.naming.ldap.SortKey;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;


public class newGameServer {
    private static final int PORT = 39995;
    private Map<Integer, Socket> clientMap = new HashMap<>();
    private ArrayList<Integer> gameIDList = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        new newGameServer().startServer();
    }

    public int generateNewGameID() {
        int newGameID = gameIDList.size() + 1;
        gameIDList.add(newGameID);
        return newGameID;
    }

    public String generateNewGameName(int gameID) {
        return "<" + gameID + ">";
    }

    public void startServer() throws IOException {
        int currentGameID = -1;
        GameProcess gameProcess = null;
        Queue <Socket> queue = new LinkedList<>();
        Queue <ClientHandler> clientQueue = new LinkedList<>();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port " + PORT);
            while (true) {
                // receive socket
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from " + clientSocket.getInetAddress().getHostAddress());

                if (!queue.isEmpty() && queue.size() < 4) {
                    queue.add(clientSocket);
                    clientMap.put(currentGameID, clientSocket);

                    ClientHandler clientHandler = new ClientHandler(clientSocket, gameProcess, currentGameID);
                    clientQueue.add(clientHandler);
                    new Thread(clientHandler).start();

                    boolean firstPlayerDecision = notifyFirstPlayerOfCurrentPlayers(
                            Objects.requireNonNull(clientQueue.peek()), clientQueue.size(), currentGameID);
                    if (firstPlayerDecision) {
                        queue.clear();
                        clientQueue.clear();
                        gameProcess.startGame();
                    }
                } else if (queue.size() == 4) {
                    clientMap.put(currentGameID, clientSocket);

                    ClientHandler clientHandler = new ClientHandler(clientSocket, gameProcess, currentGameID);
                    new Thread(clientHandler).start();

                    queue.clear();
                    clientQueue.clear();
                    gameProcess.startGame();
                }
                else {
                    queue.add(clientSocket);
                    currentGameID = generateNewGameID();
                    clientMap.put(currentGameID, clientSocket);

                    gameProcess = new GameProcess(currentGameID);
                    ClientHandler clientHandler = new ClientHandler(clientSocket, gameProcess, currentGameID);
                    clientQueue.add(clientHandler);

                    new Thread(clientHandler).start();

                    boolean firstPlayerDecision = clientHandler.firstPlayerDecision(generateNewGameName(currentGameID) +
                            "You are the first player.");

                    if (firstPlayerDecision) {
                        queue.clear();
                        clientQueue.clear();
                        gameProcess.startGame();
                    }
                }

            }
        }
    }

    private boolean notifyFirstPlayerOfCurrentPlayers(ClientHandler client, int playerCount, int gameID) throws IOException {
        return client.firstPlayerDecision(
                generateNewGameName(gameID) + "Currently, there are " + playerCount + " players in the game.");
    }
}