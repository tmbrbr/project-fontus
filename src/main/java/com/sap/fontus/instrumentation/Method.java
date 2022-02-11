package com.sap.fontus.instrumentation;

import com.sap.fontus.asm.Descriptor;
import com.sap.fontus.asm.FunctionCall;
import com.sap.fontus.utils.MethodUtils;
import com.sap.fontus.utils.Utils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class Method {
    private final int access;
    private final String owner;
    private final String name;
    private final String descriptor;
    private final String signature;
    private final String[] exceptions;
    private final boolean ownerIsInterface;

    public Method(int access, String owner, String internalName, String descriptor, String signature, String[] exceptions, boolean ownerIsInterface) {
        this.access = access;
        this.owner = owner;
        this.name = internalName;
        this.descriptor = descriptor;
        this.signature = signature;
        this.exceptions = exceptions;
        this.ownerIsInterface = ownerIsInterface;
    }

    public int getAccess() {
        return access;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public Descriptor getParsedDescriptor() {
        return Descriptor.parseDescriptor(descriptor);
    }

    public Type getDescriptorType() {
        return Type.getMethodType(descriptor);
    }

    public String getSignature() {
        return signature;
    }

    public String[] getExceptions() {
        return exceptions;
    }

    public String[] getParameterTypes() {
        return Descriptor.parseDescriptor(this.getDescriptor()).getParameters().toArray(new String[0]);
    }

    public boolean isDefault() {
        return ((this.access & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) ==
                Modifier.PUBLIC) && this.ownerIsInterface;
    }

    public static Method from(java.lang.reflect.Method method) {
        String signature = MethodUtils.getSignature(method).orElse(null);
        return new Method(method.getModifiers(), Utils.dotToSlash(method.getDeclaringClass().getName()), method.getName(), org.objectweb.asm.commons.Method.getMethod(method).getDescriptor(), signature, Arrays.stream(method.getExceptionTypes()).map(Class::getName).map(Utils::dotToSlash).toArray(String[]::new), method.getDeclaringClass().isInterface());
    }

    public FunctionCall toFunctionCall() {
        int opcode;
        if (Modifier.isStatic(this.getAccess())) {
            opcode = Opcodes.INVOKESTATIC;
        } else if (this.ownerIsInterface) {
            opcode = Opcodes.INVOKEINTERFACE;
        } else if (Modifier.isPrivate(this.getAccess())) {
            opcode = Opcodes.INVOKESPECIAL;
        } else {
            opcode = Opcodes.INVOKEVIRTUAL;
        }

        return new FunctionCall(opcode, this.owner, this.name, this.descriptor, this.ownerIsInterface);
    }

    @Override
    public String toString() {
        return owner + '.' + name + descriptor;
    }

    public boolean equalsNameAndDescriptor(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Method method = (Method) o;
        return Objects.equals(name, method.name) && Objects.equals(descriptor, method.descriptor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Method method = (Method) o;
        return access == method.access && ownerIsInterface == method.ownerIsInterface && Objects.equals(owner, method.owner) && Objects.equals(name, method.name) && Objects.equals(descriptor, method.descriptor) && Objects.equals(signature, method.signature) && Arrays.equals(exceptions, method.exceptions);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(access, owner, name, descriptor, signature, ownerIsInterface);
        result = 31 * result + Arrays.hashCode(exceptions);
        return result;
    }
}
