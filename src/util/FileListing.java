package util;

import java.io.FileNotFoundException;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public final class FileListing {

    /**
     * Demonstrate use.
     *
     * @param aArgs - <tt>aArgs[0]</tt> is the full name of an existing directory,
     *              that can be read
     */
    public static void main(String... aArgs) throws FileNotFoundException {
        File tempDir = new File(aArgs[0]);
        List<File> files = getFileListing(tempDir);

        //print out all file names, and display the order of File.compareTo
        for (File file : files) {
            System.out.println(file);
        }
    }

    /**
     * Recursively walk a directory tree and return a List of all
     * Files found; the List is sorted using File.compareTo.
     *
     * @param aStartingDir is a valid directory, which can be read.
     */
    static public List<File> getFileListing(File aStartingDir) throws FileNotFoundException {

        List<File> result = new ArrayList<File>();
        //System.out.println( "About to TRY:: " + aStartingDir.toString());

        /*
        try {
            validateDirectory(aStartingDir);
        } catch(IllegalArgumentException e) {
            return result;
        }
        */

        File[] filesAndDirs = aStartingDir.listFiles();
        List<File> filesDirs = Arrays.asList(filesAndDirs);
        for (File file : filesDirs) {
            if(file.isFile()) result.add(file);
            else if(file.isDirectory()) {
                List<File> deeperList = getFileListing(file);
                result.addAll(deeperList);
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Directory is valid if it exists, does not represent a file, and can be read.
     * @param aDirectory
     * @throws java.io.FileNotFoundException
     */
    /*
    static private void validateDirectory(File aDirectory) throws FileNotFoundException {
        if (aDirectory == null) {
            throw new IllegalArgumentException("Directory should not be null.");
        }
        if (!aDirectory.exists()) {
            throw new FileNotFoundException("Directory does not exist: " + aDirectory);
        }
        if (!aDirectory.isDirectory()) {
            throw new IllegalArgumentException("Is not a directory: " + aDirectory);
        }

        if (!aDirectory.canRead()) {
            throw new IllegalArgumentException("Directory cannot be read: " + aDirectory);
        }

    }

    */
}

