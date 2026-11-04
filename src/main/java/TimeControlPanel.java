    import javax.swing.*;
    import java.awt.*;
    import java.awt.event.*;

    public class TimeControlPanel extends JPanel {
        
    private String selectedTimeControl = "10 min";
    private String selectedCategory = "Rapid";
    private JButton startButton;
    private JLabel headerTextLabel;
    
    // Exact chess.com color scheme
    private final Color BACKGROUND_COLOR = new Color(40, 40, 40);
    private final Color BUTTON_COLOR = new Color(50, 50, 50);
    private final Color BUTTON_HOVER = new Color(70, 70, 70);
    private final Color SELECTED_COLOR = new Color(129, 182, 76); // Exact chess.com green
    private final Color START_BUTTON_COLOR = new Color(129, 182, 76);
    private final Color START_BUTTON_HOVER = new Color(149, 202, 96);
    private final Color HEADER_BG = new Color(50, 50, 50);
    
    public TimeControlPanel() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(420, 680));
        
        // Main content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(BACKGROUND_COLOR);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Collapsed dropdown header (like image 1)
        JPanel dropdownHeader = createDropdownHeader();
        contentPanel.add(dropdownHeader);
        contentPanel.add(Box.createVerticalStrut(15));
        
        // Time control sections (like image 2)
        JPanel bulletPanel = createTimeSection("🚀 Bullet", new String[]{"1 min", "1 | 1", "2 | 1"});
        JPanel blitzPanel = createTimeSection("⚡ Blitz", new String[]{"3 min", "3 | 2", "5 min"});
        JPanel rapidPanel = createTimeSection("⏱ Rapid", new String[]{"10 min", "15 | 10", "30 min"});
        JPanel customPanel = createTimeSection("⚙ Custom", new String[]{"Custom"});
        JPanel timelessPanel = createTimeSection("♾ Timeless", new String[]{"No limit"});
        
        contentPanel.add(bulletPanel);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(blitzPanel);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(rapidPanel);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(customPanel);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(timelessPanel);
        contentPanel.add(Box.createVerticalStrut(20));
        
        // Start Game button
        startButton = createStartButton();
        contentPanel.add(startButton);
        
        add(contentPanel, BorderLayout.CENTER);
    }
    
    private JPanel createDropdownHeader() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBackground(HEADER_BG);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        
        // Left side with icon and text
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setBackground(HEADER_BG);
        
        JLabel iconLabel = new JLabel("⏱");
        iconLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        iconLabel.setForeground(SELECTED_COLOR);
        
        headerTextLabel = new JLabel(selectedTimeControl + " (" + selectedCategory + ")");
        headerTextLabel.setFont(new Font("Arial", Font.BOLD, 16));
        headerTextLabel.setForeground(Color.WHITE);
        
        leftPanel.add(iconLabel);
        leftPanel.add(headerTextLabel);
        
        // Right side with dropdown arrow
        JLabel arrowLabel = new JLabel("⌄");
        arrowLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        arrowLabel.setForeground(new Color(150, 150, 150));
        
        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(arrowLabel, BorderLayout.EAST);
        
        // Rounded appearance
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60), 1, true),
            BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));
        
        return panel;
    }
    
    private JPanel createTimeSection(String title, String[] options) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Section title
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(8));
        
        // Buttons panel
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new GridLayout(1, options.length, 8, 0));
        buttonsPanel.setBackground(BACKGROUND_COLOR);
        buttonsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        for (String option : options) {
            JButton btn = createTimeButton(option);
            buttonsPanel.add(btn);
        }
        
        panel.add(buttonsPanel);
        
        return panel;
    }
    
    private JButton createTimeButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(BUTTON_COLOR);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Check if this should be selected by default
        if (text.equals(selectedTimeControl)) {
            button.setBackground(SELECTED_COLOR);
            button.setBorder(BorderFactory.createLineBorder(SELECTED_COLOR, 3, true));
        } else {
            button.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1, true));
        }
        
        // Mouse hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!text.equals(selectedTimeControl)) {
                    button.setBackground(BUTTON_HOVER);
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (!text.equals(selectedTimeControl)) {
                    button.setBackground(BUTTON_COLOR);
                }
            }
        });
        
        // Click handler
        button.addActionListener(e -> {
            if (text.equals("Custom")) {
                showCustomTimeDialog();
            } else {
                selectTimeControl(text);
            }
        });
        
        return button;
    }
    
    private void showCustomTimeDialog() {
        SoundManager.playExtraSound("tap.wav");
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Custom Time Control", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(350, 250);
        dialog.setLocationRelativeTo(this);
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(BACKGROUND_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Minutes input
        JPanel minutesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        minutesPanel.setBackground(BACKGROUND_COLOR);
        JLabel minutesLabel = new JLabel("Minutes:");
        minutesLabel.setForeground(Color.WHITE);
        minutesLabel.setPreferredSize(new Dimension(80, 25));
        JSpinner minutesSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 180, 1));
        minutesSpinner.setPreferredSize(new Dimension(80, 30));
        minutesPanel.add(minutesLabel);
        minutesPanel.add(minutesSpinner);
        
        // Increment input
        JPanel incrementPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        incrementPanel.setBackground(BACKGROUND_COLOR);
        JLabel incrementLabel = new JLabel("Increment:");
        incrementLabel.setForeground(Color.WHITE);
        incrementLabel.setPreferredSize(new Dimension(80, 25));
        JSpinner incrementSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 60, 1));
        incrementSpinner.setPreferredSize(new Dimension(80, 30));
        JLabel secLabel = new JLabel("seconds");
        secLabel.setForeground(Color.LIGHT_GRAY);
        incrementPanel.add(incrementLabel);
        incrementPanel.add(incrementSpinner);
        incrementPanel.add(secLabel);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBackground(BACKGROUND_COLOR);
        
        JButton okButton = new JButton("OK");
        okButton.setBackground(SELECTED_COLOR);
        okButton.setForeground(Color.WHITE);
        okButton.setFocusPainted(false);
        okButton.setBorderPainted(false);
        okButton.setPreferredSize(new Dimension(100, 35));
        okButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(BUTTON_COLOR);
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        cancelButton.setPreferredSize(new Dimension(100, 35));
        cancelButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        okButton.addActionListener(e -> {
            int minutes = (Integer) minutesSpinner.getValue();
            int increment = (Integer) incrementSpinner.getValue();
            
            String customTime;
            if (increment > 0) {
                customTime = minutes + " | " + increment;
            } else {
                customTime = minutes + " min";
            }
            
            selectTimeControl(customTime);
            dialog.dispose();
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        mainPanel.add(minutesPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(incrementPanel);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(buttonPanel);
        
        dialog.add(mainPanel);
        dialog.setVisible(true);
    }
    
    private void selectTimeControl(String timeControl) {
        selectedTimeControl = timeControl;
        
        // Determine category
        if (timeControl.equals("1 min") || timeControl.equals("1 | 1") || timeControl.equals("2 | 1")) {
            selectedCategory = "Bullet";
        } else if (timeControl.equals("3 min") || timeControl.equals("3 | 2") || timeControl.equals("5 min")) {
            selectedCategory = "Blitz";
        } else if (timeControl.equals("10 min") || timeControl.equals("15 | 10") || timeControl.equals("30 min")) {
            selectedCategory = "Rapid";
        } else if (timeControl.equals("No limit")) {
            selectedCategory = "Timeless";
        } else {
            selectedCategory = "Custom";
        }
        
        // Update header text
        headerTextLabel.setText(selectedTimeControl + " (" + selectedCategory + ")");
        
        // Refresh all buttons
        refreshButtons();
    }
    
    private void refreshButtons() {
        refreshComponentTree(this);
    }
    
    private void refreshComponentTree(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JButton && !(comp == startButton)) {
                JButton btn = (JButton) comp;
                if (btn.getText().equals(selectedTimeControl)) {
                    btn.setBackground(SELECTED_COLOR);
                    btn.setBorder(BorderFactory.createLineBorder(SELECTED_COLOR, 3, true));
                } else {
                    btn.setBackground(BUTTON_COLOR);
                    btn.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1, true));
                }
            } else if (comp instanceof Container) {
                refreshComponentTree((Container) comp);
            }
        }
    }
    
    private JButton createStartButton() {
        JButton button = new JButton("Start Game");
        button.setFont(new Font("Arial", Font.BOLD, 20));
        button.setForeground(Color.WHITE);
        button.setBackground(START_BUTTON_COLOR);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Rounded appearance
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(START_BUTTON_COLOR, 2, true),
            BorderFactory.createEmptyBorder(12, 20, 12, 20)
        ));
        
        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(START_BUTTON_HOVER);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(START_BUTTON_COLOR);
            }
        });
        
        return button;
    }
    
    public void setStartGameListener(ActionListener listener) {
        if (startButton != null) {
            // Remove old listeners
            for (ActionListener al : startButton.getActionListeners()) {
                startButton.removeActionListener(al);
            }
            startButton.addActionListener(listener);
        }
    }
    
    public String getSelectedTimeControl() {
        return selectedTimeControl;
    }
    
    public int[] getTimeSettings() {
        // Returns [initialTime, increment] in seconds
        // For "No limit", returns [0, 0]
        
        if (selectedTimeControl.equals("No limit")) {
            return new int[]{0, 0};
        }
        
        if (selectedTimeControl.contains("|")) {
            // Format: "3 | 2" means 3 minutes + 2 second increment
            String[] parts = selectedTimeControl.split("\\|");
            int minutes = Integer.parseInt(parts[0].trim());
            int increment = Integer.parseInt(parts[1].trim());
            return new int[]{minutes * 60, increment};
        } else {
            // Format: "10 min" means 10 minutes, no increment
            String numStr = selectedTimeControl.replaceAll("[^0-9]", "");
            if (numStr.isEmpty()) return new int[]{600, 0}; // default
            int minutes = Integer.parseInt(numStr);
            return new int[]{minutes * 60, 0};
        }
    }
}