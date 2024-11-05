package spigot;


import org.slf4j.Logger;

import com.google.inject.AbstractModule;

import common.Database;
import spigot_command.Menu;
import spigot_command.PortalsDelete;
import spigot_command.ReloadConfig;

public class Module extends AbstractModule {
	private final common.Main plugin;
	private final Logger logger;
	private final Database db;
	
	public Module(common.Main plugin, Logger logger) {
		this.plugin = plugin;
		this.logger = logger;
		String host = plugin.getConfig().getString("MySQL.Host", ""),
        	user = plugin.getConfig().getString("MySQL.User", ""),
        	password = plugin.getConfig().getString("MySQL.Password", ""),
			defaultDatabase = plugin.getConfig().getString("MySQL.Database", "");
		int port = plugin.getConfig().getInt("MySQL.Port", 0);
		this.db = new Database(logger, host, user, defaultDatabase, password, port);
    }
	
	@Override
    protected void configure() {
		bind(Database.class).toInstance(db);
		bind(org.slf4j.Logger.class).toInstance(logger);
		bind(common.Main.class).toInstance(plugin);
		bind(SocketSwitch.class);
		//bind(Database.class).in(com.google.inject.Scopes.SINGLETON);
		bind(ServerHomeDir.class);
		bind(DoServerOnline.class);
		bind(DoServerOffline.class);
		bind(PortalsConfig.class).in(com.google.inject.Scopes.SINGLETON);
		bind(Menu.class);
		bind(PortalsDelete.class);
		bind(EventListener.class);
		bind(WandListener.class);
		bind(ReloadConfig.class);
		bind(ServerStatusCache.class).in(com.google.inject.Scopes.SINGLETON);
		bind(PortFinder.class);
		bind(PlayerUtils.class);
		bind(AutoShutdown.class);
		bind(Rcon.class);
		bind(Luckperms.class);
		bind(ImageMap.class);
    }
}
