package cli;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import java.io.*;
import java.util.*;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class FDRAnalysis {

    @Option(name = "-stdPeps", required = true, usage = Messages.EXPECTEDPEPS)
    private File o_std = null;
    @Option(name = "-pin", required = true, usage = Messages.SUMMARYFILEINPUT)
    private File o_sum = null;
    @Option(name = "-from", required = false, usage = "(optional) Minimum q-value; default 0.0")
    private double o_from = 0.0;
    @Option(name = "-to", required = false, usage = "(optional) Maximum q-Value; default 0.3")
    private double o_to = 0.22;
    @Option(name = "-inc", required = false, usage = "(optional) Increment q-Value by (defines the number of steps " +
        "between min and max q-value); default 0.002")
    private double o_inc = 0.002;

    //use passThrough out if not set otherwise
    private PrintStream ps = System.out;

    enum Type { TRYPTIC, SEMI, NOSPECIFIC, CHYMO, SEMICHYMO, NTERM, CTERM };

    public static void main(String[] args) throws IOException {
        FDRAnalysis bean = new FDRAnalysis();

        //List<String> prots = new ArrayList<String>();
        //prots.add("MABCDRFGHIJKLMN");
        //String peptide = "FGHIJ";
        //System.out.println(bean.findType(prots, peptide));

        CmdLineParser parser = new CmdLineParser(bean);
        try {
            parser.setUsageWidth(140);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR + "\n");
        } catch (CmdLineException e) {
            System.err.println(Messages.AUTHOR + "\nDescription:\n" +
                "FDRAnalysis takes as an input the summary file written by MascotPercolator and a protein or peptide file,\n" +
                "with all protein or peptide entries that are believed or known to be correct. This enables a comparison\n" +
                "between the estimated and the actual false discovery rate (q-value).");
            System.err.println("\nUsage\n" +
                "java -Xmx512m -cp MascotPercolator.jar cli.FDRAnalysis [options ...]");
            System.err.println("\nOptions (replacing the \"[options ...]\" expression above):");
            parser.printUsage(System.err);
            System.err.println("\nError:");
            System.err.println(e.getMessage());
            System.err.println("");
            return;
        }
        bean.analyse();
    }

    private void analyse(File stdProteinFile, File percolatorSummary) throws IOException {
        this.o_std = stdProteinFile;
        this.o_sum = percolatorSummary;
        analyse();
    }

    public void analyse(File stdProteinFile, File percolatorSummary, PrintStream ps) throws IOException {
        this.ps = ps;
        analyse(stdProteinFile, percolatorSummary);
    }

    public void analyse() throws IOException {

        //set up passThrough set of peptides that are expected in the sample
        BufferedReader br = new BufferedReader(new FileReader(o_std));
        StringBuffer sb = new StringBuffer();
        Map<String, String> proteinSeqs = new HashMap<String, String>();
        StringBuffer allProteinSeqSB = new StringBuffer();
        String fastaHeader = null;
        String str = null;
        while ((str = br.readLine()) != null) {
            if(str.startsWith(">")) {
                if(sb.length() > 0) proteinSeqs.put(fastaHeader, sb.toString());
                sb = new StringBuffer();
                fastaHeader = str;
                continue;
            }
            str = str.trim().replaceAll("Q", "K").replaceAll("L", "I");
            sb.append(str);
            allProteinSeqSB.append(str);
        }
        if (sb.length() > 0) proteinSeqs.put(fastaHeader, sb.toString());
        String allProteinSeq = allProteinSeqSB.toString();
        br.close();


        //check identifications whether they are part of the expected peptide set or not
        ps.println("q-value\tFDRofProteinStandard");
        HashSet<String> cacheCorrect = new HashSet<String>();
        HashSet<String> cacheInCorrect = new HashSet<String>();
        int tot = 0, notFound = 0, cterm = 0, nterm = 0, nospec = 0, semi = 0, tryp = 0, chymo = 0, semiChymo = 0;
        for (double q = o_from; q <= o_to; q += o_inc) {
            HashSet<String> hs = new HashSet<String>();
            br = new BufferedReader(new FileReader(o_sum));
            br = new BufferedReader(br);
            double correct = 0;
            double incorrect = 0;

            str = br.readLine(); //header
            if(str == null) throw new NullPointerException("Results file is empty");

            //find the columns to be analysed
            String[] colNames = str.split("\t");
            final int nodef = -1;
            int scan_col = nodef;
            int q_col = nodef;
            int pep_col = nodef;
            for(int i=0; i<colNames.length; i++) {
                if (colNames[i].equals("query") || colNames[i].equals("PSMId")) scan_col = i;
                else if (colNames[i].equals("q-value")) q_col = i;
                else if (colNames[i].equals("peptide")) pep_col = i;
            }
                 
            if(scan_col == nodef || q_col == nodef || pep_col == nodef)
                throw new RuntimeException ("Results file does not contain all information: scan, q-value, peptide");

            //test results
            while ((str = br.readLine()) != null) {
                String cols[] = str.split("\t");
                //String scan = cols[scan_col]; //scan
                //if(! scan.trim().endsWith("class:1")) continue;
                String qvalueStr = cols[q_col];
                String peptide = cols[pep_col];
                if(peptide.contains(".")) {
                    peptide = peptide.split("\\.")[1]; //when peptide is reported with pre/postfix: X.PEPTIDE.X
                }

                //very specifically test whether the peptide is tryptic, semitryptic or a result of a non specific cleavage
                if (q == 0.01 && Double.parseDouble(qvalueStr) < 0.01) {
                    tot++;
                    String s = peptide.replaceAll("Q", "K").replaceAll("L", "I");
                    Type t = findType(proteinSeqs, s, str);
                    if(t == null) notFound++;
                    if(t != null) { //if peptide was found, what type is it ...
                        switch (t) {
                        case CTERM:
                            cterm++; break;
                        case NTERM:
                            nterm++; break;
                        case NOSPECIFIC:
                            nospec++; break;
                        case SEMI:
                            semi++; break;
                        case TRYPTIC:
                            tryp++; break;
                        case CHYMO:
                            chymo++; break;
                        case SEMICHYMO:
                            semiChymo++; 
                        }
                    }
                }


                if (Double.parseDouble(qvalueStr) < q) {
                    String s = peptide.replaceAll("Q", "K").replaceAll("L", "I");
                    //String s = st.nextToken();
                    if (cacheCorrect.contains(s)) {
                        correct++;
                        continue;
                    }
                    else if(cacheInCorrect.contains(s)) {
                        incorrect++;
                        continue;
                    }

                    //else find it first ...
                    if (allProteinSeq.contains(s)) {
                        correct++;
                        cacheCorrect.add(s);
                    } else {
                        incorrect++;
//if (q < 0.01) { System.out.println(peptide); }
                        cacheInCorrect.add(s);
                        //System.out.println(s);
                        hs.add(s);
                    }
                }
            }
            br.close();
            double fdr = (incorrect / (correct + incorrect));
            ps.println(q + "\t" + fdr);
        }
//        System.out.println("tot = " + tot);
//        System.out.println("notFound = " + notFound);
//        System.out.println("nterm = " + nterm);
//        System.out.println("cterm = " + cterm);
//        System.out.println("tryp = " + tryp);
//        System.out.println("semi = " + semi);
//        System.out.println("chymo = " + chymo);
//        System.out.println("semiChymo = " + semiChymo);
//        System.out.println("nospec = " + nospec);
//
//        System.out.println("aaCounter = " + new TreeMap(aaCounter));

        for(String id : id2peps.keySet()) {
//            System.out.println("id = " + id);
            List<String> peps = id2peps.get(id);
            for(String pep : peps) { 
//              System.out.println("\t" + pep);
            }
        }

        ps.flush();
    }

    Map<String, Integer> aaCounter = new HashMap<String, Integer>();
    Map<String, List<String>> id2peps = new HashMap<String, List<String>>();


    public Type findType(Map<String, String> ids2protseq, String pep, String line) {
        Type bestTypeFound = null; // null = peptide not found
        int len = pep.length();
        for(String id : ids2protseq.keySet()) {
            String prot = ids2protseq.get(id);
            int i = prot.indexOf(pep);
            if(i < 0) continue; //peptide not part of the expected peptides

            if(i < 2) return Type.NTERM; //nterm + NME processing
            if((i + len) ==  prot.length()) return Type.CTERM; //cterm

            String prefix = prot.substring(i-1,i);
    //System.out.println("prefix = " + prefix);
            boolean pre = false;
            if(prefix.equals("K") || prefix.equals("R")) pre = true;
            String last = prot.substring(i + len - 1, i + len);
    //System.out.println("last = " + last);
            boolean post = false;
            if (last.equals("K") || last.equals("R")) post = true;

            if(pre && post) return Type.TRYPTIC; //best answer
            if(pre || post) bestTypeFound = Type.SEMI; //ok answer, try to find a tryptic answer in the other proteins

            if (prefix.equals("F") || prefix.equals("L") || prefix.equals("W") || prefix.equals("Y") || prefix.equals("G")) pre = true;
            if (last.equals("F") || last.equals("L") || last.equals("W") || last.equals("Y") || prefix.equals("G")) post = true;
            if (pre && post) return Type.CHYMO; //best answer
            if (bestTypeFound == null && (pre || post)) bestTypeFound = Type.SEMICHYMO;

            else if(bestTypeFound == null) { //so, what's all the rest ?
                List<String> protPeps = id2peps.get(id);
                if(protPeps == null) protPeps = new ArrayList<String>();
                protPeps.add(prefix + "." + pep + "\t" + line);
                id2peps.put(id, protPeps);

                Integer pref = aaCounter.get("N:" + prefix);
                if(pref == null) {
                    pref = 0;
                }
                pref++;
                aaCounter.put("N:" + prefix, pref);

                Integer postf = aaCounter.get("C:" + last);
                if (postf == null) {
                    postf = 0;
                }
                postf++;
                aaCounter.put("C:" + last, postf);

                bestTypeFound = Type.NOSPECIFIC; //otherwise, it is non specific
            }
        }

        //if (bestTypeFound != null && bestTypeFound.equals(Type.NOSPECIFIC)) System.out.println("bestTypeFound = " + bestTypeFound + "\n");

        return bestTypeFound;
    }
}
