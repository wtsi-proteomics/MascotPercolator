package deprecated;

import java.util.*;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class MassRange {

    private int min;
    private int max;

    //input
    private Map<Double, Double> int2mass = new IdentityHashMap<Double, Double>();

    //results
    private Map<Double, Double> int2massOutliers;
    private Double median;
    private Double outlier;

    public MassRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public void add(double mass, double intensity) {
        if(mass >= min && mass <= max) {
            int2mass.put(intensity, mass);
        }
        int2massOutliers = null;
        median = null;
    }

    public int size() {
        return int2mass.size();
    }

    public double getMedianIntensity() {
        if(median != null) return median;

        List<Double> sortedIntensities = new ArrayList<Double>(int2mass.keySet());
        int middle = sortedIntensities.size() / 2;
        median = sortedIntensities.get(middle);
        return median;
    }

    public double getOutlierIntensity() {
        if(outlier != null) return outlier;

        // use top n peaks per m/z mass range, e.g. 7 most intense peaks in every 100m/z range
        List<Double> sortedIntensities = new ArrayList<Double>(int2mass.keySet());
        double cutoff;
        if(sortedIntensities.size() > 7) cutoff = sortedIntensities.get(7);
        else cutoff = sortedIntensities.get(sortedIntensities.size()-1);

        /*
        int middle = sortedIntensities.size() / 2; //q2
        int offset = sortedIntensities.size() / 4; //25%
        double q1 = sortedIntensities.get(middle - offset); //q1
        double q3 = sortedIntensities.get(middle + offset); //q3
        double iqr = q3 - q1; //iqr
        cutoff = q3; // q3 + 1.5 * iqr;
        */

        return cutoff;
    }

    public Map<Double, Double> getInt2MassInput() {
        return int2mass;
    }

    public Map<Double, Double> getInt2MassOutliers() {
        if(int2massOutliers != null) return int2massOutliers;

        double out = getOutlierIntensity();
        int2massOutliers = new HashMap<Double, Double>();
        for(Double intensity : int2mass.keySet()) {
            if(intensity >= out) {
                int2massOutliers.put(intensity, int2mass.get(intensity));
            }
        }
        return int2massOutliers;
    }

    public String toString() {
        return "Range: " + min + "-" + max + ", Count: " + size() + ", Median: " + getMedianIntensity() + ", Outlier: >" + getOutlierIntensity();
    }

    public void splitup(MassRange before, MassRange after) {
        if (before != null && after != null) {
            //split the data into two and put it into the before and after massRange
            List<Double> ints = new ArrayList<Double>(int2mass.keySet());
            int half = size() / 2;
            half += size() % 2;
            before.setMax(this.max);
            for (int i = 0; i < half; i++) {
                Double intensity = ints.get(i);
                before.add(int2mass.get(intensity), intensity);
            }
            after.setMin(this.min);
            for (int i = half; i < size(); i++) {
                Double intensity = ints.get(i);
                after.add(int2mass.get(intensity), intensity);
            }
        } else if (before != null) {
            //only put it in the massRange before this massRange
            before.setMax(this.max);
            for (Double intensity : int2mass.keySet()) {
                before.add(int2mass.get(intensity), intensity);
            }
        } else if (after != null) {
            //only put in the massRange after this massRange
            after.setMin(this.min);
            for (Double intensity : int2mass.keySet()) {
                after.add(int2mass.get(intensity), intensity);
            }
        }

    }
}
