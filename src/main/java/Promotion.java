import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Promotion extends JDialog implements ActionListener {

    private JButton queenBtn, rookBtn, bishopBtn, knightBtn;
    private String chosenPiece;

    public Promotion(JFrame parent, boolean isWhite) {
        super(parent, "Choose Promotion", true);
        setLayout(new GridLayout(2, 2));
        setSize(300, 150);
        setLocationRelativeTo(parent);

        queenBtn = new JButton("Queen");
        rookBtn = new JButton("Rook");
        bishopBtn = new JButton("Bishop");
        knightBtn = new JButton("Knight");

        queenBtn.addActionListener(this);
        rookBtn.addActionListener(this);
        bishopBtn.addActionListener(this);
        knightBtn.addActionListener(this);

        add(queenBtn);
        add(rookBtn);
        add(bishopBtn);
        add(knightBtn);

        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == queenBtn) chosenPiece = "Queen";
        else if (e.getSource() == rookBtn) chosenPiece = "Rook";
        else if (e.getSource() == bishopBtn) chosenPiece = "Bishop";
        else if (e.getSource() == knightBtn) chosenPiece = "Knight";
        dispose();
    }

    public String getChosenPiece() {
        return chosenPiece;
    }
}
