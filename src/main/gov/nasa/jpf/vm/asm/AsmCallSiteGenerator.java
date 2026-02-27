package gov.nasa.jpf.vm.asm;

import gov.nasa.jpf.vm.CallSiteDescriptor;
import gov.nasa.jpf.vm.CallSiteGenerator;
import gov.nasa.jpf.vm.GeneratedClassInfo;
import gov.nasa.jpf.vm.GenerationException;
import gov.nasa.jpf.vm.CallSiteUtil;
import gov.nasa.jpf.vm.BootstrapBlueprint;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Minimal ASM-based CallSite generator skeleton.
 */
public class AsmCallSiteGenerator implements CallSiteGenerator {

    @Override
    public GeneratedClassInfo generateAdapter(CallSiteDescriptor desc) throws GenerationException {
        try {
            // Special-case: generate a bootstrap helper that operates on component arrays
            String owner = desc.getOwnerClassName();
            String invoked = desc.getInvokedName();

            String base = desc.getOwnerClassName() + "#" + desc.getInvokedName();
            String fp = CallSiteUtil.shortFingerprint(base, 12);
            String className = "gov/nasa/jpf/gen/CS$" + fp;
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, className, null, "java/lang/Object", null);

            // default constructor
            org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();

            boolean emitBootstrapHelper = true;

            if (emitBootstrapHelper) {
                // public static boolean equals(java.lang.Object[] a, java.lang.Object[] b) { return java.util.Arrays.equals(a,b); }
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "equals", "([Ljava/lang/Object;[Ljava/lang/Object;)Z", null, null);
                mv.visitCode();
                // call java.util.Arrays.equals(Object[],Object[])
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "equals", "([Ljava/lang/Object;[Ljava/lang/Object;)Z", false);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();

                // public static int hashCode(java.lang.Object[] a) { return java.util.Arrays.hashCode(a); }
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "hashCode", "([Ljava/lang/Object;)I", null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "hashCode", "([Ljava/lang/Object;)I", false);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();

                // public static java.lang.String toString(java.lang.Object[] a) { return java.util.Arrays.toString(a); }
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "toString", "([Ljava/lang/Object;)Ljava/lang/String;", null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "toString", "([Ljava/lang/Object;)Ljava/lang/String;", false);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();

            } else {
                // Fallback: emit a trivial static invoke() as before
                mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "invoke", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            cw.visitEnd();
            byte[] bytes = cw.toByteArray();
            return new GeneratedClassInfo(className.replace('/', '.'), bytes);
        } catch (Throwable t) {
            throw new GenerationException("ASM generation failed", t);
        }
    }

    // Overload that accepts a blueprint; currently delegates to the primary generator.
    public GeneratedClassInfo generateAdapter(CallSiteDescriptor desc, BootstrapBlueprint blueprint) throws GenerationException {
        return generateAdapter(desc);
    }
}
