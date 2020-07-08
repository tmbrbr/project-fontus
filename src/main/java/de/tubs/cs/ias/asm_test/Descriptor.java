package de.tubs.cs.ias.asm_test;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Descriptor {

    private static final Pattern PRIMITIVE_DATA_TYPES = Pattern.compile("[ZBCSIFDJ]");
    private final Collection<String> parameters;
    private final String returnType;

    private Descriptor(Collection<String> parameters, String returnType) {
        this.parameters = parameters;
        this.returnType = returnType;
    }

    Descriptor(String[] parameters, String returnType) {
        this.parameters = Arrays.asList(parameters);
        this.returnType = returnType;
    }

    // This is kinda ugly, as a single String parameter would be applicable to the var args overload below..
    // The java spec tries to resolve overloaded methods without taking var args into account first however,
    // so a single String argument will result in this constructor being invoked.
    // See: https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.2
    Descriptor(String returnType) {
        this.parameters = Collections.emptyList();
        this.returnType = returnType;
    }

    @SuppressWarnings("OverloadedVarargsMethod")
    Descriptor(String... args) {
        List<String> arguments = Arrays.asList(args);
        int lastIndex = arguments.size() - 1;
        this.returnType = arguments.get(lastIndex);
        this.parameters = arguments.subList(0, lastIndex);
    }

    private static String replaceSuffix(String s, String from, String to) {
        if (s.endsWith(from)) {
            int lIdx = s.lastIndexOf(from);
            assert lIdx != -1;
            return s.substring(0, lIdx) + to;
        }
        return s;
    }

    public Descriptor replaceType(String from, String to) {
        Collection<String> replaced = this.parameters.stream().map(str -> replaceSuffix(str, from, to)).collect(Collectors.toList());
        String ret = replaceSuffix(this.returnType, from, to);
        return new Descriptor(replaced, ret);
    }

    public int parameterCount() {
        return this.parameters.size();
    }

    public Stack<String> getParameterStack() {
        Stack<String> pStack = new Stack<>();
        pStack.addAll(this.parameters);
        return pStack;
    }

    Collection<String> getParameters() {
        return Collections.unmodifiableCollection(this.parameters);
    }

    public String getReturnType() {
        return this.returnType;
    }

    public String toDescriptor() {
        String params = String.join("", this.parameters);
        return String.format("(%s)%s", params, this.returnType);
    }

    @Override
    public String toString() {
        String params = String.join(",", this.parameters);
        return String.format("Descriptor{parameters=[%s], returnType='%s'}", params, this.returnType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || this.getClass() != obj.getClass()) return false;
        Descriptor that = (Descriptor) obj;
        return this.parameters.equals(that.parameters) &&
                this.returnType.equals(that.returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.parameters, this.returnType);
    }

//    /**
//     * Checks whether the parameter list contains String like Parameters that need conversion before calling.
//     *
//     * @return Whether on of the parameters is a String like type
//     */
//    boolean hasStringLikeParameters() {
//        for (String p : this.getParameters()) {
//            if (InstrumentationHelper.canHandleType(p)) {
//                return true;
//            }
//        }
//        return false;
//    }

    /**
     * @param className Class name in the format "java.lang.Object"
     * @return Descriptor name in the format "Ljava/lang/Object;"
     */
    public static String classNameToDescriptorName(final String className) {
        if ("int".equals(className)) {
            return "I";
        } else if ("byte".equals(className)) {
            return "B";
        } else if ("char".equals(className)) {
            return "C";
        } else if ("double".equals(className)) {
            return "D";
        } else if ("float".equals(className)) {
            return "F";
        } else if ("long".equals(className)) {
            return "J";
        } else if ("short".equals(className)) {
            return "S";
        } else if ("boolean".equals(className)) {
            return "Z";
        } else if ("void".equals(className)) {
            return "V";
        } else {
            String trimmedClassName = className;
            if (trimmedClassName.endsWith("[]")) {
                trimmedClassName = trimmedClassName.substring(0, trimmedClassName.indexOf('['));
            }

            StringBuilder descriptor = new StringBuilder(String.format("L%s;", Utils.fixupReverse(trimmedClassName)));

            int i = 0;
            while ((i = className.indexOf("[", i + 1)) >= 0) {
                descriptor.insert(0, "[");
            }

            return descriptor.toString();
        }
    }

    /**
     * Parses a textual Descriptor and disassembles it into its types
     * TODO: maybe remove the ';'s?
     * TODO: throw exception on invalid descriptor
     * TODO: think of a nicer structure, this is really messy
     */
    public static Descriptor parseDescriptor(String descriptor) {
        ArrayList<String> out;
        StringBuilder returnType;
        try (Scanner sc = new Scanner(descriptor)) {
            sc.useDelimiter("");
            String opening = sc.next();
            assert "(".equals(opening);
            out = new ArrayList<>();
            String next = sc.next();
            StringBuilder buffer = new StringBuilder();
            boolean inType = false;
            while (!")".equals(next)) {
                buffer.append(next);
                Matcher primitivesMatcher = PRIMITIVE_DATA_TYPES.matcher(next);
                if (!inType && primitivesMatcher.matches()) {
                    out.add(buffer.toString());
                    buffer = new StringBuilder();
                } else if (!"[".equals(next)) {
                    inType = true;
                    if (";".equals(next)) {
                        out.add(buffer.toString());
                        buffer = new StringBuilder();
                        inType = false;
                    }
                }

                next = sc.next();
            }

            returnType = new StringBuilder();
            while (sc.hasNext()) {
                returnType.append(sc.next());
            }
        }

        return new Descriptor(out, returnType.toString());
    }

}
