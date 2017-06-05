package queue;

import java.io.IOException;
import java.sql.SQLException;

/**
 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public interface ServerI {

    public void ping();
    public Server.RECEIVERSTATUS submitJob(Job job) throws IOException;
    public Job getJob(String nodeHostName, String scpTargetDirectory);
    public void submitFinishedJob(Job job);
    public void updateStatus(String host, Node.NODESTATUS status);

}
