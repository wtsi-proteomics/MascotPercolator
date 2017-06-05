package cli;

/**
 * The default class to be run if the jar bundle is run as: java -jar MascotPercolator.jar
 *
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 * @author James Wright (jw13[at]sanger[dot]ac[dot]uk) 
 */
public class Messages {

    public static void main(String[] args) {
        System.out.println(AUTHOR + "\nUsage:");
        System.out.println("java -Xmx1g -cp MascotPercolator.jar cli.MascotPercolator");
        System.out.println("This extracts the Mascot search results, runs Mascot Percolator and writes result files.");
        System.out.println("\nEdit config.properties to enable/disable features that are used for Percolator training.\n" +
            "However, it is recommend to leave them at the tested default settings.\n");
    }

    public final static String AUTHOR = "\nMascot Percolator v2.00\n" + 
        "Created by Markus Brosch and James Wright at the Wellcome Trust Sanger Institute\n"+
        "Documentation: http://www.sanger.ac.uk/Software/analysis/MascotPercolator/\n" +
        "\nPlease cite:\n" +
        "- KŠll, L., Canterbury, J. D., Weston, J., Noble, W. S., and MacCoss, M. J. (2007) Semi-supervised learning for peptide identification from shotgun proteomics datasets. Nat Methods, 4(11), 923-925.\n" +
        "- Brosch, M., Yu, L., Hubbard, T., and Choudhary, J. (2009) Accurate and Sensitive Peptide Identification with Mascot Percolator. J Proteome Res, 8(6), 3176-3181.\n";

    public final static String DESCRIPTION = "\nDescription:\n" +
       "Mascot Percolator extracts all necessary data from the Mascot dat file(s),\ntrains Percolator\nand writes the results to the specified summary file.\n\n" +
       "Mascot Percolator requires a target and a separate decoy search. There are two ways to achieve this with Mascot:\n" +
       "1. Either a Mascot search is performed with the Mascot auto-decoy option enabled. In this case, the \"-target\" and \"-decoy\" parameter refer to the same logID or results file.\n" +
       "2. If however, the search is performed without having the decoy option enabled, a second Mascot search against a decoy database with identical search parameters needs to be performed. The \"-target\" and \"-decoy\" parameters are set accordingly. ";
    
    //input
    public final static String ITERATIONS = "(optional) Percolator parameter: number of training iterations; default i = 10.";
    public final static int ITERATIONSVALUE = 10;

    //mascot
    public final static String DATFILE = "(required) Log ID or path/file name of the Mascot target results dat file";
    public final static String DATID = "(required) Log ID of the Mascot target results dat file";
    public final static String DECOYFILE = "(required) Log ID or path/file name of the Mascot decoy results dat file. Note: if Mascot's 'auto-decoy' mode was used, use same logID/file as for the target parameter.";
    public final static String DECOYID = "(required) Log ID of the Mascot decoy results dat file. Note: if Mascot's 'auto-decoy' mode was used, use same logID as for the target parameter.";

    //input gist
    public final static String FEATUREFILE = "(required) Path and file name to write tab delimited features file";

    //output
    public final static String OUTFILES = "(required) Results path and file name (without extension)";
    public final static String XML = "(optional) Write supplemental XML output as defined here: http://noble.gs.washington.edu/proj/percolator/model/percolator_out.xsd";
    public final static String DATOUT = "(optional) Write a new Mascot dat file with Percolator results & scores (MascotScore = -10log10(PEP); resets Mascot thresholds)";
    public final static String OVERWRITE = "(optional) Given result files already exist, this option forces overwrite";
    public final static String PERSISTFEATURES = "(optional) Save the training data e.g. for debugging purposes";


    //fdr stuff
    public final static String EXPECTEDPEPS = "(optional) File with a list of correct peptides/proteins " +
        "(sequences simply concatenated or alternatively one sequence per line without identifiers)";
    public final static String SUMMARYFILEINPUT = "(required) Reads Mascot Percolator results from the specified summary file";
    public final static String DECOYSUMMARYFILEINPUT = "(optional) Reads Mascot Percolator decoy results from the specified summary file";

    //read only from percolator generated files
    public final static String FEATUREFILEREAD = "(required) Path and file name of tab delimited features file";
    public final static String LABELFILEREAD = "(required) Path and file name of tab delimited labels file";
    public final static String SUPPLEMENTFILEREAD = "(required) Path and file name of tab delimited supplement info file";
    public final static String SCOREFILEREAD = "(required) Path and file name of tab delimited percolator results (score) file";


    //other bits and pieces
    public final static String VERBOSE = "(optional) Show all features used for training in summary results file";
    public final static String RANKDELTA = "(optional) Maximum allowed Mascot score difference of peptide hit ranks 2..10 and top hit match. Default = -1. A setting of -1 only strictly reports the top hit match.";
    public final static String PROTEIN = "(optional) Enable protein feature that scores peptides matching the same protein higher; default off";
    public final static String RT = "(optional) Enables retention time; will only be switched on when available from input data; default off";
    public final static String SHOWDECOY = "(optional) Summary results file appends all decoy hits, e.g. if necessary for validation";
    public final static String CHARGE = "(optional) Charge filter only calculates results for specified charge. A negative charge state will consider everything equal or above charge state";
    public final static String THRESHOLD = "Qvalue Threshoold for Listing Mascot MHT and MIT results";
    public final static String HIGHCHARGE = "(optional) Calculate Higher Charge Fragment Ions";
    public final static String NOFILTER = "(optional) Do not filter away spectra with low numbers of fragment peaks.";
    public final static String CHARGEFEAT = "(optional) Use single charge feature rather than separate 1,2,3 and 4+ features.";
    public final static String AIONS = "(optional)Use Special a1 Ion feature. For HCD Data";


}
