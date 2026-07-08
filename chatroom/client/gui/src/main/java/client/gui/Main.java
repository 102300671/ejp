package client.gui;

import client.gui.ui.ConnectFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            ConnectFrame connectFrame = new ConnectFrame();
            connectFrame.setVisible(true);
        });
    }
}