package src.server;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class Game implements Runnable {
    private final List<Client> clients;
    private volatile String state; // TYPING | REQUESTING | VALIDATING | END
    private final Clock clock;
    private long startTime;
    private final static ReentrantLock lock = new ReentrantLock();
    private final Thread.Builder builder;
    private final char startLetter;
    private boolean noPlayers = false;
    private final Map<Client, List<String>> typedWords = new HashMap<>(); // client - [words]
    private final Map<Client, Map<String, List<String>>> validatedWords = new HashMap<>(); // client - [category - [words]]
    private static final List<String> categories = List.of("Animal", "Country", "Name", "Adjective", "Verb");

    public Game(List<Client> clients) {
        this.clients = clients;
        this.state = "typing";
        this.clock = Clock.systemDefaultZone();
        this.startTime = clock.millis();
        this.builder = Thread.ofVirtual().name("GameVirtualThread");
        this.startLetter = (char) (Math.random() * 26 + 'A');
    }

    @Override
    public void run() {
        System.out.println("Starting game with " + clients.size() + " players and letter: " + startLetter);

        List<Client> lostClients = new ArrayList<>();
        for (Client client : clients) {
            try {
                client.getMessageProtocol().sendMessage(new Message(Command.GAME_START, startLetter, Message.MessageType.DATA));
            } catch (IOException e) {
                System.out.println("Lost connection with client: " + client.getUsername());
                client.setPendingReconnection(true);
                lostClients.add(client);
            }
        }

        if (noPlayers || gameShouldEndDueToLackOkPlayers(lostClients)) {
            return;
        }

        // Wait for 10 seconds or until someone calls stop
        while (wait(30) && this.state.equals("typing")) {
        }
        this.resetClock();
        this.state = "requesting";

        // Request typed words
        for (Client client : clients) {
            try {
                client.getMessageProtocol().sendMessage(new Message(Command.WORDS_RECEIVE));
            } catch (IOException e) {
                // Lost connection with client
                System.out.println("Lost connection with client: " + client.getUsername());
                client.setPendingReconnection(true);
                lostClients.add(client);
            }
        }
        if (noPlayers || gameShouldEndDueToLackOkPlayers(lostClients)) {
            return;
        }

        // Wait for 2 seconds or check if everyone has sent their words
        while (wait(2) && !everyoneHasSentWords()) {
        }
        this.resetClock();
        this.state = "validating";

        // Print requested words
        for (Map.Entry<Client, List<String>> entry : typedWords.entrySet()) {
            System.out.println("words: " + entry.getValue());
        }

        var categoriesWords = getWordsByCategory();
        for (Client client : clients) {
            try {
                client.getMessageProtocol().sendMessage(new Message(Command.WORDS_VALIDATE, categoriesWords, Message.MessageType.DATA));
            } catch (IOException e) {
                // Lost connection with client
                System.out.println("Lost connection with client: " + client.getUsername());
                client.setPendingReconnection(true);
                lostClients.add(client);
            }
        }
        if (noPlayers || gameShouldEndDueToLackOkPlayers(lostClients)) {
            return;
        }

        while (wait(20) && !everyoneHasSentValidatedWords()) {
        }
        this.resetClock();

        // Calculating the results
        // Create the words frequency by category
        var categoriesWordsFrequency = new HashMap<String, HashMap<String, Integer>>();
        for (String category : categories) {
            categoriesWordsFrequency.put(category, new HashMap<>());
        }

        for (Map.Entry<Client, Map<String, List<String>>> entry : validatedWords.entrySet()) {
            var words = entry.getValue(); // category - [words]
            for (Map.Entry<String, List<String>> category : words.entrySet()) {
                var categoryName = category.getKey();
                for (String word : category.getValue()) { // For each word
                    if (categoriesWordsFrequency.get(categoryName).containsKey(word)) {
                        categoriesWordsFrequency.get(categoryName).put(word, categoriesWordsFrequency.get(categoryName).get(word) + 1);
                    } else {
                        categoriesWordsFrequency.get(categoryName).put(word, 1);
                    }
                }
            }
        }

        // Calculate the score for each player
        var playersScore = new HashMap<String, Integer>(); // username - score
        for (Client client : clients) {
            int score = 0;
            if (typedWords.get(client) == null) {
                playersScore.put(client.getUsername(), 0);
                continue;
            }
            for (int i = 0; i < categories.size(); i++) {
                var category = categories.get(i);
                var categoryWords = categoriesWordsFrequency.get(category);
                var client_category_word = typedWords.get(client).get(i);
                if (categoryWords.containsKey(client_category_word)) {
                    score += categoryWords.get(client_category_word);
                }
            }
            playersScore.put(client.getUsername(), score);
            System.out.println(score);
            client.updateScore(score);
        }

        // Send results
        for (Client client : clients) {
            try {
                client.getMessageProtocol().sendMessage(new Message(Command.GAME_END, playersScore, Message.MessageType.DATA));
                client.currGame = null;
            } catch (IOException e) {
                System.out.println("Lost connection with client: " + client.getUsername());
            }
        }

        System.out.println("Game has ended");
        this.state = "end";
        Server.removeGame(this);
    }

    public boolean wait(int seconds) {
        return (clock.millis() - startTime) / 1000 < seconds;
    }

    private void resetClock() {
        this.startTime = clock.millis();
    }

    public void stop() {
        lock.lock();
        try {
            System.out.println("[ Someone stopped the game ]");
            this.state = "requesting";
        } finally {
            lock.unlock();
        }
    }

    public void appendTypedWords(Client client, List<String> words) {
        lock.lock();
        try {
            typedWords.put(client, words);
        } finally {
            lock.unlock();
        }
    }

    public boolean everyoneHasSentWords() {
        return typedWords.size() == clients.size();
    }

    public void appendValidatedWords(Client client, HashMap<String, List<String>> words) {
        lock.lock();
        try {
            validatedWords.put(client, words);
        } finally {
            lock.unlock();
        }
    }

    public boolean everyoneHasSentValidatedWords() {
        return validatedWords.size() == categories.size();
    }

    private HashMap<String, List<String>> getWordsByCategory() {
        HashMap<String, List<String>> categoriesWords = new HashMap<>();
        for (int i = 0; i < categories.size(); i++) {
            List<String> category_words = new ArrayList<>();
            for (Map.Entry<Client, List<String>> entry : typedWords.entrySet()) {
                var client_words = entry.getValue();
                if (client_words.size() > i) {
                    var word = client_words.get(i);
                    if (!word.isEmpty())
                        if (!category_words.contains(word))
                            category_words.add(word);
                }
            }
            categoriesWords.put(categories.get(i), category_words);
        }
        return categoriesWords;
    }

    public String getState() {
        return this.state;
    }

    public List<Client> getClients() {
        return this.clients;
    }

    public boolean isActive() {
        return !this.state.equals("end");
    }

    private boolean gameShouldEndDueToLackOkPlayers(List<Client> lostClients) {
        if (clients.size() - lostClients.size() >= 2) return false;
        for (Client client : clients) {
            if (!client.isPendingReconnection()) {
                try {
                    client.setState(ClientState.QUEUE);
                    client.setGameState("waiting");
                    client.getMessageProtocol().sendMessage(new Message(Command.GAME_ABORT));
                } catch (IOException e) {
                    System.out.println("Client: " + client.getIdentification() + " has suddenly disconnected");
                }
            }
        }
        System.out.println("Game has ended due to lack of players");
        this.state = "end";
        Server.removeGame(this);
        noPlayers = true;
        return true;

//        clients.removeAll(lostClients);
//        lostClients.clear();
//        if (clients.isEmpty()) {
//            System.out.println("Game has ended due to lack of players");
//            this.state = "end";
//            Server.removeGame(this);
//            noPlayers = true;
//            return true;
//        }
//        return false;
    }

    public void removePlayer(Client client) {
        clients.remove(client);
        List<Client> lostClients = new ArrayList<>();
        for (Client c : clients) {
            try {
                client.getMessageProtocol().sendMessage(new Message(Command.PING));
            } catch (IOException e) {
                System.out.println("Lost connection with client: " + client.getUsername());
                lostClients.add(client);
            }
        }
        gameShouldEndDueToLackOkPlayers(lostClients);
    }
}
