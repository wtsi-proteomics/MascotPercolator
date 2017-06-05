package exception;

import java.io.IOException;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class FileIOException extends IOException {

    private IOException e;

    public FileIOException() {
    }

    public FileIOException(String message) {
        super(message);
    }

    public FileIOException(String message, IOException e) {
        super(message);
        this.e = e;
    }

    public IOException getNestedIOException() {
        return e;
    }

}
