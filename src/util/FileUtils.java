package util;

import java.io.*;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class FileUtils {

    public static final String TEMPFILEPREFIX = "percolator_tmp_data";

    public static File getTmpPercolatorDir() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        if (!dir.exists()) {
            dir.mkdir();           
            System.out.println("Temporary directory created: " + dir.getAbsolutePath());
        } 
        return dir;
    }

    public static PrintWriter getPrintWriterFromFile(File f) throws IOException {
        return new PrintWriter(new FileWriter(f, true), true); //append and autoflush
    }
    
    public static File getAbsDirectoryByFile(File f) throws IOException {
        String path = f.getAbsolutePath();
        String file = f.getName();
        return new File(path.split(file)[0]);
    }

    public static void scp(String source, String targetHost, String targetDirOrFile) throws IOException {
        String cmd = "scp " + source + " " + targetHost + ":" + targetDirOrFile;
        System.out.println(cmd);
        RunCommandLine.execute(cmd);
        System.out.println("scp done");
    }

    public static void sshchmod(String host, String permissionString, String file) throws IOException {
        String cmd = "ssh " + host + " chmod " + permissionString + " " + file;
        System.out.println(cmd);
        RunCommandLine.execute(cmd);
    }

    public static void chmod(String file, String chmodStr) throws IOException {
        String cmd = "chmod " + chmodStr + " " + file;
        System.out.println(cmd);
        RunCommandLine.execute(cmd);
    }

    public static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }
}
