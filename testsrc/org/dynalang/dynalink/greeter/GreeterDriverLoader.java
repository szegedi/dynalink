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
import java.dyn.MethodHandles;
import java.dyn.MethodType;

import org.dynalang.dynalink.DynamicLinker;
import org.dynalang.dynalink.DynamicLinkerFactory;
import org.dynalang.dynalink.GuardedInvocation;
import org.dynalang.dynalink.MonomorphicCallSite;
import org.dynalang.dynalink.RelinkableCallSite;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MHandle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class GreeterDriverLoader extends ClassLoader implements Opcodes {

    private static final DynamicLinker linker = new DynamicLinkerFactory().createLinker();
    
    static int linkCount = 0;
    
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
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
    
    public byte[] dump () {
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, "org/dynalang/dynalink/greeter/GreeterDriverImpl", null, "java/lang/Object", new String[] { "org/dynalang/dynalink/greeter/GreeterDriver" });

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
        mv.visitIndyMethodInsn("dyn:getProp:helloText", "(Ljava/lang/Object;)Ljava/lang/String;", 
            new MHandle(MHandle.REF_invokeStatic,
                getClass().getName().replace('.', '/'),
                "bootstrap",
                MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class).toMethodDescriptorString()),
                new Object[0]);
        mv.visitInsn(ARETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "Lorg/dynalang/dynalink/greeter/GreeterDriverImpl;", null, l0, l1, 0);
        mv.visitLocalVariable("greeter", "Ljava/lang/Object;", null, l0, l1, 1);
        mv.visitMaxs(1, 2);
        mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }
}
