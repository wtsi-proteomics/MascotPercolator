package core;

import matrix_science.msparser.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DecimalFormat;
import java.io.File;

import deprecated.MassRangeList;
import util.LoadMascotLibrary;
import util.MathHelper;
import exception.FileIOException;
import org.apache.commons.collections.map.MultiValueMap;

public class MascotParser {

    boolean debug = false;

    //msparser related

    //Important, don't make masses, modfile, modvectorVar and modvectorFix local variables, although it would be nicer!
    //The Mascot Parser library would result in "premature garbage collection issues" leading to incorrect anaylsis,
    //when a parser library with version < 2.2.05 is used. This is down to SWIG:
    //   http://www.swig.org/Release/CHANGES
    //   10/12/2007:wsfulton [Java] Ensure the premature garbage collection prevention parameter(pgcpp)is generated when there are C comments in the jtype and jstype typemaps.
    //   and http://www.matrixscience.com/parser_support.html (2.2.05).
    private ms_mascotresfile resultsFile;
    private ms_peptidesummary peptideSummary;
    private ms_searchparams searchParams;
    private ms_aahelper aahelper;
    private ms_errs err;
    private ms_masses masses;
    private ms_modfile modfile;
    private ms_modvector modvectorVar;
    private ms_modvector modvectorFix;

    //cached
    private ms_inputquery query;
    Map<Double, Double> mass2int_all;
    MultiValueMap int2masses_all;

    //some parameters
    private double msmsTolDa;
    private int massType;
    private int maxRank;
    private List<Integer> rules;
    public final static double MIO = 1000000; //ppm conversion
    private final int peakDepth = 20;

    private boolean targetMode;
    private HashMap<String, Integer> protein2numPeps = new HashMap<String, Integer>();
    private HashMap<String, Integer> protein2uniqueNumPeps = new HashMap<String, Integer>();
    private final Pattern pattern = Pattern.compile("\"(.+?)\"");
    private final Pattern modRes = Pattern.compile("\\((.*)\\)");
    private Map<String, String> modId2modName;
    private Map<String, List<String>> modId2ModifiableResidues;
    private Set<String> phosphoIDs;

    static {
        new LoadMascotLibrary();
    }

    public MascotParser(String datfile, int flags, boolean isTarget) throws FileIOException {
        this.err = new ms_errs();
        this.targetMode = isTarget;

        resultsFile = new ms_mascotresfile(datfile);

        if (!resultsFile.isValid())
            throw new FileIOException(datfile + " is either not accessible or not a valid Mascot results file.\n" +
                "Does this process run as a user which has access to the file ?");
        if (!resultsFile.isMSMS())
            throw new FileIOException(datfile + " is not a passThrough Mascot MS/MS search.");

        //read peptide summary from the results file
        peptideSummary = new ms_peptidesummary(
                resultsFile,
                flags,
                0,
                0,
                "",
                0,
                0
        );

        //how many ranks did Mascot report
        maxRank = peptideSummary.getMaxRankValue();

        //read search parameters from resultsfile
        searchParams = new ms_searchparams(resultsFile);

        //mass tolerance MS/MS
        msmsTolDa = searchParams.getITOL(); //msms mass tolerance in Da
        if (searchParams.getITOLU().trim().equals("mmu")) msmsTolDa = msmsTolDa / 1000.0; //convert into Da if mmu

        //mass type: monoisotopic or average
        String massTypeText = searchParams.getMASS();
        if (massTypeText.trim().equals("Monoisotopic")) massType = msparserConstants.MASS_TYPE_MONO;
        else if (massTypeText.trim().equals("Average")) massType = msparserConstants.MASS_TYPE_AVE;
        else throw new IllegalArgumentException(massTypeText + " was returned as mass type. Not implemented.");

        //which ions to use for the search/instrument
        String strRules[] = searchParams.getRULES().split(",");
        rules = new ArrayList<Integer>();
        for (int i = 0; i < strRules.length; i++) {
            int rule = Integer.parseInt(strRules[i]);
            if (rule >= 5)
            rules.add(rule);
        }

        //load unimod config file to set up modificaitons
        ms_umod_configfile umodConfig = new ms_umod_configfile();
        boolean foundUniModInResFile = resultsFile.getUnimod(umodConfig);
        masses = new ms_masses(umodConfig);
        modfile = new ms_modfile(umodConfig, ms_umod_configfile.MODFILE_FLAGS_ALL);

        //TODO this is a hack for old Mascot version. Not sure how to improve ...
        //this requires the mod_file from /mascot/mascot/config to be copied into MascotPercolator root folder
        if (modfile.getNumberOfModifications() == 0) {
            String legacyModFile = "mod_file";
            File f = new File(legacyModFile);
            if(f.exists()) {
                masses = new ms_masses();
                modfile = new ms_modfile(legacyModFile, masses);
            } else {
                System.err.println("The Mascot resultfile (dat) does not comprise Unimod information.\n" +
                    "Therefore file " + f.getAbsolutePath() + " is required, but was not found.\n" +
                    "Make sure it is available in the Mascot Percolator folder.");
                System.exit(-1);
            }
        }

        //set up variable modifications
        modId2modName = new HashMap<String, String>(30);
        modId2ModifiableResidues = new HashMap<String, List<String>>(30);
        phosphoIDs = new HashSet<String>(5);
        modvectorVar = new ms_modvector();
        char modIDs[] = "123456789ABCDEFGHIJKLMNOPQRSTUVW".toCharArray();
        for (int i = 1; i <= modIDs.length; i++) {
            String modname = searchParams.getVarModsName(i);
            if (modname.equals("")) continue;

            ms_modification mod = modfile.getModificationByName(modname);
            if (mod == null) {
                System.err.println("Variable Modification " + modname + " cannot be found in unimod config.");
                System.exit(-1);
            }
            modvectorVar.appendModification(mod);

            String modID = Character.toString(modIDs[i-1]);
            modId2modName.put(modID, modname);
            if(modname.toLowerCase().startsWith("phospho")) phosphoIDs.add(modID);

            Matcher m = modRes.matcher(modname);
            m.find();
            String res = m.group(1);
            List<String> modifiableRes = new ArrayList<String>();
            if(res.toLowerCase().startsWith("protein")) {
                //Protein N-term, Protein C-term, e.g.: IT_MODS=Acetyl (Protein N-term)
                modifiableRes.add(res);
            } else {
                //Split up individual AminoAcids, e.g.: IT_MODS=Deamidated (NQ),Oxidation (M)
                for(int resI = 0; resI < res.length(); resI++) {
                    modifiableRes.add(res.substring(resI, resI+1));
                }
            }
            modId2ModifiableResidues.put(modID, modifiableRes);
        }

        //set up fixed modifications
        modvectorFix = new ms_modvector();
        String fixedModsName[] = searchParams.getMODS().split(",");
        for (String modname : fixedModsName) {
            if (modname.equals("")) continue;

            ms_modification mod = modfile.getModificationByName(modname);
            if (mod == null) {
                System.err.println("Fixed Modification " + modname + " cannot be found in unimod config.");
                System.exit(-1);
            }
            modvectorVar.appendModification(mod);
        }

        //set up aa_helper object that enables the calculation of ions of a peptide
        aahelper = new ms_aahelper();
        aahelper.setMasses(masses);
        aahelper.setAvailableModifications(modvectorFix, modvectorVar);
        if (!aahelper.isValid()) {
            System.err.println("Unable to create aahelper object: " + aahelper.getLastErrorString());
        }
    }

    public List<Integer> getRules() {
        return rules;
    }

    public double getMSMSTol() {
        return msmsTolDa;
    }

    public boolean hasDecoy() {
        return searchParams.getDECOY() == 1;
    }

    public int getMaxRank() {
        return maxRank;
    }

    public int getNumQueries() {
        return resultsFile.getNumQueries();
    }

    public ms_peptide getPeptide(int queryN, int rank) {
        return peptideSummary.getPeptide(queryN, rank);
    }

    public String getPeptideMods(int queryN, int rank){
        return peptideSummary.getReadableVarMods(queryN, rank);
    }

    public ms_searchparams getSearchParams() {
        return searchParams;
    }

    public ms_modification getModById(String id) {
        //id = 1..9,A..W
        String name = modId2modName.get(id);
        return modfile.getModificationByName(name);
    }

    public List<String> getModifiableResiduesByModID(String modID) {
        return modId2ModifiableResidues.get(modID);
    }

    public String getModName(String id) {
        return modId2modName.get(id);
    }

    public boolean isPhosphoMod(String modID) {
        return phosphoIDs.contains(modID);
    }

    public Set<String> getModIDSet() {
        return modId2modName.keySet();
    }

    public double getHomologyThreshold(int query, int p) {
        return peptideSummary.getHomologyThreshold(query, p);
    }

    public double getIdentityThreshold(int query, int p) {
        return peptideSummary.getPeptideIdentityThreshold(query, p);
    }

    public ms_inputquery getQuery(int queryNo) {
       query = new ms_inputquery(resultsFile, queryNo);
       getSpectrum(query);
       return query;
    }

    public String getQueryTitle(int queryNo) {
        query = new ms_inputquery(resultsFile, queryNo);
        return query.getStringTitle(true);
    }

    public double getPeptideIntensity(int queryNo) {
        return resultsFile.getObservedIntensity(queryNo);
    }

    public double getPValue(double s, int queryNo) {
        return peptideSummary.getPeptideExpectationValue(s, queryNo);
    }

    private void getSpectrum(ms_inputquery q) {
        // read all masses & intensities
        mass2int_all = new HashMap<Double, Double>(10000); //put operator complexity: O(1)
        int2masses_all = new MultiValueMap();
        for (int ions = 1; ions <= 3; ions++) {
            for (int peakNo = 1; peakNo <= q.getNumberOfPeaks(ions); peakNo++) {
                double intensity = q.getPeakIntensity(ions, peakNo);
                double mass = q.getPeakMass(ions, peakNo);
                mass2int_all.put(mass, intensity);

                // store all masses,intensities
                mass2int_all.put(mass, intensity);
                int2masses_all.put(intensity, mass);
                //out.println("lines(c(" + mass + "," + mass + "), c(" + 0.01 + "," + intensity + "), col=\"red\")");
            }
        }
        query = q;
    }

    public Map<Double, Double> getMass2Int_topXions(ms_inputquery q, int topX) {
        if (query == null || !query.equals(q)) {
            getSpectrum(q);
        }

        // set top X ions of spectrum as used by Mascot
       Map<Double, Double> mass2int_topX = new HashMap<Double, Double>(10000);

       List<Double> sortedIntensities = new ArrayList(int2masses_all.keySet());
       Collections.sort(sortedIntensities, Collections.reverseOrder()); // sort high intensity ... low intensity
       for (int i = 0; i < topX; i++) {
           Double intensity = sortedIntensities.get(i);
           Collection<Double> masses = int2masses_all.getCollection(intensity);
           int j = -1;
           for (Double mass : masses) {
               mass2int_topX.put(mass, intensity);
               j++;
           }
           topX -= j;
       }

       return mass2int_topX;
   }


    public Map<Integer, Map<Double, Double>> getPeakDepth2Mass2Intensities_top10per100mz(ms_inputquery q) {
        // Calculate binned masses as described in SA Beausoleil & Gygi, Nature Biotechnology, October 2006
        // Bin masses into mass windows of 100m/z and retain in each window the 1..10 most intense peaks

        // Datastructure "binnedMasses":  List entries represent mass window size.
        //                                Each entry is a List that holds masses orded by intensity rank (highest to lowest)

        //sort map; put operator complexity: O(log(n))
        if (query == null || !query.equals(q)) {
            getSpectrum(q);
        }

        List<Double> sortedMasses = new ArrayList(mass2int_all.keySet());
        Collections.sort(sortedMasses);

        Map<Integer, Map<Double, Double>> peakDepth2Masses2Intensity = new HashMap<Integer, Map<Double, Double>>();

        int massWindow = 1;
        MultiValueMap int2masses = new MultiValueMap();
        for (Double mass : sortedMasses) {
            if (mass > massWindow * 100) {
                binIntensities(int2masses, peakDepth2Masses2Intensity, 100*(massWindow-1), 100*massWindow);

                int2masses = new MultiValueMap();
                massWindow++;
            }

            Double intensity = mass2int_all.get(mass);
            int2masses.put(intensity, mass);

        }
        binIntensities(int2masses, peakDepth2Masses2Intensity, 100 * (massWindow - 1), 100 * massWindow);
        return peakDepth2Masses2Intensity;
    }

    private void binIntensities(MultiValueMap int2masses, Map<Integer, Map<Double, Double>> peakDepth2Masses2Intensity,
        int minMZ, int maxMZ) {

        //sort intensities
        ArrayList<Double> intensities = new ArrayList<Double>(int2masses.keySet());
        Collections.sort(intensities, Collections.reverseOrder());
        int end = peakDepth;
        int size = intensities.size();
        if (size < peakDepth) end = size;

        for (int pd = 0; pd < end; pd++) { //high to low intensity
            Double intensity = intensities.get(pd);
            Map<Double, Double> mass2int = peakDepth2Masses2Intensity.get(pd);
            if(mass2int == null) mass2int = new HashMap<Double, Double>();
            Collection<Double> masses = int2masses.getCollection(intensity);
            for (Double mass : masses) {
                if(mass <= maxMZ && mass >= minMZ) mass2int.put(mass, intensity);
            }
            peakDepth2Masses2Intensity.put(pd, mass2int);
        }
    }

    public int calcPeptideFragmentation(ms_peptide peptide, int fragRule, boolean doublyCharged,
                                        Map<Integer, List<Double>> returnResiduePos2IonMasses) {
        //System.out.println("\n\n\n\npeptide.getPeptideStr() = " + peptide.getPeptideStr());
        int count = 0;
        double mrCalc = peptide.getMrCalc();
        //iterate trhough ion series
        ms_fragmentvector fragments = new ms_fragmentvector();
        aahelper.calcFragments(peptide, fragRule, doublyCharged, 0, mrCalc, msparserConstants.MASS_TYPE_MONO, fragments, err);
        for (int fragNo = 0; fragNo < fragments.getNumberOfFragments(); fragNo++) {
            ms_fragment fragment = fragments.getFragmentByNumber(fragNo);
            final int col = fragment.getColumn();
            final double mass = fragment.getMass();
            //final double neutralLoss = fragment.getNeutralLoss();
            List<Double> masslist = returnResiduePos2IonMasses.get(col);
            if (masslist == null) masslist = new ArrayList<Double>();
            masslist.add(mass);
            returnResiduePos2IonMasses.put(col, masslist);
            count++;
        }
        //System.out.println("returnResiduePos2IonMasses = " + returnResiduePos2IonMasses);
        if(debug) printFragmentsTable(fragments);

        return count;
    }

    //New method for examining matches to fragments of 3+ charge or above useful for ETD (JW13)
    public int calcPeptideFragmentationHighCharge(int charge, ms_peptide peptide, int fragRule,
                                        Map<Integer, List<Double>> returnResiduePos2IonMasses) {
        int count = 0;
        double mrCalc = peptide.getMrCalc();
        boolean doublyCharged = false;
        //iterate through ion series
        ms_fragmentvector fragments = new ms_fragmentvector();
        aahelper.calcFragments(peptide, fragRule, doublyCharged, 0, mrCalc, msparserConstants.MASS_TYPE_MONO, fragments, err);
        for (int fragNo = 0; fragNo < fragments.getNumberOfFragments(); fragNo++) {
            ms_fragment fragment = fragments.getFragmentByNumber(fragNo);
            final int col = fragment.getColumn();
            final double mass = (((fragment.getMass() - 1) + charge) /charge);

            if (debug) System.out.println("Series"+ fragRule + "Charge:" + charge + "Column:" + col + "\tFragment ion:" + mass);

            //final double neutralLoss = fragment.getNeutralLoss();
            List<Double> masslist = returnResiduePos2IonMasses.get(col);
            if (masslist == null) masslist = new ArrayList<Double>();
            masslist.add(mass);
            returnResiduePos2IonMasses.put(col, masslist);
            count++;
        }

        return count;
    }


    public final double matchIons(final Map<Integer, List<Double>> pos2massCalc, final Double obsMass, final Double intensity,
                                  Set<Double> returnMatchedIons, Set<Integer> returnMatchedPos) {

        List<Double> foo = new ArrayList<Double>();
        return matchIons(pos2massCalc, obsMass, intensity, returnMatchedIons, returnMatchedPos, foo, foo);
    }

    public final double matchIons(final Map<Integer, List<Double>> pos2massCalc, final Double obsMass, final Double intensity,
                            Set<Double> returnMatchedIons, Set<Integer> returnMatchedPos,
                            List<Double> returnFragmentMassError, List<Double> returnFragmentMassErrorPPM) {

        double matchedIntensity = 0;
        for (Integer pepPos : pos2massCalc.keySet()) {
            for (Double calcMass : pos2massCalc.get(pepPos)) {
                double fragMassErr = obsMass - calcMass;
                double fragMassErrAbs = Math.abs(fragMassErr);
                if (fragMassErrAbs <= msmsTolDa) {
                    returnMatchedPos.add(pepPos);
                    returnMatchedIons.add(calcMass);
                    matchedIntensity = intensity;
                    //fragement mass error Da
                    returnFragmentMassError.add(fragMassErr);
                    //fragment mass error ppm
                    double ppm = fragMassErr * MIO / calcMass;
                    if (debug) System.out.println("matched ion: " + obsMass + "\tcalc ion: " + calcMass + "\tppm: " + ppm + "\tpos: " + pepPos);
                    returnFragmentMassErrorPPM.add(ppm);
                }
            }
        }

        return matchedIntensity;
    }

    public final double matchIon(final Double calcMass, final Double obsMass, final Double intensity) {
        double fragMassErr = obsMass - calcMass;
        double fragMassErrAbs = Math.abs(fragMassErr);
        if (fragMassErrAbs <= msmsTolDa) {
            return intensity;
        }
        return 0.0;
    }

    public final List<Double> matchIonsAndGetGygiPeptideScore(ms_inputquery query,
                                                              Map<Integer, List<Double>>pos2massCalc,
                                                              Map<Integer, Map<Double, Double>> peakDepth2mass2int) {
        List<Double> calcMasses = new ArrayList<Double>(200);
        for(Integer pos : pos2massCalc.keySet()) calcMasses.addAll(pos2massCalc.get(pos));

        double massMax = query.getMassMax();
        int[] successes = new int[peakDepth]; //low to high
        int[] trials = new int[peakDepth]; //low to high
        int trial = 0;
        int success = 0;

        //iterate from high intensity to low intensity ranked masses
        for(int rank = 0; rank<peakDepth; rank++) {
            Map<Double, Double> masses2int = peakDepth2mass2int.get(rank);
            if(masses2int == null) continue;
            for(double obsMass : masses2int.keySet()) {
                trial++;
                for (Double calcMass : calcMasses) {
                    if (Math.abs(obsMass - calcMass) < msmsTolDa) {
                        success++;
                    }
                }                           
            }
            successes[rank] = success;
            trials[rank]=trial;
        }

        //System.out.println("trials = " + Arrays.toString(trials));
        //System.out.println("successes = " + Arrays.toString(successes));

        List<Double> scores = new ArrayList<Double>();
        for(int i = 0; i< peakDepth; i++) {
            int k = successes[i];
            int n = trials[i];
            scores.add(MathHelper.logPBin(n, k, n/massMax*2*msmsTolDa));
        }
        return scores;
    }


    private Map<Double, Double> getMass2IntensityAdaptive(ms_inputquery query) {
        MassRangeList mrl = new MassRangeList(query.getMassMin(), query.getMassMax(), 10);
        for (int ions = 1; ions <= 3; ions++) {
            for (int peakNo = 1; peakNo <= query.getNumberOfPeaks(ions); peakNo++) {
                mrl.add(query.getPeakMass(ions, peakNo), query.getPeakIntensity(ions, peakNo));
            }
        }
        //mrl.toRCode();

        //this is a hack, needs fixing :)
        Map<Double, Double> int2mass = mrl.getInt2massOutliers();
        Map<Double, Double> mass2int = new HashMap<Double, Double>();
        for (Double intensity : int2mass.keySet()) {
            mass2int.put(int2mass.get(intensity), intensity);
        }
        return mass2int;
    }


    public void calcProteins(HashSet<String> query_rank) {
        HashMap<String, Set<String>> tempProt2pepSet = new HashMap<String, Set<String>>();
        int numQueries = resultsFile.getNumQueries();

        for (int queryN = 1; queryN <= numQueries; queryN++) {
            for (int rank = 1; rank <= maxRank; rank++) {
                if(! DoMascotExport.hasQueryRank(query_rank, queryN, rank)) break;

                String str;
                String sb = new StringBuilder().append("q").append(queryN).append("_p").append(rank).toString();
                if (targetMode)
                    str = resultsFile.getSectionValueStr(ms_mascotresfile.SEC_PEPTIDES, sb);
                else
                    str = resultsFile.getSectionValueStr(ms_mascotresfile.SEC_DECOYPEPTIDES, sb);

                str = str.trim();
                //System.out.println("str = " + str);
                if (str.equals("") || str.equals("-1")) continue;

                //q7160_p1=0,1579.784180,0.000866,11,ESISVSSEQLAQFR,53,0000000000000000,70.11,0001002010000000000,0,0;"IPI00215983":0:215:228:1,"IPI00788926":0:102:115:1,"IPI00796435":0:149:162:1,"IPI00798267":0:81:94:1
                String lr[] = str.split(";", 2);
                //q7160_p1 = 0, 1579.784180, 0.000866, 11, ESISVSSEQLAQFR, 53, 0000000000000000, 70.11, 0001002010000000000, 0, 0;
                String peptideStr = lr[0].split(",")[4]; //peptide
                //disabled score filter: introduces bias towards the decoy database search!
                String score = lr[0].split(",")[7]; //mascot score

                //"IPI00215983":0:215:228:1,"IPI00788926":0:102:115:1,"IPI00796435":0:149:162:1,"IPI00798267":0:81:94:1
                String temp[] = lr[1].split(",");
                //System.out.println("lr[1] = " + lr[1]);
                for (String s : temp) {
                    Matcher m = pattern.matcher(s);
                    m.find();
                    String proteinID = m.group(1);
                    Set<String> peps = tempProt2pepSet.get(proteinID);
                    if (peps == null) {
                        peps = new HashSet<String>();
                    }
                    peps.add(peptideStr);
                    tempProt2pepSet.put(proteinID, peps);

                    Integer numPeps = protein2numPeps.get(proteinID);
                    if (numPeps == null) {
                        numPeps = 0;
                    }
                    numPeps++;
                    protein2numPeps.put(proteinID, numPeps);
                }
            }
        }

        for (String proteinID : tempProt2pepSet.keySet()) {
            int numUniquePeps = tempProt2pepSet.get(proteinID).size();
            protein2uniqueNumPeps.put(proteinID, numUniquePeps);
        }

        tempProt2pepSet = null;
    }


    public void getPeptideCounts(int queryN, int rank,
                                 TreeMap<Integer, Set<String>> returnNumPeps2ProtIds,
                                 TreeMap<Integer, Set<String>> returnUniqueNumPeps2ProtIds) {

        //TREEMAP keeps it sorted !

        // Gather protein Information & find protein with most unique sequences and declare it the winner.
        // code: alternatively use getProteinsWithThisPepMatch method
        Set<String> proteinIDs = getProteinIDs(queryN, rank);
        for (String proteinID : proteinIDs) {
            int i;
            if (!protein2numPeps.containsKey(proteinID))
                i = 0; //only needed when a score filter is applied in calcProteins
            else i = protein2numPeps.get(proteinID);
            Set<String> ids = returnNumPeps2ProtIds.get(i);
            if (ids == null) {
                ids = new HashSet<String>();
                returnNumPeps2ProtIds.put(i, ids);
            }
            ids.add(proteinID);

            if (!protein2uniqueNumPeps.containsKey(proteinID)) i = 0;
            else i = protein2uniqueNumPeps.get(proteinID);
            ids = null;
            ids = returnUniqueNumPeps2ProtIds.get(i);
            if (ids == null) {
                ids = new HashSet<String>();
                returnUniqueNumPeps2ProtIds.put(i, ids);
            }
            ids.add(proteinID);
        }
    }

    public Set<String> getProteinIDs(int queryN, int rank) {
        String qp = new StringBuilder().append("q").append(queryN).append("_p").append(rank).toString();
        String str;
        if (targetMode) str = resultsFile.getSectionValueStr(ms_mascotresfile.SEC_PEPTIDES, qp).trim();
        else str = resultsFile.getSectionValueStr(ms_mascotresfile.SEC_DECOYPEPTIDES, qp).trim();
        if (str.equals("-1") || str.equals("")) return new HashSet<String>(); //no match
        ////System.out.println(queryN + "_" + rank + ":" + str);
        //q7160_p1=0,1579.784180,0.000866,11,ESISVSSEQLAQFR,53,0000000000000000,70.11,0001002010000000000,0,0;"IPI00215983":0:215:228:1,"IPI00788926":0:102:115:1,"IPI00796435":0:149:162:1,"IPI00798267":0:81:94:1
        String lr[] = str.split(";", 2);
        //"IPI00215983":0:215:228:1,"IPI00788926":0:102:115:1,"IPI00796435":0:149:162:1,"IPI00798267":0:81:94:1
        Matcher m = pattern.matcher(lr[1]);
        Set<String> proteinIDs = new HashSet<String>();
        while (m.find()) {
            proteinIDs.add(m.group(1));
        }
        return proteinIDs;
    }

    /*
    public StringBuffer exportSearchInfo() throws IOException {
        StringBuffer result = new StringBuffer();
        // user name
        result.append(DoMascotExport.SEARCHINFO.USER + "\t" + searchParams.getUSERNAME() + "\n");

        //user email
        result.append(DoMascotExport.SEARCHINFO.MAIL + "\t" + searchParams.getUSEREMAIL() + "\n");

        // search title
        result.append(DoMascotExport.SEARCHINFO.TITLE + "\t" + searchParams.getCOM() + "\n");

        // file
        result.append(DoMascotExport.SEARCHINFO.FILE + "\t" + searchParams.getFILENAME() + "\n");

        // database
        result.append(DoMascotExport.SEARCHINFO.TITLE + "\t" + searchParams.getDB() + "\n");

        // tax
        result.append(DoMascotExport.SEARCHINFO.TAX + "\t" + searchParams.getTAXONOMY() + "\n");

        // enzyme
        result.append(DoMascotExport.SEARCHINFO.ENZYME + "\t" + searchParams.getCLE() + "\n");

        // mc
        result.append(DoMascotExport.SEARCHINFO.MC + "\t" + searchParams.getPFA() + "\n");

        // fixed
        result.append(DoMascotExport.SEARCHINFO.FIXED + "\t" + searchParams.getMODS() + "\n");

        // variable
        result.append(DoMascotExport.SEARCHINFO.VARIABLE + "\t" + searchParams.getIT_MODS() + "\n");

        // quant
        result.append(DoMascotExport.SEARCHINFO.QUANT + "\t" + searchParams.getQUANTITATION() + "\n");

        // pep tol + unit
        result.append(DoMascotExport.SEARCHINFO.MSTOL + "\t" + searchParams.getTOL() + "\n");
        result.append(DoMascotExport.SEARCHINFO.MSTOLUNIT + "\t" + searchParams.getTOLU() + "\n");

        // msms tol + uni
        result.append(DoMascotExport.SEARCHINFO.MSMSTOL + "\t" + searchParams.getITOL() + "\n");
        result.append(DoMascotExport.SEARCHINFO.MSMSTOLUNIT + "\t" + searchParams.getITOLU() + "\n");

        // charge
        result.append(DoMascotExport.SEARCHINFO.CHARGE + "\t" + searchParams.getCHARGE() + "\n");

        // mono / avg mass
        result.append(DoMascotExport.SEARCHINFO.MASSTYPE + "\t" + searchParams.getMASS() + "\n");

        // instrument
        result.append(DoMascotExport.SEARCHINFO.INSTRUMENT + "\t" + searchParams.getINSTRUMENT() + "\n");
        return result;
    }
    */


    private static void printFragmentsTable(ms_fragmentvector fragments) {
        System.out.print("Number of fragments: ");
        System.out.println(fragments.getNumberOfFragments());

        System.out.println("Col\tStart\tEnd\tLabel\t\t Mass\t  NL\tName\tImmon\tIntern\tReg");
        int i;
        for (i = 0; i < fragments.getNumberOfFragments(); i++) {
            ms_fragment frag = fragments.getFragmentByNumber(i);
            DecimalFormat fragMassFmt = new DecimalFormat("0.00");

            System.out.print(frag.getColumn());
            System.out.print("\t");
            System.out.print(frag.getStart());
            System.out.print("\t");
            System.out.print(frag.getEnd());
            System.out.print("\t");
            System.out.print(padding(frag.getLabel(), 10, " "));
            System.out.print("\t");
            System.out.print(padout(fragMassFmt.format(frag.getMass()), 7, " "));
            System.out.print("\t");
            System.out.print(fragMassFmt.format(frag.getNeutralLoss()));
            System.out.print("\t");
            System.out.print(frag.getSeriesName());
            System.out.print("\t");
            if (frag.isImmonium())
                System.out.print("1\t");
            else
                System.out.print("0\t");
            if (frag.isInternal())
                System.out.print("1\t");
            else
                System.out.print("0\t");
            if (frag.isRegular())
                System.out.print("1\n");
            else
                System.out.print("0\n");
        }
        System.out.println();
    }

    private static String padout(String toPrint, int length, String padding) {

        String returnValue = "";

        if (toPrint.length() < length) {
            for (int queryLoop = 1; queryLoop <= length - toPrint.length(); queryLoop++) {
                returnValue += padding;
            }
            returnValue += toPrint;
        } else returnValue = toPrint;

        return returnValue;
    }

    private static String padding(String toPrint, int length, String padding) {

        String returnValue = toPrint;

        if (toPrint.length() < length) {
            for (int queryLoop = 1; queryLoop <= length - toPrint.length(); queryLoop++) {
                returnValue += padding;
            }
        }
        return returnValue;
    }

    public double matchAIon(ms_peptide p){
        Double match = 0.0;


        return match;
    }
}
