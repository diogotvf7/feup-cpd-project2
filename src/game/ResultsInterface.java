package src.game;

import src.server.Client;
import src.server.Command;
import src.server.Message;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ResultsInterface implements DisplayInterface {
    private final Scanner scanner;
    private final Client client;

    private final HashMap<String, Integer> results;

    public ResultsInterface(Client client, HashMap<String, Integer> results) {
        this.scanner = new Scanner(System.in);
        this.client = client;
        this.results = results;
    }

    private void display() {
        System.out.println("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");
        System.out.print("\033[H\033[2J");
        System.out.flush();
        System.out.println("Game results: ");
        System.out.println();
        results.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> System.out.println((entry.getKey() + ": " + entry.getValue())));

        System.out.println();
        System.out.println("""
                Do you want to play again? 
                Type 0 to join a new queue
                Type 1 to quit
                """);
        System.out.print(">> ");
    }

    public void run() {
        try {
            display();
            while (true) {
                String input = this.scanner.nextLine();
                switch (input) {
                    case "0" -> {
                        client.setGameState("waiting");
                        client.getMessageProtocol().sendMessage(new Message(Command.PLAY));
                        return;
                    }
                    case "1" -> {
                        client.getSocket().close();
                        return;
                    }
                    default -> {
                        System.out.println("Invalid input");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Invalid input, try again.");
        }
    }
}
