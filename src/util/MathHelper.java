package util;

import org.apache.commons.math.distribution.BinomialDistributionImpl;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;


public class MathHelper {

    public static double logPBin(int n, int k, double p) {
        BinomialDistributionImpl dist = new BinomialDistributionImpl(n, p);
        double prob = dist.probability(k);
        if(prob == 0) return 150;
        return -10*Math.log10(dist.probability(k));
    }
}
