package cli;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import core.DoMascotExport;
import util.MascotDatFiles;


/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 * @author James Wright (jw13[at]sanger[dot]ac[dot]uk)
 */
public class MascotExport {

    @Option(name = "-target", required = true, usage = Messages.DATFILE)
    private String o_datS = null;
    @Option(name = "-decoy", required = true, usage = Messages.DECOYFILE)
    private String o_decoyS = null;
    @Option(name = "-f", required = true, usage = Messages.FEATUREFILE)
    private File o_features = null;
    @Option(name = "-log", required = true, usage = "Logfile name")
    private File plog = null;
    @Option(name = "-rankdelta", required = false, usage = Messages.RANKDELTA)
    private int rankDelta = 1;
//@Option(name = "-protein", required = false, usage = Messages.PROTEIN)
    private boolean protSupport = false;
    @Option(name = "-rt", required = false, usage = Messages.RT)
    private boolean rt = false;
    @Option(name = "-internal_1", required = false, usage = "Internal use only")
    private boolean showMods = false; //clashes with retention time prediction, since for this plain peptide sequence is required

    //Added for Version 1.17 (JW13)
    @Option(name = "-chargefeature", required = false, usage = Messages.CHARGEFEAT)
    private boolean chargeFeature = false;
    @Option(name = "-highcharge", required = false, usage = Messages.HIGHCHARGE)
    private boolean highCharge = false;
    @Option(name = "-nofilter", required = false, usage = Messages.NOFILTER)
    private boolean noFilter = false;
    //Added for Version 2.01
    @Option(name = "-a1Ion", required = false, usage = Messages.NOFILTER)
    private boolean aIon = false;

    public static void main(String[] args) throws IOException {
        MascotExport em = new MascotExport();
        CmdLineParser parser = new CmdLineParser(em);
        try {
            parser.setUsageWidth(150);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR + "\n");
        } catch (CmdLineException e) {
            System.err.println(Messages.AUTHOR + "\n" + Messages.DESCRIPTION);

            System.err.println("\nUsage:\n" +
                "java -Xmx512m -cp MascotPercolator.jar cli.MascotExport [options ...]");
            System.err.println("\nOptions (replacing the \"[options ...]\" expression above):");
            parser.printUsage(System.err);
            System.err.println("\nError:");
            System.err.println(e.getMessage());
            System.err.println("");
            return;
        }

        em.export();
        System.out.println("done !");
    }

    private MascotExport(){};

    private void export() throws IOException {
        FileWriter statusStream = new FileWriter(plog);

        File dat = MascotDatFiles.getDatFileFromLogID(o_datS);
        File decoy = MascotDatFiles.getDatFileFromLogID(o_decoyS);

        DoMascotExport export = new DoMascotExport(dat, decoy, o_features, rankDelta, protSupport, rt, showMods, noFilter, highCharge, chargeFeature, aIon, statusStream);
        export.export();
        statusStream.flush();
        statusStream.close();
    }
}
