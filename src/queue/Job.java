package queue;

import java.io.Serializable;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 * @author James Wright (jw13[at]sanger[dot]ac[dot]uk) 
 */
public class Job implements Serializable {

    public enum PROCESSSTATUS {
        pending, wait4scp, copying, scpDone, processing, done, failed, unknown, disconnected
    }

    private int id;
    private int targetLogID;
    private int decoyLogID;
    private String user;
    private PROCESSSTATUS status;
    private String description;
    private int rankDelta;
    private boolean proteinFeature;
    private boolean rt;
    private String dir; 
    private String resultPrefix;
    private boolean xmlFile;
    private boolean datFile;
    private boolean features;
    private boolean noFilter;
    private boolean highCharge;
    private boolean chargeFeat;
    private boolean aIonFeat;
    private int spectra;
    private int psm;
    private double pi0;
    private String submitTime;
    private String startTime;
    private String host;

    public Job(int targetLogID, int decoyLogID, String user, String desc, int ranks, boolean proteinFeature, boolean rt, String dir, String resultsPrefix, boolean xmlFile, boolean dtaFile, boolean features, boolean nofilter, boolean highCharge, boolean chargeFeat, boolean aIonFeat) {
        this.targetLogID = targetLogID;
        this.decoyLogID = decoyLogID;
        this.user = user;
        this.description = desc;
        this.rankDelta = ranks;
        this.proteinFeature = proteinFeature;
        this.rt = rt;
        this.dir = dir;
        this.resultPrefix = resultsPrefix;
        this.xmlFile = xmlFile;
        this.datFile = dtaFile;
        this.features = features;
        this.noFilter = nofilter;
        this.highCharge = highCharge;
        this.chargeFeat = chargeFeat;
        this.aIonFeat = aIonFeat;
    }


    public Job(int targetLogID, int decoyLogID, PROCESSSTATUS status, String user, String desc, int ranks, boolean proteinFeature, boolean rt, String dir, String resultsPrefix, boolean xmlFile, boolean datFile, boolean features, boolean noFilter, boolean highCharge, boolean chargeFeat, boolean aIonFeat, String host) {
        this(targetLogID, decoyLogID, user, desc, ranks, proteinFeature, rt, dir, resultsPrefix, xmlFile, datFile, features, noFilter, highCharge, chargeFeat, aIonFeat);
        this.status = status;
        this.host = host;
    }

    public Job(int id, int targetLogID, int decoyLogID, String user, String desc, PROCESSSTATUS status, int ranks, boolean proteinFeature, boolean rt, String dir, String resultsPrefix, boolean xmlFile, boolean datFile, boolean features, boolean noFilter, boolean highCharge, boolean chargeFeat, boolean aIonFeat, int spectra, int psm, double pi0, String submitTime, String startTime, String host) {
        this(targetLogID, decoyLogID, status, user, desc, ranks, proteinFeature, rt, dir, resultsPrefix, xmlFile, datFile, features, noFilter, highCharge, chargeFeat, aIonFeat, host);
        this.id = id;
        this.spectra = spectra;
        this.psm = psm;
        this.pi0 = pi0;
        this.submitTime = submitTime;
        this.startTime = startTime;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setStatus(PROCESSSTATUS status) {
        this.status = status;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSpectra(int spectra) {
        this.spectra = spectra;
    }

    public void setPsm(int psm) {
        this.psm = psm;
    }

    public void setPi0(double pi0) {
        this.pi0 = pi0;
    }

    public int getId() {
        return id;
    }

    public int getTargetLogID() {
        return targetLogID;
    }

    public int getDecoyLogID() {
        return decoyLogID;
    }

    public String getUser() {
        return user;
    }

    public PROCESSSTATUS getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public int getRankDelta() {
        return rankDelta;
    }

    public boolean proteinFeature() {
        return proteinFeature;
    }

    public boolean rt() {
        return rt;
    }

    public String getDir() {
        return dir;
    }

    public boolean hasXmlFile() {
        return xmlFile;
    }

    public boolean hasFeaturesPersisted() {
        return features;
    }

    public boolean hasNoFilterPersisted() {
        return noFilter;
    }

    public boolean hasHighChargePersisted() {
        return highCharge;
    }

    public boolean hasChargeFeatPersisted() {
        return chargeFeat;
    }

    public boolean hasAIonFeatPersisted() {
        return aIonFeat;
    }

    public String getResultPrefix() {
        return resultPrefix;
    }

    public boolean hasDatFile() {
        return datFile;
    }

    public int getSpectra() {
        return spectra;
    }

    public int getPsm() {
        return psm;
    }

    public double getPi0() {
        return pi0;
    }

    public String getSubmitTime() {
        return submitTime;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getHost() {
        return host;
    }
}
