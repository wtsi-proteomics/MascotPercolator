package util;

import java.io.*;

public class StreamGobbler extends Thread {
    private String type;
    private PrintWriter pw;
    private BufferedReader br;

    public StreamGobbler(InputStream is, String type) {
        this(is, type, null);
    }

    public StreamGobbler(InputStream is, String type, Writer redirect) {
        this.type = type;
        this.pw = new PrintWriter(redirect, true);
        this.br = new BufferedReader(new InputStreamReader(is));
    }

    public void run() {
        try {
            String line = null;
            while ((line = br.readLine()) != null) {
                if (pw != null) {
                    pw.println(line);
                    pw.flush();
                }
                //also output to console if error
                if (type.equals("ERROR")) System.out.println(line);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
    }
}