package queue;

import java.rmi.RemoteException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.lang.reflect.UndeclaredThrowableException;

import gnu.cajo.utils.ItemServer;
import gnu.cajo.utils.extra.TransparentItemProxy;
import gnu.cajo.invoke.Remote;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;
import cli.Messages;
import cli.MascotPercolator;
import util.MascotDatFiles;
import util.FileUtils;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 * @author James Wright (jw13[at]sanger[dot]ac[dot]uk)
 */
public class Server extends Thread implements ServerI {

    @Option(name = "-port", required = false, usage = "(optional) port")
    private int port = 1198;
    @Option(name = "-dbHost", required = true, usage = "database host")
    private String dbHost;
    @Option(name = "-dbAlias", required = true, usage = "database name/alias")
    private String dbAlias;
    @Option(name = "-htmlStatusFile", required = true, usage = "html status page will be written to this path and updated periodically as runs are queued & processed")
    private String htmlFile;

    private Connection con;  //database connection
    private boolean allowWebEdit = true; //TODO replace with monitor

    private Map<String, Node.NODESTATUS> nodes = new ConcurrentHashMap<String, Node.NODESTATUS>();

    public final static String service = "server";
    public final static int updateHtmlTime = 5000;

    public static enum RECEIVERSTATUS {
        FILENOTFOUND, RESULTSEXISTALREADY, SUCCESS
    }
    
    public static void main
        (String[] args) throws Exception {
        Server s = new Server();
        CmdLineParser parser = new CmdLineParser(s);
        try {
            parser.setUsageWidth(100);
            parser.parseArgument(args);
            System.err.println(Messages.AUTHOR);
        } catch (CmdLineException e) {
            System.err.println("\nUsage info:\n" +
                "java -Djava.rmi.server.hostname=yourhost -cp MascotPercolator.jar queue.Server [options ...]");
            System.err.println("\nReplace 'yourhost' with the hostname of your machine. \n" +
                "\tWhen cli.MascotPercolator is called subsequently to queue up some runs,\n" +
                "\tset the '-server' parameter to match the hostname specified here.");
            System.err.println("\nOptions (replacing the \"[options ...]\" expression above):");
            parser.printUsage(System.err);
            System.err.println("\nError:");
            System.err.println(e.getMessage());
            System.err.println("");
            return;
        }

        s.setup();
        s.start();
    }

    private Server() {
    }

    public void run() {
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    public synchronized void shutdown() throws SQLException {
        Statement st = con.createStatement();
        // db writes out to files and performs clean shuts down
        // otherwise there will be an unclean shutdown
        // when program ends
        st.execute("SHUTDOWN COMPACT");
        con.close();
        System.exit(0);
    }

    public void ping() {}

    public synchronized RECEIVERSTATUS submitJob(Job job) throws IOException {
        allowWebEdit = false;
        String expression = null;
        try {
            //first, test whether specified results file is already in queue ...
            Statement st = con.createStatement();
            expression = "SELECT * from log where dir='" + job.getDir() + "' and resultPrefix= '" + job.getResultPrefix() + "'";
            ResultSet res = st.executeQuery(expression);
            if (res.next()) {
                return RECEIVERSTATUS.RESULTSEXISTALREADY;
            }

            //then test whether the log IDs exist
            try {
                MascotDatFiles.getDatFileFromLogID(job.getTargetLogID());
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("File for log id " + job.getTargetLogID() + " not found; is this a valid Mascot log id ? ");
                return RECEIVERSTATUS.FILENOTFOUND;
            }
            try {
                MascotDatFiles.getDatFileFromLogID(job.getDecoyLogID());
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("File for log id " + job.getDecoyLogID() + " not found; is this a valid Mascot log id ? ");
                return RECEIVERSTATUS.FILENOTFOUND;
            }

            //if all is fine, we accept the new job
            Job.PROCESSSTATUS jobStatus = Job.PROCESSSTATUS.pending;
            String processingNode = job.getHost()==null ? "" : job.getHost();
            expression = "INSERT INTO log (targetLogID, decoyLogID, status, " +
                "user, desc, rankdelta, proteinFeature, rt, " +
                "dir, resultPrefix, xml, dat, features, noFilter, highCharge, chargeFeat, " +
                "submitTime, startTime, node) " +
                "VALUES (" +
                "'" + job.getTargetLogID() + "','" + job.getDecoyLogID() + "','" + jobStatus.toString() +
                "','" + job.getUser() + "','" + job.getDescription() + "'," + job.getRankDelta() + "," + job.proteinFeature() + "," + job.rt() +
                ",'" + job.getDir() + "','" + job.getResultPrefix() + "'," + job.hasXmlFile() + "," + job.hasDatFile() + "," + job.hasFeaturesPersisted() + "," + job.hasNoFilterPersisted() + "," + job.hasHighChargePersisted() + "," + job.hasChargeFeatPersisted() +
                ",'" + now() + "', '', '" + processingNode + "')";
            int i = st.executeUpdate(expression);
            if (i == -1) {
                System.out.println("db error : " + expression);
            }
            st.close();
        } catch (SQLException e) {
            System.err.println("SQL Expression: " + expression);
            e.printStackTrace();
            System.exit(-1);
        }
        System.out.println("New Job (" + job.getTargetLogID() + ";" + job.getDecoyLogID() + ") received");
        allowWebEdit = true;
        return RECEIVERSTATUS.SUCCESS;
    }

    public synchronized Job getJob(String nodeHostName, String scpTargetDirectory) {
        allowWebEdit = false;
        try{

            //check for pending jobs that wait for a specific host
            Statement st = con.createStatement();
            String expression = "SELECT * from log " +
                " where status='" + Job.PROCESSSTATUS.pending + "' " +
                " and node='" + nodeHostName + "'" +
                " order by id";
            ResultSet res = st.executeQuery(expression);
            if (res.next()) {
                Job job = getJob(res);
                res.close();
                st.close();
                job = submitJobToNode(job, st, scpTargetDirectory);
                return job;
            }
            res.close();
            st.close();

            //pending jobs to be submitted to ANY node
            st = con.createStatement();
            expression = "SELECT * from log " +
                "where status='" + Job.PROCESSSTATUS.pending + "' " +
                "order by id";
            res = st.executeQuery(expression);
            while(res.next()) {
                Job job = getJob(res);
                String host = job.getHost();
                if(host != null && !host.trim().equals("")) continue; //this job waits for a specific node
                res.close();
                st.close();
                job.setHost(nodeHostName);
                job = submitJobToNode(job, st, scpTargetDirectory);
                return job;
            }
            res.close();
            st.close();

        } catch(SQLException e) {
            e.printStackTrace();
            return null;
        }
        allowWebEdit = true;
        return null;
    }

    private synchronized Job submitJobToNode(Job job, Statement st, String scpTargetDirectory) {
        allowWebEdit = false;
        //if node has no access to the dat files, we copy them to the node via scp
        if (scpTargetDirectory != null) {
            try {
                int jobTargetLogID = job.getTargetLogID();
                File file = MascotDatFiles.getDatFileFromLogID(jobTargetLogID);
                String target = file.getAbsolutePath();
                FileUtils.scp(target, job.getHost(), scpTargetDirectory + "/" + job.getTargetLogID() + ".dat");
                FileUtils.sshchmod(job.getHost(), "666", scpTargetDirectory + "/" + job.getTargetLogID() + ".dat");

                String decoy = MascotDatFiles.getDatFileFromLogID(job.getDecoyLogID()).getAbsolutePath();
                if (!decoy.equals(target)) {
                    FileUtils.scp(decoy, job.getHost(), scpTargetDirectory + "/" + job.getDecoyLogID() + ".dat");
                    FileUtils.sshchmod(job.getHost(), "666", scpTargetDirectory + "/" + job.getDecoyLogID() + ".dat");
                }
            } catch(IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        job.setStatus(Job.PROCESSSTATUS.processing);
        System.out.println("Job (" + job.getTargetLogID() + ";" + job.getDecoyLogID() + ") is processed on '" + job.getHost() + "'");

        try {
            System.out.println("Update db log: set job (ID: " + job.getId() + "  ) status to '" + job.getStatus() + "'");
            st = con.createStatement();
            st.executeUpdate("UPDATE LOG SET status='" + job.getStatus() + "', startTime='" + now() + "', node='" + job.getHost() + "' WHERE id='" + job.getId() + "'");
            st.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        allowWebEdit = true;
        return job;
    }

    public synchronized void submitFinishedJob(Job job) {
        allowWebEdit = false;
        try {
            Statement st2 = con.createStatement();
            st2.executeUpdate("UPDATE LOG SET status='" + job.getStatus() + "'" +
                ",spectra=" + job.getSpectra() +
                ",psm=" + job.getPsm() +
                ",pi0=" + job.getPi0() +
                ",desc='" + job.getDescription() + "'" +
                " WHERE id='" + job.getId() + "'");
            st2.close();
            System.out.println("Job (" + job.getTargetLogID() + ";" + job.getDecoyLogID() + ") finished processing on " + job.getHost());
        } catch (SQLException e) {
            e.printStackTrace();
            try {
                shutdown();
            } catch (SQLException e1) {
                e1.printStackTrace();
                System.exit(-1);
            }
            System.exit(-1);
        }
        allowWebEdit = true;
        sangerSpecificHack(job);
    }

    public synchronized void updateStatus(String host, Node.NODESTATUS status) {
        nodes.put(host, status);
    }

    private synchronized static String now() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd kk:mm:ss z");
        return sdf.format(cal.getTime()).replaceAll(" ", "_");
    }

    private synchronized void sangerSpecificHack(Job job) {
        //Sanger specific hack to move result files from a temporary location back into the original Mascot data
        if (job.getDir().endsWith("team17_mascotpercolator_temp/")) {
            try {
                String targetDir = FileUtils.getAbsDirectoryByFile(MascotDatFiles.getDatFileFromLogID(job.getTargetLogID())).getCanonicalPath();
                //targetDir = targetDir.replaceAll("/mascot-2\\.2/", "/mascot/");

                File dir = new File(job.getDir());
                File filesToDelete[] = dir.listFiles();
                for (File f : filesToDelete) {
                    if (f.getName().startsWith(job.getResultPrefix())) {
                        FileUtils.scp(f.getAbsolutePath(), "mascotsrv2", targetDir);
                        FileUtils.chmod(targetDir + "/" + f.getName(), "0644");
                        if (f.delete()) {
                            System.out.println(f.getAbsolutePath() + " deleted.");
                        }
                    }
                }
                updateJobDir(job, targetDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void updateJobDir(Job job, String newDir) {
        try {
            Statement st = con.createStatement();
            st.executeUpdate("UPDATE LOG SET dir='" + newDir + "' WHERE id='" + job.getId() + "'");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private synchronized void updateJobStatus(String nodeKey, String statusBefore, String newStatus) throws IOException {
        try {
            Statement st = con.createStatement();
            String expression = "SELECT * from log where status='" + statusBefore + "' and node like '" + nodeKey + "'";
            ResultSet res = st.executeQuery(expression);
            if (res.next()) {
                Job job = getJob(res);
                st.executeUpdate("UPDATE LOG SET status='" + newStatus + "' WHERE id='" + job.getId() + "'");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            try {
                Statement st = con.createStatement();
                // db writes out to files and performs clean shuts down
                // otherwise there will be an unclean shutdown
                // when program ends
                st.execute("SHUTDOWN COMPACT");
                con.close();
                System.exit(0);
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
            System.exit(-1);
        }
    }

    private synchronized Job getJob(ResultSet res) throws SQLException {
        int id = res.getInt("id");
        int targetLogID = res.getInt("targetLogID");
        int decoyLogID = res.getInt("decoyLogID");
        String status = res.getString("status");
        String user = res.getString("user");
        String desc = res.getString("desc");
        int rankdelta = res.getInt("rankdelta");
        boolean proteinFeature = res.getBoolean("proteinFeature");
        boolean rt = res.getBoolean("rt");
        String resultPrefix = res.getString("resultPrefix");
        String dir = res.getString("dir");
        boolean xml = res.getBoolean("xml");
        boolean dat = res.getBoolean("dat");
        boolean features = res.getBoolean("features");
        boolean nofilter = res.getBoolean("noFilter");
        boolean highcharge = res.getBoolean("highCharge");
        boolean chargefeat = res.getBoolean("chargeFeat");
        boolean aion = res.getBoolean("aIon");
        int spectra = res.getInt("spectra");
        int psm = res.getInt("psm");
        double pi0 = res.getDouble("pi0");
        String submitTime = res.getString("submitTime");
        String startTime = res.getString("startTime");
        String node = res.getString("node");
        return new Job(id, targetLogID, decoyLogID, user, desc, Job.PROCESSSTATUS.valueOf(status), rankdelta, proteinFeature, rt, dir, resultPrefix, xml, dat, features, nofilter, highcharge, chargefeat, aion, spectra, psm, pi0, submitTime, startTime, node);
    }

    private void setup() throws ClassNotFoundException, IOException {
        try {
            Class.forName("org.hsqldb.jdbcDriver");
        } catch (Exception e) {
            System.out.println("ERROR: failed to load HSQLDB JDBC driver.");
            e.printStackTrace();
            System.exit(-1);
        }

        try {
            con = DriverManager.getConnection("jdbc:hsqldb:hsql://" + dbHost + "/" + dbAlias, "sa", "");
            con.setAutoCommit(true);
        } catch (SQLException e) {
            e.printStackTrace(); //this is usually thrown when e.g. dbPrefix has no read/write permissions
            System.err.println("Possible causes: \n" +
                "\tdatabase server is not running\n" +
                "\tdatabase alias incorrect: " + dbAlias + "\n" +
                "\tdatabase host incorrect: " + dbHost + "\n" +
                "To start the database server:\n" +
                "java -cp libs/hsqldb.jar org.hsqldb.Server -database.0 file:yourDatabaseFile -dbname.0 mplog");
            System.exit(-1);
        }

        //create table if not exists
        Statement stmt = null;
        try {
            stmt = con.createStatement();
            stmt.executeUpdate("SET WRITE_DELAY 0;");
            stmt.execute("CREATE TABLE LOG(" +
                "id INTEGER IDENTITY, " +
                "targetLogID INTEGER, " +
                "decoyLogID INTEGER, " +
                "status VARCHAR(50), " +
                "user VARCHAR(50), " +
                "desc VARCHAR(512), " +
                "rankdelta INTEGER, " +
                "proteinFeature BOOLEAN, " +
                "rt BOOLEAN, " +
                "dir VARCHAR(512), " +
                "resultPrefix VARCHAR(512), " +
                "xml BOOLEAN, " +
                "dat BOOLEAN, " +
                "features BOOLEAN, " +
                "nofilter BOOLEAN, " +
                "highcharge BOOLEAN, " +
                "chargefeat BOOLEAN, " +
                "aion BOOLEAN, " +
                "spectra INTEGER, " +
                "psm INTEGER, " +
                "pi0 DOUBLE, " +
                "submitTime VARCHAR(50), " +
                "startTime VARCHAR(50), " +
                "node VARCHAR(100) " +
                ")"
            );
        } catch (SQLException e) {
            //nothing to throw/report. This exception is thrown e.g. when database attempts to create table although it exists already.
            //HSQLDB does not support the "CREATE TABLE IF NOT EXISTS" syntax ...
        }

        //update status of alienated processes, e.g. when server died -> status set to unkonwn.
        //update web status page
        try {
            updateJobStatus("%", Job.PROCESSSTATUS.processing.toString(), Job.PROCESSSTATUS.unknown.toString());
        } catch (IOException e) {
            e.printStackTrace(); //e.g. thrown when no access to write updateWebTable file.
            System.exit(-1);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }

        //setup server using cajo
        try {
            Remote.config(null, port, null, 0);
            ItemServer.bind(this, service);
            ItemServer.acceptProxies();
        } catch (RemoteException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        System.out.println("Mascot Percolator queue is listening on port " + port + " ... waiting for jobs ...\n");

        //start website update thread
        Website web = new Website();
        web.start();
    }


    ///////////////////// Just a quick hack to generate a static website
    private class Website extends Thread {

        public void run() {
            while (true) {
                try {
                    Thread.sleep(updateHtmlTime);
                    updateWebTable();
                    nodes = new HashMap<String, Node.NODESTATUS>();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }

        private final Pattern dirFile = Pattern.compile("(.*[/|\\\\])(.*)");
        private synchronized void updateWebTable() throws IOException {
            if(! allowWebEdit) return;
            try {
                FileWriter fw = new FileWriter(htmlFile);
                StringBuffer sb = new StringBuffer();

                //header and available nodes
                sb.append(part1);
                sb.append(now());
                sb.append(part2);

                int i = 1;
                Iterator<String> keyIter = nodes.keySet().iterator();
                while(keyIter.hasNext()) {
                    String key = keyIter.next();
                    try {
                        sb.append("<tr>");
                        String status = nodes.get(key).toString();
                        sb.append("<td>").append(i).append("</td>");
                        sb.append("<td>").append(key).append("</td>");
                        sb.append("<td>").append(status).append("</td>");
                        sb.append("</tr>\n");
                        i++;
                    } catch(UndeclaredThrowableException e) {
                        e.printStackTrace();
                        System.err.println("Node '" + key + "'was shut down or killed.\nServer continues without this node");
                        updateJobStatus(key, Job.PROCESSSTATUS.processing.toString(), Job.PROCESSSTATUS.disconnected.toString());
                        keyIter.remove(); //TODO!
                    }
                }

                //log
                sb.append(part3);
                Statement st = con.createStatement();
                ResultSet res = st.executeQuery("SELECT * from log order by id DESC");
                while (res.next()) {
                    Job job = getJob(res);
                    sb.append("<tr>");
                    sb.append("<td>" + job.getId() + "</td>");
                    sb.append("<td>" + job.getTargetLogID() + "</td>");
                    sb.append("<td>" + job.getDecoyLogID() + "</td>");
                    sb.append("<td>" + job.getStatus() + "</td>");
                    sb.append("<td>" + job.getUser() + "</td>");
                    sb.append("<td>" + job.getDescription() + "</td>");
                    sb.append("<td>" + job.getRankDelta() + "</td>");
                    if (job.hasDatFile()) {
                        //String newDir = job.getDir().split("/mascot/mascot/")[1];
                        sb.append("<td><a href='http://mascotsrv2.internal.sanger.ac.uk/mascot/cgi/master_results.pl?file=" +
                            job.getDir() + "/" + job.getResultPrefix() + MascotPercolator.datPostfix + "'>"
                            + job.hasDatFile() + "</a></td>");
                    } else {

                            sb.append("<td>" + job.hasDatFile() + "</td>");

                    }
                    sb.append("<td>" + job.getDir() + "</td>");
                    sb.append("<td>" + job.getResultPrefix() + "</td>");
                    sb.append("<td>" + job.getSpectra() + "</td>");
                    sb.append("<td>" + job.getPsm() + "</td>");
                    sb.append("<td>" + job.getPi0() + "</td>");
                    sb.append("<td>" + job.hasNoFilterPersisted() + "</td>");
                    sb.append("<td>" + job.hasHighChargePersisted() + "</td>");
                    sb.append("<td>" + job.hasChargeFeatPersisted() + "</td>");
                    sb.append("<td>" + job.hasFeaturesPersisted() + "</td>");
                    sb.append("<td>" + job.hasXmlFile() + "</td>");
                    sb.append("<td>" + job.proteinFeature() + "</td>");
                    sb.append("<td>" + job.rt() + "</td>");
                    sb.append("<td>" + job.getHost() + "</td>");
                    sb.append("<td>" + job.getSubmitTime() + "</td>");
                    sb.append("<td>" + job.getStartTime() + "</td>");
                    sb.append("</tr>\n");
                }

                st.close();
                sb.append(footer);
                fw.write(sb.toString());
                fw.flush();
                fw.close();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private final String part1 = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n" +
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n" +
            "      \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
            "<head>\n" +
            "  <meta http-equiv=\"content-type\" content=\"text/html; charset=iso-8859-1\" />\n" +
            //"  <meta http-equiv=\"refresh\" content=\"30\" />\n" +
            "  <title>Mascot Percolator Log</title>\n" +
            "</head>\n" +
            "\n" +
            "<body>\n" +
            "<h1 style=\"text-align:center;\">Mascot Percolator Status Page</h1>\n" +
            "<p style=\"text-align:center;\"><b>";

        private final String part2 = "</b></p>" +
            "<p></p>\n" +
            "<style type=\"text/css\">\n" +
            "table.sample {\n" +
            "\tborder-width: thin;\n" +
            "\tborder-spacing: ;\n" +
            "\tborder-style: dotted;\n" +
            "\tborder-color: blue;\n" +
            "\tborder-collapse: collapse;\n" +
            "\tbackground-color: white;\n" +
            "}\n" +
            "table.sample th {\n" +
            "\tborder-width: 1px;\n" +
            "\tpadding: 3px;\n" +
            "\tborder-style: dotted;\n" +
            "\tborder-color: gray;\n" +
            "\tbackground-color: rgb(255, 255, 240);\n" +
            "\t-moz-border-radius: ;\n" +
            "}\n" +
            "table.sample td {\n" +
            "\tborder-width: 1px;\n" +
            "\tpadding: 3px;\n" +
            "\tborder-style: dotted;\n" +
            "\tborder-color: gray;\n" +
            "\tbackground-color: rgb(255, 255, 240);\n" +
            "\t-moz-border-radius: ;\n" +
            "}</style>\n" +
            "\n" +
            "<h2>Active nodes</h2>\n" +
            "\n" +
            "<table class=\"sample\" style=\"width: 400px\">\n" +
            "  <tbody>\n" +
            "    <tr>\n" +
            "      <td><strong>#</strong></td>\n" +
            "      <td><strong>host</strong></td>\n" +
            "      <td><strong>processing</strong></td>\n" +
            "    </tr>";

        private final String part3 = "</tbody>\n" +
            "</table>\n" +
            "\n" +
            "<p></p>\n" +
            "\n" +
            "<h2 style=\"text-align:left;\">Log</h2>\n" +
            "\n" +
            "<table class=\"sample\" style=\"width: 50%\">\n" +
            "  <tbody>\n" +
            "    <tr>\n" +
            "      <td><strong>id</strong></td>\n" +
            "      <td><strong>target</strong></td>\n" +
            "      <td><strong>decoy</strong></td>\n" +
            "      <td><strong>status</strong></td>\n" +
            "      <td><strong>user</strong></td>\n" +
            "      <td><strong>description</strong></td>\n" +
            "      <td><strong>rankdelta</strong></td>\n" +
            "      <td><strong>dat</strong></td>\n" +
            "      <td><strong>directory</strong></td>\n" +
            "      <td><strong>result file prefix</strong></td>\n" +
            "      <td><strong>spectra</strong></td>\n" +
            "      <td><strong>psm (q=0.01)</strong></td>\n" +
            "      <td><strong>pi0</strong></td>\n" +
            "      <td><strong>No Filter</strong></td>\n" +
            "      <td><strong>High Charge Calc.</strong></td>\n" +
            "      <td><strong>Charge Feature II</strong></td>\n" +
            "      <td><strong>features</strong></td>\n" +
            "      <td><strong>xml</strong></td>\n" +
            "      <td><strong>protein support</strong></td>\n" +
            "      <td><strong>rt feature</strong></td>\n" +
            "      <td><strong>processing host</strong></td>\n" +
            "      <td><strong>submit time</strong></td>\n" +
            "      <td><strong>processing start</strong></td>\n" +
            "    </tr>";

        private final String footer = "\n" +
            "  </tbody>\n" +
            "</table>\n" +
            "\n" +
            "<p></p>\n" +
            "\n" +
            "<h2 style=\"text-align:left;\">Result files help</h2>\n" +
            "\n" +
            "<table class=\"sample\" style=\"width: 800px\">\n" +
            "  <caption></caption>\n" +
            "  <col />\n" +
            "  <col />\n" +
            "  <tbody>\n" +
            "    <tr>\n" +
            "      <td><strong>Result file type</strong></td>\n" +
            "      <td>Result file path, using '+' as concatenation symbol</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td>Result file: tabular separated</td>\n" +
            "      <td>directory path + result file prefix + " + MascotPercolator.resultPostfix + "</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td>Result file: xml</td>\n" +
            "      <td>directory path + result file prefix + " + MascotPercolator.xmlPostfix + "</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td>Result file: Mascot result dat file with Percolator scores </td>\n" +
            "      <td>directory path + result file prefix + " + MascotPercolator.datPostfix + "</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td>Log file</td>\n" +
            "      <td>directory path + result file prefix + " + MascotPercolator.logPostfix + "</td>\n" +
            "    </tr>\n" +
            "  </tbody>\n" +
            "</table>\n" +
            "</body>\n" +
            "</html>";
        }
}
