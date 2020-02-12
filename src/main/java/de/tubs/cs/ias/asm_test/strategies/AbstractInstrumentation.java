package de.tubs.cs.ias.asm_test.strategies;

import de.tubs.cs.ias.asm_test.Descriptor;
import de.tubs.cs.ias.asm_test.Utils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractInstrumentation implements InstrumentationStrategy {
    protected final Pattern qnMatcher;
    protected final String origDesc;
    protected final String taintedDesc;
    protected final String taintedQN;
    protected final String origQN;
    protected final Pattern descPattern;

    public AbstractInstrumentation(String origDesc, String taintedDesc, String origQN, String taintedQN) {
        this.origDesc = origDesc;
        this.taintedDesc = taintedDesc;
        this.taintedQN = taintedQN;
        this.origQN = origQN;
        this.qnMatcher = Pattern.compile(origQN, Pattern.LITERAL);
        this.descPattern = Pattern.compile(origDesc);
    }

    @Override
    public Descriptor instrument(Descriptor desc) {
        return desc.replaceType(origDesc, taintedDesc);
    }

    @Override
    public String instrumentQN(String qn) {
        return this.qnMatcher.matcher(qn).replaceAll(Matcher.quoteReplacement(this.taintedQN));
    }

    @Override
    public String instrumentDesc(String desc) {
        return this.descPattern.matcher(desc).replaceAll(this.taintedDesc);
    }

    @Override
    public Optional<String> translateClassName(String className) {
        if (className.equals(Utils.fixup(this.origQN))) {
            return Optional.of(Utils.fixup(this.taintedQN));
        }
        return Optional.empty();
    }

    @Override
    public boolean handlesType(String typeName) {
        return this.origDesc.endsWith(typeName);
    }
}
