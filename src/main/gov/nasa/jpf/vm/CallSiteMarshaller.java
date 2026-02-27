package gov.nasa.jpf.vm;

/**
 * Helper utilities to marshal values between JPF VM representations and host Java objects.
 *
 * This is intentionally conservative: it handles the most common types used by
 * bootstrap helpers (Strings and boxed primitives). It provides a
 * small number of conversions used by the ASM-based generator proof-of-concept.
 */
import java.util.concurrent.ConcurrentHashMap;

public class CallSiteMarshaller {
  // registry mapping method identifier (className#uniqueMethodName) -> helper class name
  private static final ConcurrentHashMap<String,String> helperRegistry = new ConcurrentHashMap<>();
  // consolidated registry: optional blueprint metadata (component types + accessors)
  private static final ConcurrentHashMap<String, BootstrapBlueprint> compBlueprintRegistry = new ConcurrentHashMap<>();

  /**
  * Convert a JPF argument value into a host-side Java object.
   * If the value is an ElementInfo for a boxed primitive or String, it will
   * be converted to the corresponding host boxed primitive / String. For other
   * reference types we return null as a conservative fallback.
   */
  public static Object toHostObject(MJIEnv env, Object v) {
    if (v == null) return null;

    if (v instanceof ElementInfo) {
      ElementInfo ei = (ElementInfo) v;
      String cls = ei.getClassInfo().getName();

      switch (cls) {
        case "java.lang.String":
          return env.getStringObject(ei.getObjectRef());
        case "java.lang.Integer":
          return env.getIntegerObject(ei.getObjectRef());
        case "java.lang.Long":
          return env.getLongObject(ei.getObjectRef());
        case "java.lang.Double":
          return env.getDoubleObject(ei.getObjectRef());
        case "java.lang.Float":
          return env.getFloatObject(ei.getObjectRef());
        case "java.lang.Short":
          return env.getShortObject(ei.getObjectRef());
        case "java.lang.Byte":
          return env.getByteObject(ei.getObjectRef());
        case "java.lang.Character":
          return env.getCharObject(ei.getObjectRef());
        case "java.lang.Boolean":
          return env.getBooleanObject(ei.getObjectRef());
        default:
          // For arbitrary references, we don't attempt deep conversion here.
          return null;
      }
    } else {
      // primitive boxed values are passed through
      return v;
    }
  }

  public static void registerHelper(String methodKey, String helperClassName) {
    helperRegistry.put(methodKey, helperClassName);
  }
  public static void registerHelper(String methodKey, String helperClassName, BootstrapBlueprint blueprint) {
    helperRegistry.put(methodKey, helperClassName);
    if (blueprint == null) return;
    compBlueprintRegistry.put(methodKey, blueprint);
  }

  public static String lookupHelper(String methodKey) {
    return helperRegistry.get(methodKey);
  }

  public static String[] lookupComponentTypeNames(String methodKey) {
    BootstrapBlueprint bp = compBlueprintRegistry.get(methodKey);
    String[] v = bp == null ? null : bp.componentTypeNames;
    return v == null ? null : v.clone();
  }

  public static BootstrapComponent[] lookupBootstrapComponents(String methodKey) {
    BootstrapBlueprint bp = compBlueprintRegistry.get(methodKey);
    BootstrapComponent[] v = bp == null ? null : bp.accessors;
    return v == null ? null : v.clone();
  }

  // Helper: directly obtain a field value from an object as a host-side object
  private static Object extractFieldValue(MJIEnv env, int objRef, FieldInfo fi) {
    if (fi == null) return null;
    String sig = fi.getSignature();
    try {
      if (sig.startsWith("L")) {
        int r = env.getReferenceField(objRef, fi.getName());
        if ("Ljava/lang/String;".equals(sig)) {
          return env.getStringObject(r);
        } else if (r == MJIEnv.NULL) {
          return null;
        } else {
          return null;
        }
      } else {
        char t = sig.charAt(0);
        switch (t) {
          case 'I': return Integer.valueOf(env.getIntField(objRef, fi.getName()));
          case 'J': return Long.valueOf(env.getLongField(objRef, fi.getName()));
          case 'D': return Double.valueOf(env.getDoubleField(objRef, fi.getName()));
          case 'F': return Float.valueOf(env.getFloatField(objRef, fi.getName()));
          case 'S': return Short.valueOf(env.getShortField(objRef, fi.getName()));
          case 'B': return Byte.valueOf(env.getByteField(objRef, fi.getName()));
          case 'C': return Character.valueOf(env.getCharField(objRef, fi.getName()));
          case 'Z': return Boolean.valueOf(env.getBooleanField(objRef, fi.getName()));
          default: return null;
        }
      }
    } catch (Exception x) {
      return null;
    }
  }

  /**
   * Extract instance field values from a JPF object and convert them to host-side objects.
   * This is a conservative extractor that handles Strings and boxed primitives.
   */
  public static Object[] extractComponents(MJIEnv env, int objRef) {
    return extractComponents(env, objRef, null);
  }

  /**
   * Extract instance component values from a JPF object and convert them to host-side objects.
   * If methodKey is provided and component type names were registered for that key, we use
   * the declared component type order to map instance fields to components (best-effort).
   */
  public static Object[] extractComponents(MJIEnv env, int objRef, String methodKey) {
    if (objRef == MJIEnv.NULL) return null;

    ElementInfo ei = env.getElementInfo(objRef);
    ClassInfo ci = ei.getClassInfo();
    FieldInfo[] fields = ci.getInstanceFields();

    // Prefer accessor metadata if available
    BootstrapComponent[] accessors = (methodKey != null) ? lookupBootstrapComponents(methodKey) : null;
    if (accessors != null && accessors.length > 0) {
      Object[] comps = new Object[accessors.length];
      for (int i = 0; i < accessors.length; i++) {
        BootstrapComponent rc = accessors[i];
        try {
          // If accessor refers to a field (or we can treat it as a field), extract by field name
          if (rc.name != null && (rc.refKind == gov.nasa.jpf.jvm.ClassFile.REF_GETFIELD || rc.refKind == gov.nasa.jpf.jvm.ClassFile.REF_GETSTATIC || rc.desc != null && rc.desc.startsWith("L") || rc.desc != null && rc.desc.length() > 0)) {
              // try to locate a matching field in the target class (owner if provided, otherwise the instance class)
              ClassInfo targetCi = ci;
              if (rc.owner != null) {
                ClassInfo maybe = ClassLoaderInfo.getCurrentResolvedClassInfo(rc.owner);
                if (maybe != null) targetCi = maybe;
              }
              FieldInfo matchField = null;
              FieldInfo[] targetFields = targetCi.getInstanceFields();
              for (FieldInfo fi : targetFields) {
                if (fi.getName().equals(rc.name) || (rc.desc != null && fi.getSignature().equals(rc.desc))) {
                  matchField = fi;
                  break;
                }
              }
            if (matchField != null) {
              comps[i] = extractFieldValue(env, objRef, matchField);
              continue;
            }
            // If we didn't find a matching field, but we have an accessor method, try to
            // inspect the accessor's bytecode to find a GETFIELD it delegates to.
            if (matchField == null && rc.name != null && rc.desc != null) {
              try {
                MethodInfo mi = targetCi.getMethod(rc.name + rc.desc, false);
                if (mi != null) {
                  Instruction[] code = mi.code; // package-visible access
                  if (code != null) {
                    for (Instruction insn : code) {
                      if (insn == null) continue;
                      // look for instance field instructions (GETFIELD)
                      if (insn.getClass().getName().endsWith("GETFIELD") || insn.getClass().getName().endsWith("JVMInstanceFieldInstruction")) {
                        try {
                          // JVMInstanceFieldInstruction has getFieldInfo()
                          java.lang.reflect.Method m = insn.getClass().getMethod("getFieldInfo");
                          Object fiObj = m.invoke(insn);
                          if (fiObj instanceof FieldInfo) {
                            matchField = (FieldInfo) fiObj;
                            break;
                          }
                        } catch (Exception ex) {
                          // ignore and continue
                        }
                      }
                    }
                  }
                }
              } catch (Exception x) {
                // ignore and fallback
              }
            }
            // If we still didn't find a field but we have an accessor MethodInfo, invoke it in-VM
            if (matchField == null && rc.name != null && rc.desc != null) {
              try {
                MethodInfo mi = targetCi.getMethod(rc.name + rc.desc, false);
                if (mi != null) {
                  Object val = invokeAccessorInVm(env, mi, objRef);
                  // if invocation caused a repeat, bail out (caller/bridge will re-enter later)
                  if (env.isInvocationRepeated()) {
                    return null;
                  }
                  comps[i] = val;
                  continue;
                }
              } catch (Exception x) {
                // ignore
              }
            }
          }
        } catch (Exception x) {
          comps[i] = null;
        }
        comps[i] = null; // fallback if nothing matched
      }
      return comps;
    }

    // If we have component type names registered for this method key, try to map fields in that order.
    String[] compSigs = (methodKey != null) ? lookupComponentTypeNames(methodKey) : null;
    if (compSigs != null && compSigs.length > 0) {
      Object[] comps = new Object[compSigs.length];
      boolean[] used = new boolean[fields.length];
      for (int i = 0; i < compSigs.length; i++) {
        String want = compSigs[i];
        boolean found = false;
        for (int j = 0; j < fields.length; j++) {
          if (used[j]) continue;
          FieldInfo fi = fields[j];
          String sig = fi.getSignature();
          if (sig.equals(want)) {
            // extract value for this field
            try {
              comps[i] = extractFieldValue(env, objRef, fi);
            } catch (Exception x) {
              comps[i] = null;
            }
            used[j] = true;
            found = true;
            break;
          }
        }
        if (!found) {
          comps[i] = null; // best-effort: leave null if we couldn't match
        }
      }
      return comps;
    }

    // fallback: original conservative field-order extractor
    Object[] comps = new Object[fields.length];

    for (int i = 0; i < fields.length; i++) {
      FieldInfo fi = fields[i];
      try {
        comps[i] = extractFieldValue(env, objRef, fi);
      } catch (Exception x) {
        comps[i] = null;
      }
    }

    return comps;
  }

  /**
   * Invoke a MethodInfo accessor inside the JPF VM by creating a direct call frame.
   * Returns a host-side representation (ElementInfo or boxed primitive) or null.
   * If the invocation required scheduling (repeatInvocation), this method will
   * call env.repeatInvocation() and return null; callers should check
   * env.isInvocationRepeated() and return promptly so the runtime can re-enter.
   */
  private static Object invokeAccessorInVm(MJIEnv env, MethodInfo mi, int objRef) {
    ThreadInfo ti = env.getThreadInfo();
    DirectCallStackFrame frame = ti.getReturnedDirectCall();

    if (frame == null) { // first time, push direct call frame
      DirectCallStackFrame f = mi.createDirectCallStackFrame(ti, 0);
      // mark as reflective/direct
      try { f.setReflection(); } catch (Throwable x) { /* ignore */ }

      int argOffset = 0;
      if (!mi.isStatic()) {
        f.setReferenceArgument(argOffset++, objRef, null);
      }
      ti.pushFrame(f);

      // ensure class init if needed
      if (mi.isStatic()) {
        mi.getClassInfo().initializeClass(ti);
      }

      // ask the VM to re-enter and execute the pushed frame
      env.repeatInvocation();
      return null;
    } else {
      // we have returned from the direct call; extract the result from the frame
  byte rt = mi.getReturnTypeCode();
  switch (rt) {
        case Types.T_REFERENCE: {
          int r = frame.getReferenceResult();
          if (r == MJIEnv.NULL) return null;
          return env.getElementInfo(r);
        }
        case Types.T_LONG:
        case Types.T_DOUBLE: {
          long lr = frame.getLongResult();
          if (rt == Types.T_LONG) return Long.valueOf(lr);
          else return Double.valueOf(Double.longBitsToDouble(lr));
        }
        case Types.T_INT:
        case Types.T_SHORT:
        case Types.T_CHAR:
        case Types.T_BYTE:
        case Types.T_BOOLEAN:
        case Types.T_FLOAT: {
          int iv = frame.getResult();
          switch (rt) {
            case Types.T_INT: return Integer.valueOf(iv);
            case Types.T_SHORT: return Short.valueOf((short)iv);
            case Types.T_CHAR: return Character.valueOf((char)iv);
            case Types.T_BYTE: return Byte.valueOf((byte)iv);
            case Types.T_BOOLEAN: return Boolean.valueOf(iv != 0);
            case Types.T_FLOAT: return Float.valueOf(Float.intBitsToFloat(iv));
            default: return null;
          }
        }
        default:
          return null;
      }
    }
  }

  /**
   * Convert a host-side Java return value into a JPF VM reference (int). For
   * Strings and boxed primitives this creates the corresponding JPF object.
   * Returns MJIEnv.NULL for unsupported values.
   */
  public static int toVmReturn(MJIEnv env, Object hostResult) {
    if (hostResult == null) return MJIEnv.NULL;

    if (hostResult instanceof String) {
      return env.newString((String) hostResult);
    } else if (hostResult instanceof Boolean) {
      return env.newBoolean((Boolean) hostResult);
    } else if (hostResult instanceof Integer) {
      return env.newInteger((Integer) hostResult);
    } else if (hostResult instanceof Long) {
      return env.newLong((Long) hostResult);
    } else if (hostResult instanceof Double) {
      return env.newDouble((Double) hostResult);
    } else if (hostResult instanceof Float) {
      return env.newFloat((Float) hostResult);
    } else if (hostResult instanceof Short) {
      return env.newShort((Short) hostResult);
    } else if (hostResult instanceof Byte) {
      return env.newByte((Byte) hostResult);
    } else if (hostResult instanceof Character) {
      return env.newCharacter((Character) hostResult);
    } else {
      return MJIEnv.NULL;
    }
  }
}
