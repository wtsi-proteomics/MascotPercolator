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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * List Mascot Hits at specified q-value threshold
 *
 * @author James Wright
 */
public class MascotList {

    @Option(name = "-target", required = true, usage = Messages.DATFILE)
    private String o_targetS = null;
    @Option(name = "-decoy", required = true, usage = Messages.DECOYFILE)
    private String o_decoyS = null;
    @Option(name = "-dList", required = false, usage = Messages.DECOYFILE)
    private boolean dlist = false;

    private boolean concatenatedSearch = false;
    private HashMap<Integer, Double> mhtQvalues = new HashMap<Integer, Double>();
    private HashMap<Integer, Double> mitQvalues = new HashMap<Integer, Double>();

    public static void main(String[] args) throws IOException {
        MascotList mr = new MascotList();
        CmdLineParser parser = new CmdLineParser(mr);
        try {
            parser.setUsageWidth(150);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR + "\n");
        } catch (CmdLineException e) {
            System.out.println("MascotList gets data for a list of Mascot Hits using the MIT and MHT with fixed q-value threshold");
            parser.printUsage(System.err);
        }
        mr.run();
    }

    private MascotList() {
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

        //prepare output
        System.out.println("query\tmht_best_fdr\tmit_best_fdr\tpeptide\tionscore\tmods\tmass\tcharge\tdeltascore\tspectrum\tintensity\tproteins\n");
        int numQueries = target.getNumQueries();


        for (int queryN = 1; queryN <= numQueries; queryN++) {
              mhtQvalues.put(queryN, 1.0);
              mitQvalues.put(queryN, 1.0);
        }

        //even though this looks super inefficient, the Mascot Parser has cached the scores and performance is OK.
        int lastPEX = -100;
        for (double p = 5.01; p < 30; p += 0.1) {  // pex = 10 .. 1096, translating into prob = 0.1 ... 9E-4
            double pex = p + Math.exp(p - 5);     // pex = 10 .. 1096, translating into prob = 0.1 ... 9E-4
            int intPex = (int) pex;
            if(lastPEX == intPex) continue;
            else lastPEX = intPex;
            iterate(intPex, numQueries, target, decoy);
        }

        if (dlist) { writeQueries(numQueries, decoy); }
        else { writeQueries(numQueries, target); }

    }

    private void iterate(int p, int numQueries, MascotParser target, MascotParser decoy) {
        //for the given probability, loop through result set and find all peptides > thresholds
        int tAboveMIT = 0;
        int tAboveMHT = 0;
        int dAboveMIT = 0;
        int dAboveMHT = 0;
        List<Integer> queryHitsMHT = new ArrayList<Integer>();
        List<Integer> queryHitsMIT = new ArrayList<Integer>();

        for (int queryN = 1; queryN <= numQueries; queryN++) {

            //get thresholds for target
            double mitT = target.getIdentityThreshold(queryN, p);
            double mhtT = target.getHomologyThreshold(queryN, p);
            if (mhtT <= 0) mhtT = mitT;

            //get thresholds for decoy
            double mitD = decoy.getIdentityThreshold(queryN, p);
            double mhtD = decoy.getHomologyThreshold(queryN, p);
            if (mhtD <= 0) mhtD = mitD;

            int rank    = 1;
            double score    = target.getPeptide(queryN, rank).getIonsScore();
                 //concatenated database search
                 if(concatenatedSearch) {
                    Set<String> proteinIDs = target.getProteinIDs(queryN, rank);
                    if (DoMascotExport.containsDecoyProteinID(proteinIDs)) {
                         if (score > mitT) dAboveMIT++;
                         if (score > mhtT) dAboveMHT++;
                    } else {
                         if (score > mitT) { tAboveMIT++; queryHitsMIT.add(queryN);  }
                         if (score > mhtT) { tAboveMHT++; queryHitsMHT.add(queryN);  }
                    }

                //if separate target/decoy database search or auto decoy
                } else {
                     //target
                     if (score > mitT) { tAboveMIT++; if (! dlist) { queryHitsMIT.add(queryN); } }
                     if (score > mhtT) { tAboveMHT++; if (! dlist) { queryHitsMHT.add(queryN); } }
                     //decoy
                     double decoyScore = decoy.getPeptide(queryN, rank).getIonsScore();
                     if (decoyScore > mitD) { dAboveMIT++;  if (dlist) { queryHitsMIT.add(queryN); } }
                     if (decoyScore > mhtD) { dAboveMHT++;  if (dlist) { queryHitsMHT.add(queryN); } }
                }
        }

        double qMIT = concatenatedSearch ? 2.0*dAboveMIT/(double)(tAboveMIT+dAboveMIT) : (double)dAboveMIT/tAboveMIT;
        double qMHT = concatenatedSearch ? 2.0*dAboveMHT/(double)(tAboveMHT+dAboveMHT) : (double)dAboveMHT/tAboveMHT;

        for (Integer query : queryHitsMHT){
            if ( mhtQvalues.get(query) > qMHT ){  mhtQvalues.put(query, qMHT); }
        }
        for (Integer query : queryHitsMIT){
            if ( mitQvalues.get(query) > qMIT ){  mitQvalues.put(query, qMIT); }
        }

    }

     private void writeQueries(int numQueries, MascotParser target) {
     // Write list of queries and best q-value
        for (int queryN = 1; queryN <= numQueries; queryN++) {
            int rank        = 1;
            double score    = target.getPeptide(queryN, rank).getIonsScore();
            String pepSeq   = target.getPeptide(queryN, rank).getPeptideStr();
            String pepMod   = target.getPeptideMods(queryN, rank);
            double dScore   = score - target.getPeptide(queryN, 2).getIonsScore();
            double pepMass  = target.getPeptide(queryN, rank).getMrCalc();
            int pepCharge   = target.getPeptide(queryN, rank).getCharge();
            String spectrum = target.getQueryTitle(queryN);
            double pepInt   = target.getPeptideIntensity(queryN);
            String proteins = "";

            Set<String> proteinIDs = target.getProteinIDs(queryN, rank);
            for(String proteinID : proteinIDs) {
               if (! proteins.isEmpty()){ proteins += ","; }
               proteins = proteins.concat(proteinID);
            }

            StringBuffer sb = new StringBuffer();
            sb.append(queryN).append("\t").append(mhtQvalues.get(queryN)).append("\t").append(mitQvalues.get(queryN)).append("\t").append(pepSeq).append("\t")
            .append(score).append("\t").append(pepMod).append("\t").append(pepMass).append("\t").append(pepCharge).append("\t").append(dScore).append("\t").append(spectrum).append("\t").append(pepInt).append("\t").append(proteins);
            System.out.println(sb.toString());
        }
     }
}

