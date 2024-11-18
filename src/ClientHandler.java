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
    private final GameProcess process;
    private DataInputStream in;
    private DataOutputStream out;
    private int remainingMoves = 4;
    private int gameID = 0;

    private final GameEngine clientEngine;
    private MessageDealer dealer;

    private String playerName = "";


    public ClientHandler(Socket socket, GameProcess process, int gameID) {
        this.socket = socket;
        this.process = process;
        this.gameID = gameID;
        this.clientEngine = new GameEngine();
        this.dealer = new MessageDealer();

        if (process == null) {
            throw new IllegalArgumentException("GameProcess cannot be null.");
        }

        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getClientResponse() throws IOException {
        String clientResponse = in.readUTF();
        String prefix = "<" + Integer.toString(gameID) + ">";
        if (!clientResponse.startsWith(prefix)) {
            return "Pass";
        }
        return dealer.messageClean(clientResponse);
    }

    @Override
    public void run() {
        try {
            sendGameState(process.getGameState());

            while (true) {
                String clientResponse = getClientResponse();
                if (clientResponse.equals("Pass"))
                    continue;
                System.out.println("Received message from client: " + clientResponse);

                if (clientResponse.equals("start_game")) {
                    firstPlayerDecision(true);
                } else if (clientResponse.equals("wait_for_others")) {
                    System.out.println("The first player decided to wait for others to join.");
                    firstPlayerDecision(false);
                } else if (clientResponse.equals("leave")) {
                    socket.close();
                    return;
                } else if (clientResponse.startsWith("name:")) {
                    setPlayerName(clientResponse.substring(5));
                } else {
                    process.handleClientMove(clientResponse, this);
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
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                if (socket != null) {
                    socket.close();
                }
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
        message = dealer.messageWithId(message, this.gameID);
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

    public synchronized void decreaseMoves() {
        -- this.remainingMoves;
    }

    public synchronized int getRemainingMoves() {
        return this.remainingMoves;
    }

    public synchronized void setRemainingMoves(int remainingMoves) {
        this.remainingMoves = remainingMoves;
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

    public boolean firstPlayerDecision(String message) throws IOException {
        sendMessage(message);

        String clientResponse = getClientResponse();
        if (clientResponse.equals("wait_for_others")) {
            return false;
        } else {
            return true;
        }
    }

    public boolean firstPlayerDecision(boolean result) {
        return result;
    }
}