package deprecated;

import org.apache.commons.collections.CollectionUtils;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class CmpPepSetsVennDiagram {
    public static void main(String[] args) throws IOException {
        Set<String> percS = new HashSet<String>();
        Set<String> percM = new HashSet<String>();

        BufferedReader br = new BufferedReader(new FileReader("/Users/mb8/Desktop/MascotPercolator/yeast.round2/percolator.sequest.107.result.txt"));
        String line = null;
        br.readLine(); //header
        while ((line = br.readLine()) != null) {
            String el[] = line.split("\t");
            if(Double.parseDouble(el[2]) < 0.01) {
                String pep = el[4].split("\\.")[1];
                percS.add(pep);
            }
        }

        br = new BufferedReader(new FileReader("/Users/mb8/Desktop/MascotPercolator/yeast.round2/11087.11088.extended.result.txt"));
        line = null;
        br.readLine(); //header
        while ((line = br.readLine()) != null) {
            String el[] = line.split("\t");
            if (Double.parseDouble(el[4]) < 0.01) {
                String pep = el[6];
                percM.add(pep);
            }
        }

        int inter = CollectionUtils.intersection(percS, percM).size();
        System.out.println("intersection: " + inter);
        System.out.println("onlyPerc: " + (percS.size()-inter));
        System.out.println("onlyPerc: " + (percM.size()-inter));

    }
}
