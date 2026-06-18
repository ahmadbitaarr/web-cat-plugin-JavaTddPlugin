package net.sf.webcat.plugins.javatddplugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidationMessageFormatter {

    public static String formatMessage(Throwable error) {
        if (error == null) {
            return null;
        }

        if (error instanceof NoSuchMethodError) {
            return "Your test has called a method that does not exist in the interface. "
                + "The reference implementation is not guaranteed to implement this method "
                + "and as such cannot validate this test case. "
                + "The method is: " + error.getMessage();
        } 
        else if (error instanceof AssertionError) {
            String original = error.getMessage();
            if (original == null) return null;

            String msg;

            // Handle expected<X> but was<Y>
            Pattern p1 = Pattern.compile(".*expected:<?([^>]*)>? but was:<?([^>]*)>?");
            Matcher m1 = p1.matcher(original);
            if (m1.matches()) {
                String expected = m1.group(1).trim();
                String actual = m1.group(2).trim();
                msg = "Expected:\n" + expected + "\n\nActual:\n" + actual;
                return msg;
            }

            // Handle expected null
            Pattern p2 = Pattern.compile(".*expected null, but was:<?([^>]*)>?");
            Matcher m2 = p2.matcher(original);
            if (m2.matches()) {
                String actual = m2.group(1).trim();
                msg = "Expected:\n<null>\n\nActual:\n" + actual;
                return msg;
            }

            // Handle expected same object
            Pattern p3 = Pattern.compile(".*expected same:<?([^>]*)>? was not:<?([^>]*)>?");
            Matcher m3 = p3.matcher(original);
            if (m3.matches()) {
                String expected = m3.group(1).trim();
                String actual = m3.group(2).trim();
                msg = "Expected the *same* object:\n" + expected + "\n\nActual:\n" + actual;
                return msg;
            }

            // Fallback: original message
            return original;
        }

        return null;
    }
}