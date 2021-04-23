package com.sap.fontus.instrumentation.strategies;

import com.sap.fontus.asm.Descriptor;
import com.sap.fontus.config.TaintStringConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public final class InstrumentationHelper {
    private final Collection<InstrumentationStrategy> strategies = new ArrayList<>(5);
    private static InstrumentationHelper INSTANCE;

    public static InstrumentationHelper getInstance(TaintStringConfig configuration) {
        if(INSTANCE == null) {
            INSTANCE = new InstrumentationHelper(configuration);
        }
        return INSTANCE;
    }

    private InstrumentationHelper(TaintStringConfig configuration) {
        this.strategies.add(new FormatterInstrumentation(configuration));
        this.strategies.add(new MatcherInstrumentation(configuration));
        this.strategies.add(new PatternInstrumentation(configuration));
        this.strategies.add(new StringInstrumentation(configuration));
        this.strategies.add(new StringBuilderInstrumentation(configuration));
        this.strategies.add(new StringBufferInstrumentation(configuration));
        this.strategies.add(new PropertiesStrategy(configuration));
        this.strategies.add(new ProxyInstrumentation());
        this.strategies.add(new DefaultInstrumentation(configuration));
    }

    public String instrumentQN(String qn) {
        String newQN = qn;
        for (InstrumentationStrategy is : this.strategies) {
            newQN = is.instrumentQN(newQN);
        }
        return newQN;
    }

    public String instrumentForNormalCall(String desc) {
        return this.instrumentForNormalCall(Descriptor.parseDescriptor(desc)).toDescriptor();
    }

    public Descriptor instrumentForNormalCall(Descriptor desc) {
        Descriptor newDesc = desc;
        for (InstrumentationStrategy is : this.strategies) {
            newDesc = is.instrumentForNormalCall(newDesc);
        }
        return newDesc;
    }

    public String instrumentDescForIASCall(String desc) {
        String newDesc = desc;
        for (InstrumentationStrategy is : this.strategies) {
            newDesc = is.instrumentDescForIASCall(newDesc);
        }
        return newDesc;
    }

    public String translateClassName(String clazzName) {

        for (InstrumentationStrategy is : this.strategies) {
            Optional<String> os = is.translateClassName(clazzName);
            if (os.isPresent()) {
                return os.get();
            }
        }
        return clazzName;
    }

    public boolean canHandleType(String type) {
        for (InstrumentationStrategy is : this.strategies) {
            if (is.handlesType(type)) {
                return true;
            }
        }
        return false;
    }
}
