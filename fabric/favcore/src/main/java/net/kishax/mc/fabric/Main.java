package net.kishax.mc.fabric;

import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kishax.mc.common.server.DoServerOffline;
import net.kishax.mc.common.server.ServerStatusCache;
import net.kishax.mc.common.util.PlayerUtils;
import net.kishax.mc.fabric.module.Module;
import net.kishax.mc.fabric.server.AutoShutdown;
import net.kishax.mc.fabric.server.FabricLuckperms;
import net.kishax.mc.fabric.server.cmd.main.Command;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

public class Main implements ModInitializer {
  public static AtomicBoolean isHub = new AtomicBoolean(false);
  private static Injector injector = null;
  private final FabricLoader fabric;
  private final Logger logger = LoggerFactory.getLogger("Kishax");

  public Main() {
    this.fabric = FabricLoader.getInstance();
  }

  @Override
  public void onInitialize() {
    logger.info("detected fabric platform.");
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
      new Command(logger).registerCommand(dispatcher, registryAccess, environment);
    });
    ServerLifecycleEvents.SERVER_STARTED.register(server -> {
      injector = Guice.createInjector(new Module(fabric, logger, server));
      getInjector().getInstance(AutoShutdown.class).start();
      try {
        LuckPerms luckperms = LuckPermsProvider.get();
        getInjector().getInstance(FabricLuckperms.class).triggerNetworkSync();
        logger.info("linking with LuckPerms...");
        logger.info(luckperms.getPlatform().toString());
      } catch (Exception e) {
        logger.error("Error linking with LuckPerms", e);
      }
      getInjector().getInstance(PlayerUtils.class).loadPlayers();
      getInjector().getInstance(ServerStatusCache.class).serverStatusCache();
      logger.info("mod has been enabled.");
    });
    ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
      getInjector().getInstance(DoServerOffline.class).updateDatabase();
      getInjector().getInstance(AutoShutdown.class).stop();
    });
  }

  public static synchronized Injector getInjector() {
    if (Objects.isNull(injector)) {
      throw new IllegalStateException("Injector has not been initialized yet.");
    }
    return injector;
  }
}
