package src.server;

import src.db.Database;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    private static Matchmaking matchmaking = null;
    private final static ReentrantLock lock = new ReentrantLock();
    public final static ArrayList<Client> idleClients = new ArrayList<>();
    public final static Thread.Builder builder = Thread.ofVirtual().name("MyThread");

    public static ArrayList<Game> games = new ArrayList<>();

    public final static Database database = new Database();

    public enum Action {
        ADD_CLIENT,
        REMOVE_CLIENT,
        SET_CLIENT_PENDING_RECONNECTION,
        UNSET_CLIENT_PENDING_RECONNECTION
    }

    public static void main(String[] args) {
        if (args.length < 1) return;
        int port = Integer.parseInt(args[0]);
        int matchmakingType = Integer.parseInt(args[1]);

        matchmaking = matchmakingType == 0 ? new DefaultMatchmaking() : new RankedMatchmaking();

        System.out.println("Server started in port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Client connected: " + socket.getRemoteSocketAddress());
                builder.start(new Authentication(socket));
            }
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void updateIdleUsers(Action action, Client client) {
        lock.lock();
        try {
            switch (action) {
                case ADD_CLIENT -> idleClients.add(client);
                case REMOVE_CLIENT -> idleClients.remove(client);
                case SET_CLIENT_PENDING_RECONNECTION -> client.setPendingReconnection(true);
                case UNSET_CLIENT_PENDING_RECONNECTION -> client.setPendingReconnection(false);
            }
            matchmaking.startMatchmaking();
        } finally {
            lock.unlock();
        }
    }

    public static void addGame(Game game) {
        lock.lock();
        try {
            games.add(game);
        } finally {
            lock.unlock();
        }
    }

    public static void removeGame(Game game) {
        lock.lock();
        try {
            games.remove(game);
        } finally {
            lock.unlock();
        }
    }

    public static boolean isUserLoggedIn(String username) {
        boolean clientInQueue = Server.idleClients.stream().anyMatch(client1 -> client1.getUsername().equals(username) && !client1.isPendingReconnection());
        boolean clientInGame = isClientInGame(username);
        return (clientInQueue || clientInGame);
    }

    private static boolean isClientInGame(String username) {
        for (Game game : games) {
            List<Client> clientList = game.getClients();
            for (Client client1 : clientList) {
                if (client1.getUsername().equals(username)) {
                    return true;
                }
            }
        }
        return false;
    }

}