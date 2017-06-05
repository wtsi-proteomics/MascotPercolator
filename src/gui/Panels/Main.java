package gui.Panels;

import com.nexes.wizard.*;

import javax.swing.*;

public class Main {

    public static MascotPercParams mpParams;

    public static void main(String[] args) {

        mpParams = new MascotPercParams();

        Wizard wizard = new Wizard();
        wizard.getDialog().setTitle("Test Wizard Dialog");
        
        WizardPanelDescriptor descriptor1 = new TestPanel1Descriptor();
        wizard.registerWizardPanel(TestPanel1Descriptor.IDENTIFIER, descriptor1);

        WizardPanelDescriptor descriptor2 = new TestPanel2Descriptor();
        wizard.registerWizardPanel(TestPanel2Descriptor.IDENTIFIER, descriptor2);

        WizardPanelDescriptor descriptor3 = new TestPanel3Descriptor();
        wizard.registerWizardPanel(TestPanel3Descriptor.IDENTIFIER, descriptor3);

        WizardPanelDescriptor descriptor4 = new TestPanel4Descriptor();
        wizard.registerWizardPanel(TestPanel4Descriptor.IDENTIFIER, descriptor4);

        WizardPanelDescriptor descriptor5 = new TestPanel5Descriptor();
        wizard.registerWizardPanel(TestPanel5Descriptor.IDENTIFIER, descriptor5);
        
        wizard.setCurrentPanel(TestPanel1Descriptor.IDENTIFIER);
        
        int ret = wizard.showModalDialog();
        
        System.out.println("Dialog return code is (0=Finish,1=Cancel,2=Error): " + ret);
        System.out.println("Second panel selection is: " + 
            (((TestPanel2)descriptor2.getPanelComponent()).getRadioButtonSelected()));
        
        System.exit(0);
        
    }
    
}
