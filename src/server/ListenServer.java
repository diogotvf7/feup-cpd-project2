package src.server;

import src.game.GameInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

class ListenServer implements Runnable {
    private final Client client;

    public ListenServer(Client client) {
        this.client = client;
    }

    @Override
    public void run() {
        while (true) {
            Message message;
            try {
                message = client.getMessageProtocol().readMessage();
            } catch (ClassNotFoundException | IOException e) {
                System.out.println("[ListenServer]: Couldn't read message.");
                return;
            }

//            System.out.println("[Client State]: " + client.getState().toString());
//            System.out.println("[Listen Server]: " + message.toString());

            switch (client.getState()) {
                case QUEUE -> {
                    switch (message.command) {
                        case Command.GAME_START -> {
                            client.setState(ClientState.GAME);
                            client.setFirstLetter((char) message.data);
                            client.setGameState("playing");
                            System.out.println(
                                    "[SERVER] - Game started with the letter: " + message.data + " \n" +
                                            "Press Enter to start typing"
                            );
                            client.setActive_interface(new GameInterface((char) message.data, client));
                        }
                        case Command.DISCONNECT -> {
                            try {
                                client.getSocket().close();
                            } catch (IOException e) {
                                System.out.println("[ListenServer]: Couldn't close socket.");
                            }
                            System.out.println("You have been disconnected from the server");
                            return;
                        }
                        case Command.TEXT -> {
                            System.out.print(message.data);
                        }
                    }
                }
                case GAME -> {
                    switch (message.command) {
                        case Command.WORDS_RECEIVE -> {
                            if (!client.getGameState().equals("between_requests")) {
                                System.out.println("\n[SERVER] - Game is over, Press Enter to validate the words \n");
                            }

                            try {
                                System.out.println(client.getMyWords());
                                client.getMessageProtocol().sendMessage(new Message(Command.WORDS_SEND, client.getMyWords(), Message.MessageType.DATA));
                            } catch (IOException e) {
                                System.out.println("Server Disconnected");
                                return;
                            }
                            client.setGameState("between_requests");
                        }
                        case Command.WORDS_VALIDATE -> {
                            HashMap<String, ArrayList<String>> words = (HashMap<String, ArrayList<String>>) message.data;
                            client.createValidateInterface(words);
                            client.setGameState("validating");
                        }
                        case Command.GAME_END -> {
                            if (!client.getGameState().equals("between_requests")) {
                                System.out.println("\n[SERVER] - To Slow, the game has ended. Press Enter to see the winner \n");
                            }
                            HashMap<String, Integer> results = (HashMap<String, Integer>) message.data;
                            client.createResultsInterface(results);
                            client.setGameState("end_game"); // This will stop the ListenServer thread
                            client.setState(ClientState.QUEUE);
                        }
                        case Command.GAME_ABORT -> {
                            System.out.println("""
                                    \n \n \n \n \n \n \n \n \n \n \n \n \n \n
                                    Seems like everyone left the game...
                                    You won the game and +10 points!
                                    Press enter to go back to the Queue.
                                    """);
                            client.updateScore(10);
                            client.setState(ClientState.QUEUE);
                            client.setGameState("waiting");
                            try {
                                client.getMessageProtocol().sendMessage(new Message(Command.PLAY));
                            } catch (IOException e) {
                                System.out.println("Server Disconnected");
                            }
                        }
                        case Command.TEXT -> {
                            System.out.print(message.data);
                        }
                        case Command.DISCONNECT -> {
                            client.setState(ClientState.DISCONNECTED);
                            try {
                                client.getSocket().close();
                            } catch (IOException e) {
                                System.out.println("[ListenServer]: Couldn't close socket.");
                            }
                            return;
                        }
                        default -> System.out.println("Message not recognized");
                    }
                }
                case DISCONNECTED -> {
                    try {
                        client.getSocket().close();
                    } catch (IOException e) {
                        System.out.println("[ListenServer]: Couldn't close socket.");
                    }
                    return;
                }
            }
        }
    }
}