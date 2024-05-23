package src.game;

import src.server.Client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class ValidateInterface implements DisplayInterface {
    private final char firstLetter;
    private static final List<String> categories = List.of("Animal", "Country", "Name", "Adjective", "Verb");
    private final HashMap<String, ArrayList<String>> categoriesWords;
    private final HashMap<String, ArrayList<String>> validatedCategoriesWords = new HashMap<>();
    private final Scanner scanner;
    private final Client client;

    public ValidateInterface(char firstLetter, HashMap<String, ArrayList<String>> categoriesWords, Client client) {
        this.firstLetter = Character.toUpperCase(firstLetter);
        this.categoriesWords = categoriesWords;
        this.scanner = new Scanner(System.in);
        this.client = client;
    }

    private void display(String category, List<String> words) {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        System.out.println("Select the valid " + category.toUpperCase() + " words starting with " + firstLetter);
        System.out.println();
        for (int i = 0; i < words.size(); i++) {
            System.out.println((i + 1) + ". " + words.get(i));
        }
        if (words.isEmpty()) {
            System.out.println("No words :( press enter");
        }
        System.out.println();
        System.out.println("Use <n>, <n>, <n> to select the words");
        System.out.print(">> ");
    }

    public void run() throws IOException {
        for (int j = 0; j < categories.size(); ) {
            if (!client.getGameState().equals("validating")) {
                return;
            }

            String category = categories.get(j);
            List<String> words = categoriesWords.get(categories.get(j));

            display(category, words);
            String input = scanner.nextLine();

            try {
                if (input.isEmpty()) {
                    // Check if validating was stopped
                    if (!client.getGameState().equals("validating"))
                        return;

                    // Empty option: No valid words
                    j++;
                    continue;
                }

                ArrayList<String> selectedWords = new ArrayList<>();
                String[] parts = input.split(",");
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].strip();
                }

                for (String part : parts) {
                    int word = Integer.parseInt(part);
                    if (word < 1 || word > words.size()) {
                        System.out.println("Are you trying to break the game? Invalid input, try again.");
                    }
                    selectedWords.add(words.get(word - 1));
                }
                validatedCategoriesWords.put(category, selectedWords);
                j++;
            } catch (Exception e) {
                System.out.println("Invalid input, u stupid. Try again don't be a dumb");
            }
        }
        if (client.getGameState().equals("validating"))
            client.sendValidatedWords(validatedCategoriesWords);
    }
}
