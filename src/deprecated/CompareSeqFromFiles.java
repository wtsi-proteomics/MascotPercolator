package deprecated;

import org.apache.commons.collections.CollectionUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class CompareSeqFromFiles {

    public static void main(String[] args) throws Exception {

        List<String> perc = new ArrayList<String>();
        List<String> mperc = new ArrayList<String>();

        String line = null;
        BufferedReader a = new BufferedReader(new FileReader("/Users/mb8/Desktop/yeast/yeast-01.results.1rank.seqPercolatorV104.txt"));
        a.readLine();
        while ((line = a.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            st.nextToken();
            st.nextToken();
            String qval = st.nextToken();
            double q = Double.parseDouble(qval);
            String foo;
            if (q <= 0.01) {
                st.nextToken();
                String seq = st.nextToken();
                String seqA = seq.substring(0,1);
                String seqB = seq.substring(seq.length()-3, seq.length()-2);
                if((seqA.equals("R") || seqA.equals("K")) && (seqB.equals("R") || seqB.equals("K"))) {;
                    seq = seq.substring(2, seq.length() - 2);
                    perc.add(seq.replaceAll("K", "Q").replaceAll("I", "L"));
                }
            }
        }
        System.out.println("perc.size() = " + perc.size());


        a = new BufferedReader(new FileReader("/Users/mb8/Desktop/yeast/11087.3Da.exNumProt.incInt.incDScore/11087-11088-11089.summary.txt"));
        a.readLine();
        while((line = a.readLine()) != null ) {
            StringTokenizer st = new StringTokenizer(line, "\t");
            String id = st.nextToken();
            if(id.endsWith("class:-1")) break;
            st.nextToken();
            st.nextToken();
            double q = Double.parseDouble(st.nextToken());
            if(q <= 0.01) {
                mperc.add(st.nextToken().replaceAll("K", "Q").replaceAll("I", "L"));
            }
        }
        System.out.println("mperc.size() = " + mperc.size());

        System.out.println(CollectionUtils.intersection(perc, mperc).size());
        System.out.println(CollectionUtils.subtract(perc, mperc).size());
        System.out.println(CollectionUtils.subtract(mperc, perc).size());
    }
}
