package gui.Panels;

import com.nexes.wizard.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;


public class TestPanel2Descriptor extends WizardPanelDescriptor implements ActionListener {
    
    public static final String IDENTIFIER = "CONNECTOR_CHOOSE_PANEL";
    
    TestPanel2 panel2;
    
    public TestPanel2Descriptor() {
        
        panel2 = new TestPanel2();
        panel2.addCheckBoxActionListener(this);
        
        setPanelDescriptorIdentifier(IDENTIFIER);
        setPanelComponent(panel2);
        
    }
    
    public Object getNextPanelDescriptor() {
        return TestPanel3Descriptor.IDENTIFIER;
    }
    
    public Object getBackPanelDescriptor() {
        return TestPanel5Descriptor.IDENTIFIER;
    }
    
    
    public void aboutToDisplayPanel() {
        setNextButtonAccordingToCheckBox();
        System.out.println( ":2:" +  Main.mpParams.getTargetFile() + ":2:");
    }    

    public void actionPerformed(ActionEvent e) {
        setNextButtonAccordingToCheckBox();
    }
            
    
    private void setNextButtonAccordingToCheckBox() {
         if (panel2.isCheckBoxSelected())
            getWizard().setNextFinishButtonEnabled(true);
         else
            getWizard().setNextFinishButtonEnabled(false);           
    
    }
}
