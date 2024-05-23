package src.db;
import java.util.HashMap;

public class Database {
    public HashMap<String,Table> schema;

    public static final String[] tables = {
            "user:id,username,password,score",
            "game:id",
            "user_game:userid,gameid",
            "session:id,userid,expiration"
    };
    
    public Database() {
        schema = new HashMap<>();

        for (String table : tables) {
            String[] aux = table.split(":");
            String table_name = aux[0];
            String[] columns = aux[1].split(",");

            schema.put(table_name, new Table(table_name, columns));
        }
    }    

    public void write(String table, String id, String column, String value) {
        Table t = schema.get(table);
        HashMap<String, String> row = t.get(id);
        row.put(column, value);
        t.insert(row);
    }

    public void write(String table, String id, HashMap<String, String> values) {
        Table t = schema.get(table);
        HashMap<String, String> row = t.get(id);
        
        if (row == null) row = new HashMap<String,String>() {{
            put("id", id);
        }};

        for (String column : values.keySet()) 
            row.put(column, values.get(column));

        t.insert(row);
    }

    public HashMap<String, String> read(String table, String id) {
        Table t = schema.get(table);
        return t.get(id);
    }

    public String read(String table, String id, String column) {
        Table t = schema.get(table);
        HashMap<String, String> row = t.get(id);
        return row.get(column);   
    }

    public void save() {
        for (Table table : schema.values()) {
            table.save();
        }
    }

    public void close() {
        save();
    }

    public void clearSessions() {
        schema.get("session").getAll().forEach(session -> schema.get("session").delete(session.get("id")));
    }

    // To test sessions
    public static void main(String[] args){
        Database db = new Database();
        db.clearSessions();

        db.save();

        db.close();
    }

    // Other testing
    public static void main2(String[] args) {
        Database db = new Database();

        db.write(
            "user",
            "1",
            new HashMap<String, String>() {{
                put("username", "admin");
                put("password", "admin");
                put("score", "100");
            }}
        );

        db.write(
            "user",
            "2",
            new HashMap<String, String>() {{
                put("username", "user");
                put("password", "user");
                put("score", "50");
            }}
        );

        db.write(
            "game",
            "1",
            new HashMap<String, String>() {{
                put("gamestate", "running");
            }}
        );

        db.write(
            "user_game",
            "1",
            new HashMap<String, String>() {{
                put("userid", "1");
                put("gameid", "1");
            }}
        );

        db.write(
            "user_game",
            "2",
            new HashMap<String, String>() {{
                put("userid", "2");
                put("gameid", "1");
            }}
        );

        for (Table table : db.schema.values()) {
            System.out.println(table.toString(null));
            table.save();
        }

        System.out.println("=====================================");

        db.write(
            "user",
            "1",
            new HashMap<String, String>() {{
                put("score", "200");
            }}
        );

        db.write(
            "user",
            "2",
            new HashMap<String, String>() {{
                put("score", "150");
            }}
        );

        db.write(
            "user_game",
            "1",
            new HashMap<String, String>() {{
                put("gameid", "2");
            }}
        );

        db.write(
            "user_game",
            "2",
            new HashMap<String, String>() {{
                put("gameid", "2");
            }}
        );

        for (Table table : db.schema.values()) {
            System.out.println(table.toString(null));
            table.save();
        }

        db.close();
    }
}
