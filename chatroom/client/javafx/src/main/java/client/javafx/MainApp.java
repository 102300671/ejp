package client.javafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {
    
    private static Stage primaryStage;
    
    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        primaryStage.setTitle("EJP Chatroom");
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        
        showConnectScreen();
    }
    
    public static void showConnectScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/client/javafx/connect.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 450, 400);
        scene.getStylesheets().add(MainApp.class.getResource("/styles/main.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }
    
    public static void showLoginScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/client/javafx/login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 450, 400);
        scene.getStylesheets().add(MainApp.class.getResource("/styles/main.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }
    
    public static void showChatScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/client/javafx/chat.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 900, 650);
        scene.getStylesheets().add(MainApp.class.getResource("/styles/main.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }
    
    public static Stage getPrimaryStage() {
        return primaryStage;
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}