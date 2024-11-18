public class MessageDealer {
    public String messageClean(String message) {
        int pos = 0;
        for (int i = 0; i < message.length(); ++ i) {
            if (message.charAt(i) == '>') {
                pos = i + 1;
                break;
            }
        }
        return message.substring(pos, message.length());
    }

    public String messageWithId(String message, int gameID) {
        return "<" + Integer.toString(gameID) + "> " + message;
    }
}
