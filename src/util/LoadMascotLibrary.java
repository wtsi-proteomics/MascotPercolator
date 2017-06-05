package util;

import cli.MascotPercolator;

import java.io.File;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class LoadMascotLibrary {

    private static final String libmsparserj = "libmsparserj.so";
    private static final String msparserjdll = "msparserj.dll";
    private static final String libmsparserjdll = "libmsparserj.dll";

    static{
        final String path = System.getProperty("user.dir") + System.getProperty("file.separator");
        try {
            System.load(path + libmsparserj);
        } catch (UnsatisfiedLinkError e) {
            try {
                System.load(path + msparserjdll);
            } catch (UnsatisfiedLinkError e2) {
                try {
                    System.load(path + libmsparserjdll);
                } catch (UnsatisfiedLinkError e3) {
                    e.printStackTrace();
                    e2.printStackTrace();
                    System.err.println("\n\nNative code library failed to load. Please check:\n" +
                        "1. Are the native Mascot parser libraries " + libmsparserj + " (Unix) or "+ msparserjdll +" (Windows)\n" +
                        "present in the MascotPercolator directory and do they match the system architecture ? \n" +
                        "If you don't have these files already, download the MascotParser from Matrix Science at\n" +
                        "\thttp://www.matrixscience.com/msparser_download.html\n" +
                        "After unzipping, copy the files under the \"java\" subdirectory into " + path + "\n");
                    System.err.println("\n2. You may use the wrong Java environment that is incompatible with the library, e.g. 32 vs 64bit\n");
                    System.exit(-1);
                }
            }
        }
    }
}
