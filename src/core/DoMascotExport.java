package core;

import matrix_science.msparser.*;

import java.io.*;
import java.util.*;

import util.Config;
import util.LoadMascotLibrary;
import exception.FileIOException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 * @author James Wright (jw13[at]sanger[dot]ac[dot]uk) 
 */
public class DoMascotExport {

    //input files
    private File o_target = null;
    private File o_decoy = null;

    //output files
    private File o_features = null;
    private FileWriter featuresWriter;

    //parameters
    private double c_rankDelta = 1;

    private boolean c_retentiontime;
    private boolean c_proteinSupport;
    private boolean c_showMods;
    private boolean c_noFilter;
    private boolean c_highCharge;
    private boolean c_chargeFeature; 
    private boolean c_aIonFeature;

    //basic features
    private final boolean c_amt = false;
    private final boolean c_massMr = Config.properties.getBoolean("massMr");
    private final boolean c_charge = Config.properties.getBoolean("charge");
    private boolean c_mScore = true; // always on; used for initial learning iteration
    private final boolean c_deltaScore = Config.properties.getBoolean("deltaScore");
    private final boolean c_deltaM = Config.properties.getBoolean("deltaM");
    private final boolean c_absdM = Config.properties.getBoolean("absDeltaM");
    private final boolean c_isodM = Config.properties.getBoolean("isoDeltaM");
    private final boolean c_mc = Config.properties.getBoolean("missedCleavages");
    private final boolean c_varmods = Config.properties.getBoolean("relVariableModsCount");

    //extended features
    private final boolean c_totInt = Config.properties.getBoolean("totIntensity");
    private final boolean c_fragMassErr = Config.properties.getBoolean("fragMassError");
    private final boolean c_intMatchedTot = Config.properties.getBoolean("intMatchedTot");
    private final boolean c_relIntMatchedTot = Config.properties.getBoolean("relIntMatchedTot");
    private final boolean c_fracIonsMatched = Config.properties.getBoolean("fracIonsMatched");
    private final boolean c_matchedIntensity = Config.properties.getBoolean("matchedIntensity");
    private final boolean c_immonium = false;

    public final static int percolatorTargetLabel = 1;
    public final static int percolatorDecoyLabel = -1;

    //average MS2 mass error: median & IQR
    private List<Double> avgMS2MassErrorMedian = new ArrayList<Double>();
    private List<Double> avgMS2MassErrorMedianPPM = new ArrayList<Double>();
    private List<Double> avgMS2MassErrorIQR = new ArrayList<Double>();
    private List<Double> avgMS2MassErrorIQRPPM = new ArrayList<Double>();

    private MascotParser parser;
    private HashSet<String> query_rank = new HashSet<String>(50000);
    private PrintWriter out = new PrintWriter(System.out, true);

    private int maxFragCharge = 2;

    public final static String TOTALSPECTRALABEL = " queries processed. Done.";

    static {
        new LoadMascotLibrary();
    }

    StringBuffer mysb = new StringBuffer();

    public DoMascotExport(File targetDat, File decoyDat, File featuresTab, double rankDelta, boolean proteinSupport, boolean rtFeatures, boolean showMods, boolean noFilter, boolean highCharge, boolean chargeFeature, boolean aIonFeature,  Writer out) {
        this.o_target = targetDat;
        this.o_decoy = decoyDat;

        this.o_features = featuresTab;

        this.c_rankDelta = rankDelta;
        this.c_proteinSupport = proteinSupport;
        this.c_retentiontime = rtFeatures;
        this.c_showMods = showMods;
        this.c_noFilter = noFilter;
        this.c_highCharge = highCharge;
        this.c_chargeFeature = chargeFeature;
        this.c_aIonFeature = aIonFeature;

        this.out = new PrintWriter(out, true);
        if(c_amt) c_mScore = false;
    }

    public boolean export() throws IOException {
        long t1 = System.currentTimeMillis();
        featuresWriter = new FileWriter(o_features);

        out.println("\nreading file: " + o_target);
        System.out.println("\nreading file: " + o_target);

        int resultMode = ms_mascotresults.MSRES_GROUP_PROTEINS | ms_mascotresults.MSRES_SHOW_SUBSETS;
        int resultModeDecoy = ms_mascotresults.MSRES_DECOY | ms_mascotresults.MSRES_GROUP_PROTEINS | ms_mascotresults.MSRES_SHOW_SUBSETS;

        MascotParser target = null;
        MascotParser decoy = null;

        //setup target run

        out.println("setup target processing ...");
        System.out.println("setup target processing ...");
        target = new MascotParser(o_target.getAbsolutePath(), resultMode, true);
        String searchTitle = target.getSearchParams().getCOM();
        out.println("search title = " + searchTitle);
        System.out.println("search title = " + searchTitle);

        //test whether retention time is available.

        if (c_retentiontime) {
            for(int q = 1; q<target.getNumQueries(); q++) {
                if(target.getPeptide(q, 1).getIonsScore() > 0) {
                    if(target.getQuery(q).getRetentionTimes().trim().equals("")) {
                        c_retentiontime = false;
                        System.out.println("Retention time information not available. Retention time feature disabled.");
                    }
                    break;
                }
            }
        }
        if(c_retentiontime) System.out.println("Retention time information available. Retention time feature enabled.");

        featuresWriter.write(new TabData(target).getFeatureHeader() + "\n");

        boolean concatTargetDecoy = false;
        if (o_target.getAbsolutePath().equals(o_decoy.getAbsolutePath())) {
            if (!target.hasDecoy()) {
                concatTargetDecoy = true;
                System.out.println("\nNo separate decoy run detected; switched to concatenated target/decoy database search mode.");
            }
        }

        //setup decoy run only if target run was not a concatenated target/decoy database search

        if(!concatTargetDecoy) {
            out.println("\nreading file: " + o_decoy);
            System.out.println("\nreading file: " + o_decoy);
            //Mascot auto decoy run
            if (o_target.getAbsolutePath().equals(o_decoy.getAbsolutePath())) {
                out.println("setup (autodecoy mode) ...");
                System.out.println("setup (autodecoy mode) ...");
                if (!target.hasDecoy()) throw new FileIOException(o_target.getAbsolutePath() + " was not searched with auto decoy; no decoy information present.");
                decoy = new MascotParser(o_target.getAbsolutePath(), resultModeDecoy, false);

            } else {
                //seperate decoy search
                out.println("\nsetup decoy processing (separate search) ...");
                System.out.println("\nsetup decoy processing (separate search) ...");
                decoy = new MascotParser(o_decoy.getAbsolutePath(), resultMode, true);
            }
        }

        //select queries + ranks to be exported

        out.println("\nSelect all query/ranks to be exported ...");
        System.out.println("\nSelect all query/ranks to be exported ...");
        query_rank.addAll(findRanksToExport(target, 13, c_rankDelta));
        if (!concatTargetDecoy) query_rank.addAll(findRanksToExport(decoy, 13, c_rankDelta));

//Test for Lukas; score competition
//query_rank.addAll(findQueriesToExportScoreCompetition(target, decoy));

        //export target/decoy data

        //target
        out.println("\nExport target ...");
        System.out.println("\nExport target ...");
        if(c_proteinSupport) target.calcProteins(query_rank);
        exportRun(target, percolatorTargetLabel, concatTargetDecoy);
        target = null;
        System.gc();

        //decoy
        if(!concatTargetDecoy) {
            out.println("\nExport decoy ...");
            System.out.println("\nExport decoy ...");
            if (c_proteinSupport) decoy.calcProteins(query_rank);
            exportRun(decoy, percolatorDecoyLabel, concatTargetDecoy);
        }

        featuresWriter.flush();
        featuresWriter.close();

        long t2 = System.currentTimeMillis();
        out.println((t2 - t1) / 1000 + "s");
        System.out.println((t2 - t1) / 1000 + "s");
        return c_retentiontime;
    }

    public static HashSet<String> findRanksToExport(MascotParser parser, int scoreCutoff, double rankdelta) {
        HashSet<String> set = new HashSet<String>(50000);
        int numQueries = parser.getNumQueries();
        for (int queryN = 1; queryN <= numQueries; queryN++) {
            double topScore = parser.getPeptide(queryN, 1).getIonsScore();
            set.add(query_rank(queryN, 1));

            if (rankdelta == -2){
                int rank = 2;
                //double scoreN = parser.getPeptide(queryN, rank).getIonsScore();
                String seq1 = parser.getPeptide(queryN,1).getPeptideStr().trim().replaceAll("K", "Q").replaceAll("L", "I");
                String seq2 = parser.getPeptide(queryN,rank).getPeptideStr().trim().replaceAll("K", "Q").replaceAll("L", "I");

                while (rank < parser.getMaxRank() && seq1.equals(seq2)) {
                    set.add(query_rank(queryN, rank));
                    rank++;
                    seq2 = parser.getPeptide(queryN,rank).getPeptideStr().trim().replaceAll("K", "Q").replaceAll("L", "I");
                }
            } else {

                //2nd - 10th rank, if score1-scoreN<rankDelta && if scoreN>13; only for the targets, not the decoys!
                int rank = 2;
                double scoreN = parser.getPeptide(queryN, rank).getIonsScore();
                while (rank < parser.getMaxRank() && scoreN >= scoreCutoff && (topScore-scoreN) <= rankdelta) {
                    set.add(query_rank(queryN, rank));

                    rank++;
                    scoreN = parser.getPeptide(queryN, rank).getIonsScore();
                }
            }




        }
        return set;
    }

    public static HashSet<String> findQueriesToExportScoreCompetition(MascotParser target, MascotParser decoy) {
        HashSet<String> set = new HashSet<String>(50000);
        int numQueries = target.getNumQueries();
        for (int queryN = 1; queryN <= numQueries; queryN++) {
            double targetScore = target.getPeptide(queryN, 1).getIonsScore();
            double decoyScore = decoy.getPeptide(queryN, 1).getIonsScore();
            if(targetScore >= decoyScore) {
                set.add("T" + query_rank(queryN, 1));
            } else {
                set.add("D" + query_rank(queryN, 1));
            }
        }
        return set;
    }


    private static String query_rank(int query, int rank) {
        return (query + "_" + rank);
    }

    public static boolean hasQueryRank(Set<String> queryRank, int query, int rank) {
        return queryRank.contains(query + "_" + rank);
    }

    private void exportRun(MascotParser parser, int percolatorLabel, boolean concatTargetDecoyMode) throws IOException {
        this.parser = parser;
        this.avgMS2MassErrorMedian = new ArrayList<Double>();
        this.avgMS2MassErrorMedianPPM = new ArrayList<Double>();
        this.avgMS2MassErrorIQR = new ArrayList<Double>();
        this.avgMS2MassErrorIQRPPM = new ArrayList<Double>();

        int numQueries = parser.getNumQueries();

        int queryN = 1;
        for (;queryN <= numQueries; queryN++) {
            if (queryN % 1000 == 0) {
                out.println(queryN + " queries processed ... ");
                System.out.println(queryN + " queries processed ... ");
            }

            ms_peptide peptide1 = parser.getPeptide(queryN, 1);
            double score1 = peptide1.getIonsScore();

            //find score of next best non-isobaric peptide hit
            double score2 = 0;
            String pep1 = peptide1.getPeptideStr().trim().replaceAll("K", "Q").replaceAll("L", "I");
            for (int i = 2; i <= parser.getMaxRank(); i++) {
                ms_peptide peptide2 = parser.getPeptide(queryN, i);
                String pep2 = peptide2.getPeptideStr().trim().replaceAll("K", "Q").replaceAll("L", "I");
                //find non isobaric peptide
                if (! pep1.equals(pep2)) {
                    score2 = peptide2.getIonsScore();
                    break;
                }
            }

            //1st rank
            if(concatTargetDecoyMode) percolatorLabel = matchedRandomProtein(queryN, 1);
            exportFeatures(percolatorLabel, queryN, 1, peptide1, score1-score2);

            //2nd - 10th rank, if score1-scoreN<rankDelta && if scoreN>13; only for the targets, not the decoys!
           for(int rank = 2; rank < parser.getMaxRank(); rank++) {
                if(hasQueryRank(query_rank, queryN, rank)) {
                    ms_peptide peptide = parser.getPeptide(queryN, rank);
                    double scoreN = peptide.getIonsScore(); //if score == 0, there is no match.
                    if (concatTargetDecoyMode && scoreN > 0) percolatorLabel = matchedRandomProtein(queryN, rank);

                    exportFeatures(percolatorLabel, queryN, rank, peptide, scoreN-score2);
                } else {
                    break; //if say the 2nd rank is not exported, then none of the subsequent ranks will be.
                }
            }
        }
        out.println(queryN + TOTALSPECTRALABEL);
        System.out.println(queryN + " queries processed. Done.\n");
    }

    private int matchedRandomProtein(int queryN, int rank) {
        Set<String> proteinIDs = parser.getProteinIDs(queryN, rank);
        if (containsDecoyProteinID(proteinIDs)) {
            return percolatorDecoyLabel;
        }
        return percolatorTargetLabel;
    }

    public static boolean containsDecoyProteinID(Set<String> proteinIDs) {
        for (String id : proteinIDs) {
            id = id.toLowerCase();
            if (id.contains("reverse") || id.contains("###rev###")) return true;
            if (id.contains("random") || id.contains("###rnd###")) return true;
        }
        return false;
    }
    private void exportFeatures(int percolatorLabel, int queryN, int rank,
                               ms_peptide peptide, double deltaScore) throws IOException {

        final ms_inputquery query = parser.getQuery(queryN);
        final String sequence = peptide.getPeptideStr().trim(); // preserve ambig string:  peptide.getPeptideStr(false).trim();

        if (sequence == null || sequence.equals("")) return;
        if (!c_noFilter && query.getNumVals() <= 15) return;

        TabData data = new TabData(parser);
        data.setPeptide(sequence);
        data.setLabel(Integer.toString(percolatorLabel));
        data.setQuery(Integer.toString(queryN));

        String id;
        if(c_showMods) {
            //String url = new URL("file://" + .replaceAll(" ", "")).toString();            
            id = query.getStringTitle(true) + ";query:" + queryN + ";rank:" + rank;
        } else {
            id = "query:" + queryN + ";rank:" + rank;    
        }
        data.setId(id);

        if(c_retentiontime) {
            String rt = query.getRetentionTimes();
            if(rt != null && rt.contains("-")) {
                String[] rts = rt.split("-");
                double avg = Double.parseDouble(rts[0]);
                avg += Double.parseDouble(rts[1]);
                avg /= 2;
                rt = Double.toString(avg);
            }
            data.setRetentionTime(rt);
        }


        // get peptide sequence including modifications
        if(c_showMods) {
            char[] modstr = peptide.getVarModsStr().toCharArray(); //nterm mod, residue modSequence, cterm mod
            char[] seqChar = sequence.toCharArray();
            StringBuilder modSequence = new StringBuilder();
            final int modlength = modstr.length;
            for(int i = 0; i<modlength; i++) {
                if(i != 0 && i != modlength-1) {
                    // modstr contains nterm mod, residues mod, cterm mod
                    modSequence.append(seqChar[i-1]);
                }
                String c = Character.toString(modstr[i]);
                if(c.equals("0")) continue;
                modSequence.append("[").append(parser.getModName(c).replaceAll(" ","")).append("]");
            }
            data.setPeptide(modSequence.toString());
        }

        // Peptide mass
        double mrCalc = peptide.getMrCalc();
        data.setMrCalc(mrCalc);

        // Charge
        double ch = peptide.getCharge();
        data.setCharge(ch);
        data.setChargeF(ch);

        // Scores & thresholds
        double mScore = peptide.getIonsScore();
        data.setMScore(mScore);
        data.setDeltaScore(deltaScore);
        data.setMitMHT(parser.getIdentityThreshold(queryN, 20), parser.getHomologyThreshold(queryN, 20));

        // deltaM / deltaPPM / take into account wrong isotope 13, 14
        double deltaM = peptide.getDelta();
        double absDeltaM = Math.abs(deltaM);
        double iso;
        double iso1 = Math.abs(1.00335 - absDeltaM);
        double iso2 = Math.abs(2.00670 - absDeltaM);
        iso = (iso1 < iso2) ? iso1 : iso2;
        iso = (iso < absDeltaM) ? iso : absDeltaM;

        data.setDM(deltaM);
        data.setAbsDM(absDeltaM);
        data.setIsoDM(iso);

        // Modifications
        String varmodst = peptide.getVarModsStr();
        double totMods = 0;
        double totModifiable = 0;
        for(String modID : parser.getModIDSet()) {
            double numMods = StringUtils.countMatches(varmodst, modID);
            List<String> modifiableResidues = parser.getModifiableResiduesByModID(modID);
            double modifiableRes = 0;
            for(String modRes : modifiableResidues) {
                if(modRes.toLowerCase().startsWith("protein")) {
                    modifiableRes++; //Protein N-term, Protein C-term
                } else {
                    modifiableRes += StringUtils.countMatches(sequence, modRes);
                }
            }

            totMods += numMods;
            totModifiable += modifiableRes;
        }
        if (c_varmods) {
            double relMods = (totModifiable > 0) ? (totMods / totModifiable) : 0;
            data.setVarmods(relMods);
        }

        //Peptide and uniue peptide count for the top hit protein per query
        if(c_proteinSupport) {
            TreeMap<Integer, Set<String>> numPeps2ProtIds = new TreeMap<Integer, Set<String>>();
            TreeMap<Integer, Set<String>> uniqueNumPeps2ProtIds = new TreeMap<Integer, Set<String>>();
            parser.getPeptideCounts(queryN, rank, numPeps2ProtIds, uniqueNumPeps2ProtIds);
            //number unique peptides of winning protein
            double best = uniqueNumPeps2ProtIds.lastKey();
            if(best > 2) best = 2;
            else if(best > 1) best = 1; //limit numPeps to a binary factor: either there is supporting other peptides (>=2) or not.
            else best = 0;
            data.setNumUniqPeps(best);
        }

        Set<String> proteinIDs = parser.getProteinIDs(queryN, rank);
        for(String proteinID : proteinIDs) {
            data.addProtein(proteinID);
        }

        //missed cleavages
        data.setMc(peptide.getMissedCleavages());




        //features using peptide fragment calculation & ion matching; time consuming, only execute code if needed.
        if (c_totInt
            || c_fragMassErr
            || c_fracIonsMatched
            || c_matchedIntensity
            || c_intMatchedTot
            || c_relIntMatchedTot
            || c_immonium) {


            //Map<Double, Double> mass2IntObs = parser.getMass2Int_topXions(query, peptide.getPeaksUsedFromIons1()+peptide.getPeaksUsedFromIons2()+peptide.getPeaksUsedFromIons3());
            //SpectrumMatching sm = new SpectrumMatching(peptide, charge);


            if (c_highCharge) {
                maxFragCharge = 4;
            }

            Map<Integer, Map<Double, Double>> pd2mass2int = parser.getPeakDepth2Mass2Intensities_top10per100mz(query);
            SpectrumMatching sm = new SpectrumMatching(peptide, maxFragCharge);
            HashMap<Double, Double> mass2IntObs = new HashMap<Double, Double>();
            for(Integer peakDepth : pd2mass2int.keySet()) {
                mass2IntObs.putAll(pd2mass2int.get(peakDepth));
            }
            sm.match(mass2IntObs, sequence, data, maxFragCharge);

            // A Ion Feature
            if (c_aIonFeature) {
                HashMap<Integer, List<Double>> pos2massCalcA = new HashMap<Integer, List<Double>>();
                parser.calcPeptideFragmentation(peptide, ms_fragmentationrules.FRAG_A_SERIES, false, pos2massCalcA);
                Double aOneIon = pos2massCalcA.get(1).get(0);
                Double maxInt = 0.0;
                Double aIon = 0.0;
                List<Double> ObservedMasses = new ArrayList<Double>(mass2IntObs.keySet());
                for (Double obsMass : ObservedMasses) {
                    Double intensity = mass2IntObs.get(obsMass);
                    Double match = parser.matchIon(aOneIon, obsMass, intensity);
                    if (match > aIon){ aIon = match; }
                    if (intensity > maxInt){ maxInt = intensity; }
                }
                data.setAIon((aIon/maxInt));
            }

            //Gygi's PeptideScore from AScore paper
            /*Map<Integer, List<Double>> pos2massBYIonCalc = new HashMap<Integer, List<Double>>();
            parser.calcPeptideFragmentation(peptide, MascotParser.ruleB, false, pos2massBYIonCalc);
            parser.calcPeptideFragmentation(peptide, MascotParser.ruleY, false, pos2massBYIonCalc);
            if (charge > 2) parser.calcPeptideFragmentation(peptide, MascotParser.ruleB, true, pos2massBYIonCalc);
            if (charge > 2) parser.calcPeptideFragmentation(peptide, MascotParser.ruleY, true, pos2massBYIonCalc);
            List<Double> scores = parser.matchIonsAndGetGygiPeptideScore(query, pos2massBYIonCalc, pd2mass2int);
            double[] weights = new double[]{0,0.5,1,1,1,1,1,1,0.5,0};
            double binomScore = 0;
            for(int i = 0; i<weights.length; i++) binomScore += (weights[i]*scores.get(i));
            data.setBinomPepScore(binomScore);
            */
        }

        // flush features
        featuresWriter.append(data.getFeatures()).append("\n");
        featuresWriter.flush();
    }




    class SpectrumMatching {

        //for each ions series (rules), get these results:
        private Map<Integer, List<Double>>[] pos2massCalc;
        private int[] numCalcIons;
        private double[] matchedInt;
        private Set<Double>[] matchedIons;
        Set<Integer>[] matchedPositions;

        private List<Double> fragmentMassError;
        private List<Double> fragmentMassErrorPPM;

        public SpectrumMatching(ms_peptide peptide, int maxFragCharge) {

            List<Integer> rules = parser.getRules(); // see: ms_searchparams Class Reference
            //System.out.println("rules = " + rules);
            int numRules = rules.size();

            pos2massCalc = (Map<Integer, List<Double>>[]) new HashMap[numRules*maxFragCharge];
            numCalcIons = new int[numRules*maxFragCharge];
            matchedInt = new double[numRules*maxFragCharge];
            matchedIons = (Set<Double>[]) new HashSet[numRules*maxFragCharge];
            matchedPositions = (HashSet<Integer>[]) new HashSet[numRules*maxFragCharge];

            //for each rule
            int charge_rule = 0;
            for(int charge = 1; charge <= maxFragCharge; charge++) {
                for(int i = 0; i < numRules; i++) {
                    int rule = rules.get(i);  // see: ms_searchparams Class Reference, getRules();

                    //calculate theoretical fragmentation for peptides
                    pos2massCalc[charge_rule] = new HashMap<Integer, List<Double>>();
                    boolean doublyChargedSeries = false;
                    if(charge == 2) doublyChargedSeries = true;

                    if(charge > 2) {
                        numCalcIons[charge_rule] = parser.calcPeptideFragmentationHighCharge(charge, peptide, rule, pos2massCalc[charge_rule]);
                        
                    } else numCalcIons[charge_rule] = parser.calcPeptideFragmentation(peptide, rule, doublyChargedSeries, pos2massCalc[charge_rule]);

                    // prepare results data
                    matchedInt[charge_rule] = 0;
                    matchedIons[charge_rule] = new HashSet<Double>();
                    matchedPositions[charge_rule] = new HashSet<Integer>();

                    charge_rule++;
                }
            }

            fragmentMassError = new ArrayList<Double>();
            fragmentMassErrorPPM = new ArrayList<Double>();
        }

        public void match(Map<Double, Double> mass2IntObs, String sequence, TabData data, int maxFragCharge) {
            Double totalAcceptedIntensity = 0.1; //Math.log is used later, so do not default to 0;
            List<Double> sortedObservedMasses = new ArrayList<Double>(mass2IntObs.keySet());
            Collections.sort(sortedObservedMasses);

            double totalMatchedIntensity = 0.1;
            for (Double obsMass : sortedObservedMasses) {
                Double intensity = mass2IntObs.get(obsMass);
                totalAcceptedIntensity += intensity;

                boolean matched = false;
                for (int i = 0; i < parser.getRules().size()*maxFragCharge; i++) {
                    double y = parser.matchIons(pos2massCalc[i], obsMass, intensity, matchedIons[i],
                                                matchedPositions[i], fragmentMassError, fragmentMassErrorPPM);

                    // This could also be made so that each matched peak is only considered once
                    // Also might be worth rethinking this whole method and only considering the best FragmentMassError as well for each ion
                    matchedInt[i] += y;

                    // Set this is peaks want to be considered multiple times
                    //totalMatchedIntensity += y;

                    if (y > 0)  matched = true;        
                }

                if (matched) totalMatchedIntensity += intensity;
            }

            //ion matching statistics
            data.set(numCalcIons, matchedIons, totalAcceptedIntensity, matchedInt);
            data.setLongestConseqSeries(getLongestConsecutiveIonSeries(matchedPositions));
            data.setIntMatchedTot(Math.log(totalMatchedIntensity));
            data.setRelIntMatchedTot(totalMatchedIntensity/totalAcceptedIntensity);
            data.setTotInt(Math.log(totalAcceptedIntensity));

            //mean fragment mass errors
            data.setFragDeltaM(fragmentMassError);
            data.setFragDeltaMPPM(fragmentMassErrorPPM);
        }

        private int getLongestConsecutiveIonSeries(Set<Integer> matchedPositions[]) {
            int longestSeries = 0;
            for(int r = 0; r < matchedPositions.length; r++) {
                ArrayList<Integer> positions = new ArrayList<Integer>(matchedPositions[r]);
                Collections.sort(positions);
                int longest = 0;
                int lastPos = 0;
                for(int i = 0; i < positions.size(); i++) {
                    int pos = positions.get(i);
                    if(lastPos+1 == pos) longest++;
                    else {
                        if(longest > longestSeries) longestSeries = longest;
                        longest = 1;
                    }
                    lastPos = pos;
                }
                if (longest > longestSeries) longestSeries = longest;
            }
            return longestSeries;
        }
    }



    class TabData {

        //data.setB1(numCalcBIons, matchedBIons, (matchedIntB/totalAcceptedIntensity), length);

        MascotParser parser;

        private String id, label, query;
        private String retentionTime; //can be ""
        private double dM;
        private double absDM;
        private double isoDM;
        private double charge1, charge2, charge3, charge4plus;
        private double mrCalc, charge, mScore, deltaScore, mit, mht;
        private double numUniqPeps;
        private double mc, varmods, totInt, intMatchedTot, relIntMatchedTot, aIon;

        private List<Double> fragDeltaM, fragDeltaMPPM;
        private int longestIonseries;
        private Set<Double> ionsMatched[];
        private int numCalcIons[];
        private double totalAcceptedIntensity;
        private double matchedInt[];

        String peptide;
        List<String> proteins = new ArrayList<String>();

        public TabData(MascotParser parser) {
            this.parser = parser;
        }

        public String getFeatureHeader() {
            this.parser = parser;
            
            StringBuilder sb = new StringBuilder();
            sb.append("id\tlabel\t"); //id and label; needs to be column 1, 2 in the input file for percolator
            if (c_retentiontime) sb.append("rt\tdM\t"); //retention time features; needs to be column 3, 4 in the input file for percolator

            if (c_mScore) sb.append("mScore\t");
            if (c_deltaScore) sb.append("deltaScore\t");
            if (c_amt) sb.append("amt\t");

            if (c_massMr) sb.append("mrCalc\t");
            if (c_charge){
                if (c_chargeFeature) sb.append("charge\t");
                else sb.append("charge1\tcharge2\tcharge3\tcharge4plus\t");
            }
            if (c_deltaM) sb.append("dM\tdMppm\t");
            if (c_absdM) sb.append("absDM\tabsDMppm\t");
            if (c_isodM) sb.append("isoDM\tisoDMppm\t");


            if (c_proteinSupport) sb.append("protSupport\t");

            if (c_mc) sb.append("mc\t");
            if (c_varmods) sb.append("varMods").append("\t");
            if (c_aIonFeature) sb.append("a1Ion").append("\t");
            if (c_totInt) sb.append("totInt\t");

            if (c_intMatchedTot) sb.append("intMatchedTot\t");
            if (c_relIntMatchedTot) sb.append("relIntMatchedTot\t");

            if (c_fragMassErr) {
                sb.append("fragDeltaM_median\t");
                sb.append("fragDeltaM_iqr\t");
                sb.append("fragDeltaMPPM_median\t");
                sb.append("fragDeltaMPPM_iqr\t");
            }

            if (c_fracIonsMatched) sb.append("longestIonseries\t");

            for(int i = 0; i < parser.getRules().size(); i++) {
                int rule = parser.getRules().get(i);
                if (c_fracIonsMatched) sb.append("fracIons_series_" + rule + "\t");
                if (c_matchedIntensity) sb.append("relIntMatched_series_" + rule + "\t");
            }

            for (int i = 0; i < parser.getRules().size(); i++) {
                int rule = parser.getRules().get(i);
                if (c_fracIonsMatched) sb.append("fracIons_2+series_" + rule + "\t");
                if (c_matchedIntensity) sb.append("relIntMatched_2+series_" + rule + "\t");
            }

            sb.append("peptide\tproteins");
            return sb.toString();

            /*
            if (c_varMods) {
                Set<String> keys = parser.getModIDSet();
                for(String s : keys) sb.append(parser.getModName(s).replaceAll(" ", "")).append("\t");
                sb.append("numMods\t");
            }
            */
        }

        private String getFeatures() {
            StringBuilder sb = new StringBuilder();
            sb.append(id).append("\t").append(label).append("\t");
            if (c_retentiontime) sb.append(retentionTime).append("\t").append(dM).append("\t");

            if (c_mScore) sb.append(mScore).append("\t");
            if (c_deltaScore) sb.append(getDScore()).append("\t");
            if (c_amt) sb.append(getAmt()).append("\t");

            if (c_massMr) sb.append(mrCalc).append("\t");
            if (c_charge){
                if (c_chargeFeature) sb.append(charge).append("\t");
                else sb.append(charge1).append("\t").append(charge2).append("\t").append(charge3).append("\t").append(charge4plus).append("\t");
            }
            if (c_deltaM) sb.append(dM).append("\t").append(getDMppm()).append("\t");
            if (c_absdM) sb.append(absDM).append("\t").append(getAbsDMppm()).append("\t");
            if (c_isodM) sb.append(isoDM).append("\t").append(getIsoDMppm()).append("\t");

            if (c_proteinSupport) sb.append(numUniqPeps).append("\t");

            if (c_mc) sb.append(mc).append("\t");
            if (c_varmods) sb.append(varmods).append("\t");
            if (c_aIonFeature) sb.append(aIon).append("\t");
            if (c_totInt) sb.append(totInt).append("\t");
            if (c_intMatchedTot) sb.append(intMatchedTot).append("\t"); 
            if (c_relIntMatchedTot) sb.append(relIntMatchedTot).append("\t");

            if (c_fragMassErr) {
                DescriptiveStatistics ds = new DescriptiveStatistics();
                if(fragDeltaM.size() > 0) {
                    for(Double d : fragDeltaM) ds.addValue(d);
                    double median = ds.getPercentile(50);
                    double iqr = ds.getPercentile(75) - ds.getPercentile(25);
                    if(iqr == 0) iqr = 3*median; //bad default value
                    sb.append(median).append("\t").append(iqr).append("\t");
                    avgMS2MassErrorMedian.add(median);
                    avgMS2MassErrorIQR.add(iqr);

                    ds = new DescriptiveStatistics();
                    for (Double d : fragDeltaMPPM) ds.addValue(d);
                    median = ds.getPercentile(50);
                    iqr = ds.getPercentile(75) - ds.getPercentile(25);
                    if (iqr == 0) iqr = 3 * median; //bad default value
                    sb.append(median).append("\t").append(iqr).append("\t");
                    avgMS2MassErrorMedianPPM.add(median);
                    avgMS2MassErrorIQRPPM.add(iqr);
                //} else if(avgMS2MassErrorIQR.size() > 10) { //use 2*average of last datapoints;
                  //  sb.append(2*avgOfList(avgMS2MassErrorMedian)).append("\t").append(2*avgOfList(avgMS2MassErrorIQR)).append("\t"); //bad Da default value
                  //  sb.append(2*avgOfList(avgMS2MassErrorMedianPPM)).append("\t").append(2*avgOfList(avgMS2MassErrorIQRPPM)).append("\t"); //bad ppm default value
                } else {
                    //last resort for default values - only if there are too few datapoints to get an average value
                    //IMPORTANT! Don't put in too large default values, otherwise the overall distribution could introduce artificats
                    sb.append(0.1).append("\t").append(0.5).append("\t"); //bad Da default value
                    sb.append(100).append("\t").append(250).append("\t"); //bad ppm default value
                }
            }

            if (c_fracIonsMatched) sb.append(longestIonseries).append("\t");

            for (int i = 0; i < parser.getRules().size()*maxFragCharge; i++) {
                if (c_fracIonsMatched) sb.append(Math.round(((double)(ionsMatched[i].size()))/(double)numCalcIons[i] * 100)).append("\t");
                if (c_matchedIntensity) sb.append(Math.round(matchedInt[i]/totalAcceptedIntensity * 100)).append("\t");
            }

            sb.append("X.").append(peptide).append(".X\t");
            Iterator<String> iterator = proteins.iterator();
            while(iterator.hasNext()) sb.append(iterator.next()).append("\t");

            return sb.toString();
        }

        private double avgOfList(List<Double> list) {
            Double sum = 0d;
            for(Double d : list) sum += d;
            double size = list.size();
            return sum/size;
        }


       // getters (for derived values only)

        public double getDMppm() {
            return dM * MascotParser.MIO / mrCalc;
        }

        public double getAbsDMppm() {
            return absDM * MascotParser.MIO / mrCalc;
        }

        public double getIsoDMppm() {
            return isoDM * MascotParser.MIO / mrCalc;
        }

        public double getAmt() {
            if (mht <= 0) {
                mht = mit;
            }
            return mScore - mht;
        }

        public double getDScore() {
            return deltaScore;
        }

        // setters

        public void addProtein(String protein) {
            proteins.add(protein);
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public void setRetentionTime(String retentionTime) {
            if(retentionTime == null || retentionTime.equals("")) {
                this.retentionTime = "0";
            } else {
                this.retentionTime = retentionTime;
            }
        }

        public void setDM(double dM) {
            this.dM = dM;
        }

        public void setAIon(double a) {
            this.aIon = a;
        }

        public void setAbsDM(double absDM) {
            this.absDM = absDM;
        }

        public void setIsoDM(double isoDM) {
            this.isoDM = isoDM;
        }

        public void setMrCalc(double mrCalc) {
            this.mrCalc = mrCalc;
        }

        public void setChargeF(double charge) {
            charge1 = 0;
            charge2 = 0;
            charge3 = 0;
            charge4plus = 0;
            if(charge == 1) charge1 = 1;
            if(charge == 2) charge2 = 1;
            if(charge == 3) charge3 = 1;
            if(charge > 3) charge4plus = 1;
        }

        public void setCharge(double c) {
            this.charge = c;
        }

        public void setMScore(double mScore) {
            this.mScore = mScore;
        }

        public void setDeltaScore(double deltaScore) {
            this.deltaScore = deltaScore;
        }

        public void setMitMHT(double mit, double mht) {
            this.mit = mit;
            this.mht = mht;
        }

        public void setNumUniqPeps(double numUniqPeps) {
            this.numUniqPeps = numUniqPeps;
        }

        public void setMc(double mc) {
            this.mc = mc;
        }

        public void setVarmods(double varmods) {
            this.varmods = varmods;
        }

        public void setTotInt(double totInt) {
            this.totInt = totInt;
        }

        public void setIntMatchedTot(double intMatchedTot) {
            this.intMatchedTot = intMatchedTot;
        }

        public void setRelIntMatchedTot(double relIntMatchedTot) {
            this.relIntMatchedTot = relIntMatchedTot;
        }

        public void setFragDeltaM(List<Double> fragDeltaM) {
            this.fragDeltaM = fragDeltaM;
        }

        public void setFragDeltaMPPM(List<Double> fragDeltaMPPM) {
            this.fragDeltaMPPM = fragDeltaMPPM;
        }

        public void setLongestConseqSeries(int longestseries) {
            this.longestIonseries = longestseries;
        }

        public void set(int numCalcIons[], Set<Double>ionsMatched[], double totIntensityAccepted, double[] matchedIntensity) {
            this.numCalcIons = numCalcIons;
            this.totalAcceptedIntensity = totIntensityAccepted;
            this.matchedInt = matchedIntensity;
            this.ionsMatched = ionsMatched;
        }

        public void setPeptide(String peptide) {
            this.peptide = peptide;
        }

        public void setProteins(List<String> proteins) {
            this.proteins = proteins;
        }
    }
}