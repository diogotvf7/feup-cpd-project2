package src.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Table {
    public String table_name;
    public String[] columns;
    public HashMap<String, HashMap<String, String>> rows;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static final String path = "tables/";

    public Table(String table_name, String[] columns) {
        this.table_name = table_name;
        this.columns = columns;

        rows = new HashMap<>();

        try {
            File fd = new File(path + table_name + ".csv");
            if (fd.createNewFile()) {
                FileWriter fw = new FileWriter(fd);
                fw.write(String.join(",", columns) + "\n");
                fw.close();
            } else {
                BufferedReader reader = new BufferedReader(new FileReader(fd));
                String line = reader.readLine(); // Skip header
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",");
                    HashMap<String, String> row = new HashMap<>();
                    for (int i = 0; i < values.length; i++) {
                        row.put(columns[i], values[i]);
                    }
                    rows.put(values[0], row);
                }
                reader.close();
            }
        } catch (IOException e) {
            System.out.println("Error creating table \"" + table_name + "\".");
            e.printStackTrace();
        }
    }

    public HashMap<String, String> get(String query) {
        lock.readLock().lock();
        try {
            return rows.get(query);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<HashMap<String, String>> getAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(rows.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean insert(HashMap<String, String> row) {
        lock.writeLock().lock();
        try {
            rows.put(row.get(columns[0]), row);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean delete(String query) {
        lock.writeLock().lock();
        try {
            return rows.remove(query) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String toString(String filter) {
        lock.readLock().lock();
        try {
            StringBuilder result = new StringBuilder();

            result.append("--- ").append(table_name).append("\n");
            result.append(String.join(",     ", columns)).append("\n");
            for (HashMap<String, String> row : rows.values()) {
                if (filter == null || row.get(filter) != null) {
                    List<String> values = new ArrayList<>();
                    for (String column : columns) {
                        values.add(row.get(column));
                    }
                    result.append(String.join(",     ", values)).append("\n");
                }
            }

            return result.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void save() {
        lock.writeLock().lock();
        try {
            FileWriter fw = new FileWriter(path + table_name + ".csv");
            fw.write(String.join(",", columns) + "\n");
            for (HashMap<String, String> row : rows.values()) {
                List<String> values = new ArrayList<>();
                for (String column : columns) {
                    values.add(row.get(column));
                }
                fw.write(String.join(",", values) + "\n");
            }
            fw.close();
        } catch (IOException e) {
            System.out.println("Error saving table \"" + table_name + "\".");
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void load() {
        lock.writeLock().lock();
        try {
            File fd = new File(path + table_name + ".csv");
            BufferedReader reader = new BufferedReader(new FileReader(fd));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                HashMap<String, String> row = new HashMap<>();
                for (int i = 0; i < values.length; i++) {
                    row.put(columns[i], values[i]);
                }
                rows.put(values[0], row);
            }
            reader.close();
        } catch (IOException e) {
            System.out.println("Error loading table \"" + table_name + "\".");
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
