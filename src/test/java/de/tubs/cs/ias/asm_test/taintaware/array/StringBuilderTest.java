package de.tubs.cs.ias.asm_test.taintaware.array;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

public class StringBuilderTest {
    private static final int TAINT = IASTaintSource.TS_CS_UNKNOWN_ORIGIN.getId();

    @Test
    public void testConstructor1() {
        IASString string = new IASString("Hello World");

        IASStringBuilder sb = new IASStringBuilder(string);

        assertTrue(sb.isUninitialized());
        assertFalse(sb.isTainted());
        assertEquals("Hello World", sb.toString());
    }

    @Test
    public void testConstructor2() {
        IASString string = new IASString("Hello World", true);

        IASStringBuilder sb = new IASStringBuilder(string);

        assertTrue(sb.isInitialized());
        assertTrue(sb.isTainted());
        assertEquals("Hello World", sb.toString());
    }

    @Test
    public void testAppend1() {
        IASStringBuilder sb = new IASStringBuilder();
        IASString toAppend = IASString.fromString("Hello World");

        sb.append(toAppend);

        assertEquals("Hello World", sb.toString());
        assertFalse(sb.isTainted());
    }

    @Test
    public void testAppend2() {
        IASStringBuilder sb = new IASStringBuilder("Hello");
        IASString toAppend = IASString.fromString(" World");

        sb.append(toAppend);

        assertEquals("Hello World", sb.toString());
        assertFalse(sb.isTainted());
    }

    @Test
    public void testAppend3() {
        IASStringBuilder sb = new IASStringBuilder("Hello");
        IASString toAppend = new IASString(" World", true);

        sb.append(toAppend);

        assertEquals("Hello World", sb.toString());
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, TAINT, TAINT, TAINT, TAINT, TAINT, TAINT}, sb.getTaints());
    }

    @Test
    public void testAppend4() {
        IASStringBuilder sb = new IASStringBuilder("Hello");
        sb.setTaint(true);
        IASString toAppend = new IASString(" World", true);

        sb.append(toAppend);

        assertEquals("Hello World", sb.toString());
        assertArrayEquals(new int[]{TAINT, TAINT, TAINT, TAINT, TAINT, TAINT, TAINT, TAINT, TAINT, TAINT, TAINT}, sb.getTaints());
    }

    @Test
    public void testAppend5() {
        IASStringBuilder sb = new IASStringBuilder("Hello");
        sb.setTaint(true);
        IASString toAppend = new IASString(" World");

        sb.append(toAppend);

        assertEquals("Hello World", sb.toString());
        assertArrayEquals(new int[]{TAINT, TAINT, TAINT, TAINT, TAINT, 0, 0, 0, 0, 0, 0}, sb.getTaints());
    }

    @Test
    public void testInsert() {
        IASString s2 = new IASString("World");
        IASString s1 = new IASString("Hello ");
        IASStringBuffer sb1 = new IASStringBuffer(s2);

        sb1.setTaint(true);

        IASStringBuffer sb2 = sb1.insert(0, s1);

        assertEquals("Hello World", sb1.toString());
        assertEquals("Hello World", sb2.toString());
        assertFalse(s1.isTainted());
        assertFalse(s2.isTainted());
        assertTrue(sb1.isTainted());
        assertTrue(sb2.isTainted());
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, TAINT, TAINT, TAINT, TAINT, TAINT}, sb1.getTaints());
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, TAINT, TAINT, TAINT, TAINT, TAINT}, sb2.getTaints());
    }

    @Test
    public void testReplace1() {
        IASStringBuffer sb1 = new IASStringBuffer("hello");
        IASString s = new IASString("HELLO");

        sb1.setTaint(true);
        s.setTaint(false);

        IASStringBuffer sb2 = sb1.replace(0, 2, s);

        assertEquals("HELLOllo", sb1.toString());
        assertEquals("HELLOllo", sb2.toString());
        assertFalse(s.isTainted());
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, TAINT, TAINT, TAINT}, sb1.getTaints());
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, TAINT, TAINT, TAINT}, sb2.getTaints());
    }
}
