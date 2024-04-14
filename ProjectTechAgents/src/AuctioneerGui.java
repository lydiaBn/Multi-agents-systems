import jade.core.AID;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 @author Giovanni Caire - TILAB
 editted by Yuri Ardila, 2014 for blindAuction project
 */
class AuctioneerGUI extends JFrame {

    private Auctioneer myAgent;

    private JTextField titleField, priceField,reserveField;

    AuctioneerGUI(Auctioneer a) {
        super(a.getLocalName() + ": Add item");

        myAgent = a;

        JPanel p = new JPanel();
        p.setLayout(new GridLayout(4, 2));
        p.add(new JLabel("Item Name:"));
        titleField = new JTextField(15);
        p.add(titleField);
        p.add(new JLabel("Initial Price:"));
        priceField = new JTextField(15);
        p.add(priceField);
        p.add(new JLabel("Reserve Price:")); // Label for reserve price
        reserveField = new JTextField(15); // Input field for reserve price
        p.add(reserveField);
        getContentPane().add(p, BorderLayout.CENTER);

        JButton addButton = new JButton("Add");
        addButton.addActionListener( new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                try {
                    String title = titleField.getText().trim();
                    String price = priceField.getText().trim();
                    String reservePrice = reserveField.getText().trim(); // Get the value of the reserve price field
                    myAgent.updateCatalogue(title, Integer.parseInt(price), Integer.parseInt(reservePrice)); // Pass the reserve price to the updateCatalogue method
                    titleField.setText("");
                    priceField.setText("");
                    reserveField.setText(""); // Clear the reserve price field after adding the item
                }
                catch (Exception e) {
                    JOptionPane.showMessageDialog(AuctioneerGUI.this, "Invalid values. "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } );
        p = new JPanel();
        p.add(addButton);
        getContentPane().add(p, BorderLayout.SOUTH);

        // Make the agent terminate when the user closes
        // the GUI using the button on the upper right corner
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                myAgent.doDelete();
            }
        } );

        setResizable(false);
    }

    public void showGui() {
        pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int centerX = (int)screenSize.getWidth() / 2;
        int centerY = (int)screenSize.getHeight() / 2;
        setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
        super.setVisible(true);
    }
}