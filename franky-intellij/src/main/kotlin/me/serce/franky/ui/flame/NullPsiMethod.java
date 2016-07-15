package me.serce.franky.ui.flame;

import com.google.common.reflect.AbstractInvocationHandler;
import com.intellij.psi.PsiMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;


/*
Kotlin, you are drunk, go home

val NULL_PSI_METHOD = Proxy.newProxyInstance(PsiMethod::class.java.classLoader,
        arrayOf(PsiMethod::class.java), object : AbstractInvocationHandler() {
    override fun handleInvocation(p0: Any?, p1: Method?, p2: Array<out Any>?) = null
}) as PsiMethod

 */

public class NullPsiMethod {
    public static final PsiMethod NULL_PSI_METHOD = (PsiMethod) Proxy.newProxyInstance(PsiMethod.class.getClassLoader(),
            new Class<?>[]{PsiMethod.class}, new AbstractInvocationHandler() {
                @Override
                protected Object handleInvocation(Object o, Method method, Object[] objects) throws Throwable {
                    return null;
                }
            });
}
