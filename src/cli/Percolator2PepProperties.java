package cli;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import java.io.*;
import java.util.HashMap;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class Percolator2PepProperties {

    @Option(name = "-pin", required = true, usage = "Percolator result file path")
    private File percolatorIn = null;

    public static void main(String[] args) throws IOException {
        Percolator2PepProperties bean = new Percolator2PepProperties();
        CmdLineParser parser = new CmdLineParser(bean);
        try {
            parser.setUsageWidth(140);
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.err);
            return;
        }
        bean.analyse();
    }

    private void analyse() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(percolatorIn));
        String line = br.readLine(); //header
        if (line == null) throw new NullPointerException("Results file is empty");

        //find the correct columns numbers
        String[] colNames = line.split("\t");
        Integer query_col = null, rank_col = null, q_col = null, pep_col = null, peptide_col = null;
        for (int i = 0; i < colNames.length; i++) {
            if (colNames[i].equals("query") || colNames[i].equals("PSMId")) query_col = i;
            else if (colNames[i].equals("rank")) rank_col = i;
            else if (colNames[i].equals("q-value")) q_col = i;
            else if (colNames[i].equals("posterior_error_probability")) pep_col = i;
            else if (colNames[i].equals("peptide")) peptide_col = i;
        }
        if (query_col == null || rank_col == null || q_col == null || pep_col == null || peptide_col == null) {
            throw new RuntimeException("Results file does not contain all information: scan, q-value, peptide");
        }

        //read Percolator resutls file
        HashMap<Integer, Integer> krCount = new HashMap<Integer, Integer>();
        int acceptedPeptides = 0;
        while ((line = br.readLine()) != null) {
            String cols[] = line.split("\t");
            double qval = Double.parseDouble(cols[q_col]);
            if(qval > 0.01) continue;
            acceptedPeptides++;
            String peptide = cols[peptide_col];
            
            Integer kr = KR(peptide);
            Integer sum = krCount.get(kr);
            if(sum == null) sum = 0;
            sum++;
            krCount.put(kr, sum);
        }
        System.out.println("acceptedPeptides = " + acceptedPeptides);
        br.close();

        System.out.println(krCount);
    }

    private int KR(String peptide) {
        peptide = peptide.toUpperCase();
        int kr = 0;
        for(int i = 0; i<peptide.length(); i++) {
            String c = peptide.substring(i, i+1);
            if(c.equals("K") || c.equals("R")) kr++;
        }
        return kr;
    }
}
