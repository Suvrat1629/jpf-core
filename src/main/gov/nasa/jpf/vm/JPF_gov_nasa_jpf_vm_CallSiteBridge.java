package gov.nasa.jpf.vm;

import gov.nasa.jpf.annotation.MJI;

/**
 * Native peer bridge that dispatches SAM calls to ASM-generated helpers.
 *
 * This bridge provides a small set of entry points (bridge0, bridge1, bridge2)
 * for methods with 0..2 parameters (besides the implicit 'this'). They are
 * intentionally simple and accept primitive int parameters for refs/boxed
 * primitives since the native invocation passes boxed primitives / int refs.
 */
public class JPF_gov_nasa_jpf_vm_CallSiteBridge extends NativePeer {

  @MJI
  public int bridge0(MJIEnv env, int thisRef) {
    try {
      MethodInfo mi = env.getMethodInfo();
      String key = mi.getClassInfo().getName() + "#" + mi.getUniqueName();
      String helperCls = CallSiteMarshaller.lookupHelper(key);
      if (helperCls == null) {
        return MJIEnv.NULL;
      }

      Class<?> cls = Class.forName(helperCls);
      java.lang.reflect.Method toStringM = cls.getMethod("toString", Object[].class);

      Object[] comps = CallSiteMarshaller.extractComponents(env, thisRef, key);
      if (env.isInvocationRepeated()) {
        return MJIEnv.NULL;
      }
      String res = (String) toStringM.invoke(null, new Object[] { comps });
      return env.newString(res);
    } catch (Throwable t) {
      t.printStackTrace();
      return MJIEnv.NULL;
    }
  }

  @MJI
  public int bridge1(MJIEnv env, int thisRef, int a) {
    try {
      MethodInfo mi = env.getMethodInfo();
      String key = mi.getClassInfo().getName() + "#" + mi.getUniqueName();
      String helperCls = CallSiteMarshaller.lookupHelper(key);
      if (helperCls == null) {
        return MJIEnv.NULL;
      }

      Class<?> cls = Class.forName(helperCls);
      java.lang.reflect.Method equalsM = cls.getMethod("equals", Object[].class, Object[].class);

  Object[] comps = CallSiteMarshaller.extractComponents(env, thisRef, key);
  if (env.isInvocationRepeated()) {
    return 0;
  }
  ElementInfo otherEi = env.getElementInfo(a);
  Object[] otherComps = CallSiteMarshaller.extractComponents(env, otherEi.getObjectRef(), key);
  if (env.isInvocationRepeated()) {
    return 0;
  }

      Boolean r = (Boolean) equalsM.invoke(null, new Object[] { comps, otherComps });
      return r.booleanValue() ? 1 : 0; // boolean primitives are returned as int by MJI
    } catch (Throwable t) {
      t.printStackTrace();
      return 0;
    }
  }

  @MJI
  public int bridge2(MJIEnv env, int thisRef, int a, int b) {
    // For completeness - route to equals with two args (component-wise compare)
    try {
      MethodInfo mi = env.getMethodInfo();
      String key = mi.getClassInfo().getName() + "#" + mi.getUniqueName();
      String helperCls = CallSiteMarshaller.lookupHelper(key);
      if (helperCls == null) {
        return MJIEnv.NULL;
      }

      Class<?> cls = Class.forName(helperCls);
      java.lang.reflect.Method equalsM = cls.getMethod("equals", Object[].class, Object[].class);

  Object[] comps = CallSiteMarshaller.extractComponents(env, thisRef, key);
  if (env.isInvocationRepeated()) {
    return 0;
  }
  ElementInfo otherEi = env.getElementInfo(a);
  Object[] otherComps = CallSiteMarshaller.extractComponents(env, otherEi.getObjectRef(), key);
  if (env.isInvocationRepeated()) {
    return 0;
  }

      Boolean r = (Boolean) equalsM.invoke(null, new Object[] { comps, otherComps });
      return r.booleanValue() ? 1 : 0;
    } catch (Throwable t) {
      t.printStackTrace();
      return 0;
    }
  }
  
  @MJI
  public int bridgeGeneric(MJIEnv env, int thisRef, int argsArrayRef) {
    try {
      MethodInfo mi = env.getMethodInfo();
      String key = mi.getClassInfo().getName() + "#" + mi.getUniqueName();
      String helperCls = CallSiteMarshaller.lookupHelper(key);
      if (helperCls == null) {
        return MJIEnv.NULL;
      }

      Class<?> cls = Class.forName(helperCls);

      // retrieve host-side argument array from JPF
      Object[] hostArgs = env.getArgumentArray(argsArrayRef);

      // Decide what helper to call based on arity / method name
      // prefer toString(Object[]), equals(Object[],Object[]), hashCode(Object[])
      if (hostArgs == null || hostArgs.length == 0) {
        java.lang.reflect.Method toStringM = cls.getMethod("toString", Object[].class);
        String res = (String) toStringM.invoke(null, new Object[] { new Object[0] });
        return env.newString(res);
      } else if (hostArgs.length == 1) {
        java.lang.reflect.Method toStringM = cls.getMethod("toString", Object[].class);
        String res = (String) toStringM.invoke(null, new Object[] { hostArgs });
        return env.newString(res);
      } else if (hostArgs.length == 2) {
        java.lang.reflect.Method equalsM = cls.getMethod("equals", Object[].class, Object[].class);
        Boolean r = (Boolean) equalsM.invoke(null, new Object[] { hostArgs[0], hostArgs[1] });
        return r.booleanValue() ? 1 : 0;
      } else {
        // fallback to toString
        java.lang.reflect.Method toStringM = cls.getMethod("toString", Object[].class);
        String res = (String) toStringM.invoke(null, new Object[] { hostArgs });
        return env.newString(res);
      }
    } catch (Throwable t) {
      t.printStackTrace();
      return MJIEnv.NULL;
    }
  }
}
