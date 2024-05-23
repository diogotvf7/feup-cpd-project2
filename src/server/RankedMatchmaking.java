package src.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RankedMatchmaking implements Matchmaking {
    @Override
    public void startMatchmaking() {
        System.out.println("Trying to start ranked Matchmaking");

        int PLAYERS_PER_LOBBY = 2;
        int totalPlayers = Server.idleClients.size();
        int totalLobbies = totalPlayers / PLAYERS_PER_LOBBY;

        List<Client> availableClients = new ArrayList<>(Server.idleClients.stream().
                filter(client -> !client.isPendingReconnection()).
                toList());
        Comparator<Client> clientComparator = Comparator.comparing(Client::getElo);
        availableClients.sort(clientComparator);

        Matchmaking.createLobby(PLAYERS_PER_LOBBY, totalLobbies, availableClients);
    }
}
