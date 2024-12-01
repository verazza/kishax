package keyp.forev.fmc.velocity.libs;

import java.net.URLClassLoader;

import keyp.forev.fmc.common.libs.ClassManager;

public class VClassManager {
    public enum JDA {
        SUB_COMMAND(
            "net.dv8tion.jda.api.interactions.commands.build.SubcommandData",
            new Class<?>[]{String.class, String.class}
        ),
        TEXT_CHANNEL(
            "net.dv8tion.jda.api.entities.channel.concrete.TextChannel",
            new Class<?>[]{String.class}
        );
        private String clazzName;
        private Class<?>[] parameterTypes;
        private URLClassLoader urlClassLoader;
        JDA(String clazzName, Class<?>[] parameterTypes) {
            this.clazzName = clazzName;
            this.parameterTypes = parameterTypes;
            if (ClassManager.urlClassLoaderMap != null) {
                this.urlClassLoader = ClassManager.urlClassLoaderMap.get(VPackageManager.JDA);
            }
        }
        
        public ClassManager get() throws ClassNotFoundException {
            Class<?> clazz = Class.forName(clazzName, true, urlClassLoader);
            return new ClassManager(clazz, parameterTypes);
        }
    }
}