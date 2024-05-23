package src.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

class ListenClient implements Runnable {
    private final Client client;

    public ListenClient(Client client) {
        this.client = client;
    }

    @Override
    public void run() {

        //TODO: This will never terminate the thread...
        while (true) {
            Message message;
            switch (client.getState()) {
                case QUEUE -> {
                    //TODO: concurrent modification exception
//                    if (Server.idleClients.stream().noneMatch(client1 -> client1.getSocket().equals(client.getSocket())))
//                        continue;
                    try {
                        client.getMessageProtocol().sendMessage(new Message(Command.PING));
                        message = client.getMessageProtocol().readMessage();
                    } catch (IOException e) {
                        System.out.println("Client: " + client.getIdentification() + " has suddenly disconnected");
                        Server.updateIdleUsers(Server.Action.SET_CLIENT_PENDING_RECONNECTION, client);
                        return;
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    if (message.command.equals(Command.DISCONNECT)) {
                        System.out.println("Client: " + client.getIdentification() + " has disconnected");
                        try {
                            client.getMessageProtocol().sendMessage(new Message(Command.DISCONNECT));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        Server.updateIdleUsers(Server.Action.REMOVE_CLIENT, client);
                        return;
                    }
                    if (message.command.equals(Command.PLAY)) {
                        Server.updateIdleUsers(Server.Action.ADD_CLIENT, client);
                        System.out.println("Client: " + client.getIdentification() + " agrees to play again");
                    }
                }
                case GAME -> {
                    if (!((client.currGame != null) && client.currGame.isActive())) continue;
                    try {
                        message = client.getMessageProtocol().readMessage();
                    } catch (IOException | ClassNotFoundException e) {
                        System.out.println("Client: " + client.getIdentification() + " has suddenly disconnected");
                        return;
                    }

                    if (message == null) continue;
                    // Validate session token
                    if (!validateToken(message)) continue;

                    switch (message.command) {
                        case Command.STOP -> {
                            if (client.currGame.getState().equals("typing")) {
                                System.out.println("stop");
                                client.currGame.stop();
                            }
                        }
                        case Command.WORDS_SEND -> {
                            if (message.data instanceof List) {
                                client.currGame.appendTypedWords(client, (List<String>) message.data);
                            }
                        }
                        case Command.WORDS_VALIDATE -> {
                            if (message.data instanceof HashMap) {
                                client.currGame.appendValidatedWords(client, (HashMap<String, List<String>>) message.data);
                            }
                        }
                        case Command.PLAY -> {
                            Server.updateIdleUsers(Server.Action.ADD_CLIENT, client);
                            System.out.println("Client: " + client.getIdentification() + " agrees to play again");
                        }
                        case Command.DISCONNECT -> {
                            System.out.println("Client: " + client.getIdentification() + " has disconnected");
                            try {
                                client.getMessageProtocol().sendMessage(new Message(Command.DISCONNECT));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            client.getCurrGame().removePlayer(client);
                            return;
                        }
                        default -> {
                        }
                    }
                }
                case DISCONNECTED -> {
                    System.out.println("[ListenClient]: DISCONNECTED");
                }
            }
        }
    }

    public boolean validateToken(Message message) {
        try {
            if (message.token == null) {
                client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Not Authorized: nonexistent auth token", Message.MessageType.DATA));
                client.getMessageProtocol().sendMessage(new Message(Command.DISCONNECT));
                return false;
            }
            // Verify if the token is valid in the database
            if (Server.database.schema.get("session").getAll().stream().noneMatch(session -> session.get("id").equals(message.token))) {
                client.getMessageProtocol().sendMessage(new Message(Command.ERROR, "Not Authorized: no session associated with token", Message.MessageType.DATA));
                client.getMessageProtocol().sendMessage(new Message(Command.DISCONNECT));
                return false;
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}