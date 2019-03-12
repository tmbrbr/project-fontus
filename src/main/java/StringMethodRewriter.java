import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;
import java.util.regex.Matcher;


import static org.objectweb.asm.Opcodes.*;

public class StringMethodRewriter extends ClassVisitor {
    private static final Logger LOGGER = Logger.getLogger(StringMethodRewriter.class.getName());

    private final Collection<BlackListEntry> blacklist = new ArrayList<>();

    StringMethodRewriter(ClassVisitor cv) {
        super(ASM7, cv);
        this.blacklist.add(new BlackListEntry("main", "([Ljava/lang/String;)V", ACC_PUBLIC + ACC_STATIC));
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                   String signature, Object value) {

        Matcher descMatcher = Constants.strPattern.matcher(descriptor);
        if(descMatcher.find()) {
            LOGGER.info(String.format("Replacing field %d:%s (%s)", access, name, descriptor));
            String newDescriptor = descMatcher.replaceAll(Constants.TStringDesc);
            return super.visitField(access, name, newDescriptor, signature, value);
        } else {
            return super.visitField(access, name, descriptor, signature, value);
        }
    }

        @Override
    public MethodVisitor visitMethod(
            final int access,
            final String name,
            final String descriptor,
            final String signature,
            final String[] exceptions) {

        Matcher descMatcher = Constants.strPattern.matcher(descriptor);
        MethodVisitor mv;

        if (!this.blacklist.contains(new BlackListEntry(name, descriptor, access)) && descMatcher.find()) {
            LOGGER.info(String.format("Rewriting method signature %s (%s)", name, descriptor));
            String newDescriptor = descMatcher.replaceAll(Constants.TStringDesc);
            mv = super.visitMethod(access, name, newDescriptor, signature, exceptions);
        } else {
            mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        return new StringRewriterVisitor(mv);
    }
}
