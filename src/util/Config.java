package util;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.ConfigurationException;

/**
 * Usage: private static final String foobar = Config.properties.getString("foobar");

 * @author Markus Brosch (mb8[at]sanger[dot]ac[dot]uk)
 */
public class Config {

    private static final String file = "config.properties";
    public static PropertiesConfiguration properties;

    static {
        try {
            properties = new PropertiesConfiguration(file);
            properties.load();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void save() throws ConfigurationException {
        properties.save();
    }
}
