package deprecated;

import java.util.regex.*;

public class RegExTest {
    public static void main(String[] args) {
        String target = "Foo,Bar;\"Quoted text\";Foo2;Bar2";

        Pattern p = Pattern.compile("(?:;+|^)(?:([\"'])(.*?)\\1|([^;]+))");
        Matcher m = p.matcher(target);
        while (m.find()) {
            System.out.println(m.group(1) != null ? m.group(2) : m.group(3));
        }
    }
}