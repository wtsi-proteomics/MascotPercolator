package gui.Panels;


import com.nexes.wizard.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;

public class TestPanel4 extends JPanel {

    private javax.swing.JLabel anotherBlankSpace;
    private javax.swing.JLabel blankSpace;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel welcomeTitle;
    private javax.swing.JLabel yetAnotherBlankSpace1;

    private JPanel contentPanel;
    private JLabel iconLabel;
    private JSeparator separator;
    private JLabel targetLabel;
    private JLabel decoyLabel;
    private JLabel userLabel;
    private JLabel fileLabel;
    private JLabel textLabel;
    private JPanel titlePanel;

    private JTextField targetDat;
    private JTextField decoyDat;
    private JTextField userID;
    private JTextField fileNameOut;
    private javax.swing.JCheckBox jCheckBoxFiltering;
    private javax.swing.JCheckBox jCheckBoxHighCharge;
    private javax.swing.JCheckBox jCheckBoxChargeFeatureType;
    private javax.swing.JCheckBox jCheckBoxCreateNewDat;




    public TestPanel4() {

        super();

        contentPanel = getContentPanel();
        contentPanel.setBorder(new EmptyBorder(new Insets(10, 10, 10, 10)));

        ImageIcon icon = getImageIcon();

        titlePanel = new javax.swing.JPanel();
        textLabel = new javax.swing.JLabel();

        iconLabel = new javax.swing.JLabel();
        separator = new javax.swing.JSeparator();

        setLayout(new java.awt.BorderLayout());


        titlePanel.setLayout(new java.awt.BorderLayout());
        titlePanel.setBackground(Color.gray);

        textLabel.setBackground(Color.gray);
        textLabel.setFont(new Font("MS Sans Serif", Font.BOLD, 14));
        textLabel.setText("Favorite Connector Type");
        textLabel.setBorder(new EmptyBorder(new Insets(10, 10, 10, 10)));
        textLabel.setOpaque(true);

        iconLabel.setBackground(Color.gray);
        if (icon != null)
            iconLabel.setIcon(icon);

        titlePanel.add(textLabel, BorderLayout.CENTER);
        titlePanel.add(iconLabel, BorderLayout.EAST);
        titlePanel.add(separator, BorderLayout.SOUTH);

        add(titlePanel, BorderLayout.NORTH);
        JPanel secondaryPanel = new JPanel();
        secondaryPanel.add(contentPanel, BorderLayout.NORTH);
        add(secondaryPanel, BorderLayout.WEST);

    }

    public void addTargetTextActionListener(DocumentListener l) {
        targetDat.getDocument().addDocumentListener(l);
    }

    public void addDecoyTextActionListener(DocumentListener l) {
        decoyDat.getDocument().addDocumentListener(l);
    }

    public void addUserTextActionListener(DocumentListener l) {
        userID.getDocument().addDocumentListener(l);
    }

    public boolean areTextFieldsEntered() {
        return !(targetDat.getText().equals("") || decoyDat.getText().equals("") || userID.getText().equals(""));
    }

    private JPanel getContentPanel() {

        JPanel contentPanel1 = new JPanel();

        welcomeTitle = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        blankSpace = new javax.swing.JLabel();

        targetLabel = new javax.swing.JLabel();
        decoyLabel = new javax.swing.JLabel();
        userLabel = new javax.swing.JLabel();
        fileLabel = new javax.swing.JLabel();

        targetDat = new JTextField();
        decoyDat = new JTextField();
        userID = new JTextField();

        fileNameOut = new JTextField();

        anotherBlankSpace = new javax.swing.JLabel();

        jCheckBoxChargeFeatureType = new JCheckBox();
        jCheckBoxCreateNewDat  = new JCheckBox();
        jCheckBoxFiltering = new JCheckBox();
        jCheckBoxHighCharge = new JCheckBox();

        yetAnotherBlankSpace1 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();

        jCheckBoxCreateNewDat.setSelected(true);

        contentPanel1.setLayout(new java.awt.BorderLayout());

        welcomeTitle.setText("Please enter target and decoy log IDs:");
        contentPanel1.add(welcomeTitle, java.awt.BorderLayout.NORTH);

        jPanel1.setLayout(new java.awt.GridLayout(0, 1));

        jPanel1.add(blankSpace);

        targetLabel.setText("Mascot Search Log (Target):");
        jPanel1.add(targetLabel);
        jPanel1.add(targetDat);
        decoyLabel.setText("Mascot Search Log (Decoy):");
        jPanel1.add(decoyLabel);
        jPanel1.add(decoyDat);
        userLabel.setText("User ID:");
        jPanel1.add(userLabel);
        jPanel1.add(userID);
        fileLabel.setText("OutputFile Label (Optional):");
        jPanel1.add(fileLabel);
        jPanel1.add(fileNameOut);

        jPanel1.add(anotherBlankSpace);

        jCheckBoxFiltering.setText("No Filtering:");
        jPanel1.add(jCheckBoxFiltering);

        jCheckBoxHighCharge.setText("Use High Charge Fragments:");
        jPanel1.add(jCheckBoxHighCharge);

        jCheckBoxChargeFeatureType.setText("Type II Charge Feature:");
        jPanel1.add(jCheckBoxChargeFeatureType);

        jCheckBoxCreateNewDat.setText("Create New Dat File:");
        jPanel1.add(jCheckBoxCreateNewDat);

        jPanel1.add(yetAnotherBlankSpace1);

        contentPanel1.add(jPanel1, java.awt.BorderLayout.CENTER);

        jLabel1.setText("Note that the 'Next' button is disabled until you enter Log IDs in the boxes above.");
        contentPanel1.add(jLabel1, java.awt.BorderLayout.SOUTH);

        return contentPanel1;
    }

    private ImageIcon getImageIcon() {

        //  Icon to be placed in the upper right corner.

        return null;
    }

    public String getTarget() {
        return targetDat.getText();
    }

    public String getDecoy() {
        return decoyDat.getText();
    }

    public String getUser() {
        return userID.getText();
    }

    public String getName() {
        return fileNameOut.getText();
    }

    public boolean getFiltering() {
        return jCheckBoxFiltering.isSelected();
    }

    public boolean getHighCharge() {
        return jCheckBoxHighCharge.isSelected();
    }

    public boolean getTypeII() {
        return jCheckBoxChargeFeatureType.isSelected();
    }

    public boolean getNewDat() {
        return jCheckBoxCreateNewDat.isSelected();
    }
}

