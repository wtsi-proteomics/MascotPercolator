package deprecated;

import java.util.*;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class MassRangeList {

    //intput
    private int min;
    private int max;
    private int minPeaksPerBin;
    private Map<Double, Double> int2mass = new IdentityHashMap<Double, Double>();

    //internal
    private List<MassRange> massRanges = new ArrayList<MassRange>();
    private boolean wasOptimised = false;

    public MassRangeList(double min, double max, int minPeaks) {
        this.max = (int)max;
        this.min = (int)min;
        this.minPeaksPerBin = minPeaks;

        for(int i = this.min; i <= this.max-100; i+=50) {
            MassRange mr = new MassRange(i,i+100);
            massRanges.add(mr);
        }
    }

    public void add(double mass, double intensity) {
        for(MassRange mr : massRanges) {
            mr.add(mass, intensity);
        }
        wasOptimised = false;
    }

    public Map<Double, Double> getInt2massOutliers() {
        if(!wasOptimised) optimise();
        for (MassRange mr : massRanges) {
            Map<Double, Double> foo = mr.getInt2MassOutliers();
            int2mass.putAll(foo);
        }
        return int2mass;
    }

    public String toString() {
        if (!wasOptimised) optimise();

        StringBuilder sb = new StringBuilder();
        for(MassRange mr : massRanges) {
            sb.append(mr.toString()).append("\n");
        }
        return sb.toString();
    }

    public void optimise() {
        boolean anotherRun = true;
        while(anotherRun) {
            anotherRun = reduce();
        }
        wasOptimised = true;
    }

    private boolean reduce() {
        if(massRanges.size() == 1) return false;

        for (int i = 0; i < massRanges.size(); i++) {
            MassRange mr = massRanges.get(i);
            if(mr.size() < minPeaksPerBin) {
                //get MassRange before
                int before = i-1;
                MassRange mrBefore = null;
                if (before >= 0) mrBefore = massRanges.get(before);
                //get MassRange after
                int after = i + 1;
                MassRange mrAfter = null;
                if (after < massRanges.size()) mrAfter = massRanges.get(after);
                //get rid of mr and put it half half into mrBefore and mrAfter
                mr.splitup(mrBefore, mrAfter);

                massRanges.remove(i);
                return true;
            }
        }
        return false;
    }

    public void toRCode() {
        //if(!wasOptimised) optimise();
        for (MassRange mr : massRanges) {           
            Map<Double, Double> foo = mr.getInt2MassInput();
            for (Double i : foo.keySet()) {
                Double mass = foo.get(i);
                System.out.println("lines(c(" + mass + "," + mass + "), c(" + 0.01 + "," + i + "), col=\"black\")");
            }

            foo = mr.getInt2MassOutliers();
            for(Double i : foo.keySet()) {
                Double mass = foo.get(i);
                System.out.println("lines(c(" + mass + "," + mass + "), c(" + 0.01 + "," + i + "), col=\"red\")");
            }
        }

        for (MassRange mr : massRanges) {
            int min = mr.getMin();
            int max = mr.getMax();
            double med = mr.getMedianIntensity();
            double out = mr.getOutlierIntensity();
            System.out.println("lines(c(" + min + "," + max + "), c(" + med + "," + med + "), col=\"green\")");
            System.out.println("lines(c(" + min + "," + max + "), c(" + out + "," + out + "), col=\"red\")");
        }
    }
}
