package server.user;

public class User {
  private int id;
  private String username;
  private String passward;
  private String created_at;
  public User(int id, String name, String passward, String created_at) {
    this.id = id;
    this.username = username;
    this.passward = passward;
    this.created_at = created_at;
  }
}