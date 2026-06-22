package net.sf.webcat.plugins.javatddplugin;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.AssertionFailedError;
import junit.framework.Test;

import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.util.StringUtils;


// -------------------------------------------------------------------------
/**
 * A custom Web-CAT JUnit formatter that captures expected/actual values from
 * assertion failures and errors.
 *
 * This formatter intentionally extends HintingJUnitResultFormatter so Web-CAT's
 * existing hint/scoring behavior is preserved. It does not print to stdout or
 * stderr. Instead, it appends output to the formatter-controlled output buffer
 * that Ant writes to the configured formatter output file.
 */
public class ExpectedActualFormatter
    extends HintingJUnitResultFormatter
{
    // ----------------------------------------------------------
    /**
     * Handles normal JUnit assertion failures.
     *
     * @param test the test that failed
     * @param error the assertion failure
     */
    @Override
    public void addFailure(Test test, AssertionFailedError error)
    {
        super.addFailure(test, error);
        appendExpectedActual(test, error, "failure");
    }


    // ----------------------------------------------------------
    /**
     * Handles errors and non-standard assertion failures.
     *
     * @param test the test that errored
     * @param error the thrown error/exception
     */
    @Override
    public void addError(Test test, Throwable error)
    {
        super.addError(test, error);
        appendExpectedActual(test, error, "error");
    }


    // ----------------------------------------------------------
    /**
     * Adds this formatter's output to the same controlled output buffer used by
     * the parent formatter. The parent class writes this buffer to Ant's
     * configured formatter output file.
     *
     * @param buffer the formatter output buffer
     * @param suite the JUnit suite that completed
     */
    @Override
    protected void outputForSuite(StringBuffer buffer, JUnitTest suite)
    {
        super.outputForSuite(buffer, suite);

        if (expectedActualOutput.length() > 0)
        {
            buffer.append(StringUtils.LINE_SEP);
            buffer.append("# Expected/Actual Diagnostics");
            buffer.append(StringUtils.LINE_SEP);
            buffer.append("# ---------------------------");
            buffer.append(StringUtils.LINE_SEP);
            buffer.append(expectedActualOutput.toString());
        }

        expectedActualOutput.setLength(0);
    }


    // ----------------------------------------------------------
    /**
     * Extracts expected/actual values and appends them to this formatter's
     * controlled output buffer.
     *
     * @param test the test associated with the failure/error
     * @param error the thrown failure/error
     * @param resultType either "failure" or "error"
     */
    private void appendExpectedActual(
        Test test,
        Throwable error,
        String resultType)
    {
        ExpectedActualPair pair = extractExpectedActual(error);

        if (pair == null)
        {
            return;
        }

        synchronized (expectedActualOutput)
        {
            expectedActualOutput.append("# ---------------------------");
            expectedActualOutput.append(StringUtils.LINE_SEP);

            expectedActualOutput.append("# Test: ");
            expectedActualOutput.append(testName(test));
            expectedActualOutput.append(StringUtils.LINE_SEP);

            expectedActualOutput.append("# Type: ");
            expectedActualOutput.append(resultType);
            expectedActualOutput.append(StringUtils.LINE_SEP);

            expectedActualOutput.append("# Expected:");
            expectedActualOutput.append(StringUtils.LINE_SEP);
            appendCommentedMultiline(expectedActualOutput, pair.expected);

            expectedActualOutput.append("# Actual:");
            expectedActualOutput.append(StringUtils.LINE_SEP);
            appendCommentedMultiline(expectedActualOutput, pair.actual);

            expectedActualOutput.append("# ---------------------------");
            expectedActualOutput.append(StringUtils.LINE_SEP);
        }
    }


    // ----------------------------------------------------------
    /**
     * Attempts to extract expected/actual values from the Throwable or one of
     * its causes.
     *
     * The order is:
     * 1. public getExpected()/getActual() methods, used by comparison failures
     * 2. regex fallback for messages like expected:<x> but was:<y>
     *
     * @param error the failure/error to inspect
     * @return expected/actual pair, or null if not available
     */
    private ExpectedActualPair extractExpectedActual(Throwable error)
    {
        Throwable current = error;

        while (current != null)
        {
            String expected = invokeStringGetter(current, "getExpected");
            String actual = invokeStringGetter(current, "getActual");

            if (expected != null && actual != null)
            {
                return new ExpectedActualPair(expected, actual);
            }

            ExpectedActualPair fromMessage =
                extractExpectedActualFromMessage(current.getMessage());

            if (fromMessage != null)
            {
                return fromMessage;
            }

            current = current.getCause();
        }

        return null;
    }


    // ----------------------------------------------------------
    /**
     * Uses reflection so the formatter can work with both JUnit comparison
     * failure types without depending on one exact implementation.
     *
     * @param error the Throwable to inspect
     * @param methodName the getter name
     * @return the getter value as a String, or null if unavailable
     */
    private String invokeStringGetter(Throwable error, String methodName)
    {
        try
        {
            Method method = error.getClass().getMethod(methodName);
            Object value = method.invoke(error);

            if (value != null)
            {
                return value.toString();
            }
        }
        catch (Exception ignored)
        {
            // Getter does not exist or cannot be accessed. Use message regex.
        }

        return null;
    }


    // ----------------------------------------------------------
    /**
     * Fallback parser for common JUnit assertion messages.
     *
     * @param message the failure/error message
     * @return expected/actual pair, or null if not recognized
     */
    private ExpectedActualPair extractExpectedActualFromMessage(String message)
    {
        if (message == null)
        {
            return null;
        }

        Matcher matcher = ANGLE_EXPECTED_ACTUAL.matcher(message);

        if (matcher.matches())
        {
            return new ExpectedActualPair(matcher.group(1), matcher.group(2));
        }

        matcher = PLAIN_EXPECTED_ACTUAL.matcher(message);

        if (matcher.matches())
        {
            return new ExpectedActualPair(
                matcher.group(1).trim(),
                matcher.group(2).trim());
        }

        return null;
    }


    // ----------------------------------------------------------
    /**
     * Appends a possibly multiline value as comments so the output remains safe
     * if it is later concatenated into a Perl-style .inc file.
     *
     * @param buffer the output buffer
     * @param value the value to append
     */
    private void appendCommentedMultiline(StringBuffer buffer, String value)
    {
        if (value == null)
        {
            value = "null";
        }

        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);

        for (int i = 0; i < lines.length; i++)
        {
            buffer.append("#   ");
            buffer.append(lines[i]);
            buffer.append(StringUtils.LINE_SEP);
        }
    }


    // ----------------------------------------------------------
    /**
     * Safely formats the test name.
     *
     * @param test the JUnit test
     * @return readable test name
     */
    private String testName(Test test)
    {
        if (test == null)
        {
            return "<unknown test>";
        }

        return test.toString();
    }


    // ----------------------------------------------------------
    /**
     * Simple expected/actual holder.
     */
    private static class ExpectedActualPair
    {
        public ExpectedActualPair(String expected, String actual)
        {
            this.expected = expected;
            this.actual = actual;
        }

        public final String expected;
        public final String actual;
    }


    private static final Pattern ANGLE_EXPECTED_ACTUAL = Pattern.compile(
        "(?s).*expected:\\s*<(.*?)>\\s*but\\s*was:\\s*<(.*?)>.*");

    private static final Pattern PLAIN_EXPECTED_ACTUAL = Pattern.compile(
        "(?s).*expected:\\s*(.*?)\\s*but\\s*was:\\s*(.*).*");

    private final StringBuffer expectedActualOutput = new StringBuffer();
}