package keyp.forev.fmc.common.libs;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;

public class ClassManager {
    private Class<?> clazz;
    private Class<?>[] parameterTypes;
    // private URLClassLoader urlClassLoader;
    // このクラスにアクセスするときは、
    // このクラスを継承しているEnumクラス郡を媒介にすることで、
    // このクラスを取得することができる
    // 例として、velocity/Mainクラスに記載
    public ClassManager(Class<?> clazz, Class<?>[] parameterTypes) {
        this.clazz = clazz;
        this.parameterTypes = parameterTypes;
    }

    // 複数コンストラクタで、URLClassLoaderを使う場合
    public ClassManager(URLClassLoader urlClassLoaderBase) {
        this(null, null);
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public Constructor<?> getConstructor() {
        try {
            return clazz.getConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object createInstance(Object... initargs) {
        try {
            Constructor<?> constructor = getConstructor();
            if (constructor != null) {
                return constructor.newInstance(initargs);
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }
}