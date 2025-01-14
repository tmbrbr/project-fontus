package com.sap.fontus.instrumentation;

import com.sap.fontus.Constants;
import com.sap.fontus.asm.*;
import com.sap.fontus.config.Configuration;
import com.sap.fontus.config.Position;
import com.sap.fontus.config.Sink;
import com.sap.fontus.config.Source;
import com.sap.fontus.instrumentation.transformer.*;
import com.sap.fontus.taintaware.unified.*;
import com.sap.fontus.taintaware.unified.reflect.*;
import com.sap.fontus.utils.LogUtils;
import com.sap.fontus.utils.Logger;
import com.sap.fontus.utils.Utils;
import com.sap.fontus.utils.lookups.CombinedExcludedLookup;
import com.sap.fontus.asm.resolver.IClassResolver;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.*;


@SuppressWarnings("deprecation")
public class MethodTaintingVisitor extends BasicMethodVisitor {
    private static final Logger logger = LogUtils.getLogger();
    private final boolean implementsInvocationHandler;
    private final String owner;

    private final String name;
    private final String methodDescriptor;
    private final IClassResolver resolver;

    private int line;

    private MethodProxies methodProxies;

    /**
     * Some dynamic method invocations can't be handled generically. Add proxy functions here.
     */
    private static final Map<ProxiedDynamicFunctionEntry, Runnable> dynProxies = new HashMap<>();
    private final String ownerSuperClass;
    private final List<LambdaCall> jdkLambdaMethodProxies;
    private final boolean isOwnerInterface;

    private int used;
    private int usedAfterInjection;

    private final InstrumentationHelper instrumentationHelper;

    private final Configuration config;

    private final CombinedExcludedLookup combinedExcludedLookup;
    private final List<DynamicCall> bootstrapMethods;
    private final SignatureInstrumenter signatureInstrumenter;
    private final com.sap.fontus.instrumentation.Method caller;
    private final int passInsideIdx;

    private final ClassLoader loader;

    public MethodTaintingVisitor(int acc, String owner, String name, String methodDescriptor, MethodVisitor methodVisitor, IClassResolver resolver, Configuration config, boolean implementsInvocationHandler, InstrumentationHelper instrumentationHelper, CombinedExcludedLookup combinedExcludedLookup, List<DynamicCall> bootstrapMethods, List<LambdaCall> jdkLambdaMethodProxies, String ownerSuperClass, ClassLoader loader, boolean isOwnerInterface, com.sap.fontus.instrumentation.Method caller) {
        super(Opcodes.ASM9, methodVisitor);
        this.resolver = resolver;
        this.owner = owner;
        this.isOwnerInterface = isOwnerInterface;
        this.config = config;
        this.combinedExcludedLookup = combinedExcludedLookup;
        this.methodProxies = new MethodProxies(this.combinedExcludedLookup);
        this.bootstrapMethods = bootstrapMethods;
        logger.info("Instrumenting method: {}{}", name, methodDescriptor);
        this.used = Type.getArgumentsAndReturnSizes(methodDescriptor) >> 2;
        this.usedAfterInjection = this.used;
        int passIdx = this.config.shouldPropagateTaint(acc, owner, name, methodDescriptor);
        if ((acc & Opcodes.ACC_STATIC) != 0) {
            // Don't have a 'this' pointer
            this.used--;
            passIdx--;
        }
        this.name = name;
        this.methodDescriptor = methodDescriptor;
        this.instrumentationHelper = instrumentationHelper;
        this.signatureInstrumenter = new SignatureInstrumenter(this.api, this.instrumentationHelper);
        this.implementsInvocationHandler = implementsInvocationHandler;
        this.jdkLambdaMethodProxies = jdkLambdaMethodProxies;
        this.loader = loader;
        this.ownerSuperClass = ownerSuperClass;
        this.caller = caller;
        this.passInsideIdx = passIdx;

    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        // TODO: Why do we ignore this?
        //String desc = this.instrumentationHelper.instrumentQN(descriptor);
        //String sig = this.signatureInstrumenter.instrumentSignature(signature);
        //System.out.printf("Local var: %s: %s [%s] -> %s [%s]%n", name, descriptor, signature, desc, sig);
        super.visitLocalVariable(name, descriptor, signature, start, end, index);
    }

    /**
     * See https://stackoverflow.com/questions/47674972/getting-the-number-of-local-variables-in-a-method
     * for keeping track of used locals..
     */
    @Override
    public void visitFrame(
            int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        if (type != Opcodes.F_NEW) {
            throw new IllegalStateException("only expanded frames supported");
        }
        int l = numLocal;
        for (int ix = 0; ix < numLocal; ix++) {
            if (local[ix] == Opcodes.LONG || local[ix] == Opcodes.DOUBLE) {
                l++;
            }
        }
        if (l > this.used) {
            this.used = l;
        }
        super.visitFrame(type, numLocal, local, numStack, stack);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        int newMax = var + Utils.storeOpcodeSize(opcode);
        if (newMax > this.used) {
            this.used = newMax;
        }
        super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        super.visitMaxs(maxStack, Math.max(this.used, this.usedAfterInjection));
    }

    public void visitMethodInsn(FunctionCall fc) {
        logger.info("Invoking [{}] {}.{}{}", Utils.opcodeToString(fc.getOpcode()), fc.getOwner(), fc.getName(), fc.getDescriptor());
        super.visitMethodInsn(fc.getOpcode(), fc.getOwner(), fc.getName(), fc.getDescriptor(), fc.isInterface());
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == Opcodes.ARETURN && this.isInvocationHandlerMethod(this.name, this.methodDescriptor)) {
            // Handling, that method proxies return the correct type (we're in a InvocationHandler.invoke implementation)
            super.visitVarInsn(Opcodes.ALOAD, 1); // Load proxy param
            super.visitVarInsn(Opcodes.ALOAD, 2); // Load method param
            super.visitVarInsn(Opcodes.ALOAD, 3); // Load args param
            String resultConverterDescriptor = String.format("(L%s;L%s;L%s;[L%s;)L%s;", Utils.dotToSlash(Object.class.getName()), Utils.dotToSlash(Object.class.getName()), Utils.dotToSlash(Method.class.getName()), Utils.dotToSlash(Object.class.getName()), Utils.dotToSlash(Object.class.getName()));
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASReflectionProxy.class), "handleInvocationProxyCall", resultConverterDescriptor, false);
        }
        if(opcode == Opcodes.ARETURN && this.passInsideIdx >= 0) {
            // Assumption is stack looks like this: IASTaintInformationable, IASString
            super.visitInsn(Opcodes.DUP_X1);
            // Stack: IASString, IASTaintInformationable, IASString
            super.visitInsn(Opcodes.SWAP);
            // Stack: IASString, IASString, IASTaintInformationable
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(IASString.class), "setTaintLengthAdjusted", String.format("(L%s;)V", Type.getInternalName(IASTaintInformationable.class)), false);
            // Stack: IASString
        }
        super.visitInsn(opcode);
    }

    private boolean isInvocationHandlerMethod(String name, String descriptor) {
        boolean nameEquals = "invoke".equals(name);
        String expectedDescriptor = String.format("(L%s;L%s;[L%s;)L%s;", Utils.dotToSlash(Object.class.getName()), Utils.dotToSlash(Method.class.getName()), Utils.dotToSlash(Object.class.getName()), Utils.dotToSlash(Object.class.getName()));
        boolean descriptorEquals = descriptor.equals(expectedDescriptor);
        return nameEquals && descriptorEquals && this.implementsInvocationHandler;
    }

    /**
     * Replace access to fields of type IASString/IASStringBuilder
     */
    @Override
    public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {

        if (this.combinedExcludedLookup.isJdkClass(owner) && this.instrumentationHelper.canHandleType(descriptor)) {
            if ((opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC)) {
                this.mv.visitMethodInsn(Opcodes.INVOKESTATIC, Constants.ConversionUtilsQN, Constants.ConversionUtilsToOrigName, Constants.ConversionUtilsToOrigDesc, false);
                this.mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(descriptor).getInternalName());
                this.mv.visitFieldInsn(opcode, owner, name, descriptor);
            } else {
                this.mv.visitFieldInsn(opcode, owner, name, descriptor);
                this.mv.visitMethodInsn(Opcodes.INVOKESTATIC, Constants.ConversionUtilsQN, Constants.ConversionUtilsToConcreteName, Constants.ConversionUtilsToConcreteDesc, false);
                Type fieldType = Type.getType(descriptor);
                String instrumentedFieldDescriptor = this.instrumentationHelper.instrumentQN(fieldType.getInternalName());
                this.mv.visitTypeInsn(Opcodes.CHECKCAST, instrumentedFieldDescriptor);
            }
            return;
        }

        if (this.instrumentationHelper.instrumentFieldIns(this.mv, opcode, owner, name, descriptor)) {
            return;
        }
    }

    /**
     * All method calls are handled here.
     */
    @Override
    public void visitMethodInsn(
            final int opcode,
            final String owner,
            final String name,
            final String descriptor,
            final boolean isInterface) {
        FunctionCall fc = new FunctionCall(opcode, owner, name, descriptor, isInterface);

        if (this.combinedExcludedLookup.isFontusClass(owner)) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            return;
        }

        // If a method has a defined proxy, apply it right away
        fc = this.methodProxies.shouldBeProxied(fc);

        FunctionCall functionCall = this.instrumentationHelper.rewriteOwnerMethod(fc);
        if (functionCall != null) {
            this.rewriteParametersAndReturnTypeForInstrumentedCall(functionCall, fc);
            return;
        }

        if ("toString".equals(this.name) && this.methodDescriptor.equals(Type.getMethodDescriptor(Type.getType(IASString.class)))
                && opcode == Opcodes.INVOKESPECIAL && "toString".equals(name) && descriptor.equals(Type.getMethodDescriptor(Type.getType(String.class)))
                && this.combinedExcludedLookup.isPackageExcludedOrJdk(owner)
                && !this.combinedExcludedLookup.isPackageExcludedOrJdk(this.ownerSuperClass)) {
            Descriptor instrumented = new Descriptor(Type.getType(IASString.class));
            super.visitMethodInsn(Opcodes.INVOKESPECIAL, this.ownerSuperClass, name, instrumented.toDescriptor(), false);
            return;
        }

        if (fc.isVirtualOrStaticMethodHandleLookup()) {
            this.generateVirtualOrStaticMethodHandleLookupIntercept(fc);
            return;
        }

        if (fc.isConstructorMethodHandleLookup()) {
            this.generateConstructorMethodHandleLookupIntercept(fc);
            return;
        }

        if (fc.isSpecialMethodHandleLookup()) {
            this.generateSpecialMethodHandleLookupIntercept(fc);
            return;
        }

        // Call any functions which manipulate function call parameters and return types
        // for example sources, sinks and JDK functions
        if (!fc.isRelevantMethodHandleInvocation() && this.rewriteParametersAndReturnType(fc)) {
            return;
        }
        boolean passThrough = this.config.shouldPassThroughTaint(fc);
        if(passThrough) {
            // stack: object, TString -> Object, TString, TString
            super.visitInsn(Opcodes.DUP);
            // stack: Object, TString, TString -> Object, TString, IASTaintInformationable
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(IASString.class), "getTaintInformation", String.format("()L%s;", Type.getInternalName(IASTaintInformationable.class)), false);
            // stack: Object, TString, IASTaintInformationable -> IASTaintInformationable, Object, TString, IASTaintInformationable
            super.visitInsn(Opcodes.DUP_X2);
            // stack: IASTaintInformationable, Object, TString, IASTaintInformationable -> IASTaintInformationable, Object, TString
            super.visitInsn(Opcodes.POP);
        }
        String desc = this.instrumentationHelper.instrumentForNormalCall(fc.getDescriptor());
        // TODO: hack that we can't reset desc for fc here
        if (desc.equals(fc.getDescriptor())) {
            logger.info("Skipping invoke [{}] {}.{}{}", Utils.opcodeToString(fc.getOpcode()), fc.getOwner(), fc.getName(), fc.getDescriptor());
        } else {
            logger.info("Rewriting invoke containing String-like type [{}] {}.{}{} to {}.{}{}", Utils.opcodeToString(fc.getOpcode()), fc.getOwner(), fc.getName(), fc.getDescriptor(), fc.getOwner(), fc.getName(), desc);
        }
        super.visitMethodInsn(fc.getOpcode(), fc.getOwner(), fc.getName(), desc, fc.isInterface());
        if(passThrough) {
            // Stack: IASTaintInformationable, TString -> TString, IASTaintInformationable, TString
            super.visitInsn(Opcodes.DUP_X1);
            // Stack: TString, IASTaintInformationable, TString -> TString, TString, IASTaintInformationable, TString
            super.visitInsn(Opcodes.DUP_X1);
            // Stack: TString, TString, IASTaintInformationable, TString -> TString, TString, IASTaintInformationable
            super.visitInsn(Opcodes.POP);
            // Stack: TString, TString, IASTaintInformationable -> TString
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(IASString.class), "setTaint", String.format("(L%s;)V", Type.getInternalName(IASTaintInformationable.class)), false);
        }
    }

    private void storeArgumentsToLocals(FunctionCall call) {
        Stack<String> params = call.getParsedDescriptor().getParameterStack();

        int i = Utils.getArgumentsStackSize(call.getDescriptor());
        while (!params.isEmpty()) {
            String param = params.pop();

            i -= Type.getType(param).getSize();

            int storeOpcode = Type.getType(param).getOpcode(Opcodes.ISTORE);

            super.visitVarInsn(storeOpcode, this.used + i);
        }
        this.usedAfterInjection = Math.max(this.used + Utils.getArgumentsStackSize(call.getDescriptor()), this.usedAfterInjection);
    }

    private void generateVirtualOrStaticMethodHandleLookupIntercept(FunctionCall fc) {
        Label label1 = new Label();
        Label label3 = new Label();

        // Copying the class reference to the top of the Stack
        this.storeArgumentsToLocals(fc);

        int classLocal = this.used;
        int nameLocal = classLocal + 1;
        int methodTypeLocal = nameLocal + 1;
        int isJdkLocal = this.used + Utils.getArgumentsStackSize(fc.getDescriptor());
        int methodHandleLocal = isJdkLocal + 1;

        super.visitVarInsn(Opcodes.ALOAD, classLocal);
        super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASLookupUtils.class), "isJdkOrExcluded", new Descriptor(new String[]{Type.getDescriptor(Class.class)}, Type.getDescriptor(boolean.class)).toDescriptor(), false);
        super.visitVarInsn(Opcodes.ISTORE, isJdkLocal);

        super.visitVarInsn(Opcodes.ILOAD, isJdkLocal);
        super.visitJumpInsn(Opcodes.IFEQ, label1);
        {
            super.visitVarInsn(Opcodes.ALOAD, methodTypeLocal);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASLookupUtils.class), "uninstrumentForJdk", Type.getMethodDescriptor(Type.getType(MethodType.class), Type.getType(MethodType.class)), false);
            super.visitVarInsn(Opcodes.ASTORE, methodTypeLocal);
        }
        super.visitLabel(label1);

        super.visitVarInsn(Opcodes.ALOAD, classLocal);
        super.visitVarInsn(Opcodes.ALOAD, nameLocal);
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(IASString.class), "getString", Type.getMethodDescriptor(Type.getType(String.class)), false);
        super.visitVarInsn(Opcodes.ALOAD, methodTypeLocal);

        this.visitMethodInsn(fc);

        super.visitVarInsn(Opcodes.ASTORE, methodHandleLocal);

        super.visitVarInsn(Opcodes.ILOAD, isJdkLocal);
        super.visitJumpInsn(Opcodes.IFEQ, label3);
        {
            super.visitVarInsn(Opcodes.ALOAD, methodHandleLocal);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASLookupUtils.class), "convertForJdk", Type.getMethodDescriptor(Type.getType(MethodHandle.class), Type.getType(MethodHandle.class)), false);
            super.visitVarInsn(Opcodes.ASTORE, methodHandleLocal);
        }
        super.visitLabel(label3);

        super.visitVarInsn(Opcodes.ALOAD, methodHandleLocal);
        this.usedAfterInjection = Math.max(this.used + Utils.getArgumentsStackSize(fc.getDescriptor()) + 2, this.usedAfterInjection);
    }

    private void generateConstructorMethodHandleLookupIntercept(FunctionCall fc) {
        Label label = new Label();

        // Copying the class reference to the top of the Stack
        this.storeArgumentsToLocals(fc);

        int classLocal = this.used;
        int methodTypeLocal = classLocal + 1;
        int isJdkLocal = this.used + Utils.getArgumentsStackSize(fc.getDescriptor());

        super.visitVarInsn(Opcodes.ALOAD, classLocal);
        super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASLookupUtils.class), "isJdkOrExcluded", new Descriptor(new String[]{Type.getDescriptor(Class.class)}, Type.getDescriptor(boolean.class)).toDescriptor(), false);
        super.visitVarInsn(Opcodes.ISTORE, isJdkLocal);

        super.visitVarInsn(Opcodes.ILOAD, isJdkLocal);
        super.visitJumpInsn(Opcodes.IFEQ, label);
        {
            super.visitVarInsn(Opcodes.ALOAD, methodTypeLocal);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASLookupUtils.class), "uninstrumentForJdk", Type.getMethodDescriptor(Type.getType(MethodType.class), Type.getType(MethodType.class)), false);
            super.visitVarInsn(Opcodes.ASTORE, methodTypeLocal);
        }
        super.visitLabel(label);

        super.visitVarInsn(Opcodes.ALOAD, classLocal);
        super.visitVarInsn(Opcodes.ALOAD, methodTypeLocal);

        this.visitMethodInsn(fc);

        this.usedAfterInjection = Math.max(this.used + Utils.getArgumentsStackSize(fc.getDescriptor()) + 1, this.usedAfterInjection);
    }

    private void generateSpecialMethodHandleLookupIntercept(FunctionCall fc) {
        Label label1 = new Label();
        Label label3 = new Label();

        // Copying the class reference to the top of the Stack
        this.storeArgumentsToLocals(fc);

        int classLocal = this.used;
        int nameLocal = classLocal + 1;
        int methodTypeLocal = nameLocal + 1;
        int callerLocal = methodTypeLocal + 1;
        int isJdkLocal = this.used + Utils.getArgumentsStackSize(fc.getDescriptor());
        int methodHandleLocal = isJdkLocal + 1;

        super.visitVarInsn(Opcodes.ALOAD, classLocal);
        super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASLookupUtils.class), "isJdkOrExcluded", new Descriptor(new String[]{Type.getDescriptor(Class.class)}, Type.getDescriptor(boolean.class)).toDescriptor(), false);
        super.visitVarInsn(Opcodes.ISTORE, isJdkLocal);

        super.visitVarInsn(Opcodes.ILOAD, isJdkLocal);
        super.visitJumpInsn(Opcodes.IFEQ, label1);
        {
            super.visitVarInsn(Opcodes.ALOAD, methodTypeLocal);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASLookupUtils.class), "uninstrumentForJdk", Type.getMethodDescriptor(Type.getType(MethodType.class), Type.getType(MethodType.class)), false);
            super.visitVarInsn(Opcodes.ASTORE, methodTypeLocal);
        }
        super.visitLabel(label1);

        super.visitVarInsn(Opcodes.ALOAD, classLocal);
        super.visitVarInsn(Opcodes.ALOAD, nameLocal);
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(IASString.class), "getString", Type.getMethodDescriptor(Type.getType(String.class)), false);
        super.visitVarInsn(Opcodes.ALOAD, methodTypeLocal);
        super.visitVarInsn(Opcodes.ALOAD, callerLocal);

        this.visitMethodInsn(fc);

        super.visitVarInsn(Opcodes.ASTORE, methodHandleLocal);

        super.visitVarInsn(Opcodes.ILOAD, isJdkLocal);
        super.visitJumpInsn(Opcodes.IFEQ, label3);
        {
            super.visitVarInsn(Opcodes.ALOAD, methodHandleLocal);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASLookupUtils.class), "convertForJdk", Type.getMethodDescriptor(Type.getType(MethodHandle.class), Type.getType(MethodHandle.class)), false);
            super.visitVarInsn(Opcodes.ASTORE, methodHandleLocal);
        }
        super.visitLabel(label3);

        super.visitVarInsn(Opcodes.ALOAD, methodHandleLocal);
        this.usedAfterInjection = Math.max(this.used + Utils.getArgumentsStackSize(fc.getDescriptor()) + 2, this.usedAfterInjection);
    }




    private boolean rewriteParametersAndReturnTypeForInstrumentedCall(FunctionCall call, FunctionCall uninstrumentedCall) {

        MethodParameterTransformer transformer = new MethodParameterTransformer(this, call);

        // Add Sink transformations
        Sink sink = this.config.getSinkConfig().getSinkForFunction(uninstrumentedCall, new Position(this.owner, this.name, this.line));
        if (sink != null) {
	    System.out.printf("Adding IASString sink: %s%s:%s%n", uninstrumentedCall.getOwner(), uninstrumentedCall.getName(), uninstrumentedCall.getDescriptor());
            logger.info("Adding sink checks for [{}] {}.{}{}", Utils.opcodeToString(uninstrumentedCall.getOpcode()), uninstrumentedCall.getOwner(), uninstrumentedCall.getName(), uninstrumentedCall.getDescriptor());
            SinkTransformer t = new SinkTransformer(sink, this.instrumentationHelper, this.used, this.caller.toFunctionCall());
            transformer.addParameterTransformation(t);
            transformer.addReturnTransformation(t);
        }

        // No transformations required
        if (!transformer.needsTransformation()) {
	    this.visitMethodInsn(call);
            return false;
        }

        // Do the transformations
        transformer.modifyStackParameters(this.used);
        this.usedAfterInjection = Math.max(this.used + transformer.getExtraStackSlots(), this.usedAfterInjection);

        this.visitMethodInsn(call);

        // Modify Return parameters
        transformer.modifyReturnType();

        logger.info("Finished transforming parameters for [{}] {}.{}{}", Utils.opcodeToString(uninstrumentedCall.getOpcode()), uninstrumentedCall.getOwner(), uninstrumentedCall.getName(), uninstrumentedCall.getDescriptor());
        return true;
    }

    private boolean rewriteParametersAndReturnType(FunctionCall call) {
        boolean isExcluded = this.combinedExcludedLookup.isJdkOrAnnotation(call.getOwner()) || this.combinedExcludedLookup.isExcluded(call.getOwner());

        if (isExcluded) {
            String desc = this.instrumentationHelper.uninstrumentForJdkCall(call.getDescriptor());
            call = new FunctionCall(call.getOpcode(), call.getOwner(), call.getName(), desc, call.isInterface());
        }

        MethodParameterTransformer transformer = new MethodParameterTransformer(this, call);

        // Add always apply transformer
        FunctionCall converter = this.config.getConverterForReturnValue(call, true);
        if (converter != null) {
            transformer.addReturnTransformation(new AlwaysApplyReturnGenericTransformer(converter));
        }

        if (this.config.needsParameterConversion(call)) {
            // Always apply transformation for the parameters as well (even JDK classes)
            transformer.addParameterTransformation(new RegularParameterTransformer(call, this.instrumentationHelper, this.config));
        }

        // Add JDK transformations
        if (isExcluded) {
            logger.info("Transforming JDK method call for [{}] {}.{}{}", Utils.opcodeToString(call.getOpcode()), call.getOwner(), call.getName(), call.getDescriptor());
            JdkMethodTransformer t = new JdkMethodTransformer(call, this.instrumentationHelper, this.config);
            transformer.addParameterTransformation(t);
            transformer.addReturnTransformation(t);
        }

        // Add Source transformations
        Source source = this.config.getSourceConfig().getSourceForFunction(call);
        if ((source != null) && (source.isAllowedCaller(this.caller.toFunctionCall()))) {
            logger.info("Adding source tainting for [{}] {}.{}{} for caller {}.{}", Utils.opcodeToString(call.getOpcode()), call.getOwner(), call.getName(), call.getDescriptor(), this.caller.getOwner(), this.caller.getName());
            SourceTransformer t = new SourceTransformer(source, this.used, this.caller.toFunctionCall());
            transformer.addReturnTransformation(t);
        }

        // Add Sink transformations
        Sink sink = this.config.getSinkConfig().getSinkForFunction(call, new Position(this.owner, this.name, this.line));
        if (sink != null) {
            logger.info("Adding sink checks for [{}] {}.{}{}", Utils.opcodeToString(call.getOpcode()), call.getOwner(), call.getName(), call.getDescriptor());
            SinkTransformer t = new SinkTransformer(sink, this.instrumentationHelper, this.used, this.caller.toFunctionCall());
            transformer.addParameterTransformation(t);
            transformer.addReturnTransformation(t);
        }

        // No transformations required
        if (!transformer.needsTransformation()) {
            return false;
        }

        // Do the transformations
        transformer.modifyStackParameters(this.used);
        this.usedAfterInjection = Math.max(this.used + transformer.getExtraStackSlots(), this.usedAfterInjection);

        // Instrument descriptor if source/sink is not a JDK class
        if (!isExcluded) {
            String desc = this.instrumentationHelper.instrumentForNormalCall(call.getDescriptor());
            call = new FunctionCall(call.getOpcode(), call.getOwner(), call.getName(), desc, call.isInterface());
        }
        this.visitMethodInsn(call);

        // Modify Return parameters
        transformer.modifyReturnType();

        logger.info("Finished transforming parameters for [{}] {}.{}{}", Utils.opcodeToString(call.getOpcode()), call.getOwner(), call.getName(), call.getDescriptor());
        return true;
    }

    /**
     * The 'ldc' instruction loads a constant value out of the constant pool.
     * <p>
     * It might load String values, so we have to transform them.
     */
    @Override
    public void visitLdcInsn(Object value) {

        if (this.instrumentationHelper.handleLdc(this.mv, value)) {
            return;
        }

        if (value instanceof Type) {
            Type type = (Type) value;
            int sort = type.getSort();
            if (sort == Type.OBJECT) {
                if (this.instrumentationHelper.handleLdcType(this.mv, type)) {
                    return;
                }
                //TODO: handle Arrays etc..
            } else if (sort == Type.ARRAY) {
                if (this.instrumentationHelper.handleLdcArray(this.mv, type)) {
                    return;
                }
            }
        }
        super.visitLdcInsn(value);
    }


    /**
     * We want to override some instantiations of classes with our own types
     */
    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        // TODO All instrumented classes not only strings
        if (/*this.shouldRewriteCheckCast &&*/ opcode == Opcodes.CHECKCAST && Constants.StringQN.equals(type)) {
            logger.info("Rewriting checkcast to call to TString.fromObject(Object obj)");
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASStringUtils.class), "fromObject", String.format("(%s)%s", Constants.ObjectDesc, Type.getDescriptor(IASString.class)), false);
            super.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(IASString.class));
            return;
        }
        logger.info("Visiting type [{}] instruction: {}", type, opcode);
        String newType = this.instrumentationHelper.rewriteTypeIns(type);
        super.visitTypeInsn(opcode, newType);
    }

    @Override
    public void visitInvokeDynamicInsn(
            final String name,
            final String descriptor,
            final Handle bootstrapMethodHandle,
            final Object... bootstrapMethodArguments) {

        if (MethodTaintingVisitor.shouldBeDynProxied(name, descriptor)) {
            return;
        }

        if ("java/lang/invoke/LambdaMetafactory".equals(bootstrapMethodHandle.getOwner()) &&
                ("metafactory".equals(bootstrapMethodHandle.getName()) || "altMetafactory".equals(bootstrapMethodHandle.getName()))) {
            Handle realFunction = (Handle) bootstrapMethodArguments[1];
            Type desc = Type.getType(descriptor);

            LambdaCall call = new LambdaCall(Type.getMethodType(descriptor).getReturnType(), realFunction, desc);
            if (call.isInstanceCall()) {
                call.setConcreteImplementationType(desc.getArgumentTypes().length == 1 ? desc.getArgumentTypes()[0] : null);
            }

            MethodTaintingUtils.invokeVisitLambdaCall(this.getParentVisitor(), this.instrumentationHelper, call.getProxyDescriptor(this.loader, this.instrumentationHelper), call, this.owner, name, descriptor, this.isOwnerInterface, bootstrapMethodHandle, bootstrapMethodArguments);

            if (MethodTaintingUtils.needsLambdaProxy(descriptor, realFunction, (Type) bootstrapMethodArguments[2], this.instrumentationHelper)) {
                this.jdkLambdaMethodProxies.add(call);
            }
        } else if ("makeConcatWithConstants".equals(name)) {
            this.rewriteConcatWithConstants(name, descriptor, bootstrapMethodArguments);
        } else {
            String desc = this.instrumentationHelper.instrumentForNormalCall(descriptor);

            String instrumentedBootstrapDesc = this.instrumentationHelper.instrumentForNormalCall(bootstrapMethodHandle.getDesc());

            Handle instrumentedOriginalHandle = new Handle(bootstrapMethodHandle.getTag(), bootstrapMethodHandle.getOwner(), bootstrapMethodHandle.getName(), instrumentedBootstrapDesc, bootstrapMethodHandle.isInterface());

            Handle proxyHandle = new Handle(bootstrapMethodHandle.getTag(), this.owner, "$fontus$" + bootstrapMethodHandle.getName() + bootstrapMethodHandle.hashCode(), bootstrapMethodHandle.getDesc(), bootstrapMethodHandle.isInterface());

            DynamicCall dynamicCall = new DynamicCall(instrumentedOriginalHandle, proxyHandle, bootstrapMethodArguments);

            this.bootstrapMethods.add(dynamicCall);

            logger.info("invokeDynamic {}{}", name, descriptor);
            super.visitInvokeDynamicInsn(name, desc, proxyHandle, bootstrapMethodArguments);
        }
    }

    private void rewriteConcatWithConstants(String name, String descriptor, Object[] bootstrapMethodArguments) {
        logger.info("Trying to rewrite invokeDynamic {}{} towards Concat!", name, descriptor);

        Descriptor desc = Descriptor.parseDescriptor(descriptor);
        assert bootstrapMethodArguments.length == 1;
        Object fmtStringObj = bootstrapMethodArguments[0];
        assert fmtStringObj instanceof String;
        String formatString = (String) fmtStringObj;
        int parameterCount = desc.parameterCount();
        MethodTaintingUtils.pushNumberOnTheStack(this.getParentVisitor(), parameterCount);
        super.visitTypeInsn(Opcodes.ANEWARRAY, Constants.ObjectQN);
        int currRegister = this.used;
        super.visitVarInsn(Opcodes.ASTORE, currRegister);
        // newly created array is now stored in currRegister, concat operands on top
        Stack<String> parameters = desc.getParameterStack();
        int paramIndex = 0;
        while (!parameters.empty()) {
            String parameter = parameters.pop();
            // Convert topmost value (if required)
            MethodTaintingUtils.invokeConversionFunction(this.getParentVisitor(), parameter);
            // put array back on top
            super.visitVarInsn(Opcodes.ALOAD, currRegister);
            // swap array and object to array
            super.visitInsn(Opcodes.SWAP);
            // push the index where the value shall be stored
            MethodTaintingUtils.pushNumberOnTheStack(this.getParentVisitor(), paramIndex);
            // swap, this puts them into the order arrayref, index, value
            super.visitInsn(Opcodes.SWAP);
            // store the value into arrayref at index, next parameter is on top now (if there are any more)
            super.visitInsn(Opcodes.AASTORE);
            paramIndex++;
        }

        // Load the format String constant
        super.visitLdcInsn(formatString);
        // Load the param array
        super.visitVarInsn(Opcodes.ALOAD, currRegister);
        // Call our concat method
        super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(IASStringUtils.class), "concat", Constants.CONCAT_DESC, false);
    }

    /**
     * Is there a dynamic proxy defined? If so apply and return true.
     */
    private static boolean shouldBeDynProxied(String name, String descriptor) {
        ProxiedDynamicFunctionEntry pdfe = new ProxiedDynamicFunctionEntry(name, descriptor);
        if (dynProxies.containsKey(pdfe)) {
            logger.info("Proxying dynamic call to {}{}", name, descriptor);
            Runnable pf = dynProxies.get(pdfe);
            pf.run();
            return true;
        }
        return false;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        if(this.passInsideIdx >= 0) {
            this.visitVarInsn(Opcodes.ALOAD, this.passInsideIdx);
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(IASString.class), "getTaintInformationCopied", String.format("()L%s;", Type.getInternalName(IASTaintInformationable.class)), false);
        }
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
        super.visitMethodInsn(opcode, owner, name, descriptor);
    }


    @Override
    public void visitJumpInsn(int opcode, Label label) {
        if (opcode == Opcodes.IF_ACMPEQ) {
            // Returns 1
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Constants.CompareProxyQN, Constants.CompareProxyEqualsName, Constants.CompareProxyEqualsDesc, false);
            // Expects something different from 0
            super.visitJumpInsn(Opcodes.IFNE, label);
        } else if (opcode == Opcodes.IF_ACMPNE) {
            // Returns 0
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Constants.CompareProxyQN, Constants.CompareProxyEqualsName, Constants.CompareProxyEqualsDesc, false);
            // Expects 0
            super.visitJumpInsn(Opcodes.IFEQ, label);
        } else {
            super.visitJumpInsn(opcode, label);
        }
    }


    @Override
    public void visitLabel(Label label) {
        super.visitLabel(label);
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        super.visitIincInsn(var, increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        String instrumented = this.instrumentationHelper.instrumentQN(descriptor);
        super.visitMultiANewArrayInsn(instrumented, numDimensions);
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return super.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        super.visitTryCatchBlock(start, end, handler, type);
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
        return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        super.visitLineNumber(line, start);
        this.line = line;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }
}
