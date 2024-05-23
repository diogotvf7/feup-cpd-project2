package src.server;

public enum Command {
    ACK,
    LOGIN,
    REGISTER,
    DISCONNECT,
    RECONNECT,
    AUTH,
    TEXT,
    GAME_START,
    GAME_END,
    GAME_ABORT,
    PLAY,
    STOP,
    WORDS_SEND,
    WORDS_VALIDATE,
    WORDS_RECEIVE,
    INVALID_COMMAND,
    ERROR,
    PING;

    static Command strToCmd(String command) {
        return switch (command) {
            case "LOGIN" -> Command.LOGIN;
            case "REGISTER" -> Command.REGISTER;
            case "ACK" -> Command.ACK;
            case "ERROR" -> Command.ERROR;
            case "PING" -> Command.PING;
            case "START_GAME" -> Command.GAME_START;
            case "RCV_WORDS" -> Command.WORDS_RECEIVE;
            case "END_GAME" -> Command.GAME_END;
            case "AUTH" -> Command.AUTH;
            case "DISCONNECT" -> Command.DISCONNECT;
            case "TEXT" -> Command.TEXT;
            case "PLAY" -> Command.PLAY;
            case "SND_WORDS" -> Command.WORDS_SEND;
            case "STOP" -> Command.STOP;
            case "VALIDATE" -> Command.WORDS_VALIDATE;
            default -> Command.INVALID_COMMAND;
        };
    }
}