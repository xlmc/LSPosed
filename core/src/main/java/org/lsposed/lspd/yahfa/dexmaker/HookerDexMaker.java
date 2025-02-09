/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */

package org.lsposed.lspd.yahfa.dexmaker;

import org.lsposed.lspd.core.yahfa.HookMain;
import org.lsposed.lspd.nativebridge.Yahfa;
import org.lsposed.lspd.util.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;

import de.robv.android.xposed.LspHooker;
import de.robv.android.xposed.XposedBridge;

@SuppressWarnings("rawtypes")
public class HookerDexMaker {

    public static final String METHOD_NAME_BACKUP = "backup";
    public static final String FIELD_NAME_HOOKER = "hooker";
    private static final HashMap<Class<?>, Character> descriptors = new HashMap<>() {{
        put(int.class, 'I');
        put(boolean.class, 'Z');
        put(char.class, 'C');
        put(long.class, 'J');
        put(short.class, 'S');
        put(float.class, 'F');
        put(double.class, 'D');
        put(byte.class, 'B');
        put(void.class, 'V');
        put(Object.class, 'L');
    }};

    private Class<?> mReturnType;
    private Class<?>[] mActualParameterTypes;

    private Executable mMember;
    private XposedBridge.AdditionalHookInfo mHookInfo;
    private LspHooker mHooker;

    private static Class<?>[] getParameterTypes(Executable method, boolean isStatic) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = parameterTypes[i].isPrimitive() ? parameterTypes[i] : Object.class;
        }
        if (isStatic) {
            return parameterTypes;
        }
        int parameterSize = parameterTypes.length;
        Class<?>[] newParameterTypes = new Class<?>[parameterSize + 1];
        newParameterTypes[0] = Object.class;
        System.arraycopy(parameterTypes, 0, newParameterTypes, 1, parameterSize);
        return newParameterTypes;
    }

    private static char getDescriptor(Class<?> clazz) {
        return descriptors.getOrDefault(clazz, 'L');
    }

    private static char[] getDescriptors(Class<?>[] classes) {
        var descriptors = new char[classes.length];
        for (int i = 0; i < classes.length; ++i) {
            descriptors[i] = getDescriptor(classes[i]);
        }
        return descriptors;
    }

    public void start(Executable member, XposedBridge.AdditionalHookInfo hookInfo) throws Exception {
        if (member instanceof Method) {
            Method method = (Method) member;
            mReturnType = method.getReturnType();
            mActualParameterTypes = getParameterTypes(method, Modifier.isStatic(method.getModifiers()));
        } else if (member instanceof Constructor) {
            Constructor constructor = (Constructor) member;
            mReturnType = void.class;
            mActualParameterTypes = getParameterTypes(constructor, false);
        }
        mMember = member;
        mHookInfo = hookInfo;

        long startTime = System.nanoTime();
        doMake(member instanceof Constructor ? "constructor" : member.getName());
        long endTime = System.nanoTime();
        Utils.logD("Hook time: " + (endTime - startTime) / 1e6 + "ms");
    }

    private void doMake(String methodName) throws Exception {
        Class<?> hookClass = Yahfa.buildHooker(LspHooker.class.getClassLoader(), getDescriptor(mReturnType), getDescriptors(mActualParameterTypes), methodName, LspHooker.class.getCanonicalName());
        if (hookClass == null) throw new IllegalStateException("Failed to hook " + methodName);
        // Execute our newly-generated code in-process.
        Method backupMethod = hookClass.getMethod(METHOD_NAME_BACKUP, mActualParameterTypes);
        mHooker = new LspHooker(mHookInfo, mMember, backupMethod);
        var hooker = hookClass.getDeclaredField(FIELD_NAME_HOOKER);
        hooker.setAccessible(true);
        hooker.set(null, mHooker);
        Method hookMethod = hookClass.getMethod(methodName, mActualParameterTypes);
        HookMain.backupAndHook(mMember, hookMethod, backupMethod);
    }

    public LspHooker getHooker() {
        return mHooker;
    }
}
