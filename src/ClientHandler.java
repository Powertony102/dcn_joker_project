import java.net.Socket;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;

class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private DataInputStream in;
    private DataOutputStream out;
    private int remainingMoves = 4;
    private int waitDecision = -1;

    private String playerName = "";

    private int score = 0;
    private int level = 1;
    private int combo = 0;
    private int totalMoves = 0;


    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;

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
                    this.waitDecision = 1;
                } else if (clientResponse.equals("leave")) {
                    this.waitDecision = 0;
//                    closeConnection();
                } else if (clientResponse.startsWith("name:")) {
                    setPlayerName(clientResponse.substring(5));
                } else if (clientResponse.startsWith("score")) {
                    updatePersonalScore(clientResponse);
                } else if (clientResponse.equals("closeWindow")) {
                    closeConnection();
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

    private void updatePersonalScore(String pscore) {
        String[] parts = pscore.split(",");
        this.score = Integer.parseInt(parts[1]);
        this.level = Integer.parseInt(parts[2]);
        this.combo = Integer.parseInt(parts[3]);
        this.totalMoves = Integer.parseInt(parts[4]);
    }

    public ArrayList<Integer> getResult() {
        ArrayList<Integer> result = new ArrayList<>();
        result.add(this.score);
        result.add(this.level);
        return result;
    }

    public int isWaitDecision() {
        return this.waitDecision;
    }

    public void sendPersonalScore() throws IOException {
        out.writeUTF("PersonalScore");
        out.flush();
    }

    public void closeConnection() {
        try {
            if (in != null) {
                in.close();  // 关闭输入流
            }
            if (out != null) {
                out.close();  // 关闭输出流
            }
            if (socket != null) {
                socket.close();  // 关闭 socket 连接
            }
            System.out.println("Connection closed for player: " + playerName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}