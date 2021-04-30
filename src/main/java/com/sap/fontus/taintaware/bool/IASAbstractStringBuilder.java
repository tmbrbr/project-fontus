package com.sap.fontus.taintaware.bool;


import com.sap.fontus.Constants;
import com.sap.fontus.taintaware.IASTaintAware;
import com.sap.fontus.taintaware.shared.*;

import java.util.Collections;
import java.util.List;

@SuppressWarnings({"SynchronizedMethod", "ReturnOfThis", "WeakerAccess", "ClassWithTooManyConstructors", "ClassWithTooManyMethods", "Since15"})
public abstract class IASAbstractStringBuilder implements IASAbstractStringBuilderable, IASTaintAware {

    // TODO: accessed in both  and unsynchronized methods
    private StringBuilder stringBuilder;
    private boolean tainted = false;

    @Override
    public boolean isTainted() {
        return this.tainted;
    }

    @Override
    public List<IASTaintRange> getTaintRanges() {
        if (isTainted()) {
            return Collections.singletonList(new IASTaintRange(0, this.length(), IASTaintSourceRegistry.TS_CS_UNKNOWN_ORIGIN));
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public void setTaint(boolean taint) {
        if (this.stringBuilder.length() > 0 || !taint) {
            this.tainted = taint;
        }
    }

    @Override
    public void setTaint(IASTaintSource source) {
        if (this.stringBuilder.length() > 0 || source == null) {
            this.tainted = source != null;
        }
    }

    @Override
    public void setContent(String content, List<IASTaintRange> taintRanges) {
        this.stringBuilder = new StringBuilder(content);
        IASTaintRanges ranges = new IASTaintRanges(taintRanges);
        ranges.resize(0, this.length(), 0);
        this.setTaint(ranges.isTainted());
    }

    private void mergeTaint(IASTaintAware other) {
        if (other != null) {
            this.tainted |= other.isTainted();
        }
    }


    public IASAbstractStringBuilder() {
        this.stringBuilder = new StringBuilder();
    }

    public IASAbstractStringBuilder(int capacity) {
        this.stringBuilder = new StringBuilder(capacity);
    }

    public IASAbstractStringBuilder(IASStringable str) {
        this.stringBuilder = new StringBuilder(str.getString());
        this.mergeTaint(str);
    }

    public IASAbstractStringBuilder(String str) {
        this.stringBuilder = new StringBuilder(str.length() + 16);
        this.stringBuilder.append(str);
    }


    public IASAbstractStringBuilder(CharSequence seq) {
        this.stringBuilder = new StringBuilder(seq.length() + 16);
        this.stringBuilder.append(seq);
        if (seq instanceof IASTaintAware) {
            IASTaintAware ta = (IASTaintAware) seq;
            this.mergeTaint(ta);
        }
    }

    public IASAbstractStringBuilder(StringBuffer buffer) {
        this.stringBuilder = new StringBuilder(buffer); //TODO: do a deep copy? Can something mess us up as this is shared?
        this.tainted = false;
    }

    public IASAbstractStringBuilder(StringBuilder sb) {
        this.stringBuilder = sb;
        this.tainted = false;
    }

    @Override
    public int length() {
        return this.stringBuilder.length();
    }


    @Override
    public int capacity() {
        return this.stringBuilder.capacity();
    }


    @Override
    public void ensureCapacity(int minimumCapacity) {
        this.stringBuilder.ensureCapacity(minimumCapacity);
    }


    @Override
    public void trimToSize() {
        this.stringBuilder.trimToSize();
    }

    @Override
    public StringBuilder getStringBuilder() {
        return new StringBuilder(this.stringBuilder);
    }


    @Override
    public void setLength(int newLength) {
        this.stringBuilder.setLength(newLength);
    }

    @Override
    public char charAt(int index) {
        return this.stringBuilder.charAt(index);
    }


    @Override
    public int codePointAt(int index) {
        return this.stringBuilder.codePointAt(index);
    }


    @Override
    public int codePointBefore(int index) {
        return this.stringBuilder.codePointBefore(index);
    }


    @Override
    public int codePointCount(int beginIndex, int endIndex) {
        return this.stringBuilder.codePointCount(beginIndex, endIndex);
    }

    @Override
    public int offsetByCodePoints(int index, int codePointOffset) {
        return this.stringBuilder.offsetByCodePoints(index, codePointOffset);
    }

    @Override
    public void getChars(int srcBegin, int srcEnd, char[] dst,
                         int dstBegin) {
        this.stringBuilder.getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    @Override
    public void setCharAt(int index, char ch) {
        this.stringBuilder.setCharAt(index, ch);
    }

    @Override
    public IASAbstractStringBuilder append(Object obj) {
        // TODO: fix?
        this.stringBuilder.append(obj);
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(IASStringable str) {
        if (str == null) {
            String s = null;
            this.stringBuilder.append(s);
            return this;
        }
        this.stringBuilder.append(str.getString());
        this.mergeTaint(str);
        return this;
    }

    public IASAbstractStringBuilder append(String str) {
        this.stringBuilder.append(str);
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(IASAbstractStringBuilderable sb) {
        this.stringBuilder.append(sb.getStringBuilder());
        this.mergeTaint(sb);
        return this;
    }

    public IASAbstractStringBuilder append(IASAbstractStringBuilder sb) {
        this.stringBuilder.append(sb.getStringBuilder());
        this.mergeTaint(sb);
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(CharSequence csq) {
        this.stringBuilder.append(csq);
        if (csq instanceof IASTaintAware) {
            IASTaintAware ta = (IASTaintAware) csq;
            this.mergeTaint(ta);
        }
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(CharSequence csq, int start, int end) {
        this.stringBuilder.append(csq, start, end);
        if (csq instanceof IASTaintAware) {
            IASTaintAware ta = (IASTaintAware) csq;
            this.mergeTaint(ta);
        }
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(char[] str) {
        this.stringBuilder.append(str);
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(char[] str, int offset, int len) {
        this.stringBuilder.append(str, offset, len);
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(boolean b) {
        this.stringBuilder.append(b);
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(char c) {
        this.stringBuilder.append(c);
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(int i) {
        this.stringBuilder.append(i);
        return this;
    }

    @Override
    public IASAbstractStringBuilder appendCodePoint(int codePoint) {
        this.stringBuilder.appendCodePoint(codePoint);
        return this;
    }


    @Override
    public IASAbstractStringBuilder append(long lng) {
        this.stringBuilder.append(lng);
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(float f) {
        this.stringBuilder.append(f);
        return this;
    }

    @Override
    public IASAbstractStringBuilder append(double d) {
        this.stringBuilder.append(d);
        return this;
    }


    @Override
    public IASAbstractStringBuilder delete(int start, int end) {
        this.stringBuilder.delete(start, end);
        if (this.stringBuilder.length() == 0) {
            this.tainted = false;
        }
        return this;
    }


    @Override
    public IASAbstractStringBuilder deleteCharAt(int index) {
        this.stringBuilder.deleteCharAt(index);
        if (this.stringBuilder.length() == 0) {
            this.tainted = false;
        }
        return this;
    }


    @Override
    public IASAbstractStringBuilder replace(int start, int end, IASStringable str) {
        this.stringBuilder.replace(start, end, str.getString());
        this.mergeTaint(str);
        return this;
    }

    @Override
    public IASString substring(int start) {
        return this.substring(start, this.stringBuilder.length());
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new IASString(this.stringBuilder.substring(start, end), this.tainted);
    }


    @Override
    public IASString substring(int start, int end) {
        return new IASString(this.stringBuilder.substring(start, end), this.tainted);
    }

    @Override
    public IASAbstractStringBuilder insert(int index, char[] str, int offset,
                                           int len) {
        this.stringBuilder.insert(index, str, offset, len);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int offset, Object obj) {
        return this.insert(offset, IASString.valueOf(obj));
    }

    @Override
    public IASAbstractStringBuilder insert(int offset, IASStringable str) {
        this.stringBuilder.insert(offset, str);
        this.mergeTaint(str);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int offset, char[] str) {
        this.stringBuilder.insert(offset, str);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int dstOffset, CharSequence s) {
        if (s instanceof IASTaintAware) {
            IASTaintAware ta = (IASTaintAware) s;
            this.mergeTaint(ta);
        }
        this.stringBuilder.insert(dstOffset, s);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int dstOffset, CharSequence s,
                                           int start, int end) {
        if (s instanceof IASTaintAware) {
            IASTaintAware ta = (IASTaintAware) s;
            this.mergeTaint(ta);
        }
        this.stringBuilder.insert(dstOffset, s, start, end);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int offset, boolean b) {
        this.stringBuilder.insert(offset, b);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int offset, char c) {
        this.stringBuilder.insert(offset, c);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int offset, int i) {
        this.stringBuilder.insert(offset, i);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int offset, long l) {
        this.stringBuilder.insert(offset, l);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int offset, float f) {
        this.stringBuilder.insert(offset, f);
        return this;
    }

    @Override
    public IASAbstractStringBuilder insert(int offset, double d) {
        this.stringBuilder.insert(offset, d);
        return this;
    }

    @Override
    public int indexOf(IASStringable str) {
        // Note, synchronization achieved via invocations of other StringBuffer methods
        return this.stringBuilder.indexOf(str.getString());
    }

    @Override
    public int indexOf(IASStringable str, int fromIndex) {
        return this.stringBuilder.indexOf(str.getString(), fromIndex);
    }

    @Override
    public int lastIndexOf(IASStringable str) {
        // Note, synchronization achieved via invocations of other StringBuffer methods
        return this.lastIndexOf(str, this.stringBuilder.length()); //TODO: correct?
    }

    @Override
    public int lastIndexOf(IASStringable str, int fromIndex) {
        return this.stringBuilder.lastIndexOf(str.getString(), fromIndex);
    }

    @Override
    public IASAbstractStringBuilder reverse() {
        this.stringBuilder.reverse();
        return this;
    }

    @Override
    public IASString toIASString() {
        return new IASString(this.stringBuilder.toString(), this.tainted);
    }

    public String toString() {
        return this.stringBuilder.toString();
    }

    @Override
    public int compareTo(IASAbstractStringBuilderable o) {
        if (Constants.JAVA_VERSION < 11) {
            return this.toIASString().compareTo(IASString.valueOf(o));
        } else {
            return this.stringBuilder.compareTo(o.getStringBuilder());
        }
    }
}
