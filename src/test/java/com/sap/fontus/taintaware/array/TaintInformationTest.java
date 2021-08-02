package com.sap.fontus.taintaware.array;

import com.sap.fontus.config.Configuration;
import com.sap.fontus.config.TaintMethod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TaintInformationTest {

    @BeforeAll
    public static void init() {
        Configuration.setTestConfig(TaintMethod.ARRAY);
    }

    @Test
    public void testSetTaint1() {
        IASTaintInformation ti = new IASTaintInformation(5);

        ti.setTaint(0, 5, 1);

        assertTrue(ti.isTainted());
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, ti.getTaints());
    }

    @Test
    public void testSetTaint2() {
        IASTaintInformation ti = new IASTaintInformation(5);

        ti.setTaint(1, 4, 1);

        assertTrue(ti.isTainted());
        assertArrayEquals(new int[]{0, 1, 1, 1, 0}, ti.getTaints());
    }

    @Test
    public void testSetTaint3() {
        IASTaintInformation ti = new IASTaintInformation(0);

        ti.setTaint(0, 5, 1);

        assertTrue(ti.isTainted());
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, ti.getTaints());
    }

    @Test
    public void testSetTaint4() {
        IASTaintInformation ti = new IASTaintInformation(0);

        ti.setTaint(1, 4, 1);

        assertTrue(ti.isTainted());
        assertArrayEquals(new int[]{0, 1, 1, 1}, ti.getTaints());
    }

    @Test
    public void testSetTaint5() {
        IASTaintInformation ti = new IASTaintInformation(3);

        ti.setTaint(0, 5, 1);

        assertTrue(ti.isTainted());
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, ti.getTaints());
    }

    @Test
    public void testSetTaint6() {
        IASTaintInformation ti = new IASTaintInformation(3);

        ti.setTaint(1, 4, 1);

        assertTrue(ti.isTainted());
        assertArrayEquals(new int[]{0, 1, 1, 1}, ti.getTaints());
    }

    @Test
    public void testSetTaint7() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{2, 2, 2, 2, 2});

        ti.setTaint(0, 5, 1);

        assertTrue(ti.isTainted());
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, ti.getTaints());
    }

    @Test
    public void testSetTaint8() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{2, 2, 2, 2, 2});

        ti.setTaint(1, 4, 1);

        assertTrue(ti.isTainted());
        assertArrayEquals(new int[]{2, 1, 1, 1, 2}, ti.getTaints());
    }

    @Test
    public void testGetTaint1() {
        IASTaintInformation ti = new IASTaintInformation(0);

        int[] taint = ti.getTaints();

        assertArrayEquals(new int[0], taint);
    }

    @Test
    public void testGetTaint2() {
        IASTaintInformation ti = new IASTaintInformation(1);

        int[] taint = ti.getTaints();

        assertArrayEquals(new int[1], taint);
    }

    @Test
    public void testGetTaint3() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{1, 2, 3});

        int[] taint = ti.getTaints();

        assertArrayEquals(new int[]{1, 2, 3}, taint);
    }

    @Test
    public void testGetTaintByIndex1() {
        IASTaintInformation ti = new IASTaintInformation(0);

        assertThrows(IndexOutOfBoundsException.class, () -> {
            ti.getTaints(0, 1);
        });
    }

    @Test
    public void testGetTaintByIndex2() {
        IASTaintInformation ti = new IASTaintInformation(3);

        assertThrows(IndexOutOfBoundsException.class, () -> {
            ti.getTaints(1, 5);
        });
    }

    @Test
    public void testGetTaintByIndex3() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{1, 2, 3, 4, 5});

        int[] taint = ti.getTaints(1, 4);

        assertArrayEquals(new int[]{2, 3, 4}, taint);
    }

    @Test
    public void testResize1() {
        IASTaintInformation ti = new IASTaintInformation(0);

        ti.resize(5);

        assertEquals(5, ti.getLength());
    }

    @Test
    public void testResize2() {
        IASTaintInformation ti = new IASTaintInformation(2);

        ti.resize(5);

        assertEquals(5, ti.getLength());
    }

    @Test
    public void testResize3() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{0, 0});

        ti.resize(5);

        assertEquals(5, ti.getLength());
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, ti.getTaints());
    }

    @Test
    public void testResize4() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{1, 2});

        ti.resize(5);

        assertEquals(5, ti.getLength());
        assertArrayEquals(new int[]{1, 2, 0, 0, 0}, ti.getTaints());
    }

    @Test
    public void testResize5() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{1, 2, 3, 4, 5});

        ti.resize(2);

        assertEquals(2, ti.getLength());
        assertArrayEquals(new int[]{1, 2}, ti.getTaints());
    }

    @Test
    public void testResize6() {
        IASTaintInformation ti = new IASTaintInformation(5);

        ti.resize(2);

        assertEquals(2, ti.getLength());
    }

    @Test
    public void testRemoveAll1() {
        IASTaintInformation ti = new IASTaintInformation(0);

        ti.clearTaint(0,0);

        assertFalse(ti.isTainted());
    }

    @Test
    public void testRemoveAll2() {
        IASTaintInformation ti = new IASTaintInformation(3);

        ti.clearTaint(0, 3);

        assertFalse(ti.isTainted());
    }

    @Test
    public void testRemoveAll3() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{1, 2, 3});

        ti.clearTaint(0, 3);

        assertFalse(ti.isTainted());
    }

    @Test
    public void testInsert1() {
        IASTaintInformation ti = new IASTaintInformation(0);

        ti.insertTaint(5, new int[]{1, 2, 3});

        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 1, 2, 3}, ti.getTaints());
    }

    @Test
    public void testInsert2() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{0, 0, 0, 0, 0});

        ti.insertTaint(5, new int[]{1, 2, 3});

        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 1, 2, 3}, ti.getTaints());
    }

    @Test
    public void testInsert3() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{0, 0, 0});

        ti.insertTaint(2, new int[]{1, 2, 3});

        assertArrayEquals(new int[]{0, 0, 1, 2, 3, 0}, ti.getTaints());
    }

    @Test
    public void testRemoveTaintFor1() {
        IASTaintInformation ti = new IASTaintInformation(new int[]{1, 2, 3, 4, 5});

        ti.deleteWithShift(1, 4);

        assertArrayEquals(new int[]{1, 5}, ti.getTaints());
    }

    @Test
    public void testRemoveTaintFor3() {
        IASTaintInformation ti = new IASTaintInformation(5);

        ti.deleteWithShift(1, 4);

        assertFalse(ti.isTainted());
    }

    @Test
    public void testRemoveTaintFor4() {
        IASTaintInformation ti = new IASTaintInformation(5);

        ti.deleteWithShift(1, 4);

        assertFalse(ti.isTainted());
    }

    @Test
    public void testRemoveTaintFor5() {
        IASTaintInformation ti = new IASTaintInformation(0);

        assertThrows(IndexOutOfBoundsException.class, () -> ti.deleteWithShift(1, 4));
    }

    @Test
    public void testRemoveTaintFor6() {
        IASTaintInformation ti = new IASTaintInformation(0);

        assertThrows(IndexOutOfBoundsException.class, () -> ti.deleteWithShift(1, 4));
    }
}
