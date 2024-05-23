package src.server;

import src.db.Table;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.IntStream;

public class Authentication implements Runnable {
    private final Client client;
    private Boolean authenticated = false;
    private Boolean clientConnected = true;

    private static final String REGISTER_REGEX = "/register\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)";

    private static final String LOGIN_REGEX = "/login\\s+(\\S+)\\s+(\\S+)";


    public Authentication(Socket socket) {
        this.client = new Client(socket, null);
    }

    @Override
    public void run() {
        try {
            client.getMessageProtocol().sendMessage(
                    new Message(Command.TEXT,
                            """
                                    Welcome to the Stop Game!
                                    Write one of the following commands:
                                    r <username> <password> <confirm_password>      <--->   Register
                                    l <username> <password>                         <--->   Login
                                    d                                               <--->   Disconnect
                                    >>>\s""",
                            Message.MessageType.DATA)
            );
        } catch (IOException e) {
            return;
        }

        while (this.clientConnected && !this.authenticated) {
            authenticationProcess();
        }
        Server.builder.start(new ListenClient(client));
    }

    private void authenticationProcess() {
        try {
            Message message = client.getMessageProtocol().readMessage();

            //TODO: If the user is already connected, send error message
            switch (message.command) {
                case Command.REGISTER -> {
                    Table users = Server.database.schema.get("user");

                    if (message.data.toString().split(" ").length != 3) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Wrong amount of arguments! Try again.", Message.MessageType.DATA));
                        return;
                    }
                    String username = message.data.toString().split(" ")[0];
                    String password = message.data.toString().split(" ")[1];
                    String password_check = message.data.toString().split(" ")[2];

                    if (!password.equals(password_check)) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Passwords do not match", Message.MessageType.DATA));
                        return;
                    }

                    for (var user : users.getAll()) {
                        if (user.get("username").equals(username)) {
                            client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Username already exists", Message.MessageType.DATA));
                            return;
                        }
                    }

                    var registered_users = users.getAll();
                    String lastId = "0";
                    if (!users.getAll().isEmpty()) {
                        lastId = registered_users.getLast().get("id");
                    }
                    String newId = Integer.parseInt(lastId) + 1 + "";
                    users.insert(new HashMap<>() {{
                        put("id", newId); //TODO: This should be Autoincrement :(
                        put("username", username);
                        put("password", Password.hash(password));
                        put("score", "0");
                    }});
                    Server.database.save();

                    String token = this.createUserSession(newId);
                    this.successfulAuthentication(client, username, token);
                    return;
                }
                case Command.LOGIN -> {
                    System.out.println(client.getSocket());

                    if (message.data.toString().split(" ").length != 2) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Wrong amount of arguments! Try again.", Message.MessageType.DATA));
                        return;
                    }
                    String username = message.data.toString().split(" ")[0];
                    String password = message.data.toString().split(" ")[1];

                    // Check if user exists and password is correct
                    Table users = Server.database.schema.get("user");
                    HashMap<String, String> foundUser = null;
                    for (var user : users.getAll()) {
                        if (user.get("username").equals(username)) {
                            foundUser = user;
                            break;
                        }
                    }
                    if (foundUser == null) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "User not found", Message.MessageType.DATA));
                        return;
                    }
                    if (!Password.check(password, foundUser.get("password"))) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Invalid password", Message.MessageType.DATA));
                        return;
                    }
                    if (Server.isUserLoggedIn(username)) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "User already logged in. Try again later", Message.MessageType.DATA));
                        return;
                    }

                    // If user was pending reconnection
                    if (Server.idleClients.stream().anyMatch(client -> client.getUsername().equals(username))) {
                        removePreviousSession(foundUser.get("id"));
                        String token = this.createUserSession(foundUser.get("id"));

                        int index = IntStream.range(0, Server.idleClients.size())
                                .filter(i -> Server.idleClients.get(i).getUsername().equals(username))
                                .findFirst()
                                .orElse(-1); // returns -1 if no match found

                        if (index != -1) {
                            Server.idleClients.remove(index);
                            this.successfulAuthentication(client, username, token);
                            System.out.println("Client reconnected");
                        }

                        return;
                    }

                    String token = this.createUserSession(foundUser.get("id"));
                    this.successfulAuthentication(client, username, token);
                    return;
                }
                case Command.DISCONNECT -> {
                    client.getMessageProtocol().sendMessage(new Message(Command.DISCONNECT));
                    System.out.println("client: " + client.getIdentification() + " has disconnected");
                    Server.updateIdleUsers(Server.Action.REMOVE_CLIENT, client);
                    return;
                }
                case Command.RECONNECT -> {
                    if (message.token == null) return;

                    Table sessions = Server.database.schema.get("session");
                    HashMap<String, String> foundSession = null;
                    for (var session : sessions.getAll()) {
                        if (session.get("id").equals(message.token)) {
                            foundSession = session;
                            break;
                        }
                    }
                    if (foundSession == null) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Session not found", Message.MessageType.DATA));
                        return;
                    }

                    // Check if the session is still valid
                    if (Long.parseLong(foundSession.get("expiration")) < System.currentTimeMillis()) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Session expired", Message.MessageType.DATA));
                        return;
                    }

                    String userId = foundSession.get("userid");
                    Table users = Server.database.schema.get("user");
                    HashMap<String, String> foundUser = null;
                    for (var user : users.getAll()) {
                        if (user.get("id").equals(userId)) {
                            foundUser = user;
                            break;
                        }
                    }
                    if (foundUser == null) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "User not found", Message.MessageType.DATA));
                        return;
                    }
                    if (Server.isUserLoggedIn(foundUser.get("username"))) {
                        client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "User already logged in. Try again later.", Message.MessageType.DATA));
                        return;
                    }

                    this.successfulAuthentication(client, foundUser.get("username"), message.token);
                    return;
                }
                default -> {
                }
            }

            client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Please choose one of the presented options.", Message.MessageType.DATA));

        } catch (IOException | ClassNotFoundException e) {
            this.clientConnected = false;
            client.getMessageProtocol().close();
        }
    }

    private String createUserSession(String userId) {
        String token = UUID.randomUUID().toString();

        Table sessions = Server.database.schema.get("session");
        String expiration = System.currentTimeMillis() + 1000 * 60 * 60 + ""; // 60minutes

        sessions.insert(new HashMap<>() {{
            put("id", token);
            put("userid", userId);
            put("expiration", expiration);
        }});

        for (var session : sessions.getAll()) {
            if (Long.parseLong(session.get("expiration")) < System.currentTimeMillis()) {
                sessions.delete(session.get("id"));
            }
        }

        Server.database.save();

        return token;
    }

    private void removePreviousSession(String userId) {
        Table sessions = Server.database.schema.get("session");
        for (var session : sessions.getAll()) {
            if (session.get("id").equals(userId))
                sessions.delete(session.get("id"));
        }
    }

    private void successfulAuthentication(Client client, String username, String token) throws IOException {
        client.setUsername(username);
        client.setToken(token);
        client.getMessageProtocol().sendMessage(new Message(Command.AUTH, username, token));
        Server.updateIdleUsers(Server.Action.ADD_CLIENT, client);
        this.authenticated = true;
    }
}