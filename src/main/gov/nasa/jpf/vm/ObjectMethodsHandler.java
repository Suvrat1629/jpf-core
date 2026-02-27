package gov.nasa.jpf.vm;

import gov.nasa.jpf.vm.asm.AsmCallSiteGenerator;
import gov.nasa.jpf.vm.CallSiteDescriptor;
import gov.nasa.jpf.vm.GeneratedClassInfo;

/**
 * Handler for ObjectMethods / record bootstrap support.
 */
public class ObjectMethodsHandler implements BootstrapHandler {

  @Override
  public boolean canHandle(BootstrapMethodInfo bmi) {
    return bmi.getBmType() == BootstrapMethodInfo.BMType.OBJECT_METHODS;
  }

  @Override
  public boolean handle(ThreadInfo ti, BootstrapMethodInfo bmi, String samUniqueName,
                        String[] freeVariableTypeNames, Object[] freeVariableValues) {
    ClassLoaderInfo cli = bmi.enclosingClass.getClassLoaderInfo();
    AsmCallSiteGenerator gen = new AsmCallSiteGenerator();
    String[] compTypes = bmi.getComponentTypeNames();
    CallSiteDescriptor desc = new CallSiteDescriptor(bmi.enclosingClass.getName(), samUniqueName, "", null, compTypes);
    try {
      BootstrapBlueprint blueprint = BootstrapResolver.resolve(bmi);
      GeneratedClassInfo gci = gen.generateAdapter(desc, blueprint);
      ClassInfo helperCi = cli.getResolvedClassInfo(gci.getClassName(), gci.getClassBytes(), 0, gci.getClassBytes().length);

      String key = bmi.enclosingClass.getName() + "#" + samUniqueName;
      CallSiteMarshaller.registerHelper(key, gci.getClassName(), blueprint);

      // compute and push result
      MJIEnv env = new MJIEnv(ti);
      computeAndPushObjectMethodsResult(env, ti, samUniqueName, bmi, freeVariableTypeNames, freeVariableValues);
      return env.isInvocationRepeated();
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private void computeAndPushObjectMethodsResult(MJIEnv env, ThreadInfo ti, String samUniqueName, BootstrapMethodInfo bmi,
                                                 String[] freeVariableTypeNames, Object[] freeVariableValues) {
    String key = bmi.enclosingClass.getName() + "#" + samUniqueName;
    String helperCls = CallSiteMarshaller.lookupHelper(key);
    if (helperCls == null) return;

    try {
      Class<?> cls = Class.forName(helperCls);

      int recordRef = (freeVariableValues.length > 0 && freeVariableValues[0] instanceof ElementInfo)
              ? ((ElementInfo) freeVariableValues[0]).getObjectRef() : MJIEnv.NULL;
      Object[] comps = CallSiteMarshaller.extractComponents(env, recordRef, key);
      if (env.isInvocationRepeated()) return;

      StackFrame sf = ti.getModifiableTopFrame();

      if ("toString".equals(samUniqueName)) {
        java.lang.reflect.Method m = cls.getMethod("toString", Object[].class);
        String res = (String) m.invoke(null, new Object[]{comps});
        int strRef = ti.getHeap().newString(res, ti).getObjectRef();
        sf.pushRef(strRef);
      } else if ("equals".equals(samUniqueName)) {
        int otherRef = (freeVariableValues.length > 1 && freeVariableValues[1] instanceof ElementInfo)
                ? ((ElementInfo) freeVariableValues[1]).getObjectRef() : MJIEnv.NULL;
        Object[] otherComps = CallSiteMarshaller.extractComponents(env, otherRef, key);
        if (env.isInvocationRepeated()) return;

        java.lang.reflect.Method m = cls.getMethod("equals", Object[].class, Object[].class);
        Boolean res = (Boolean) m.invoke(null, new Object[]{comps, otherComps});
        sf.push(res ? 1 : 0);
      } else if ("hashCode".equals(samUniqueName)) {
        java.lang.reflect.Method m = cls.getMethod("hashCode", Object[].class);
        Integer res = (Integer) m.invoke(null, new Object[]{comps});
        sf.push(res);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
