package gui.Panels;

import com.nexes.wizard.WizardPanelDescriptor;

/**
 * Created with IntelliJ IDEA.
 * User: jw13
 * Date: 28/06/12
 * Time: 13:24
 * To change this template use File | Settings | File Templates.
 */
public class TestPanel5Descriptor extends WizardPanelDescriptor {

    TestPanel5 panel5;

    public static final String IDENTIFIER = "MP_PARAMS_VALIDATE";

    public TestPanel5Descriptor() {
        panel5 = new TestPanel5();
        setPanelDescriptorIdentifier(IDENTIFIER);
        setPanelComponent(panel5);
    }

    public Object getNextPanelDescriptor() {
        return TestPanel2Descriptor.IDENTIFIER;
    }

    public Object getBackPanelDescriptor() {
        return TestPanel4Descriptor.IDENTIFIER;
    }

    public void aboutToDisplayPanel() {
        panel5.updatePanel();
        System.out.println( "::" +  Main.mpParams.getTargetFile() + "::");
    }


}
