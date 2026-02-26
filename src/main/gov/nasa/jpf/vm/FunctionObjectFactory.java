/*
 * Copyright (C) 2014, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The Java Pathfinder core (jpf-core) platform is licensed under the
 * Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.nasa.jpf.vm;

import java.lang.invoke.*;
import java.util.ArrayList;
import java.util.List;

import gov.nasa.jpf.jvm.JVMStackFrame;
import gov.nasa.jpf.vm.CallSiteDescriptor;
import gov.nasa.jpf.vm.GeneratedClassInfo;
import gov.nasa.jpf.vm.asm.AsmCallSiteGenerator;

/**
 * @author Nastaran Shafiei <nastaran.shafiei@gmail.com>
 */
public class FunctionObjectFactory {
  /**
   * Return JVM class objects from JPF class labels
   *
   * @param className name of the class
   * @return the class object associated with the input
   */
  private static Class<?> parseType(String className) {
    switch (className) {
      case "byte":
        return byte.class;
      case "short":
        return short.class;
      case "int":
        return int.class;
      case "long":
        return long.class;
      case "float":
        return float.class;
      case "double":
        return double.class;
      case "char":
        return char.class;
      case "boolean":
        return boolean.class;
      //This is not how this case should be handled, but there seems to be inconsistencies in how JPF treats this label
      default:
        return Object.class;
    }
  }

  /**
   * Map the function parseType across the input array typeNames and return the result
   *
   * @param typeNames array of class labels
   * @return array of class objects corresponding to the labels
   */
  private static Class<?>[] getPTypes(String[] typeNames) {
    Class<?>[] pTypes = new Class<?>[typeNames.length];
    for (int i = 0; i < typeNames.length; i++) {
      pTypes[i] = parseType(typeNames[i]);
    }
    return pTypes;
  }

  /**
   * "Dereference" the the JPF ElementInfo object in the MJIEnv environment as return the wrapped object to JVM
   *
   * @param env MJIEnv environment
   * @param ei  referenced ElementInfo object
   * @return JVM representation of the wrapped ElementInfo object
   */
  private static Object derefElementInfo(MJIEnv env, ElementInfo ei) {
    String name = ei.getClassInfo().getName();
    int objRef = ei.getObjectRef();
    switch (name) {
      case "java.lang.Byte":
        Byte byteObject = env.getByteObject(objRef);
        return byteObject.byteValue();
      case "java.lang.Short":
        Short shortObject = env.getShortObject(objRef);
        return shortObject.shortValue();
      case "java.lang.Integer":
        Integer integerObject = env.getIntegerObject(objRef);
        return integerObject.intValue();
      case "java.lang.Long":
        Long longObject = env.getLongObject(objRef);
        return longObject.longValue();
      case "java.lang.Float":
        Float floatObject = env.getFloatObject(objRef);
        return floatObject.floatValue();
      case "java.lang.Double":
        Double doubleObject = env.getDoubleObject(objRef);
        return doubleObject.doubleValue();
      case "java.lang.Character":
        Character charObject = env.getCharObject(objRef);
        return charObject.charValue();
      case "java.lang.Boolean":
        Boolean boolObject = env.getBooleanObject(objRef);
        return boolObject.booleanValue();
      default:
        //This block is structured in this unsatisfying way due to the limitations of JPF dereferencing
        //This is likely where all subsequent errors in string concatenation will reside
        try {
          return env.getStringObject(objRef);
        } catch (Exception e) {
          try {
            return ei.toString();
          } catch (Exception ee) {
            ee.printStackTrace();
            return null;
          }
        }
    }
  }

  public int getFunctionObject(int bsIdx, ThreadInfo ti, ClassInfo fiClassInfo, String samUniqueName, BootstrapMethodInfo bmi,
                               String[] freeVariableTypeNames, Object[] freeVariableValues) {

    ClassLoaderInfo cli = bmi.enclosingClass.getClassLoaderInfo();
    ClassInfo funcObjType = cli.getResolvedFuncObjType(bsIdx, fiClassInfo, samUniqueName, bmi, freeVariableTypeNames);

    funcObjType.registerClass(ti);

    ElementInfo ei;
    Heap heap = ti.getHeap();

    if (bmi.getBmType() == BootstrapMethodInfo.BMType.STRING_CONCATENATION) {
      createConcatStringCall(ti, bmi.getBmArg(), freeVariableTypeNames, freeVariableValues);
      return MJIEnv.NULL;
    } else if (bmi.getBmType() == BootstrapMethodInfo.BMType.OBJECT_METHODS) {
      // Clean final version: generate helper + compute result + push to stack, return NULL
      // No function object, no SAM replacement, no bridge registration for SAM
      AsmCallSiteGenerator gen = new AsmCallSiteGenerator();
      String[] compTypes = bmi.getComponentTypeNames();
      CallSiteDescriptor desc = new CallSiteDescriptor(bmi.enclosingClass.getName(), samUniqueName, "", null, compTypes);
      try {
        GeneratedClassInfo gci = gen.generateAdapter(desc);
        ClassInfo helperCi = cli.getResolvedClassInfo(gci.getClassName(), gci.getClassBytes(), 0, gci.getClassBytes().length);

        // better key: record class + method name
        String key = bmi.enclosingClass.getName() + "#" + samUniqueName;
        gov.nasa.jpf.vm.RecordComponent[] rcs = bmi.getRecordComponents();
        CallSiteMarshaller.registerHelper(key, gci.getClassName(), rcs);

        // compute result and push it directly to the stack (like string concat does)
        computeAndPushRecordResult(ti, samUniqueName, bmi, freeVariableTypeNames, freeVariableValues);
        return MJIEnv.NULL;
      } catch (Exception e) {
        e.printStackTrace();
        return MJIEnv.NULL;
      }
    } else {
      ei = heap.newObject(funcObjType, ti); // In the case of Lambda Expressions
    }

    //It does not make sense to call setFuncObjFields in the case of string concatenation since the String object
    //in the JPF heap has only three fields. This call will fail for any concatenation with more than three variables.
    if (bmi.getBmType() != BootstrapMethodInfo.BMType.STRING_CONCATENATION) {
      setFuncObjFields(ei, bmi, freeVariableTypeNames, freeVariableValues);
    }

    return ei.getObjectRef();
  }

  /**
   * This function receives invokedynamic's args and 'recipe' arg of BSM
   * in case of string concat and convert them to proper data format.
   * Them use these converted data as args and create a new stack frame
   * to call the helper function java.lang.String.generateStringByConcatenatingArgs
   * which concats these args to string.
   *
   * 'recipe' is in fact a string serves as a template to indicate how the args
   * are to be concated to form a string. We split it into pieces: containing string
   * elements and placeholders for the free variables (invokedynamic's args).
   * Then we create a reference array to hold these pieces in their converted format.
   * (convert primitive type to Java world String and VM world String to Java world String).
   * Finally we pass the ref. array as the arg to call the helper function.
   *
   * @param ti                    ThreadInfo object
   * @param recipe                a template indicating how to concat the String
   * @param freeVariableTypeNames VM world String of the input variable types
   * @param freeVariableValues    representation of the input variable values (ElementInfo for Java world
   *                              object, boxed VM world object for Java world primitive)
   * @return concatenated string
   */
  public void createConcatStringCall(ThreadInfo ti,
                                     String recipe,
                                     String[] freeVariableTypeNames,
                                     Object[] freeVariableValues) {
    final String freeVariablePlaceholder = "";
    List<String> args = new ArrayList<>();
    int startIdx = 0;
    // 1. Split recipe into pieces, leave placeholders
    // for free variables (invokedynamic's args)
    for (int i = 0; i < (int) recipe.length(); i++) {
      char ch = recipe.charAt(i);
      // '\u0002' in recipe is a placeholder for BSM static args.
      // It is rarely used except String literals containing some
      // special chars (\u0001 or \u0002) are to be concated.
      // We don't support BSM static args in case of string concat yet.
      assert ch != '\u0002' : "We don't support BSM static args for string concat yet";
      if (ch == '\u0001') {
        if (startIdx != i) {
          args.add(recipe.substring(startIdx, i));
        }
        startIdx = i + 1;
        args.add(freeVariablePlaceholder);
      }
    }
    if (startIdx != recipe.length()) {
      args.add(recipe.substring(startIdx));
    }

    // 2. Prepare for the arg for the helper function
    ElementInfo argArrObj = ti.getHeap().newArray("Ljava/lang/Object;", args.size(), ti);
    int freeVarIdx = 0;
    int argIdx = 0;
    for (String arg : args) {
      if (arg.equals(freeVariablePlaceholder)) {
        String fVarTypeName = freeVariableTypeNames[freeVarIdx];
        ElementInfo argObj = null;
        if (Types.isBasicType(fVarTypeName)) {
          String basicTypesString = freeVariableValues[freeVarIdx].toString();
          argObj = ti.getHeap().newString(basicTypesString, ti);
        } else {
          argObj = (ElementInfo) freeVariableValues[freeVarIdx];
        }
        if (argObj == null) {
          argArrObj.asReferenceArray()[argIdx] = MJIEnv.NULL;
        } else {
          argArrObj.asReferenceArray()[argIdx] = argObj.objRef;
        }
        freeVarIdx++;
      } else {
        argArrObj.asReferenceArray()[argIdx] = ti.getHeap().newString(arg, ti).objRef;
      }
      argIdx++;
    }

    // 3. Create a new stack frame to call the helper function
    ClassInfo stringCI = ti.resolveReferencedClass("java.lang.String");
    MethodInfo concatMtd = stringCI.getMethod("generateStringByConcatenatingArgs",
        "([Ljava/lang/Object;)Ljava/lang/String;", false);
    JVMStackFrame concatStackFrame = new JVMStackFrame(concatMtd);
    concatStackFrame.setLocalReferenceVariable(0, argArrObj.objRef);
    ti.pushFrame(concatStackFrame);
  }

  /**
   * Concatenate the given inputs in the JVM according to the recipe given by the BootstrapMethodInfo object using
   * the bootstrap method StringConcatFactory.makeConcatWithConstants and return the resulting string
   *
   * @param ti                    ThreadInfo object
   * @param freeVariableTypeNames string representation of the input variable types
   * @param freeVariableValues    representation of the input variable values
   * @param bmi                   JPF representation of bootstrap method parameters from the constant pool
   * @return concatenated string
   */
  public String makeConcatWithStrings(ThreadInfo ti, String[] freeVariableTypeNames, Object[] freeVariableValues, BootstrapMethodInfo bmi) {
    MJIEnv env = new MJIEnv(ti);
    Class<?>[] pTypes = getPTypes(freeVariableTypeNames);
    Object[] convFreeVarVals = new Object[freeVariableValues.length];
    for (int i = 0; i < freeVariableValues.length; i++) {
      if (freeVariableValues[i] instanceof ElementInfo) {
        //Dereference composite types
        convFreeVarVals[i] = derefElementInfo(env, (ElementInfo) freeVariableValues[i]);
      } else {
        //Copy primitive types
        convFreeVarVals[i] = freeVariableValues[i];
      }
    }
    MethodType concatType = MethodType.methodType(String.class, pTypes);
    String recipe = bmi.getBmArg();
    try {
      CallSite cs = StringConcatFactory.makeConcatWithConstants(MethodHandles.lookup(), "", concatType, recipe, new Object[0]);
      MethodHandle target = cs.getTarget();
      Object result = target.invokeWithArguments(convFreeVarVals);
      return (String) result;
    } catch (Throwable e) {
      e.printStackTrace();
      return null;
    }
  }

  public void setFuncObjFields(ElementInfo funcObj, BootstrapMethodInfo bmi, String[] freeVarTypeNames, Object[] freeVarValues) {
    Fields fields = funcObj.getFields();

    // Fields.setXXXValue() needs field offset instead of field "index"
    int fieldOffset = 0;
    for (int i = 0; i < freeVarTypeNames.length; i++) {
      String typeName = freeVarTypeNames[i];
      if (typeName.equals("byte")) {
        fields.setByteValue(fieldOffset, (Byte) freeVarValues[i]);
      } else if (typeName.equals("char")) {
        fields.setCharValue(fieldOffset, (Character) freeVarValues[i]);
      } else if (typeName.equals("short")) {
        fields.setShortValue(fieldOffset, (Short) freeVarValues[i]);
      } else if (typeName.equals("int")) {
        fields.setIntValue(fieldOffset, (Integer) freeVarValues[i]);
      } else if (typeName.equals("float")) {
        fields.setFloatValue(fieldOffset, (Float) freeVarValues[i]);
      } else if (typeName.equals("long")) {
        fields.setLongValue(fieldOffset, (Long) freeVarValues[i]);
      } else if (typeName.equals("double")) {
        fields.setDoubleValue(fieldOffset, (Double) freeVarValues[i]);
      } else if (typeName.equals("boolean")) {
        fields.setBooleanValue(fieldOffset, (Boolean) freeVarValues[i]);
      } else {
        if (freeVarValues[i] == null) {
          fields.setReferenceValue(fieldOffset, MJIEnv.NULL);
        } else {
          ElementInfo obj = (ElementInfo) freeVarValues[i];
          if (!obj.getClassInfo().isInstanceOf(freeVarTypeNames[i])) {
            throw new RuntimeException("Unexpected free variable type for lambda expression");
          }
          fields.setReferenceValue(fieldOffset, obj.getObjectRef());
        }
      }
      if (typeName.equals("long") || typeName.equals("double")) {
        fieldOffset += 2;
      } else {
        fieldOffset += 1;
      }
    }
  }

  /**
   * Compute and push the ObjectMethods result (toString/equals/hashCode) for a record-like object.
   * Uses provided MJIEnv for potential in-VM accessor invocation. Returns true if the call
   * requested a repeatInvocation (so caller should return immediately).
   */
  /**
   * Compute and push the ObjectMethods result (toString/equals/hashCode) for a record-like object.
   * Creates its own MJIEnv and returns void; if it triggers repeatInvocation the caller will re-enter later.
   */
  private void computeAndPushRecordResult(ThreadInfo ti, String samUniqueName, BootstrapMethodInfo bmi,
                                          String[] freeVariableTypeNames, Object[] freeVariableValues) {
    String key = bmi.enclosingClass.getName() + "#" + samUniqueName;
    String helperCls = CallSiteMarshaller.lookupHelper(key);
    if (helperCls == null) return;

    try {
      Class<?> cls = Class.forName(helperCls);

      MJIEnv env = new MJIEnv(ti);

      // first arg is almost always the record instance ("this")
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
