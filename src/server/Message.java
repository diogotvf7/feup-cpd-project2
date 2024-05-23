package src.server;

import java.io.Serializable;

/**
 * "type": COMMAND | TEXT | ARRAY | OBJECT
 * "data": String, Array or Object
 * "token": Sent by the client to authenticate the user
 */

public class Message implements Serializable {
    public Command command;
    public Object data = null;
    public String token = null;

    public enum MessageType {
        DATA,
        TOKEN
    }

    public Message(Command command, Object content, MessageType type) {
        this.command = command;
        switch (type) {
            case DATA:
                this.data = content;
            case TOKEN:
                this.token = content.toString();
        }
    }

    public Message(Command command, Object data, String token) {
        this.command = command;
        this.data = data;
        this.token = token;
    }

    public Message(Command command) {
        this.command = command;
    }

    public String toString() {
        return "Message: \n    Command: " + this.command + "\n    Data: " + this.data + "\n    Token: " + this.token;
    }
}