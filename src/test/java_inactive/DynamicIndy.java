import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.MH_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_7;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Modified from the code published by Remi Forax, as attachment to this blog
 * post:
 * <http://weblogs.java.net/blog/forax/archive/2011/01/07/call-invokedynamic
 * -java>
 *
 * @author Attila Szegedi
 */
public class DynamicIndy extends ClassLoader {
    private int ID_GENERATOR = 0;

    public MethodHandle invokeDynamic(String name, MethodType desc, Class<?> bsmClass, String bsmName, MethodType bsmType, Object... bsmArgs) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(V1_7, ACC_PUBLIC | ACC_SUPER, "Gen"+(ID_GENERATOR++), null, "java/lang/Object", null);

    MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    init.visitCode();
    init.visitVarInsn(ALOAD, 0);
    init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
    init.visitInsn(RETURN);
    init.visitMaxs(-1, -1);
    init.visitEnd();

    String descriptor = desc.toMethodDescriptorString();

    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC|ACC_STATIC, "invokedynamic", descriptor, null, null);
    int slot = 0;
    for(Type parameterType: Type.getArgumentTypes(descriptor)) {
      mv.visitVarInsn(parameterType.getOpcode(ILOAD), slot);
      slot += parameterType.getSize();
    }

    mv.visitInvokeDynamicInsn(name, descriptor,
        new org.objectweb.asm.MethodHandle(MH_INVOKESTATIC,
            bsmClass.getName().replace('.', '/'),
            bsmName,
            bsmType.toMethodDescriptorString()),
            bsmArgs);

    Type returnType = Type.getReturnType(descriptor);
    mv.visitInsn(returnType.getOpcode(IRETURN));

    mv.visitMaxs(-1, -1);
    mv.visitEnd();

    cw.visitEnd();

    byte[] array = cw.toByteArray();
    Class<?> clazz = defineClass(null, array, 0, array.length);

    try {
      return MethodHandles.lookup().findStatic(clazz, "invokedynamic", desc);
    }catch (IllegalAccessException|NoSuchMethodException e) {
      throw (AssertionError)new AssertionError().initCause(e);
    }
  }
}
