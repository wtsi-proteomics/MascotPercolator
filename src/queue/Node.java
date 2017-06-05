package queue;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import java.net.UnknownHostException;
import java.net.InetAddress;
import java.io.IOException;
import java.io.File;
import java.lang.reflect.UndeclaredThrowableException;

import cli.MascotPercolator;
import cli.Messages;
import gnu.cajo.utils.extra.TransparentItemProxy;
import exception.ReturnValueException;
import util.FileUtils;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class Node extends Thread {

    @Option(name = "-server", required = true, usage = "Server host name, where Mascot Percolator queue is running")
    private String server = null;
    @Option(name = "-serverPort", required = false, usage = "(optional) Port of Mascot Percolator queue server")
    private int serverPort = 1198;
    @Option(name = "-copyDat", required = false, usage = "(optional) Given the node has no access to the Mascot dat file location as specified in the config.properties file, it is copied via scp to a temporary file on the node. Currently only implemented for Unix.")
    private boolean copyDat = false;

    private File tmpMascotDatDir = null;
    private ServerI serverI = null;
    private String nodeHostName;
    private boolean oneShotNode = false;

    public enum NODESTATUS {
        idle, processing
    }

    private NODESTATUS status = NODESTATUS.idle;

    public static void main(String[] args) {
        Node node = new Node();
        CmdLineParser parser = new CmdLineParser(node);
        try {
            parser.setUsageWidth(120);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR);
        } catch (CmdLineException e) {
            System.err.println(
                Messages.AUTHOR + "\n" + Messages.DESCRIPTION);
            System.err.println("\nUsage info:\n" +
                "java -cp MascotPercolator.jar queue.Node [options ...]");
            System.err.println("\nOptions (replacing the \"[options ...]\" expression above):");
            parser.printUsage(System.err);
            System.err.println("\nError:");
            System.err.println(e.getMessage());
            System.err.println("");
            return;
        }
        node.setup();
        node.start();
    }

    private Node() {
    }

    protected Node(String server, int serverPort, boolean copyDat, boolean oneShotNode) {
        this.server = server;
        this.serverPort = serverPort;
        this.copyDat = copyDat;
        this.oneShotNode = oneShotNode;
        setup();
    }

    private void setup() {
        //get local host name
        try {
            nodeHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        //if node has no access to Mascot dat files, they need to be copied into a temp directory
        if(copyDat) {
            try {
                tmpMascotDatDir = FileUtils.getTmpPercolatorDir();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Node can not create temporary directory: " + tmpMascotDatDir.getAbsolutePath());
                System.exit(-1);
            }
            System.out.println("Node is accepting temporary copies of Mascot result files under " + tmpMascotDatDir.getAbsolutePath());
        }

        //start thread that updates server on node status
        StatusUpdate su = new StatusUpdate();
        su.start();
    }

    public String getHostName() {
        return nodeHostName;
    }

    private String getScpTargetLocation() {
        // !!! tmpMascotDatDir is null if copyDat was false !!!
        return tmpMascotDatDir!=null ? tmpMascotDatDir.getAbsolutePath() : null;
    }

    public void run() {
        //connect to server
        getServer();
        System.out.println("Node started; successfully connected to queue server ... ");

        while (true) {
            try {
                status = NODESTATUS.idle;
                Thread.sleep(5000);

                //getJob(nodeHostName, scpTargetDirectory):Job
                Job job = getServer().getJob(nodeHostName, getScpTargetLocation());
                if(job == null) continue;

                //if(scp done) process
                status = NODESTATUS.processing;
                try {
                    System.out.println("Processing job ...");
                    MascotPercolator mp = new MascotPercolator(job, copyDat, true);
                    MascotPercolator.Summary summary = mp.mascot2percolator();

                    job.setStatus(Job.PROCESSSTATUS.done);
                    job.setDescription(summary.getSearchTitle());
                    job.setSpectra(summary.getSpectra());
                    job.setPsm(summary.getPsm());
                    job.setPi0(summary.getPi0());
                    System.out.println("Job finished processing ...\n");
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Job failed! Node continues to work. Waiting for next job ...");
                    job.setStatus(Job.PROCESSSTATUS.failed);
                } catch (ReturnValueException e) {
                    e.printStackTrace();
                    System.out.println("Job failed! Node continues to work. Waiting for next job ...");
                    job.setStatus(Job.PROCESSSTATUS.failed);
                }

                //job done, tell server
                getServer().submitFinishedJob(job);
                if (oneShotNode) System.exit(0);                
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized ServerI getServer() {
        try {
            if(serverI == null) return connectServer();

            serverI.ping();
            return serverI;
        } catch (UndeclaredThrowableException e) {
            //keep looping until a connection is found;
            //e.g. when job is finished and wants to report to server a connection is required.
            //Node waits in the meanwhile
            return connectServer();
        }
    }

    boolean printErr = true;
    private synchronized ServerI connectServer() {
        while (true) {
            try {
                String serverURL = "//" + server + ":" + serverPort + "/" + Server.service;
                serverI = (ServerI) TransparentItemProxy.getItem(serverURL, new Class[]{ServerI.class});
                if(serverI != null) {
                    System.err.println("Reconnected to server.");
                    return getServer();
                }
            } catch (Exception e2) {
                if (printErr) {
                    e2.printStackTrace();
                    System.err.println("\nIt seems that no queue server is running on " + server + ":" + serverPort + "\n" +
                        "\tTry 'java -cp MascotPercolator.jar queue.Server' for usage information.\n" +
                        "\tNode tries to reconnect every 10s ...\n");
                }
                System.err.print(".");
                printErr = false;

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }


    private class StatusUpdate extends Thread {
        public void run() {
            while (true) {
                try {
                    Thread.sleep(Server.updateHtmlTime/2);
                    if(serverI != null) getServer().updateStatus(nodeHostName, status);
                } catch(UndeclaredThrowableException e) {
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
