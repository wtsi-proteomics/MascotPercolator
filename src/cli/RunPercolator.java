package cli;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import java.io.*;

import util.Config;
import util.RunCommandLine;
import util.FileUtils;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 * @author James Wright (jw13[at]sanger[dot]ac[dot]uk) 
 */
public class RunPercolator {

    @Option(name = "-f", required = true, usage = Messages.FEATUREFILEREAD)
    private File f = null;
    @Option(name = "-i", required = false, usage = Messages.OUTFILES)
    private String outFilesName = null;
    @Option(name = "-x", required = false, usage = Messages.XML)
    private String xml = null;
    @Option(name = "-rt", required = true, usage = Messages.RT)
    private boolean rt = false;

    private final static String executable = Config.properties.getString("executable");
    public final static String config_default_feature = "config.default.feature";

    public static void main(String[] args) throws IOException {
        RunPercolator rp = new RunPercolator();
        CmdLineParser parser = new CmdLineParser(rp);
        try {
            parser.setUsageWidth(130);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR + "\n");
        } catch (CmdLineException e) {
            System.err.println(Messages.AUTHOR + "\nDescription:\n" +
                "RunPercolator is directly interfacing Percolator.\n" +
                "Percolator is trained by the feature and label files generated e.g. by an earlier MascotExport run.");
            System.err.println("\nUsage:\n" +
                "java -Xmx512m -cp MascotPercolator.jar cli.RunPercolator [options ...]");
            System.err.println("\nOptions (replacing the \"[options ...]\" expression above):");
            parser.printUsage(System.err);
            System.err.println("\nError:");
            System.err.println(e.getMessage());
            System.err.println("");
            return;
        }
        rp.execute();
    }

    private int execute() throws IOException {
        File scoreFile = new File(outFilesName + MascotPercolator.resultPostfix);
        Writer scoreWriter = FileUtils.getPrintWriterFromFile(scoreFile);
        Writer logWriter = FileUtils.getPrintWriterFromFile(new File(outFilesName + MascotPercolator.logPostfix));

        int ret = execute(executable, rt, xml, outFilesName+MascotPercolator.decoyPostfix, f.getAbsolutePath(), scoreWriter, logWriter);
        if (ret != 0) {
            scoreFile.delete();
        }
        scoreWriter.flush();
        scoreWriter.close();
        logWriter.flush();
        logWriter.close();
        return ret;
    }

    public static int execute(String percolatorPath, boolean retentionTime, String xml, String decoyResults, String features,
                              Writer stdoutStream, Writer percolatorLogStream) throws IOException {

        if(! new File(config_default_feature).exists())
            throw new IOException(config_default_feature + " file not found; required from version 1.08 onwards. Copy from the unzipped Mascot Percolator download.");

        String dFlag = "";
        if(retentionTime) dFlag = " -D ";

        String xmlFlag = "";
        if(xml!=null && xml.trim().length() > 0) xmlFlag = " -X " + xml + " ";

        String decoyResultsFlag = "";
        if(decoyResults!=null && decoyResults.trim().length() > 0) decoyResultsFlag = " -B " + decoyResults + " ";

        String cmd = percolatorPath + " -W " + config_default_feature + " -j " + features + xmlFlag + decoyResultsFlag + dFlag;
        return RunCommandLine.execute(cmd, stdoutStream, percolatorLogStream);
    }
}
