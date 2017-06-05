package queue;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;
import cli.Messages;
import cli.MascotPercolator;

import java.io.File;
import java.io.IOException;

import gnu.cajo.utils.extra.TransparentItemProxy;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 * @author James Wright (jw13[at]sanger[dot]ac[dot]uk) 
 */
public class SubmitJob {
    @Option(name = "-target", required = true, usage = Messages.DATID)
    private int targetID;
    @Option(name = "-decoy", required = true, usage = Messages.DECOYID)
    private int decoyID;
    @Option(name = "-out", required = true, usage = Messages.OUTFILES)
    private String outFilesName = null;
    @Option(name = "-rankdelta", required = false, usage = Messages.RANKDELTA)
    private int rankDelta = 1;
    @Option(name = "-xml", required = false, usage = Messages.XML)
    private boolean xml = false;
    @Option(name = "-newDat", required = false, usage = Messages.DATOUT)
    private boolean newDat = false;
    @Option(name = "-rt", required = false, usage = Messages.RT)
    private boolean rt = false;
    @Option(name = "-server", required = true, usage = "Server host name, where Mascot Percolator queue is running")
    private String ip = null;
    @Option(name = "-serverPort", required = false, usage = "(optional) Port of Mascot Percolator queue server")
    private int port = 1198;
    @Option(name = "-desc", required = false, usage = "Data description; otherwise the original search title is used")
    private String desc = "";
    @Option(name = "-user", required = true, usage = "User or project description, e.g. mark")
    private String user = "";
    @Option(name = "-features", required = false, usage = Messages.PERSISTFEATURES)
    private boolean persistFeatures = false;

    //Added for Version 1.17 (JW13)
    @Option(name = "-chargefeat", required = false, usage = Messages.CHARGEFEAT)
    private boolean chargeFeature = false;
    @Option(name = "-highcharge", required = false, usage = Messages.HIGHCHARGE)
    private boolean highCharge = false;
    @Option(name = "-nofilter", required = false, usage = Messages.NOFILTER)
    private boolean noFilter = false;
    //Added for Version 2.01
    @Option(name = "-a1Ion", required = false, usage = Messages.NOFILTER)
    private boolean aIon = false;

    public static void main(String[] args) throws Exception {

        SubmitJob sj = new SubmitJob();
        CmdLineParser parser = new CmdLineParser(sj);
        try {
            parser.setUsageWidth(120);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR);
        } catch (CmdLineException e) {
            System.err.println(
                Messages.AUTHOR + "\n" + Messages.DESCRIPTION);
            System.err.println("\nUsage info:\n" +
                "java -Xmx512m -cp MascotPercolator.jar queue.SubmitJob [options ...]");
            System.err.println("\nOptions (replacing the \"[options ...]\" expression above):");
            parser.printUsage(System.err);
            System.err.println("\nError:");
            System.err.println(e.getMessage());
            System.err.println("");
            return;
        }

        sj.submit();
    }

    private void submit() {
        //input validation
        String resultsFilePath = outFilesName + MascotPercolator.resultPostfix;
        File summaryFile = new File(resultsFilePath);
        if (summaryFile.exists()) {
            System.err.println(summaryFile.getAbsolutePath() + " already exists. \n" +
                "Specify a different file path.\nExecution failed.\n");
            System.exit(-1);
        }

        //create job
        String path = summaryFile.getAbsolutePath();
        String name = summaryFile.getName();
        path = path.split(name)[0];
        try {
            name = name.split(MascotPercolator.resultPostfix)[0];
        } catch(ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            System.err.println("Likely you have only specified the output director but not the actual output file");
            System.exit(-1);
        }
        Job job = new Job(targetID, decoyID, user, desc, rankDelta, false, rt, path, name, xml, newDat, persistFeatures, noFilter, highCharge, chargeFeature, aIon);

        //connect to server
        ServerI q = null;
        try {
            q = (ServerI) TransparentItemProxy.getItem(
                "//" + ip + ":" + port + "/" + Server.service, new Class[]{ServerI.class}
            );
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("\nIt seems that no queue server is running ... \n" +
                "Try 'java -jar MascotPercolator.jar queue.Server' for usage information.\n");
            System.exit(-1);
        }

        //submit job and do some more input validation
        Server.RECEIVERSTATUS status = null;
        try {
            status = q.submitJob(job);
        } catch (IOException e) {
            System.out.println("Job was cancelled; Exception thrown on server:");
            e.printStackTrace();
        }
        
        switch(status) {
            case FILENOTFOUND:
                System.out.println("Job was cancelled; File for log ID was not found.\n" +
                    "Check log ID or your config file that defines the Mascot dat file directory.\n");
                break;
            case RESULTSEXISTALREADY:
                System.out.println("Job was cancelled; Another job uses " + path + name + " already\n");
                break;
            case SUCCESS:
                System.out.println("Job was successfully submitted to Mascot Percolator Queue ...\n");
                break;
        }
    }
}
