import java.lang.reflect.Array;
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
    private int totalMoves = 0;

    private String playerName = "";

    protected ArrayList <Integer> result = new ArrayList<>();

    private int score = 0, preScore = 0;
    private int level = 1, preLevel = 1;
    private int combo = 0, preCombo = 0;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        result.add(this.score);
        result.add(this.level);
        result.add(this.combo);

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
        ++ this.totalMoves;
    }

    public int getRemainingMoves() {
        return this.remainingMoves;
    }

    public void setRemainingMoves(int remainingMoves) {
        this.remainingMoves = remainingMoves;
    }

    private void updateScoreBoard() {
        this.preScore = this.score;
        this.preLevel = this.level;
        this.preCombo = this.combo;
        this.score = this.server.gameEngine.getScore();
        this.level = this.server.gameEngine.getLevel();
        this.combo = this.server.gameEngine.getCombo();

        this.result.set(0, this.result.get(0) + (this.score - this.preScore));
        this.result.set(1, this.result.get(1) + (this.level - this.preLevel));
        if (this.combo == 0)
            this.result.set(2, this.preCombo);
        else
            this.result.set(2, this.result.get(2) + (this.combo - this.preCombo));
//        System.out.println();
    }

    public ArrayList<Integer> getResult() {
        return this.result;
    }

    public String sendPersonalScore() throws IOException {
        String pscore = "PersonalScore,";
        try {
            updateScoreBoard();
            pscore += "score," + result.get(0) + ",";
            pscore += "level," + result.get(1) + ",";
            pscore += "combo," + result.get(2) + ",";
            pscore += "total," + this.totalMoves;
            System.out.println(pscore);
            out.writeUTF(pscore);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pscore;
    }
}