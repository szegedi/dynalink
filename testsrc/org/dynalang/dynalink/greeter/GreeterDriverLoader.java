/*
   Copyright 2009 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.dynalang.dynalink.greeter;

import java.dyn.CallSite;
import java.dyn.MethodType;

import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.RelinkableCallSite;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class GreeterDriverLoader extends ClassLoader implements Opcodes {

    private static final DynamicLinker linker = new DynamicLinkerFactory().createLinker();
    
    static int linkCount = 0;
    
    public static CallSite bootstrap(Class<?> caller, String name, MethodType type) {
        final RelinkableCallSite callSite = new MonomorphicCallSite(name, type) {
            @Override
            public void setGuardedInvocation(GuardedInvocation guardedInvocation) {
                super.setGuardedInvocation(guardedInvocation);
                ++linkCount;
            }
        };
        linker.link(callSite);
        return callSite;
    }
    
    GreeterDriverLoader(ClassLoader parent) {
        super(parent);
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException
    {
        if("org.dynalang.dynalink.greeter.GreeterDriverImpl".equals(name)) {
            final byte[] bytecode = dump();
            return defineClass(name, bytecode, 0, bytecode.length);
        }
        return super.findClass(name);
    }
    
    public static byte[] dump () {
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, "org/dynalang/dynalink/greeter/GreeterDriverImpl", null, "java/lang/Object", new String[] { "org/dynalang/dynalink/greeter/GreeterDriver" });

        cw.visitSource("GreeterDriverImpl.java", null);

        {
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(13, l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        mv.visitInsn(RETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "Lorg/dynalang/dynalink/greeter/GreeterDriverImpl;", null, l0, l1, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        }
        {
        mv = cw.visitMethod(ACC_PUBLIC, "invokeGetHelloText", "(Ljava/lang/Object;)Ljava/lang/String;", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(20, l0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEDYNAMIC, "java/dyn/InvokeDynamic", "dyn:getProp:helloText", "(Ljava/lang/Object;)Ljava/lang/String;");
        mv.visitInsn(ARETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "Lorg/dynalang/dynalink/greeter/GreeterDriverImpl;", null, l0, l1, 0);
        mv.visitLocalVariable("greeter", "Ljava/lang/Object;", null, l0, l1, 1);
        mv.visitMaxs(1, 2);
        mv.visitEnd();
        }
        {
        mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(16, l0);
        mv.visitLdcInsn(Type.getType("Lorg/dynalang/dynalink/greeter/GreeterDriverLoader;"));
        mv.visitLdcInsn("bootstrap");
        mv.visitMethodInsn(INVOKESTATIC, "java/dyn/Linkage", "registerBootstrapMethod", "(Ljava/lang/Class;Ljava/lang/String;)V");
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLineNumber(17, l1);
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }
}
