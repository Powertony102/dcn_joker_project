import org.ietf.jgss.GSSManager;

import java.lang.reflect.Array;
import java.net.Socket;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private DataInputStream in;
    private DataOutputStream out;
    private int remainingMoves = 4;

    private final GameEngine clientEngine;

    private String playerName = "";


    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.clientEngine = new GameEngine();

        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            sendGameState(server.getGameState());

            while (true) {
                String clientResponse = in.readUTF();
                System.out.println("Received message from client: " + clientResponse);

                if (clientResponse.equals("start_game")) {
                    server.startGame();
                } else if (clientResponse.equals("wait_for_others")) {
                    System.out.println("The first player decided to wait for others to join.");
                } else if (clientResponse.equals("wait_next_game")) {
                    server.addToWaitingQueue(this);
                } else if (clientResponse.equals("leave")) {
                    socket.close();
                    return;
                } else if (clientResponse.startsWith("name:")) {
                    setPlayerName(clientResponse.substring(5));
                } else {
                    server.handleClientMove(clientResponse, this);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setPlayerName(String name) {
        this.playerName = name;
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public void sendMessage(String message) throws IOException {
        try {
            out.flush();
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendGameState(String gameState) {
        try {
            out.writeUTF(gameState);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void decreaseMoves() {
        -- this.remainingMoves;
    }

    public int getRemainingMoves() {
        return this.remainingMoves;
    }

    public void setRemainingMoves(int remainingMoves) {
        this.remainingMoves = remainingMoves;
    }

    private void updateGameState(String gameState) {
        String[] parts = gameState.split(",");
        int index = 0;

        if (parts[0].equals("board")) {
            index++;
            for (int r = 0; r < GameEngine.SIZE; r++) {
                for (int c = 0; c < GameEngine.SIZE; c++) {
                    this.clientEngine.board[r * GameEngine.SIZE + c] = Integer.parseInt(parts[index++]);
                }
            }
        }
    }

    public ArrayList<Integer> getResult() {
        ArrayList<Integer> result = new ArrayList<>();
        result.add(this.clientEngine.getScore());
        result.add(this.clientEngine.getLevel());
        return result;
    }

    public void sendPersonalScore() throws IOException {
        out.writeUTF("PersonalScore");
        out.flush();
    }
}