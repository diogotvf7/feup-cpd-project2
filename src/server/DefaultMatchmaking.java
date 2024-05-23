package src.server;

import java.util.ArrayList;
import java.util.List;


public class DefaultMatchmaking implements Matchmaking {

    @Override
    public void startMatchmaking() {
        System.out.println("Trying to start default Matchmaking");

        int PLAYERS_PER_LOBBY = 2;
        int totalPlayers = Server.idleClients.size();
        int totalLobbies = totalPlayers / PLAYERS_PER_LOBBY;
        List<Client> availableClients = new ArrayList<>(Server.idleClients.stream().
                filter(client -> !client.isPendingReconnection()).
                toList());

        System.out.println("idleClients: " + Server.idleClients);
        System.out.println("availableClients: " + availableClients);

        Matchmaking.createLobby(PLAYERS_PER_LOBBY, totalLobbies, availableClients);
    }
}
