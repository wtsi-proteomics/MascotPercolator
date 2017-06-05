package util;

import exception.FileIOException;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class MascotDatFiles {

    private final static String RESULTFILESPATH = Config.properties.getString("datfiles_path");

    public static File getDatFileFromLogID(int logId) throws FileIOException {
        return getDatFileFromLogID(Integer.toString(logId));
    }

    public static File getDatFileFromLogID(String logId_or_file) throws FileIOException {
        //when user has not specified a file path or log id, return null (expected by mascot percolator)
        if (logId_or_file == null) return null;

        logId_or_file = logId_or_file.trim();

        //test whether it is a local file
        File file = new File(logId_or_file);
        if(file.exists()) return file;

        //ok, is it a log ID ? 
        try {
            List<File> datFiles = FileListing.getFileListing(new File(RESULTFILESPATH));
            for (File f : datFiles) {
                String name = f.getName().replaceAll("F0*", "");
                if (name.equals(logId_or_file + ".dat")) {
                    return f;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new FileIOException("\n" +
                "Mascot log ID: '" + logId_or_file + "' was not found!\n" +
                "Check whether the Mascot log ID is correct.\n" +
                "Check whether the path to the Mascot result files is correct (config.properties). \n" +
                "Alternatively enter the full path, e.g. \"/mascot/mascot/resutls/F00001.dat\"\n", e);
        }
        return null;
    }    
}
