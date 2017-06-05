package cli;

import matrix_science.msparser.ms_mascotresults;
import core.MascotParser;
import core.DoMascotExport;
import util.MascotDatFiles;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

/**
 * 1. separate target and decoy
 * 2. same target and decoy - autodecoy
 * 3. concatenated target and decoy
 *
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class MascotROC {

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
        MascotROC mr = new MascotROC();
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

    private MascotROC() {
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
        System.out.println("p\tmit_fdr\tmit_target\tmit_decoy\tmht_fdr\tmht_target\tmht_decoy\trank_delta");
        int numQueries = target.getNumQueries();

        //even though this looks super inefficient, the Mascot Parser has cached the scores and performance is OK.
        int lastPEX = -100;
        for (double p = 9.3; p < 20; p += 0.1) {  // pex = 10 .. 1096, translating into prob = 0.1 ... 9E-4
            double pex = p + Math.exp(p - 9);     // pex = 10 .. 1096, translating into prob = 0.1 ... 9E-4
            int intPex = (int) pex;
            if(lastPEX == intPex) continue;
            else lastPEX = intPex;
            iterate(intPex, numQueries, target, decoy);
        }
    }

    private void iterate(int p, int numQueries, MascotParser target, MascotParser decoy) {
        //for the given probabiity, loop through result set and find all peptides > thresholds
        int tAboveMIT = 0;
        int tAboveMHT = 0;
        int dAboveMIT = 0;
        int dAboveMHT = 0;

        for (int queryN = 1; queryN <= numQueries; queryN++) {
            //get thresholds for target



            double mitT = target.getIdentityThreshold(queryN, p);
            double mhtT = target.getHomologyThreshold(queryN, p);
            if (mhtT <= 0) mhtT = mitT;
            //get thresholds for decoy
            double mitD = decoy.getIdentityThreshold(queryN, p);
            double mhtD = decoy.getHomologyThreshold(queryN, p);
            if (mhtD <= 0) mhtD = mitD;

            for (int rank = 1; rank <= target.getMaxRank(); rank++) {
                if(! DoMascotExport.hasQueryRank(query_rank, queryN, rank)) break;

                int pCharge     = target.getPeptide(queryN, rank).getCharge();
                double score    = target.getPeptide(queryN, rank).getIonsScore();



                if (chargeF == 0 || chargeF == pCharge || ( chargeF < 0 && (pCharge + chargeF) >= 0)){


                    //concatenated database search
                    if(concatenatedSearch) {
                     Set<String> proteinIDs = target.getProteinIDs(queryN, rank);
                     if (DoMascotExport.containsDecoyProteinID(proteinIDs)) {
                         if (score > mitT) dAboveMIT++;
                         if (score > mhtT) dAboveMHT++;
                     } else {
                         if (score > mitT) tAboveMIT++;
                         if (score > mhtT) tAboveMHT++;
                     }


                    //if separate target/decoy database search or auto decoy
                    } else {
                     //target
                     if (score > mitT) tAboveMIT++;
                     if (score > mhtT) tAboveMHT++;
                     //decoy
                     double decoyScore = decoy.getPeptide(queryN, rank).getIonsScore();
                     if (decoyScore > mitD) dAboveMIT++;
                     if (decoyScore > mhtD) dAboveMHT++;

                    }
                }
            }
        }

        double fdrMIT = concatenatedSearch ? 2.0*dAboveMIT/(double)(tAboveMIT+dAboveMIT) : (double)dAboveMIT/tAboveMIT;
        double fdrMHT = concatenatedSearch ? 2.0*dAboveMHT/(double)(tAboveMHT+dAboveMHT) : (double)dAboveMHT/tAboveMHT;

        StringBuffer sb = new StringBuffer();
        sb.append(1.0/p).append("\t")
            .append(fdrMIT).append("\t").append(tAboveMIT).append("\t").append(dAboveMIT).append("\t")
            .append(fdrMHT).append("\t").append(tAboveMHT).append("\t").append(dAboveMHT).append("\t").append(rankDelta);
        System.out.println(sb.toString());
    }
}

