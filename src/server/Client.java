package src.server;

import src.db.Table;
import src.game.DisplayInterface;
import src.game.GameInterface;
import src.game.ResultsInterface;
import src.game.ValidateInterface;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class Client {
    private Socket socket;
    private String username;
    private MessageProtocol messageProtocol;
    private Boolean pendingReconnection = false;

    public volatile ClientState state = ClientState.QUEUE;
    private volatile String game_state = "waiting"; // playing, between_requests, validating, end_game

    public Game currGame;
    private String token = "";
    private char firstLetter;
    private DisplayInterface active_interface = null;
    private boolean isActive = true;

    public Client(Socket socket, MessageProtocol messageProtocol) {
        this.socket = socket;
        this.messageProtocol = new MessageProtocol(this);
    }

    public Socket getSocket() {
        return socket;
    }

    public String getUsername() {
        return username;
    }

    public Game getCurrGame() {
        return currGame;
    }

    public String getIdentification() {
        return username == null ? socket.getRemoteSocketAddress().toString() : username;
    }

    public Boolean isPendingReconnection() {
        return this.pendingReconnection;
    }

    public MessageProtocol getMessageProtocol() {
        return messageProtocol;
    }

    public int getElo() {
        Table users = Server.database.schema.get("user");

        for (var user : users.getAll()) {
            if ((user.get("username").equals(username))) {
                return Integer.parseInt(user.get("score"));
            }
        }
        return 0;
    }

    public String getToken() {
        return token;
    }

    public String getGameState() {
        return this.game_state;
    }

    public void setPendingReconnection(Boolean pendingReconnection) {
        this.pendingReconnection = pendingReconnection;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setFirstLetter(char firstLetter) {
        this.firstLetter = firstLetter;
    }

    public void setActive_interface(DisplayInterface active_interface) {
        this.active_interface = active_interface;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setGameState(String state) {
        this.game_state = state;
    }

    static String readInput() throws IOException {
        Scanner scanner = new Scanner(System.in);
        return scanner.nextLine();
    }

    public void typeMessage() throws IOException {
        Scanner scanner = new Scanner(System.in);
        String message = scanner.nextLine();

        this.messageProtocol.sendMessage(new Message(Command.strToCmd(message), this.token, Message.MessageType.TOKEN));
    }

    public ClientState getState() {
        return state;
    }

    public void setState(ClientState state) {
        this.state = state;
    }

    public void disconnect() throws IOException {
        socket.close();
        isActive = false;
        this.messageProtocol.sendMessage(new Message(Command.DISCONNECT));

    }

    public static void main(String[] args) {
        if (args.length < 2) return;
        String hostname = args[0];
        int port = Integer.parseInt(args[1]);

        String previousSessionToken = "";
        while (true) {
            try {
                Socket socket = new Socket(hostname, port);
                Client client = new Client(socket, null);

                if (!client.getSocket().isConnected()) {
                    System.out.println("Could not connect to the server");
                    return;
                }

                // Print Welcome Text from Server
                if (previousSessionToken.isEmpty()) {
                    System.out.println(client.messageProtocol.readMessage().data);
                }

                // Authentication
                authenticationLoop:
                do {
                    if (!previousSessionToken.isEmpty()) {
                        System.out.println("Reconnecting to the server...");
                        client.setToken(previousSessionToken);
                        client.messageProtocol.sendMessage(new Message(Command.RECONNECT, previousSessionToken, Message.MessageType.DATA));
                    } else {
                        String input = readInput();
                        if (input.startsWith("r")) {
                            String auth_data = input.substring(2);
                            client.getMessageProtocol().sendMessage(new Message(Command.REGISTER, auth_data, Message.MessageType.DATA));
                        } else if (input.startsWith("l")) {
                            String auth_data = input.substring(2);
                            client.getMessageProtocol().sendMessage(new Message(Command.LOGIN, auth_data, Message.MessageType.DATA));
                        } else if (input.equals("d")) {
                            client.getMessageProtocol().sendMessage(new Message(Command.DISCONNECT));
                            return;
                        } else {
                            client.getMessageProtocol().sendMessage(new Message(Command.TEXT, input, Message.MessageType.DATA)); // TODO (diogotvf7): is this really needed?
                        }
                    }

                    Message message = client.messageProtocol.readMessage();
                    switch (message.command) {
                        case Command.AUTH -> {
                            var token = message.token;
                            client.setUsername(message.data.toString());
                            client.setToken(token);
                            previousSessionToken = token;
                            client.setState(ClientState.QUEUE);
                            System.out.println("Authentication Successful!\nEntered in matchmaking queue...");
                            break authenticationLoop;
                        }
                        case Command.DISCONNECT -> {
                            client.socket.close();
                            client.disconnect();
                            System.out.println("You have been disconnected from the server");
                            return;
                        }
                        case Command.TEXT, Command.ERROR -> {
                            System.out.println(message.data);
                        }
                        default -> {
                            System.out.println("Invalid command");
                        }
                    }
                } while (!client.getSocket().isClosed());

                var listenServer = new ListenServer(client);
                Thread.ofVirtual().start(listenServer);

                while (!client.getSocket().isClosed()) {
                    switch (client.game_state) {
                        case "waiting":
                            System.out.println("""
                                    \n \n \n
                                    You're currently on queue!
                                    Type 'disconnect' if you want to leave.
                                    """);
                            Scanner scanner = new Scanner(System.in);
                            String input = scanner.nextLine();
                            if (input.equalsIgnoreCase("disconnect")) {
                                client.setGameState("between_requests");
                                client.getMessageProtocol().sendMessage(new Message(Command.DISCONNECT));
                                return;
                            }
                            break;
                        case "playing", "validating", "end_game":
                            client.active_interface.run();
                            break;
                        case "between_requests":
                            break; // do nothing; wait for the server to send a new request
                        default:
                            System.out.println("Invalid game state");
                    }
                }

                if (client.game_state.equals("end_game")) {
                    System.out.println("""
                            \n \n \n \n \n \n \n \n \n \n \n \n
                            Thanks for playing! Bye bye!
                            """);
                    break;
                }

                if (client.getState() == ClientState.DISCONNECTED) {
                    System.out.print("""
                            \n \n \n \n \n \n \n \n \n \n \n \n
                            You've been disconnected from the server
                            Sad to see you go :(
                            """);
                    break;
                }

                client.reconnect(hostname, port);

            } catch (ConnectException e) {
                System.out.println("Could not connect to the server");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } catch (UnknownHostException e) {
                System.out.println("Server unknown: " + hostname);
            } catch (SocketException e) {
                System.out.println("Connection with the server has been lost");
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean isAuthenticated() {
        return !this.token.isEmpty();
    }

    public void stopGame() throws IOException {
        this.game_state = "playing";
        this.messageProtocol.sendMessage(new Message(Command.STOP));
    }

    public void sendValidatedWords(HashMap<String, ArrayList<String>> words) throws IOException {
        this.messageProtocol.sendMessage(new Message(Command.WORDS_VALIDATE, words, Message.MessageType.DATA));
        System.out.println("Thanks for validating the words!");
        this.game_state = "between_requests";
    }

    public List<String> getMyWords() {
        if (!(this.active_interface instanceof GameInterface)) {
            System.out.println("Active interface is not a GameInterface");
        }
        return ((GameInterface) this.active_interface).getWords();
    }

    public void createValidateInterface(HashMap<String, ArrayList<String>> words) {
        this.active_interface = new ValidateInterface(firstLetter, words, this);
    }

    public void createResultsInterface(HashMap<String, Integer> results) {
        this.active_interface = new ResultsInterface(this, results);
    }

    public boolean gameIsActive() {
        return !this.game_state.equals("waiting") && !this.game_state.equals("end_game");
    }

    public void updateScore(int score) {
        int newScore = this.getElo() + score;
        System.out.println("New Score: " + newScore);
        Table users = Server.database.schema.get("user");
        for (var user : users.getAll()) {
            if ((user.get("username").equals(username))) {
                Server.database.write(
                        "user",
                        user.get("id"),
                        new HashMap<>() {{
                            put("score", String.valueOf(newScore));
                        }}
                );
                Server.database.save();
            }
        }
    }

    private void reconnect(String hostname, int port) {
        while (true) {
            try {
                this.socket = new Socket(hostname, port);
                this.messageProtocol = new MessageProtocol(this);

                if (!this.socket.isConnected()) {
                    System.out.println("Could not reconnect to the server");
                    continue;
                }

                System.out.println("Reconnected to the server");
                return;
            } catch (UnknownHostException e) {
                System.out.println("Unknown host: " + hostname);
            } catch (IOException e) {
                System.out.println("Reconnection failed: " + e.getMessage());
            }

            try {
                System.out.println("Attempting to reconnect...");
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
