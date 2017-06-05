package deprecated;

import java.util.*;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class CountPepsAndProts {


    public static void main(String[] args) throws Exception {

        Set<String> uPeps = new HashSet<String>();
        List<String> peps = new ArrayList<String>();
        Set<String> prots = new HashSet<String>();

        BufferedReader a = new BufferedReader(
            new FileReader("/Users/mb8/Desktop/MascotPercolator/yeast.round2/11087.11088.extended.result.txt"));

        String line = null;
        a.readLine(); //header
        while ((line = a.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, "\t");

            String id = st.nextToken();
            if (id.endsWith("class:-1")) break;

            st.nextToken(); // query
            st.nextToken(); // rank
            st.nextToken(); // score
            double q = Double.parseDouble(st.nextToken());
            st.nextToken(); //pep
            String peptide = st.nextToken();
            st.nextToken(); //modpep
            String proteins = st.nextToken();
            StringTokenizer protTokeniszer = new StringTokenizer(proteins, ";");
            String protein = protTokeniszer.nextToken(); //only top hit protein

            if (q <= 0.01) {
                uPeps.add(peptide);
                peps.add(peptide);
                prots.add(protein);
            }
        }

        System.out.println("uPeps = "   + uPeps.size());
        System.out.println("peps = " + peps.size());
        System.out.println("prots = " + prots.size());
    }
}