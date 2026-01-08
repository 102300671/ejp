package server.room;
import java.util.ArrayList;

public abstract class Room {
    private String name;
    private String id;
    private ArrayList<String> username;
    private ArrayList<String> userid;
    private int userCount;

    public Room(String name, String id) {
        this.name = name;
        this.id = id;
        this.username = new ArrayList<>();
        this.userid = new ArrayList<>();
        this.userCount = 0;
    }
    public String getName() {
        return name;
    }
    public String getId() {
        return id;
    }

    public void addUser(String username, String userid) {
        this.username.add(username);
        this.userid.add(userid);
        this.userCount++;
    }
}
