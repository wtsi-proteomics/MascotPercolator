package cli;

import core.DoMascotExport;
import core.MascotParser;
import matrix_science.msparser.ms_mascotresults;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import util.MascotDatFiles;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * 1. separate target and decoy
 * 2. same target and decoy - autodecoy
 * 3. concatenated target and decoy
 *
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class MascotJROC {

    @Option(name = "-target", required = true, usage = Messages.DATFILE)
    private String o_targetS = null;
    @Option(name = "-decoy", required = true, usage = Messages.DECOYFILE)
    private String o_decoyS = null;
    @Option(name = "-rankdelta", required = true, usage = Messages.RANKDELTA)
    private int rankDelta = 1;
    @Option(name = "-charge", required = false, usage = Messages.CHARGE)
    private int chargeF = 0;

    private boolean concatenatedSearch = false;
    private Set<String> query_rank = new HashSet<String>(50000);

    public static void main(String[] args) throws IOException {
        MascotJROC mr = new MascotJROC();
        CmdLineParser parser = new CmdLineParser(mr);
        try {
            parser.setUsageWidth(150);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR + "\n");
        } catch (CmdLineException e) {
            System.out.println("MascotROC gets data for a ROC curve using the MIT and MHT with varying probabilities");
            parser.printUsage(System.err);
        }
        mr.run();
    }

    private MascotJROC() {
    }

    private void run() throws IOException {
        //set target-decoy mode
        File datT = MascotDatFiles.getDatFileFromLogID(o_targetS);
        File datD = MascotDatFiles.getDatFileFromLogID(o_decoyS);

        MascotParser target = new MascotParser(datT.getAbsolutePath(), ms_mascotresults.MSRES_GROUP_PROTEINS | ms_mascotresults.MSRES_SHOW_SUBSETS, true);
        MascotParser decoy;
        if (o_decoyS.equals(o_targetS) ) {
            if (target.hasDecoy()) {
                System.err.println("Auto Target/Decoy search mode");
                decoy = new MascotParser(datD.getAbsolutePath(),
                    ms_mascotresults.MSRES_DECOY | ms_mascotresults.MSRES_GROUP_PROTEINS | ms_mascotresults.MSRES_SHOW_SUBSETS, false);
            } else {
                System.err.println("Concatenated Target/Decoy search mode");
                concatenatedSearch = true;
                decoy = target;
            }
        } else {
            System.err.println("Separate Target/Decoy search mode");
            decoy = new MascotParser(datD.getAbsolutePath(),
                ms_mascotresults.MSRES_GROUP_PROTEINS | ms_mascotresults.MSRES_SHOW_SUBSETS, true);
        }

        query_rank.addAll(DoMascotExport.findRanksToExport(target, 13, rankDelta));
        query_rank.addAll(DoMascotExport.findRanksToExport(decoy, 13, rankDelta));

        //prepare output

        int numQueries = target.getNumQueries();
        iterate(numQueries, target);
        iterate(numQueries, decoy);
    }

    private void iterate(int numQueries, MascotParser set) {
        System.out.println("PSM\tscore\tp");
        for (int queryN = 1; queryN <= numQueries; queryN++) {
            for (int rank = 1; rank <= set.getMaxRank(); rank++) {
                if(! DoMascotExport.hasQueryRank(query_rank, queryN, rank)) break;
                double score    = set.getPeptide(queryN, rank).getIonsScore();
                double p = set.getPValue(score, queryN);
                System.out.println( queryN + "_" + rank + "\t" + score + "\t" + p );
                System.out.println();
            }
        }
    }


}