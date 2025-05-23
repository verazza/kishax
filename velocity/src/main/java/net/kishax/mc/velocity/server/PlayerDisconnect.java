package net.kishax.mc.velocity.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kishax.mc.common.database.Database;
import net.kishax.mc.velocity.discord.MessageEditor;
import net.kishax.mc.velocity.util.RomaToKanji;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

public class PlayerDisconnect {
  private final ProxyServer server;
  private final Logger logger;
  private final Database db;
  private final ConsoleCommandSource console;
  private final MessageEditor discordME;

  @Inject
  public PlayerDisconnect(Logger logger, ProxyServer server, Database db, BroadCast bc, ConsoleCommandSource console,
      RomaToKanji conv, MessageEditor discordME) {
    this.logger = logger;
    this.server = server;
    this.db = db;
    this.console = console;
    this.discordME = discordME;
  }

  public void menteDisconnect(List<String> UUIDs) {
    for (Player player : server.getAllPlayers()) {
      // if(!player.hasPermission("group.super-admin"))
      if (!UUIDs.contains(player.getUniqueId().toString())) {
        playerDisconnect(
            false,
            player,
            Component.text("現在メンテナンス中です。").color(NamedTextColor.BLUE));
      } else {
        Component menteLeaveMessage = Component.text("スーパーアドミン認証...PASS")
            .appendNewline()
            .appendNewline()
            .append(Component.text("ALL CORRECT"))
            .appendNewline()
            .appendNewline()
            .append(Component.text("メンテナンスモードが有効になりました。"))
            .append(Component.text("スーパーアドミン以外を退出させました。"))
            .color(NamedTextColor.GREEN);

        player.sendMessage(menteLeaveMessage);
      }
    }

    Component consoleMessage = Component.text("メンテナンスモードが有効になりました。")
        .appendNewline()
        .append(Component.text("スーパーアドミン以外を退出させました。"))
        .color(NamedTextColor.GREEN);

    console.sendMessage(consoleMessage);
  }

  public void playerDisconnect(Boolean bool, Player player, TextComponent component) {
    player.disconnect(component);
    if (!(bool))
      return;
    String query = "UPDATE members SET ban=? WHERE uuid=?;";
    try (Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(query)) {
      ps.setBoolean(1, true);
      ps.setString(2, player.getUniqueId().toString());
      int rsAffected = ps.executeUpdate();
      if (rsAffected > 0) {
        try {
          discordME.AddEmbedSomeMessage("Invader", player);
        } catch (Exception e) {
          logger.error("An exception occurred while executing the AddEmbedSomeMessage method: {}", e.getMessage());
          for (StackTraceElement ste : e.getStackTrace()) {
            logger.error(ste.toString());
          }
        }
      }
    } catch (SQLException | ClassNotFoundException e) {
      // スタックトレースをログに出力
      logger.error("An onChat error occurred: " + e.getMessage());
      for (StackTraceElement element : e.getStackTrace()) {
        logger.error(element.toString());
      }
    }
  }
}
