package gui.Panels;

import com.nexes.wizard.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;


public class TestPanel4Descriptor extends WizardPanelDescriptor implements DocumentListener {

    public static final String IDENTIFIER = "MP_SET_PANEL";

    TestPanel4 panel4;

    public TestPanel4Descriptor() {

        panel4 = new TestPanel4();
        panel4.addTargetTextActionListener(this);
        panel4.addDecoyTextActionListener(this);
        panel4.addUserTextActionListener(this);

        setPanelDescriptorIdentifier(IDENTIFIER);
        setPanelComponent(panel4);

    }

    public Object getNextPanelDescriptor() {
        return TestPanel5Descriptor.IDENTIFIER;
    }

    public Object getBackPanelDescriptor() {
        return TestPanel1Descriptor.IDENTIFIER;
    }


    public void aboutToDisplayPanel() {
        setNextButtonAccordingToCheckBox();
    }

    public void aboutToHidePanel() {
        Main.mpParams.setTargetFile(panel4.getTarget());
        Main.mpParams.setDecoyFile(panel4.getDecoy());
        Main.mpParams.setUserID(panel4.getUser());
        Main.mpParams.setOutFileName(panel4.getName());
        Main.mpParams.setNoFiltering(panel4.getFiltering());
        Main.mpParams.setHighCharge(panel4.getHighCharge());
        Main.mpParams.setChargeTypeII(panel4.getTypeII());
        Main.mpParams.setNewDat(panel4.getNewDat());
        System.out.println("Updated mpParams.");
        System.out.println(  Main.mpParams.getTargetFile());
    }

    public void changedUpdate(DocumentEvent e) {
        setNextButtonAccordingToCheckBox();
    }

    public void removeUpdate(DocumentEvent e) {
        setNextButtonAccordingToCheckBox();
    }

    public void insertUpdate(DocumentEvent e) {
        setNextButtonAccordingToCheckBox();
    }

    private void setNextButtonAccordingToCheckBox() {
        if (panel4.areTextFieldsEntered())
            getWizard().setNextFinishButtonEnabled(true);
        else
            getWizard().setNextFinishButtonEnabled(false);

    }
}
