import javax.swing.*;
import java.awt.*;

public class OpeningNamePanel extends JPanel {
    private JLabel openingLabel;
    private final Color BACKGROUND_COLOR = new Color(40, 40, 40);
    private final Color TEXT_COLOR = new Color(186, 202, 68); // Yellow/lime color
    
    public OpeningNamePanel() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(350, 60));
        setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(60, 60, 60)));
        
        JLabel titleLabel = new JLabel("Opening:");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setForeground(Color.GRAY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
        
        openingLabel = new JLabel("Start Position");
        openingLabel.setFont(new Font("Arial", Font.BOLD, 16));
        openingLabel.setForeground(TEXT_COLOR);
        openingLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 5, 10));
        
        add(titleLabel, BorderLayout.NORTH);
        add(openingLabel, BorderLayout.CENTER);
    }
    
    public void setOpeningName(String openingName) {
        if (openingName == null || openingName.isEmpty()) {
            openingLabel.setText("Unknown Opening");
            openingLabel.setForeground(Color.GRAY);
        } else {
            openingLabel.setText(openingName);
            openingLabel.setForeground(TEXT_COLOR);
        }
    }
    
    public void reset() {
        openingLabel.setText("Start Position");
        openingLabel.setForeground(TEXT_COLOR);
    }
}