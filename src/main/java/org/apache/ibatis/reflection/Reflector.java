/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.util.MapUtil;

/**
 * 此类表示一组缓存的类定义信息，允许在属性之间轻松映射名称和 geter/setter 方法。
 *
 * @author Clinton Begin
 */
public class Reflector {
    // 一个方法句柄，用于判断对象是否为记录
    private static final MethodHandle isRecordMethodHandle = getIsRecordMethodHandle();
    // 记录的类型
    private final Class<?> type;
    // 可读属性的名称数组
    private final String[] readablePropertyNames;
    // 可写属性的名称数组
    private final String[] writablePropertyNames;
    // 存储设置属性值的方法
    private final Map<String, Invoker> setMethods = new HashMap<>();
    // 存储获取属性值的方法
    private final Map<String, Invoker> getMethods = new HashMap<>();
    // 存储设置属性值时的参数类型
    private final Map<String, Class<?>> setTypes = new HashMap<>();
    // 存储获取属性值时的返回类型
    private final Map<String, Class<?>> getTypes = new HashMap<>();
    // 默认构造函数
    private Constructor<?> defaultConstructor;
    // 用于存储属性名称的大小写不敏感的映射
    private final Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

    /**
     * Reflector类的构造函数。
     * 该构造函数用于初始化一个反射器实例，针对给定的类，自动分析并记录其构造函数、方法和字段信息。
     * 这包括但不限于：添加默认构造函数、分析并记录getter、setter方法以及字段信息。
     * 对于记录类型（Java 14+的record类型），会进行额外的处理以支持其特性。
     *
     * @param clazz 要反射的类。这个参数指定了反射器将要分析和操作的类。
     */
    public Reflector(Class<?> clazz) {
        type = clazz;
        addDefaultConstructor(clazz); // 添加默认构造函数
        Method[] classMethods = getClassMethods(clazz); // 获取并分析类中的所有方法

        if (isRecord(type)) { // 检查类是否为记录类型
            addRecordGetMethods(classMethods); // 为记录类型添加特化的getter方法处理
        } else {
            addGetMethods(classMethods); // 添加getter方法
            addSetMethods(classMethods); // 添加setter方法
            addFields(clazz); // 添加字段信息
        }

        // 初始化可读属性和可写属性名称数组
        readablePropertyNames = getMethods.keySet().toArray(new String[0]);
        writablePropertyNames = setMethods.keySet().toArray(new String[0]);

        // 基于不区分大小写的属性名，初始化大小写不敏感的属性映射
        for (String propName : readablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
        for (String propName : writablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
    }

    private void addRecordGetMethods(Method[] methods) {
        Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0)
            .forEach(m -> addGetMethod(m.getName(), m, false));
    }

    /**
     * 向当前对象添加一个默认构造函数的引用。
     * 这个方法会遍历指定类的所有构造函数，寻找一个无参数的构造函数。
     * 如果找到了这样的构造函数，那么它的引用将被保存在this.defaultConstructor中。
     *
     * @param clazz 要检查构造函数的类
     */
    private void addDefaultConstructor(Class<?> clazz) {
        // 获取类中所有的构造函数
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        // 流式处理所有构造函数，筛选出无参数的构造函数，并尝试获取其中一个
        Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0).findAny()
            // 如果找到无参数构造函数，将其赋值给this.defaultConstructor
            .ifPresent(constructor -> this.defaultConstructor = constructor);
    }


    /**
     * 将所有无参数且符合getter方法命名规范的方法添加到一个映射中，以解决可能的getter方法冲突。
     * 具体步骤为：
     * 1. 遍历传入的方法数组，筛选出无参数且命名符合getter规范的方法。
     * 2. 将这些方法根据它们对应的属性名（由PropertyNamer.methodToProperty转换）组织到一个映射中，
     *    如果有同名属性的方法冲突，则将这些方法添加到对应的冲突列表中。
     * 3. 最后，调用resolveGetterConflicts方法解决这些getter方法的冲突。
     *
     * @param methods 类中的所有方法数组。
     */
    private void addGetMethods(Method[] methods) {
        // 使用HashMap来存储可能存在的getter方法冲突，键为属性名，值为冲突的getter方法列表
        Map<String, List<Method>> conflictingGetters = new HashMap<>();

        // 流式处理methods数组，筛选出无参数且命名符合getter规范的方法，然后为每个方法解决冲突
        Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
            .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));

        // 解决getter方法冲突
        resolveGetterConflicts(conflictingGetters);
    }


    /**
     * 解决Getter方法冲突的方法。
     * 对于每个具有冲突的属性名（key），此方法将选择一个“胜利者”方法（即合适的getter方法），并处理这些方法中的任何歧义。
     * 如果存在多个具有相同返回类型的getter方法，且不是布尔类型，则视为歧义。布尔类型的getter方法，如果其名称以"is"开头，将被优先考虑。
     *
     * @param conflictingGetters 包含冲突getter方法的映射，其中key为属性名，value为该属性名对应的多个getter方法列表。
     */
    private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
        for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
            Method winner = null; // 初始胜者方法，用于比较和最终确定最佳getter方法
            String propName = entry.getKey(); // 当前处理的属性名
            boolean isAmbiguous = false; // 标记是否存在歧义

            for (Method candidate : entry.getValue()) { // 遍历所有候选getter方法
                if (winner == null) {
                    winner = candidate; // 如果还没有胜者，将当前方法设为胜者
                    continue;
                }

                Class<?> winnerType = winner.getReturnType(); // 当前胜者方法的返回类型
                Class<?> candidateType = candidate.getReturnType(); // 当前候选方法的返回类型

                if (candidateType.equals(winnerType)) {
                    // 如果返回类型相同，进一步比较
                    if (!boolean.class.equals(candidateType)) {
                        isAmbiguous = true; // 非布尔类型且返回类型相同，视为歧义
                        break;
                    }
                    // 对于布尔类型，优先选择以"is"开头的方法
                    if (candidate.getName().startsWith("is")) {
                        winner = candidate;
                    }
                } else if (candidateType.isAssignableFrom(winnerType)) {
                    // 如果候选方法的返回类型是当前胜者方法返回类型的子类型，不做处理，当前胜者保持不变
                } else if (winnerType.isAssignableFrom(candidateType)) {
                    // 如果当前胜者方法的返回类型是候选方法返回类型的父类型，更新胜者为当前候选方法
                    winner = candidate;
                } else {
                    // 如果没有继承关系，视为歧义
                    isAmbiguous = true;
                    break;
                }
            }
            // 将最终胜者方法和是否存在歧义的信息添加到结果中
            addGetMethod(propName, winner, isAmbiguous);
        }
    }


    /**
     * 添加一个用于获取属性值的方法。
     * @param name 属性名。
     * @param method 对应的getter方法。
     * @param isAmbiguous 是否为重载且类型不明确的getter方法。如果为true，则表示该方法的类型无法根据JavaBeans规范唯一确定。
     */
    private void addGetMethod(String name, Method method, boolean isAmbiguous) {
        // 根据方法是否为不明确的重载方法，创建不同的MethodInvoker实例
        MethodInvoker invoker = isAmbiguous ? new AmbiguousMethodInvoker(method, MessageFormat.format(
            "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
            name, method.getDeclaringClass().getName())) : new MethodInvoker(method);
        getMethods.put(name, invoker);

        // 解析方法的返回类型，并将其转换为Class类型后存储
        Type returnType = TypeParameterResolver.resolveReturnType(method, type);
        getTypes.put(name, typeToClass(returnType));
    }


    /**
     * 将具有设置功能的方法添加到集合中，并解决可能存在的设置方法冲突。
     * 该方法遍历传入的方法数组，筛选出参数为一个且为设置器方法（即方法名符合setter命名规则）的方法，
     * 并将这些方法根据其对应的属性名分组，记录可能存在的冲突。最后，通过调用resolveSetterConflicts方法解决这些冲突。
     *
     * @param methods 包含待检查方法的方法数组。
     */
    private void addSetMethods(Method[] methods) {
        // 使用HashMap来存储具有冲突的设置方法，其中键为属性名，值为冲突的设置方法列表
        Map<String, List<Method>> conflictingSetters = new HashMap<>();

        // 筛选出参数为一个且为设置器方法的方法，并为其添加冲突记录
        Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
            .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));

        // 解决设置方法的冲突
        resolveSetterConflicts(conflictingSetters);
    }


    private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
        if (isValidPropertyName(name)) {
            List<Method> list = MapUtil.computeIfAbsent(conflictingMethods, name, k -> new ArrayList<>());
            list.add(method);
        }
    }

    private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
        for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
            String propName = entry.getKey();
            List<Method> setters = entry.getValue();
            Class<?> getterType = getTypes.get(propName);
            boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
            boolean isSetterAmbiguous = false;
            Method match = null;
            for (Method setter : setters) {
                if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
                    // should be the best match
                    match = setter;
                    break;
                }
                if (!isSetterAmbiguous) {
                    match = pickBetterSetter(match, setter, propName);
                    isSetterAmbiguous = match == null;
                }
            }
            if (match != null) {
                addSetMethod(propName, match);
            }
        }
    }

    private Method pickBetterSetter(Method setter1, Method setter2, String property) {
        if (setter1 == null) {
            return setter2;
        }
        Class<?> paramType1 = setter1.getParameterTypes()[0];
        Class<?> paramType2 = setter2.getParameterTypes()[0];
        if (paramType1.isAssignableFrom(paramType2)) {
            return setter2;
        }
        if (paramType2.isAssignableFrom(paramType1)) {
            return setter1;
        }
        MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
            MessageFormat.format(
                "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.", property,
                setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
        setMethods.put(property, invoker);
        Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
        setTypes.put(property, typeToClass(paramTypes[0]));
        return null;
    }

    private void addSetMethod(String name, Method method) {
        MethodInvoker invoker = new MethodInvoker(method);
        setMethods.put(name, invoker);
        Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
        setTypes.put(name, typeToClass(paramTypes[0]));
    }

    private Class<?> typeToClass(Type src) {
        Class<?> result = null;
        if (src instanceof Class) {
            result = (Class<?>) src;
        } else if (src instanceof ParameterizedType) {
            result = (Class<?>) ((ParameterizedType) src).getRawType();
        } else if (src instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) src).getGenericComponentType();
            if (componentType instanceof Class) {
                result = Array.newInstance((Class<?>) componentType, 0).getClass();
            } else {
                Class<?> componentClass = typeToClass(componentType);
                result = Array.newInstance(componentClass, 0).getClass();
            }
        }
        if (result == null) {
            result = Object.class;
        }
        return result;
    }

    private void addFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (!setMethods.containsKey(field.getName())) {
                // issue #379 - removed the check for final because JDK 1.5 allows
                // modification of final fields through reflection (JSR-133). (JGB)
                // pr #16 - final static can only be set by the classloader
                int modifiers = field.getModifiers();
                if ((!Modifier.isFinal(modifiers) || !Modifier.isStatic(modifiers))) {
                    addSetField(field);
                }
            }
            if (!getMethods.containsKey(field.getName())) {
                addGetField(field);
            }
        }
        if (clazz.getSuperclass() != null) {
            addFields(clazz.getSuperclass());
        }
    }

    private void addSetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            setMethods.put(field.getName(), new SetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            setTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private void addGetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            getMethods.put(field.getName(), new GetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            getTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    /**
     * 检查给定的属性名是否有效。
     * 有效的属性名不应以"$"开头，且不能是"serialVersionUID"或"class"。
     *
     * @param name 待检查的属性名。
     * @return 返回 true 如果属性名有效，否则返回 false。
     */
    private boolean isValidPropertyName(String name) {
        // 检查属性名是否以"$"开头，是否等于"serialVersionUID"或"class"
        return (!name.startsWith("$") && !"serialVersionUID".equals(name) && !"class".equals(name));
    }


    /**
     * 此方法返回一个数组，其中包含此类中声明的所有方法和任何超类。我们使用这种方法，
     * 而不是更简单的 <code>Class.getMethods（），</code>因为我们也想寻找私有方法。
     * <p>
     * 获取给定类及其父类和实现的接口中所有的独特方法。
     * 这个方法会递归地查找类、其父类以及所有实现的接口中的方法，并去除重复的方法。
     * 注意，只返回声明了的方法，不会返回继承的方法。
     *
     * @param clazz 要查找方法的类
     * @return 一个Method数组，包含给定类中所有独特的公共和受保护的方法
     */
    private Method[] getClassMethods(Class<?> clazz) {
        // 使用HashMap来存储独特的方法，以方法名作为键
        Map<String, Method> uniqueMethods = new HashMap<>();
        Class<?> currentClass = clazz;

        // 遍历当前类及其父类，直到Object类，收集所有方法
        while (currentClass != null && currentClass != Object.class) {
            // 向uniqueMethods中添加当前类声明的方法，避免重复
            addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

            // 检查当前类实现的接口，添加接口中声明的方法
            Class<?>[] interfaces = currentClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                addUniqueMethods(uniqueMethods, anInterface.getMethods());
            }

            // 移动到父类继续查找
            currentClass = currentClass.getSuperclass();
        }

        // 将uniqueMethods中的方法收集到一个Collection中
        Collection<Method> methods = uniqueMethods.values();

        // 将Collection转换为数组并返回
        return methods.toArray(new Method[0]);
    }


    /**
     * 将独特的方法添加到唯一方法的映射中。
     * 遍历给定的方法数组，将非桥接方法（即非代理方法）根据其签名添加到映射中。
     * 如果同一个签名的方法已经存在于映射中，则说明有子类重写了该方法，因此只保留第一个遇到的方法。
     *
     * @param uniqueMethods 用于存储唯一方法的映射，键为方法的签名，值为方法对象。
     * @param methods 要处理的方法数组。
     */
    private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
        for (Method currentMethod : methods) {
            // 排除桥接方法，只关注实际定义的方法
            if (!currentMethod.isBridge()) {
                String signature = getSignature(currentMethod);
                // 检查方法是否已知，即是否已存在相同的签名方法
                if (!uniqueMethods.containsKey(signature)) {
                    uniqueMethods.put(signature, currentMethod);
                }
            }
        }
    }


    /**
     * 生成方法的签名字符串。
     * 该签名包含方法的返回类型、方法名和参数类型。
     * 参数类型按顺序以逗号分隔，并在第一个参数前加上冒号。
     *
     * @param method 待生成签名的方法对象。
     * @return 表示方法签名的字符串。
     */
    private String getSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        // 获取并添加方法的返回类型
        Class<?> returnType = method.getReturnType();
        sb.append(returnType.getName()).append('#');
        // 添加方法名
        sb.append(method.getName());
        // 获取并添加方法的参数类型
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
        }
        return sb.toString();
    }


    /**
     * Checks whether can control member accessible.
     *
     * @return If can control member accessible, it return {@literal true}
     * @since 3.5.0
     */
    public static boolean canControlMemberAccessible() {
        try {
            SecurityManager securityManager = System.getSecurityManager();
            if (null != securityManager) {
                securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
            }
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    /**
     * Gets the name of the class the instance provides information for.
     *
     * @return The class name
     */
    public Class<?> getType() {
        return type;
    }

    public Constructor<?> getDefaultConstructor() {
        if (defaultConstructor != null) {
            return defaultConstructor;
        }
        throw new ReflectionException("There is no default constructor for " + type);
    }

    public boolean hasDefaultConstructor() {
        return defaultConstructor != null;
    }

    public Invoker getSetInvoker(String propertyName) {
        Invoker method = setMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    public Invoker getGetInvoker(String propertyName) {
        Invoker method = getMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    /**
     * Gets the type for a property setter.
     *
     * @param propertyName - the name of the property
     * @return The Class of the property setter
     */
    public Class<?> getSetterType(String propertyName) {
        Class<?> clazz = setTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /**
     * Gets the type for a property getter.
     *
     * @param propertyName - the name of the property
     * @return The Class of the property getter
     */
    public Class<?> getGetterType(String propertyName) {
        Class<?> clazz = getTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /**
     * Gets an array of the readable properties for an object.
     *
     * @return The array
     */
    public String[] getGetablePropertyNames() {
        return readablePropertyNames;
    }

    /**
     * Gets an array of the writable properties for an object.
     *
     * @return The array
     */
    public String[] getSetablePropertyNames() {
        return writablePropertyNames;
    }

    /**
     * Check to see if a class has a writable property by name.
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a writable property by the name
     */
    public boolean hasSetter(String propertyName) {
        return setMethods.containsKey(propertyName);
    }

    /**
     * Check to see if a class has a readable property by name.
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a readable property by the name
     */
    public boolean hasGetter(String propertyName) {
        return getMethods.containsKey(propertyName);
    }

    public String findPropertyName(String name) {
        return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
    }

    /**
     * 判断给定的类是否为Java记录（record）类型。该方法适用于Java 15及以下版本，作为Class.isRecord()的替代方案。
     *
     * @param clazz 待检查的类，其类型为Class<?>，表示待检查的任意类。
     * @return 返回一个布尔值，如果该类是记录类型，则为true；否则为false。
     * @throws ReflectionException 如果在调用过程中发生异常，比如使用反射调用失败。
     */
    private static boolean isRecord(Class<?> clazz) {
        try {
            // 尝试使用方法句柄invokeExact调用Class的isRecord方法，返回结果为布尔值。
            return isRecordMethodHandle != null && (boolean) isRecordMethodHandle.invokeExact(clazz);
        } catch (Throwable e) {
            // 如果在调用过程中发生异常，抛出反射异常。
            throw new ReflectionException("Failed to invoke 'Class.isRecord()'.", e);
        }
    }


    private static MethodHandle getIsRecordMethodHandle() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType mt = MethodType.methodType(boolean.class);
        try {
            return lookup.findVirtual(Class.class, "isRecord", mt);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }
}
