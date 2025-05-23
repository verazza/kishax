package net.kishax.mc.velocity.server.cmd.sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kishax.mc.common.server.Luckperms;
import net.kishax.mc.common.settings.PermSettings;
import net.kishax.mc.velocity.Main;
import net.kishax.mc.velocity.discord.MessageEditor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class CEnd implements SimpleCommand {
  private final Main plugin;
  private final ProxyServer server;
  private final Logger logger;
  private final MessageEditor discordME;
  private final Luckperms lp;
  private final String[] subcommands = {"cend"};

  @Inject
  public CEnd (Main plugin, ProxyServer server, Logger logger, MessageEditor discordME, Luckperms lp) {
    this.plugin = plugin;
    this.server = server;
    this.logger = logger;
    this.discordME = discordME;
    this.lp = lp;
  }

  @Override
  public void execute(Invocation invocation) {
    CommandSource source = invocation.source();
    if (source instanceof Player player) {
      if (!lp.hasPermission(player.getUsername(), PermSettings.CEND.get())) {
        source.sendMessage(Component.text("権限がありません。").color(NamedTextColor.RED));
        return;
      }
    }

    Main.isVelocity = false; //フラグをfalseに

    // 非同期処理を実行
    //discordME.AddEmbedSomeMessage("End");
    CompletableFuture<Void> addEmbedFuture = CompletableFuture.runAsync(() -> {
      try {
        discordME.AddEmbedSomeMessage("End");
      } catch (Exception e) {
        logger.error("An exception occurred while executing the AddEmbedSomeMessage method: {}", e.getMessage());
        for (StackTraceElement ste : e.getStackTrace()) {
          logger.error(ste.toString());
        }
      }
    });

    // 両方の非同期処理が完了した後にシャットダウンを実行
    CompletableFuture<Void> allTasks = CompletableFuture.allOf(addEmbedFuture);

    allTasks.thenRun(() -> {
      server.getScheduler().buildTask(plugin, () -> {
        //discord.logoutDiscordBot();
        //server.shutdown();
        logger.info("discordME.AddEmbedSomeMessageメソッドが終了しました。");
      }).schedule(); // タスクをスケジュールしてシャットダウンを行う
    });
  }

  @Override
  public List<String> suggest(Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();
    List<String> ret = new ArrayList<>();
    switch (args.length) {
      case 0, 1 -> {
        for (String subcmd : subcommands) {
          if (!source.hasPermission("kishax.proxy." + subcmd)) continue;
          ret.add(subcmd);
        }
        return ret;
      }
    }
    return Collections.emptyList();
  }
}
