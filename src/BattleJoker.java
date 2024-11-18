import javafx.application.Application;
import javafx.stage.Stage;

import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.SQLException;

public class BattleJoker extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            GetNameDialog dialog = new GetNameDialog();

            String serverIP = dialog.getServerIP();
            int serverPort = dialog.getServerPort();

            GameWindow win = new GameWindow(primaryStage, serverIP, serverPort);
            win.setName(dialog.getPlayername());

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void stop() {
        try {
            Database.disconnect();
        } catch (SQLException ignored) {
        }
    }

    public static void main(String[] args) {
        System.setErr(new FilteredStream(System.err));  // All JavaFX'es version warnings will not be displayed

        launch();
    }

}

class FilteredStream extends PrintStream {

    public FilteredStream(OutputStream out) {
        super(out);
    }

    @Override
    public void println(String x) {
        if (x != null && !x.contains("SLF4J: "))
            super.println(x);
    }

    @Override
    public void print(String x) {
        if (x!= null && !x.contains("WARNING: Loading FXML document with JavaFX API of version 18"))
            super.print(x);
    }
}

