package src.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class MessageProtocol {
    private Client client;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;

    public MessageProtocol(Client client) {
        try {
            this.client = client;
            this.outputStream = new ObjectOutputStream(client.getSocket().getOutputStream());
            this.inputStream = new ObjectInputStream(client.getSocket().getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
            this.close();
        }
    }

    public void sendMessage(Message message) throws IOException {
        message.token = client.getToken(); // Append token to message
        outputStream.writeObject(message);
        outputStream.flush();
    }

    public Message readMessage() throws IOException, ClassNotFoundException {
        return (Message) inputStream.readObject();
    }

    public void close() {
        try {
            if (client.getSocket() != null)
                client.getSocket().close();
            if (inputStream != null)
                inputStream.close();
            if (outputStream != null)
                outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
