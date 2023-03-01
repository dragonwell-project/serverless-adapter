package com.alibaba.jvm.util;

import java.lang.reflect.Method;

public class DragonwellUtils {

    public static Method registerClassLoader;
    public static Method calculateSignatureForName;
    static {
        Class<?> klazz;
        try {
            klazz = Class.forName("com.alibaba.util.Utils");
            registerClassLoader = klazz.getDeclaredMethod("registerClassLoader", ClassLoader.class, String.class);
            calculateSignatureForName = klazz.getDeclaredMethod("calculateSignatureForName", String.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new Error("[Fatal] Cannot find com.alibaba.util.Utils.registerClassLoader(ClassLoader, String) | com.alibaba.util.Utils.calculateSignatureForName(String)?");
        }
    }

}
