package src.game;

import src.server.Client;
import src.server.Command;
import src.server.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class GameInterface implements DisplayInterface {
    private final char firstLetter;
    private static final List<String> categories = List.of("Animal", "Country", "Name", "Adjective", "Verb");
    private final List<String> words;
    private final Scanner scanner;
    private final Client client;

    public GameInterface(char firstLetter, Client client) {
        this.firstLetter = Character.toUpperCase(firstLetter);
        this.words = new ArrayList<>(Collections.nCopies(categories.size(), ""));
        this.scanner = new Scanner(System.in);
        this.client = client;
    }

    private void display() {
        System.out.print("\033[H\033[2J");
        System.out.println("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");
        System.out.flush();
        System.out.println("Fill the categories with words starting in: " + firstLetter);
        System.out.println();
        for (int i = 0; i < categories.size(); i++) {
            System.out.println((i + 1) + ". " + categories.get(i) + ": " + words.get(i));
        }
        System.out.println();
        System.out.println("Type <n>. <word> being <n> the category number");
        System.out.println("Type 'disconnect' to leave the game");
        System.out.println("Type 'stop' to end the game");
        System.out.print(">> ");
    }

    public List<String> getWords() {
        return words;
    }

    public void run() {
        try {
            display();
            String input = this.scanner.nextLine();

            if (input.equalsIgnoreCase("stop")) {
                client.stopGame();
                return;
            }

            if (input.equalsIgnoreCase("disconnect")) {
                client.setGameState("between_requests");
                client.getMessageProtocol().sendMessage(new Message(Command.DISCONNECT));
                return;
            }

            String[] parts = input.split("\\. ");
            int category = Integer.parseInt(parts[0]);
            String word = parts[1].toUpperCase();
            if (word.charAt(0) == firstLetter) {
                words.set(category - 1, word);
            }
        } catch (Exception e) {
            if (client.getGameState().equals("playing"))
                System.out.println("Invalid input, try again.");
        }
    }
}
