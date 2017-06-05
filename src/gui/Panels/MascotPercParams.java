package gui.Panels;

public class MascotPercParams {

    private String targetFile;
    private String decoyFile;
    private String userID;
    private String outFileName;
    private int rankDelta;
    private boolean noFiltering;
    private boolean highCharge;
    private boolean chargeTypeII;
    private boolean newDat;

    public MascotPercParams() {

        targetFile = "";
        decoyFile = "";
        userID = "";
        outFileName = "";
        rankDelta = -1;
        noFiltering = false;
        highCharge = false;
        chargeTypeII = false;
        newDat = true;

    }

    public void setTargetFile (String target){
        this.targetFile = target;
    }

    public void setDecoyFile (String decoy){
        this.decoyFile = decoy;
    }

    public void setUserID(String user) {
        this.userID = user;
    }

    public void setOutFileName(String outName) {
        this.outFileName = outName;
    }

    public void setRankDelta(int rank) {
        this.rankDelta = rank;
    }

    public void setNoFiltering(boolean filtering) {
        this.noFiltering = filtering;
    }

    public void setHighCharge(boolean highC) {
        this.highCharge = highC;
    }

    public void setChargeTypeII(boolean typeII) {
        this.chargeTypeII = typeII;
    }

    public void setNewDat(boolean nDat) {
        this.newDat = nDat;
    }

    public String getTargetFile() {
        return targetFile;
    }

    public String getDecoyFile() {
        return decoyFile;
    }

    public String getUserID() {
        return userID;
    }

    public String getOutFileName() {
        return outFileName;
    }

    public int getRankDelta() {
        return rankDelta;
    }

    public boolean isNoFiltering() {
        return noFiltering;
    }

    public boolean isHighCharge() {
        return highCharge;
    }

    public boolean isChargeTypeII() {
        return chargeTypeII;
    }

    public boolean isNewDat() {
        return newDat;
    }
}
