package cli;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import core.DoMascotExport;
import util.MascotDatFiles;
import util.Config;
import util.FileUtils;
import queue.Job;
import exception.FileIOException;
import exception.ReturnValueException;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 * @author James Wright (jw13[at]sanger[dot]ac[dot]uk) 
 */
public class MascotPercolator {
    @Option(name = "-target", required = true, usage = Messages.DATFILE)
    private String targetS = null;
    @Option(name = "-decoy", required = true, usage = Messages.DECOYFILE)
    private String decoyS = null;
    @Option(name = "-out", required = true, usage = Messages.OUTFILES)
    private String outFilesName = null;
    @Option(name = "-validate", required = false, usage = Messages.EXPECTEDPEPS)
    private File stdPeps = null;
    @Option(name = "-rankdelta", required = false, usage = Messages.RANKDELTA)
    private double rankDelta = -1;
//@Option(name = "-protein", required = false, usage = Messages.PROTEIN)
    private boolean protSupport = false;
    @Option(name = "-xml", required = false, usage = Messages.XML)
    private boolean xml = false;
    @Option(name = "-newDat", required = false, usage = Messages.DATOUT)
    private boolean newDat = false;
    @Option(name = "-rt", required = false, usage = Messages.RT)
    private boolean rt = false;
    @Option(name = "-overwrite", required = false, usage = Messages.OVERWRITE)
    private boolean overwrite = false;
    @Option(name = "-features", required = false, usage = Messages.PERSISTFEATURES)
    private boolean persistFeatures = false;
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

    private final String executable = Config.properties.getString("executable");

    private Writer logStream = null;
    private File summaryFile;
    private boolean tempCopiesOfDatFiles = false;

    public static final String resultPostfix = ".tab.txt";
    public static final String decoyPostfix = "_decoy.tab.txt";
    public static final String logPostfix = ".log.txt";
    public static final String featuresPostfix = ".features.txt";
    public static final String datPostfix = ".datp";
    public static final String xmlPostfix = ".xml";

    public static void main(String[] args) throws IOException, ReturnValueException {
        MascotPercolator m2p = new MascotPercolator();
        CmdLineParser parser = new CmdLineParser(m2p);
        try {
            parser.setUsageWidth(120);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR);
        } catch (CmdLineException e) {
            System.err.println(
                Messages.AUTHOR + "\n" + Messages.DESCRIPTION);
            System.err.println("\nUsage info:\n" +
                "java -Xmx1g -cp MascotPercolator.jar cli.MascotPercolator [options ...]");
            System.err.println("\nOptions (replacing the \"[options ...]\" expression above):");
            parser.printUsage(System.err);
            System.err.println("\nError:");
            System.err.println(e.getMessage());
            System.err.println("");
            return;
        }

        m2p.setResultsFilePath();
        m2p.mascot2percolator();
    }

    private MascotPercolator() {
    }

    private void setResultsFilePath() throws FileIOException {
        //setup and input validation
        targetS = MascotDatFiles.getDatFileFromLogID(targetS).getAbsolutePath();
        decoyS = MascotDatFiles.getDatFileFromLogID(decoyS).getAbsolutePath();
    }

    public MascotPercolator(Job job, boolean datWasCopiedToNode, boolean overwrite) throws IOException, ReturnValueException {
        this.overwrite = overwrite;
        this.tempCopiesOfDatFiles = datWasCopiedToNode;
        if(datWasCopiedToNode) {
            this.targetS = getTempDatLocation(job.getTargetLogID());
            this.decoyS = getTempDatLocation(job.getDecoyLogID());
        } else {
            this.targetS = Integer.toString(job.getTargetLogID());
            this.decoyS = Integer.toString(job.getDecoyLogID());
            setResultsFilePath();
        }
        //input validation
        if (!(new File(targetS).exists() && new File(decoyS).exists())) {
            System.err.println(targetS + " or " + decoyS + " does not exist or cannot accessed.\nExecution failed.\n");
            throw new FileIOException(targetS + " or " + decoyS + " does not exist or cannot be accessed.\nExecution failed.\n");
        }

        this.outFilesName = job.getDir()+job.getResultPrefix();
        this.rankDelta = job.getRankDelta();
        this.protSupport = job.proteinFeature();
        this.rt = job.rt();
        this.newDat = job.hasDatFile();
        this.xml = job.hasXmlFile();
        this.persistFeatures = job.hasFeaturesPersisted();
        this.noFilter = job.hasNoFilterPersisted();
        this.highCharge = job.hasHighChargePersisted();
        this.chargeFeature = job.hasChargeFeatPersisted();
        this.aIon = job.hasAIonFeatPersisted();
    }


    public Summary mascot2percolator() throws IOException, ReturnValueException {
        //input validation
        String resultsFilePath = outFilesName + resultPostfix;
        summaryFile = new File(resultsFilePath);

        if (summaryFile.exists()) {
            if (!overwrite) {
                System.err.println(summaryFile.getAbsolutePath() + " already exists. " +
                    "Please specify a different output path.\nExecution failed.\n");
                throw new FileIOException(summaryFile.getAbsolutePath() + " already exists. Execution failed.");
            } else {
                summaryFile.delete();
                summaryFile.createNewFile();
            }
        }

        //lets start working ...
        File features = null;
        try {

            //create log and result file
            String xmlFilePath, newDatFilePath;
            File logFile, target, decoy;
            String decoyResults = outFilesName + decoyPostfix;
            try {
                logFile = new File(outFilesName + logPostfix);
                logStream = new FileWriter(logFile);
                xmlFilePath = null;
                if (xml) xmlFilePath = outFilesName + xmlPostfix;
                newDatFilePath = null;
                if (newDat) newDatFilePath = outFilesName + datPostfix;
                target = MascotDatFiles.getDatFileFromLogID(targetS);
                decoy = MascotDatFiles.getDatFileFromLogID(decoyS);
            } catch(IOException e) {
                System.err.println("\nDoes the directory exist ? Are permissions set correctly ?");
                throw e;
            }

            //create temp files
            try {
                if(persistFeatures) {
                    features = new File(outFilesName + featuresPostfix);
                } else {
                    features = File.createTempFile("percolator_features", ".tmp.txt", FileUtils.getTmpPercolatorDir());
                }
                System.out.println("features file created: " + features.getAbsolutePath());
            } catch (IOException e) {
                throw new FileIOException("Mascot Export failed: temporary file could not be created in this folder:\n " +
                    FileUtils.getTmpPercolatorDir() + "\n" +
                    "Do you have the permission to write to this folder ?", e);
            }

            //export data from mascot target file(s)
            DoMascotExport em = new DoMascotExport(target, decoy, features, rankDelta, protSupport, rt, showMods, noFilter, highCharge, chargeFeature, aIon, logStream);
            boolean retentionTimeAvailable = em.export();
            if(!retentionTimeAvailable) rt = false; //switch off RT in case its was not available from the data, so Percolator runs without the RT features

            //Run Percolator on the files generated before
            System.out.println("\nPercolator started ...");
            Writer summaryWriter = FileUtils.getPrintWriterFromFile(summaryFile);
            int returnValue = RunPercolator.execute(executable, rt, xmlFilePath, decoyResults, features.getAbsolutePath(), summaryWriter, logStream);
            if(returnValue != 0) {
                summaryFile.delete();
                throw new ReturnValueException("Percolator run failed. Return value: " + returnValue + "\nAlso check log file: " + outFilesName + logPostfix);
            }
            summaryWriter.flush();
            summaryWriter.close();
            if(!persistFeatures) features.delete();
            System.out.println("Percolator run finished; exit code: " + returnValue);

            if (stdPeps != null) {
                System.out.println("Starting FDR analysis; considering peptides from: " + stdPeps + " as correct ...");
                FDRAnalysis fdr = new FDRAnalysis();
                PrintStream pw = new PrintStream(new FileOutputStream(outFilesName + ".fdr.txt"));
                fdr.analyse(stdPeps, summaryFile, pw);
                pw.flush();
                pw.close();
                System.out.println("FDR done");
            }
            if(newDat) {
                System.out.println("Start generating new Mascot dat file with Mascot Percolator results ...");
                Percolator2MascotDat p2m;
                if(targetS.trim().equals(decoyS.trim())) {
                    p2m = new Percolator2MascotDat(summaryFile, decoyResults, targetS, new File(newDatFilePath));
                } else {
                    p2m = new Percolator2MascotDat(summaryFile, targetS, new File(newDatFilePath));
                }
                p2m.convert();
                System.out.println("New dat file generated");
            }
        } catch(IOException e) {
            if(logStream != null) e.printStackTrace(new PrintWriter(logStream));
            throw(e);
        } catch(ReturnValueException e) {
            if (logStream != null) e.printStackTrace(new PrintWriter(logStream));
            throw(e);
        } finally {
            if(tempCopiesOfDatFiles) {
                //extra level of validation to make sure these are temp files
                if(targetS.startsWith(FileUtils.getTmpPercolatorDir().getAbsolutePath())) {
                    System.out.println("deleting temporary copy of dat file: " + targetS);
                    new File(targetS).delete();
                }
                if (decoyS.startsWith(FileUtils.getTmpPercolatorDir().getAbsolutePath())) {
                    System.out.println("deleting temporary copy of dat file: " + decoyS);
                    new File(decoyS).delete();
                }
            }
            if(logStream != null) {
                logStream.flush();
                logStream.close();
            }
        }

        return new Summary(new File((outFilesName + logPostfix)));
    }

    public File getSummaryFile() {
        return summaryFile;
    }
    
    public static String getTempDatLocation(int datId) throws IOException {
        return FileUtils.getTmpPercolatorDir() + "/" + datId + ".dat";
    }

    //parse out some information from the log file. Not very robust, but right now the only way to get pi0 and number of IDs > 1% from Percolator.
    public class Summary {
        private String searchTitle = null;
        private int spectra = -1;
        private int psm = -1;
        private double pi0 = -1;

        private Pattern titlePattern = Pattern.compile("^search\\stitle\\s=(.*)");
        private Pattern spectraPattern = Pattern.compile("(\\d+) queries processed. Done.");
        private Pattern pi0Pattern = Pattern.compile("^Selecting pi_0=(\\d\\.\\d+)");
        private Pattern psmPattern = Pattern.compile("^New pi_0 estimate on merged list gives (\\d+) PSMs over q=0.01");

        boolean titleFound = false;
        boolean spectraFound = false;
        boolean pi0Found = false;            
        Summary(File logfile) {
            try {
                Scanner scanner = new Scanner(logfile);
                while(scanner.hasNextLine()) {
                    String line = scanner.nextLine();

                    if(!titleFound) {
                        Matcher m = titlePattern.matcher(line);
                        if(m.find()) {
                            searchTitle = m.group(1);
                            titleFound = true;
                        }
                    }

                    if(!spectraFound) {
                        Matcher m = spectraPattern.matcher(line);
                        if(m.find()) {
                            spectra = Integer.parseInt(m.group(1));
                            spectraFound = true;
                        }
                    }

                    if (!pi0Found) {
                        Matcher m = pi0Pattern.matcher(line);
                        if (m.find()) {
                            pi0 = Double.parseDouble(m.group(1));
                            pi0Found = true;
                        }
                    }

                    Matcher m = psmPattern.matcher(line);
                    if (m.find()) {
                        psm = Integer.parseInt(m.group(1));
                        scanner.close();
                        return;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }


        public String getSearchTitle() {
            return searchTitle;
        }

        public int getSpectra() {
            return spectra;
        }

        public int getPsm() {
            return psm;
        }

        public double getPi0() {
            return pi0;
        }
    }
}
