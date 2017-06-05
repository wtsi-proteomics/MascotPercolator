package cli;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import util.MascotDatFiles;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class Percolator2MascotDat {

    @Option(name = "-in", required = true, usage = Messages.SUMMARYFILEINPUT)
    private File pin = null;
    @Option(name = "-inDecoy", required = false, usage = Messages.DECOYSUMMARYFILEINPUT)
    private File pDecoyIn = null;
    @Option(name = "-dat", required = true, usage = "Mascot search log ID")
    private String mascotIn = null;
    @Option(name = "-mout", required = true, usage = "Modified Mascot out dat file path")
    private File out = null;
    @Option(name = "-ex", required = false, usage = "(optional) File path to a peptide exclusion list such as contaminants")
    private File ex = null;
    FileWriter fw = null;

    private Mode mode = Mode.passThrough;

    // fields used in peptide section of the results file:

    //last processed query number
    private String lastQuery;
    //q87_p1=0,941.470566,0.000762,7,DDSPDLPK,16,4000000030,55.18,0001002000000000000,0,0;"ProteinA":0:107:114:1,"ProteinB":0:131:138:1
    private final Pattern resultsPattern = Pattern.compile("^q(\\d+)_p(\\d+)=(.*)");
    //q895_p10_terms=K,L
    //q895_p10_primary_nl = 02000000000000
    //q895_p10_subst = 6, X, N
    private final Pattern supportingInfoPattern = Pattern.compile("^q(\\d+)_p(\\d+)(_.+)");
    //temp storage of sorted PEPs
    private TreeMap<Double,List<String>> pep2rank = new TreeMap<Double, List<String>>(Collections.reverseOrder());
    //temp storage of new mascot output
    private HashMap<String,String> rank2text = new HashMap<String, String>(15);

    //fields used in the summary section of the results file:
    private final Pattern qmatchPattern = Pattern.compile("^(qmatch\\d+=)\\d+");
    private final Pattern qplugholePattern = Pattern.compile("^(qplughole\\d+=)\\d+");
    private final Pattern warnPattern = Pattern.compile("^warn(\\d+)=.*");

    private enum Mode {
        passThrough, peptides, decoyPeptides, summary, header, skip };

    public static void main(String[] args) throws IOException {
        Percolator2MascotDat bean = new Percolator2MascotDat();
        CmdLineParser parser = new CmdLineParser(bean);
        try {
            parser.setUsageWidth(140);
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.err);
            return;
        }
        bean.convert();
    }

    private Percolator2MascotDat() {
    }


    public Percolator2MascotDat(File percolatorResults, String mascotDat, File mascotDatOut) {
        this.pin = percolatorResults;
        this.mascotIn = mascotDat;
        this.out = mascotDatOut;
    }

    public Percolator2MascotDat(File percolatorResults, String percolatorDecoyResults, String mascotDat, File mascotDatOut) {
        this(percolatorResults, mascotDat,  mascotDatOut);
        this.pDecoyIn = new File(percolatorDecoyResults);
    }

    public void convert() throws IOException {
        ////////////////////////////////////
        // read peptide exclusion list    //
        ////////////////////////////////////
        BufferedReader br = null;
        String line = null;
        Set<String> toBeExcludedPeps = null;
        if(ex != null) {
            br = new BufferedReader(new FileReader(ex));
            toBeExcludedPeps = new HashSet<String>();
            while ((line = br.readLine()) != null) {
                toBeExcludedPeps.add(line.trim().toUpperCase());
            }
        }

        ////////////////////////////////////
        // read Mascot Percolator results //
        ////////////////////////////////////
        br = new BufferedReader(new FileReader(pin));
        line = br.readLine(); //header
        if (line == null) throw new NullPointerException("Results file is empty");

        //find the correct columns numbers
        String[] colNames = line.split("\t");
        Integer psmID_col = null, q_col = null, pep_col = null, peptide_col = null;
        for (int i = 0; i < colNames.length; i++) {
            if (colNames[i].equals("PSMId")) psmID_col = i;
            else if (colNames[i].equals("q-value")) q_col = i;
            else if (colNames[i].equals("posterior_error_probability")) pep_col = i;
            else if (colNames[i].equals("posterior_error_prob")) pep_col = i;
            else if (colNames[i].equals("peptide")) peptide_col = i;
        }
        if (psmID_col == null || q_col == null || pep_col == null || peptide_col == null) {
            throw new RuntimeException("Results file does not contain all information: scan, q-value, peptide");
        }

        //read Percolator results file
        HashMap<String, DataBean> percolatorResults = new HashMap<String, DataBean>(100000);
        while ((line = br.readLine()) != null) {
            String cols[] = line.split("\t");
            String[] query_rank = cols[psmID_col].split(";");
            String query = query_rank[0].split("query:")[1];
            String rank = query_rank[1].split("rank:")[1];
            String qval = cols[q_col];
            String pep = cols[pep_col];
            String peptide = cols[peptide_col];
            //when peptide is reported with pre/postfix: X.PEPTIDE.X
            if (peptide.contains(".")) {
                peptide = peptide.split("\\.")[1];
            }
            //exclude unwanted peptides
            if(toBeExcludedPeps != null && toBeExcludedPeps.contains(peptide.toUpperCase())) continue;
            //otherwise read Percolator results
            percolatorResults.put(getId(query, rank), new DataBean(qval, pep));
        }
        br.close();

        //read Percolator decoy results file
        HashMap<String, DataBean> percolatorDecoyResults = new HashMap<String, DataBean>(100000);
        if(pDecoyIn != null) {
            br = new BufferedReader(new FileReader(pDecoyIn));
            line = br.readLine(); //header
            if (line == null) throw new NullPointerException("Results file is empty");
            //read Percolator results file
            while ((line = br.readLine()) != null) {
                String cols[] = line.split("\t");
                String[] query_rank = cols[psmID_col].split(";");
                String query = query_rank[0].split("query:")[1];
                String rank = query_rank[1].split("rank:")[1];
                String qval = cols[q_col];
                String pep = cols[pep_col];
                String peptide = cols[peptide_col];
                //when peptide is reported with pre/postfix: X.PEPTIDE.X
                if (peptide.contains(".")) {
                    peptide = peptide.split("\\.")[1];
                }
                //exclude unwanted peptides
                if (toBeExcludedPeps != null && toBeExcludedPeps.contains(peptide.toUpperCase())) continue;
                //otherwise read Percolator results
                percolatorDecoyResults.put(getId(query, rank), new DataBean(qval, pep));
            }
            br.close();
        }

        /////////////////////////////
        //Read Mascot results file //
        /////////////////////////////

        //Match query/rank and replace Mascot score with -10log10(pep)
        //Re-order ranks based on new MascotScore
        int highestExistingWarningNo = -1;
        fw = new FileWriter(out);
        br = new BufferedReader(new FileReader(MascotDatFiles.getDatFileFromLogID(mascotIn)));
        while ((line = br.readLine()) != null) {
            if (line.startsWith("DECOY = 1") && pDecoyIn ==null) {
                fw.write("DECOY = 0");
                continue;
            }

            // "--" occurs at end of a section/mode, therefore clean up things for current mode
            if(line.startsWith("--")) {
                if (mode == Mode.header) {
                    highestExistingWarningNo = highestExistingWarningNo >= 0 ? highestExistingWarningNo + 1 : 0;
                    fw.write("warn" + highestExistingWarningNo + "=Result file re-written by Mascot Percolator using scores derived from Percolator PEP values\n");
                }
                if (mode == Mode.peptides) {
                    writeReRankedMascotText(pep2rank, rank2text);
                    pep2rank = new TreeMap<Double, List<String>>(Collections.reverseOrder());
                    rank2text = new HashMap<String, String>(15);
                }
                if (mode == Mode.decoyPeptides) {
                    writeReRankedMascotText(pep2rank, rank2text);
                    pep2rank = new TreeMap<Double, List<String>>(Collections.reverseOrder());
                    rank2text = new HashMap<String, String>(15);
                }
                
            // identify new secion type
            } else if(line.startsWith("Content-Type:")) {
                mode = Mode.passThrough; //default mode
                if (line.contains("header")) mode = Mode.header;
                if (line.contains("peptides")) mode = Mode.peptides;
                if (line.contains("summary")) mode = Mode.summary;
                if(pDecoyIn != null) {
                    if (line.contains("decoy_peptides")) mode = Mode.decoyPeptides;
                } else {
                    if (line.contains("decoy")) mode = Mode.skip;
                }
            }

            //act upon current mode
            switch(mode) {
                case skip:
                    continue;
                case passThrough:
                    fw.write(line + "\n"); // write out other things right away if it is not content type peptides
                    continue;
                case header:
                    fw.write(line + "\n");
                    Matcher m = warnPattern.matcher(line);
                    if (m.find()) {
                        int warn = Integer.parseInt(m.group(1));
                        if(warn > highestExistingWarningNo) highestExistingWarningNo = warn;
                    }
                    continue;
                case peptides:
                    peptides(line, percolatorResults, resultsPattern, supportingInfoPattern);
                    continue;
                case decoyPeptides:
                    peptides(line, percolatorDecoyResults, resultsPattern, supportingInfoPattern);
                    continue;
                case summary: summary(line);
            }
        }

        br.close();
        fw.flush();
        fw.close();
    }

    private String getId(String query, String rank) {
        return query + ":" + rank;
    }

    private void peptides(String line, HashMap<String, DataBean> percolatorResults, Pattern pattern1, Pattern pattern2) throws IOException {
        Matcher m = pattern1.matcher(line);
        boolean matched = false; //ugly, but for speed up since otherwise I'd have to call Matcher msub = supportingInfoPattern.matcher(line) here as well.
        if(m.find()) {
            matched = true;
            String query = m.group(1);
            String rank = m.group(2);
            String values = m.group(3);
            String id = getId(query, rank);

            if (lastQuery != null && !lastQuery.equals(query)) {
                //Each query consists of up to 10 ranks, that need resorting.
                //Therefore we scan all lines until a new query comes up and write out the old stuff.
                writeReRankedMascotText(pep2rank, rank2text);
                pep2rank = new TreeMap<Double, List<String>>(Collections.reverseOrder());
                rank2text = new HashMap<String, String>(15);
            }

            if (values.equals("-1")) {
                fw.write(line + "\n");
            } else {
                DataBean db = percolatorResults.get(id);
                if(db != null) { // we only care about ranks that were found with Percolator
                    Double pep = null;
                    try {
                        pep = -10 * Math.log10(Double.valueOf(db.getPep()));
                    } catch(NumberFormatException e) {
                        e.printStackTrace();
                        System.err.println("It appears that Percolator did not succeed calculating PEP values,\n" +
                            "Dat file generation failed");
                        out.delete();
                        System.exit(-1);
                    }

                    StringWriter sw = new StringWriter();
                    String[] semi = values.split(";");
                    String[] comma = semi[0].split(",");
                    sw.append("=");
                    sw.append(comma[0]).append(",");
                    sw.append(comma[1]).append(",");
                    sw.append(comma[2]).append(",");
                    sw.append(comma[3]).append(",");
                    sw.append(comma[4]).append(","); //peptide
                    sw.append(comma[5]).append(",");
                    sw.append(comma[6]).append(",");
                    sw.append(Double.toString(pep)).append(",");
                    sw.append(comma[8]).append(",");
                    sw.append(comma[9]).append(",");
                    sw.append(comma[10]).append(";");
                    sw.append(semi[1]);

                    //rank 2 text
                    if (rank2text.containsKey(rank)){
                        String text = rank2text.get(rank);
                        rank2text.put(rank, text + "$$$" + sw.toString());
                    } else {
                        rank2text.put(rank, sw.toString());
                    }
                    //pep 2 ranks
                    List<String> ranks = pep2rank.get(pep);
                    if(ranks == null) {
                        ranks = new ArrayList<String>();
                    }
                    ranks.add(rank);
                    pep2rank.put(pep, ranks);
                }
            }
            lastQuery = query;
        }

        if(!matched) {
            Matcher msub = pattern2.matcher(line);
            if(msub.find()) {
                String query = msub.group(1);
                String rank = msub.group(2);
                String id = query + ":" + rank;
                DataBean db = percolatorResults.get(id);
                if (db != null) {
                    if(line.contains("_db=")){
                        if (lastQuery != null && !lastQuery.equals(query)) {
                            //Each query consists of up to 10 ranks, that need resorting.
                            //Therefore we scan all lines until a new query comes up and write out the old stuff.
                            writeReRankedMascotText(pep2rank, rank2text);
                            pep2rank = new TreeMap<Double, List<String>>(Collections.reverseOrder());
                            rank2text = new HashMap<String, String>(15);
                        }
                        rank2text.put(rank, msub.group(3));
                    } else {
                        if(!query.equals(lastQuery)) throw new RuntimeException("Same query number as in last line expected: "
                                + "last query# " + lastQuery
                                + ", this query# " + query);

                        String text = rank2text.get(rank);
                        rank2text.put(rank, text + "$$$" + msub.group(3));
                    }
                }
                lastQuery = query;
            } else {
                fw.write(line + "\n");
            }
        }
    }

    private void summary(String line) throws IOException {
        Matcher m = qplugholePattern.matcher(line);
        if (m.find()) {
            fw.write(m.group(1) + "1\n");
            return;
        }

        m = qmatchPattern.matcher(line);
        if (m.find()) {
            fw.write(m.group(1) + "1\n");
            return;
        }

        fw.write(line + "\n");
    }

    private void writeReRankedMascotText(TreeMap<Double, List<String>> pep2rank, HashMap<String, String> rank2text) throws IOException {
        int i = 1;
        for(Double pep : pep2rank.keySet()) { // loop reverse order tree
            List<String> ranks = pep2rank.get(pep);
            for(String rank : ranks) {
                String text = rank2text.get(rank);
                String[] texts = text.split("\\$\\$\\$");
                final String id = "q" + lastQuery + "_p" + i;
                for(String t : texts) {
                    fw.write(id + t + "\n");
                }
                i++;
            }
        }
    }

    class DataBean {
        private String q;
        private String pep;

        public DataBean(String q, String pep) {
            this.q = q;
            this.pep = pep;
        }

        public String getQ() {
            return q;
        }

        public String getPep() {
            return pep;
        }
    }
}
