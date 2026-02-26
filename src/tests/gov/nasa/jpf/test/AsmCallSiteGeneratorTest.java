package gov.nasa.jpf.test;

import gov.nasa.jpf.vm.CallSiteDescriptor;
import gov.nasa.jpf.vm.GeneratedClassInfo;
import gov.nasa.jpf.vm.asm.AsmCallSiteGenerator;
import org.junit.Test;
import org.objectweb.asm.ClassReader;

import static org.junit.Assert.*;

public class AsmCallSiteGeneratorTest {

    @Test
    public void generateSimpleAdapter() throws Exception {
        AsmCallSiteGenerator gen = new AsmCallSiteGenerator();
        // simulate an ObjectMethods bootstrap request for a record with two components
        String[] componentSigs = new String[] {"Ljava/lang/String;", "I"};
        CallSiteDescriptor desc = new CallSiteDescriptor("java.lang.invoke.ObjectMethods", "objectMethods", "", null, componentSigs);
        GeneratedClassInfo info = gen.generateAdapter(desc);
        assertNotNull(info);
        assertTrue(info.hasBytes());
        byte[] bytes = info.getClassBytes();
        assertNotNull(bytes);

        // Verify that ASM can read the generated class
        ClassReader cr = new ClassReader(bytes);
        String name = cr.getClassName();
        assertNotNull(name);

        // Load generated class in host JVM and invoke helper methods reflectively
        ClassLoader loader = new ClassLoader() {
            public Class<?> define(byte[] b) {
                return defineClass(null, b, 0, b.length);
            }
        };
    // define directly using reflection on ClassLoader.defineClass
        java.lang.reflect.Method defineM = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
        defineM.setAccessible(true);
        Class<?> cls = (Class<?>) defineM.invoke(loader, null, bytes, 0, bytes.length);

        // call equals(Object[],Object[])
        java.lang.reflect.Method equalsM = cls.getMethod("equals", Object[].class, Object[].class);
        Object[] a = new Object[] {"x", Integer.valueOf(1)};
        Object[] b = new Object[] {"x", Integer.valueOf(1)};
        Boolean res = (Boolean) equalsM.invoke(null, new Object[] {a, b});
        assertTrue(res.booleanValue());

        java.lang.reflect.Method hashM = cls.getMethod("hashCode", Object[].class);
        Integer h = (Integer) hashM.invoke(null, new Object[] {a});
        assertNotNull(h);

        java.lang.reflect.Method toStrM = cls.getMethod("toString", Object[].class);
        String s = (String) toStrM.invoke(null, new Object[] {a});
        assertNotNull(s);
    }
}
