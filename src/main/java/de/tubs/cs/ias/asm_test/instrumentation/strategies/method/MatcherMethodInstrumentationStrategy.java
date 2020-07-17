package de.tubs.cs.ias.asm_test.instrumentation.strategies.method;

import de.tubs.cs.ias.asm_test.Constants;
import de.tubs.cs.ias.asm_test.config.TaintStringConfig;
import de.tubs.cs.ias.asm_test.instrumentation.strategies.MatcherInstrumentation;
import org.objectweb.asm.MethodVisitor;

import java.util.regex.Matcher;

public class MatcherMethodInstrumentationStrategy extends AbstractMethodInstrumentationStrategy {

    public MatcherMethodInstrumentationStrategy(MethodVisitor parentVisitor, TaintStringConfig taintStringConfig) {
        super(parentVisitor, taintStringConfig.getTMatcherDesc(), taintStringConfig.getTMatcherQN(), Constants.TMatcherToMatcherName, Matcher.class, taintStringConfig, new MatcherInstrumentation(taintStringConfig));
    }
}
