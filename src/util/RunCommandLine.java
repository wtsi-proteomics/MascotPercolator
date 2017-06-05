package util;

import exception.FileIOException;

import java.io.*;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class RunCommandLine {

    public static int execute(String command) throws IOException {
        StringWriter stdOutWriter = new StringWriter();
        StringWriter errOutWriter = new StringWriter();
        return execute(command, stdOutWriter, errOutWriter);
    }

    public static int execute(String command, Writer stdOutStream, Writer errorOutStream) throws IOException {
        int exitValue;
        Runtime rt = Runtime.getRuntime();

        Process proc = null;
        try {
            proc = rt.exec(command);
        } catch (IOException e) {
            System.err.println("Can not run this command: '" + command + "'");
            throw(e);
        }

        StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT", stdOutStream);
        StreamGobbler logGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR", errorOutStream);
        logGobbler.start();
        outputGobbler.start();

        // wait for process to finish and test the exit value. Exception, if not done and test again.
        while (true) {
            try {
                exitValue = proc.exitValue();
                if(exitValue != 0) throw new IOException("Return value != 0\n" + errorOutStream.toString());
                break;
            }
            catch (IllegalThreadStateException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }

        // Wait for output gobblers to finish forwarding the output
        while (outputGobbler.isAlive() || logGobbler.isAlive()) {
        }

        if(proc!=null) proc.destroy();
        return exitValue;
    }   
}