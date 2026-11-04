import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class PlaceholderScreen extends JPanel {
    
    private String featureName;
    private ActionListener backListener;
    
    public PlaceholderScreen(String featureName, ActionListener listener) {
        this.featureName = featureName;
        this.backListener = listener;
        
        setLayout(new BorderLayout());
        setBackground(UiTheme.same(new Color(48, 46, 43)));
        setPreferredSize(new Dimension(640, 680)); // Set preferred size for proper layout
        
        initializeComponents();
    }
    
    private void initializeComponents() {
        // Center panel with message
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(UiTheme.same(new Color(48, 46, 43)));
        
        JLabel titleLabel = new JLabel(featureName);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 40));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel messageLabel = new JLabel("Coming Soon!");
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 24));
        messageLabel.setForeground(UiTheme.c(186, 202, 68));
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(titleLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        centerPanel.add(messageLabel);
        centerPanel.add(Box.createVerticalGlue());
        
        // Back button at bottom
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(UiTheme.same(new Color(48, 46, 43)));
        
        JButton backButton = new JButton("Back to Menu");
        backButton.setPreferredSize(new Dimension(200, 40));
        backButton.setFont(new Font("Arial", Font.BOLD, 16));
        backButton.setForeground(Color.WHITE);
        backButton.setBackground(UiTheme.c(118, 150, 86));
        backButton.setFocusPainted(false);
        backButton.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        backButton.setActionCommand("BACK_TO_MENU");
        backButton.addActionListener(backListener);
        
        // Hover effect
        backButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                backButton.setBackground(UiTheme.c(186, 202, 68));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                backButton.setBackground(UiTheme.c(118, 150, 86));
            }
        });
        
        bottomPanel.add(backButton);
        
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }
}