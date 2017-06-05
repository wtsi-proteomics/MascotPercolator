package cli;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import util.MascotDatFiles;
import util.FileUtils;
import util.RunCommandLine;
import core.MascotParser;
import matrix_science.msparser.ms_mascotresults;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class MascotROC2 {

    @Option(name = "-target", required = true, usage = Messages.DATFILE)
    private String o_targetS = null;
    @Option(name = "-decoy", required = true, usage = Messages.DECOYFILE)
    private String o_decoyS = null;
    @Option(name = "-rankdelta", required = true, usage = Messages.RANKDELTA)
    private int rankDelta = 1;
    @Option(name = "-qvality", required = true, usage = Messages.RANKDELTA)
    private String qvalPath;
    @Option(name = "-out", required = true, usage = Messages.OUTFILES)
    private String outFilesName = null;

    private final String pfMscore = "mscore.txt";
    private final String pfMscoreLog = "mscore.log";
    private final String pfMit = "mit.txt";
    private final String pfMitlog = "mit.log";
    private final String pfMht = "mht.txt";
    private final String pfMhtlog = "mht.log";

    private boolean concatenatedSearch = false;
    private FileWriter mScoreTargetOut;
    private FileWriter mScoreDecoyOut;
    private FileWriter mitTargetOut;
    private FileWriter mitDecoyOut;
    private FileWriter mhtTargetOut;
    private FileWriter mhtDecoyOut;
    private MascotParser targetParser;
    private MascotParser decoyParser;

    private double count = 0;

    public static void main(String[] args) throws IOException {
        MascotROC2 mr = new MascotROC2();
        CmdLineParser parser = new CmdLineParser(mr);
        try {
            parser.setUsageWidth(150);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR + "\n");
        } catch (CmdLineException e) {
            System.out.println("MascotROC gets data for a ROC curve using the MIT and MHT with varying probabilities");
            parser.printUsage(System.err);
        }
        mr.run();
    }

    private MascotROC2() {
    }

    private void run() throws IOException {
        //set targetParser-decoy mode
        File datT = MascotDatFiles.getDatFileFromLogID(o_targetS);
        File datD = MascotDatFiles.getDatFileFromLogID(o_decoyS);

        File mScoreTarget = File.createTempFile("mascot_roc_mscoreT", ".tmp.txt", FileUtils.getTmpPercolatorDir());
 //mScoreTarget.deleteOnExit();
        File mScoreDecoy = File.createTempFile("mascot_roc_mscoreD", ".tmp.txt", FileUtils.getTmpPercolatorDir());
 //mScoreDecoy.deleteOnExit();

        File mitTarget = File.createTempFile("mascot_roc_mitT", ".tmp.txt", FileUtils.getTmpPercolatorDir());
 // mitTarget.deleteOnExit();
        File mitDecoy = File.createTempFile("mascot_roc_mitD", ".tmp.txt", FileUtils.getTmpPercolatorDir());
 //       mitDecoy.deleteOnExit();

        File mhtTarget = File.createTempFile("mascot_roc_mhtT", ".tmp.txt", FileUtils.getTmpPercolatorDir());
 //       mhtTarget.deleteOnExit();
        File mhtDecoy = File.createTempFile("mascot_roc_mhtD", ".tmp.txt", FileUtils.getTmpPercolatorDir());
 //       mhtDecoy.deleteOnExit();

        mScoreTargetOut = new FileWriter(mScoreTarget);
        mScoreDecoyOut = new FileWriter(mScoreDecoy);

        mitTargetOut = new FileWriter(mitTarget);
        mitDecoyOut = new FileWriter(mitDecoy);

        mhtTargetOut = new FileWriter(mhtTarget);
        mhtDecoyOut = new FileWriter(mhtDecoy);


        targetParser = new MascotParser(datT.getAbsolutePath(), ms_mascotresults.MSRES_GROUP_PROTEINS | ms_mascotresults.MSRES_SHOW_SUBSETS, true);
        decoyParser = null;
        if (o_decoyS.equals(o_targetS) ) {
            if (targetParser.hasDecoy()) {
                System.err.println("Auto Target/Decoy search mode");
                decoyParser = new MascotParser(datD.getAbsolutePath(),
                    ms_mascotresults.MSRES_DECOY | ms_mascotresults.MSRES_GROUP_PROTEINS | ms_mascotresults.MSRES_SHOW_SUBSETS, false);
            } else {
                System.err.println("Concatenated Target/Decoy search mode currently not supported");
            }
        } else {
            System.err.println("Separate Target/Decoy search mode");
            decoyParser = new MascotParser(datD.getAbsolutePath(),
                ms_mascotresults.MSRES_GROUP_PROTEINS | ms_mascotresults.MSRES_SHOW_SUBSETS, true);
        }
        extract(false);
        extract(true);

        mScoreTargetOut.flush();
        mScoreTargetOut.close();
        mScoreDecoyOut.flush();
        mScoreDecoyOut.close();

        mitTargetOut.flush();
        mitTargetOut.close();
        mitDecoyOut.flush();
        mitDecoyOut.close();

        mhtTargetOut.flush();
        mhtTargetOut.close();
        mhtDecoyOut.flush();
        mhtDecoyOut.close();

        File mscoreF = new File(outFilesName + pfMscore);
        File mscoreLogF = new File(outFilesName + pfMscoreLog);

        File mitF = new File(outFilesName + pfMit);
        File mitLogF = new File(outFilesName + pfMitlog);
        
        File mhtF = new File(outFilesName + pfMht);
        File mhtLogF = new File(outFilesName + pfMhtlog);

        try {
            execute(mScoreTarget, mScoreDecoy, mscoreF, mscoreLogF);
        } catch (IOException e) {}

        try {
            execute(mitTarget, mitDecoy, mitF, mitLogF);
        } catch (IOException e) {}

        try {
            execute(mhtTarget, mhtDecoy, mhtF, mhtLogF);
        } catch (IOException e) {}
    }

//    private void getRanksToBeExported(MascotParser parser, HashSet<String> query_rank) {
//        for (int queryN = 1; queryN <= parser.getNumQueries(); queryN++) {
//            double topScore = parser.getPeptide(queryN, 1).getIonsScore();
//            for (int rank = 1; rank <= parser.getMaxRank(); rank++) {
//                double score = parser.getPeptide(queryN, rank).getIonsScore();
//                if (rank == 1 || (topScore - score <= rankDelta)) {
//                    query_rank.add(queryN + "_" + rank);
//                }
//            }
//        }
//    }

    private void extract(boolean decoy) throws IOException {
        MascotParser parser = decoy ? decoyParser : targetParser;

        for (int queryN = 1; queryN <= parser.getNumQueries(); queryN++) {
  // System.out.println("queryN = " + queryN);
            double topScore = parser.getPeptide(queryN, 1).getIonsScore();
            double mit = parser.getIdentityThreshold(queryN, 20);
            double mht = parser.getHomologyThreshold(queryN, 20);
            if (mht <= 0) mht = mit;

            for (int rank = 1; rank <= parser.getMaxRank(); rank++) {
                double score = parser.getPeptide(queryN, rank).getIonsScore();                
//                if(rank != 1 && (decoy || (topScore - score > rankDelta))) continue;

                if(decoy) {
  // System.out.println("\tdecoy: " + score);
                    mScoreDecoyOut.write(topScore + "\n");
                    mitDecoyOut.write((topScore - mit) + "\n");
                    mhtDecoyOut.write((topScore - mht) + "\n");
                } else {
  // System.out.println("\ttarget: " + score);
                    mScoreTargetOut.write(score + "\n");
                    mitTargetOut.write((score - mit) + "\n");
                    mhtTargetOut.write((score - mht) + "\n");
                }
            }
        }
    }

    private void execute(File target, File decoy, File result, File log) throws IOException {
        Writer scoreWriter = FileUtils.getPrintWriterFromFile(result);
        Writer logWriter = FileUtils.getPrintWriterFromFile(log);

        String run = qvalPath + " " + target.getAbsolutePath() + " " + decoy.getAbsolutePath();
        System.out.println(run);
        RunCommandLine.execute(run, scoreWriter, logWriter);

        scoreWriter.flush();
        scoreWriter.close();
        logWriter.flush();
        logWriter.close();
    }
}
