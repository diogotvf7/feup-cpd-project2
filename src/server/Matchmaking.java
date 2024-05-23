package src.server;

import java.util.ArrayList;
import java.util.List;

public interface Matchmaking {
    void startMatchmaking();

    static void createLobby(int PLAYERS_PER_LOBBY, int totalLobbies, List<Client> availableClients) {
        for (int i = 0; i < totalLobbies; i++) {
            List<Client> players = new ArrayList<>();
            int j = 0;
            while (j < PLAYERS_PER_LOBBY && !availableClients.isEmpty()) {
                players.add(availableClients.removeFirst());
                j++;
            }
            try{
                if (j == PLAYERS_PER_LOBBY) {

                    var game = new Game(players);

                    System.out.println("Starting a new Matchmaking with " + players.stream().map(Client::getUsername).toList());

                    for(Client client : players){
                        client.setState(ClientState.GAME);
                        client.currGame = game;
                        Server.idleClients.remove(client);
                    }

                    Server.addGame(game);
                    Server.builder.start(game);
                }
            } catch (Exception e) {
                System.out.println("Error creating the game lobby");
                return;
            }
        }
    }
}
