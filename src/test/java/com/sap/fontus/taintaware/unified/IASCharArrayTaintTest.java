package com.sap.fontus.taintaware.unified;

import com.sap.fontus.config.Configuration;
import com.sap.fontus.config.TaintMethod;
import com.sap.fontus.taintaware.range.IASTaintInformation;
import com.sap.fontus.taintaware.shared.IASBasicMetadata;
import com.sap.fontus.taintaware.shared.IASTaintMetadata;
import com.sap.fontus.taintaware.shared.IASTaintSourceRegistry;
import com.sap.fontus.taintaware.testHelper.THelper;
import com.sap.fontus.taintaware.testHelper.TaintMatcher;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.sap.fontus.taintaware.testHelper.RangeChainer.range;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class IASCharArrayTaintTest {

    private final static IASTaintMetadata md = new IASBasicMetadata(IASTaintSourceRegistry.getInstance().getOrRegisterObject("dummy"));

    @BeforeAll
    public static void init() {
        Configuration.setTestConfig(TaintMethod.RANGE);
    }

    @Test
    public void testCharArrayTaint() {
        IASString foo = new IASString("foo");
        ((IASTaintInformation) THelper.get(foo)).addRange(0, 3, md);

        // Extract to char
        char[] chars = new char[foo.length()];
        foo.getChars(0, foo.length(), chars, 0);

        // Create a new string from the chars
        IASString bar = new IASString(chars);

        MatcherAssert.assertThat(bar, TaintMatcher.taintEquals(range(0, 3, md)));
    }

    @Test
    public void testCharArrayTaintStringBuilder() {
        IASStringBuilder foo = new IASStringBuilder("foo");
        ((IASTaintInformation) THelper.get(foo)).addRange(0, 3, md);

        // Extract to char
        char[] chars = new char[foo.length()];
        foo.getChars(0, foo.length(), chars, 0);

        // Create a new string from the chars
        IASString bar = new IASString(chars);

        MatcherAssert.assertThat(bar, TaintMatcher.taintEquals(range(0, 3, md)));
    }

    @Test
    public void testCharArrayTaintSubTaint() {
        IASStringBuilder foo = new IASStringBuilder("foobar");
        ((IASTaintInformation) THelper.get(foo)).addRange(2, 4, md);

        // Extract to char
        char[] chars = new char[foo.length()];
        foo.getChars(0, foo.length(), chars, 0);

        // Create a new string from the chars
        IASString bar = new IASString(chars);
        assertEquals("foobar", bar.getString());
        MatcherAssert.assertThat(bar, TaintMatcher.taintEquals(range(2, 4, md)));
    }

    @Test
    public void testCharArrayTaintSubString() {
        IASStringBuilder foo = new IASStringBuilder("foobar");
        ((IASTaintInformation) THelper.get(foo)).addRange(3, 6, md);

        // Extract to char
        char[] chars = new char[3];
        foo.getChars(3, 6, chars, 0);

        // Create a new string from the chars
        IASString bar = new IASString(chars);

        assertEquals("bar", bar.getString());
        MatcherAssert.assertThat(bar, TaintMatcher.taintEquals(range(0, 3, md)));
    }

    @Test
    public void testCharArrayTaintDstSubString() {
        IASStringBuilder foo = new IASStringBuilder("foobar");
        ((IASTaintInformation) THelper.get(foo)).addRange(3, 6, md);

        // Extract to char
        char[] chars = new char[4];
        chars[0] = 'B';
        foo.getChars(3, 6, chars, 1);

        // Create a new string from the chars
        IASString bar = new IASString(chars);

        assertEquals("Bbar", bar.getString());
        MatcherAssert.assertThat(bar, TaintMatcher.taintEquals(range(1, 4, md)));
    }

    @Test
    public void testCharArrayTaintNewStringOffset() {
        IASString foo = new IASString("foobar");
        ((IASTaintInformation) THelper.get(foo)).addRange(0, 3, md);

        // Extract to char
        char[] chars = new char[foo.length()];
        foo.getChars(0, foo.length(), chars, 0);

        // Create a new string from the chars
        IASString bar = new IASString(chars, 2, 3);

        assertEquals("oba", bar.getString());
        MatcherAssert.assertThat(bar, TaintMatcher.taintEquals(range(0, 1, md)));
    }

}
