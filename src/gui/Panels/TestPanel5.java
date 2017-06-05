package gui.Panels;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.net.URL;


public class TestPanel5 extends JPanel {

    private JLabel blankSpace;
    private JLabel jLabel1;
    private JLabel jLabel2;
    private JLabel jLabel3;
    private JLabel jLabel4;
    private JLabel jLabel5;
    private JLabel jLabel6;
    private JLabel jLabel7;
    private JLabel jLabel8;
    private JLabel jLabel9;
    private JLabel jLabel10;

    private JLabel welcomeTitle;
    private JPanel contentPanel;

    private JLabel iconLabel;
    private ImageIcon icon;

    public TestPanel5() {

        iconLabel = new JLabel();
        contentPanel = getContentPanel();
        contentPanel.setBorder(new EmptyBorder(new Insets(10, 10, 10, 10)));

        icon = getImageIcon();

        setLayout(new java.awt.BorderLayout());

        if (icon != null)
            iconLabel.setIcon(icon);

        iconLabel.setBorder(new EtchedBorder(EtchedBorder.RAISED));

        add(iconLabel, BorderLayout.WEST);

        JPanel secondaryPanel = new JPanel();
        secondaryPanel.add(contentPanel, BorderLayout.NORTH);
        add(secondaryPanel, BorderLayout.CENTER);
    }


    public void updatePanel(){
        jLabel1.setText("Target Dat File: " + Main.mpParams.getTargetFile());
        jLabel2.setText("Decoy Dat File: " + Main.mpParams.getDecoyFile());
        jLabel3.setText("User: " + Main.mpParams.getUserID());
        jLabel10.setText("Optional Label: " + Main.mpParams.getOutFileName());
        jLabel4.setText("Filter: " + Main.mpParams.isNoFiltering());
        jLabel5.setText("High Charge Fragments: " + Main.mpParams.isHighCharge());
        jLabel7.setText("Type II Charge Feature: " + Main.mpParams.isChargeTypeII());
        jLabel6.setText("New Dat file: " + Main.mpParams.isNewDat());
    }

    private JPanel getContentPanel() {

        JPanel contentPanel1 = new JPanel();
        JPanel jPanel1 = new JPanel();

        welcomeTitle = new JLabel();
        blankSpace = new JLabel();
        jLabel2 = new JLabel();
        jLabel1 = new JLabel();
        jLabel3 = new JLabel();
        jLabel4 = new JLabel();
        jLabel5 = new JLabel();
        jLabel7 = new JLabel();
        jLabel6 = new JLabel();
        jLabel8 = new JLabel();
        jLabel9 = new JLabel();

        jLabel10 = new JLabel();

        contentPanel1.setLayout(new java.awt.BorderLayout());

        welcomeTitle.setFont(new java.awt.Font("MS Sans Serif", Font.BOLD, 11));
        welcomeTitle.setText("These are the params with which you have chosen to run Mascot Percolator");
        contentPanel1.add(welcomeTitle, java.awt.BorderLayout.NORTH);

        jPanel1.setLayout(new java.awt.GridLayout(0, 1));

        jPanel1.add(blankSpace);
        System.out.println( ":NC:" +  Main.mpParams.getTargetFile() + ":NC:");
        jLabel1.setText("Target Dat File: " + Main.mpParams.getTargetFile());
        jPanel1.add(jLabel1);
        jLabel2.setText("Decoy Dat File: " + Main.mpParams.getDecoyFile());
        jPanel1.add(jLabel2);
        jLabel3.setText("User: " + Main.mpParams.getUserID());
        jPanel1.add(jLabel3);
        jLabel10.setText("Optional Label: " + Main.mpParams.getOutFileName());
        jPanel1.add(jLabel10);
        jLabel4.setText("Filter: " + Main.mpParams.isNoFiltering());
        jPanel1.add(jLabel4);
        jLabel5.setText("High Charge Fragments: " + Main.mpParams.isHighCharge());
        jPanel1.add(jLabel5);
        jLabel7.setText("Type II Charge Feature: " + Main.mpParams.isChargeTypeII());
        jPanel1.add(jLabel7);
        jLabel6.setText("New Dat file: " + Main.mpParams.isNewDat());
        jPanel1.add(jLabel6);
        jPanel1.add(jLabel8);
        jLabel9.setText("Press the 'Next' button to continue....");
        jPanel1.add(jLabel9);

        contentPanel1.add(jPanel1, java.awt.BorderLayout.CENTER);

        return contentPanel1;

    }

    private ImageIcon getImageIcon() {
        return new ImageIcon((URL)getResource("clouds.jpg"));
    }

    private Object getResource(String key) {

        URL url = null;
        String name = key;

        if (name != null) {

            try {
                Class c = Class.forName("com.nexes.test.Main");
                url = c.getResource(name);
            } catch (ClassNotFoundException cnfe) {
                System.err.println("Unable to find Main class");
            }
            return url;
        } else
            return null;

    }

}
