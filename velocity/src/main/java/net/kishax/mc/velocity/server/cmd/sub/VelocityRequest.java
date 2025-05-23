package net.kishax.mc.velocity.server.cmd.sub;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kishax.mc.common.database.Database;
import net.kishax.mc.common.server.Luckperms;
import net.kishax.mc.common.util.PlayerUtils;
import net.kishax.mc.velocity.discord.Discord;
import net.kishax.mc.velocity.discord.EmojiManager;
import net.kishax.mc.velocity.discord.MessageEditor;
import net.kishax.mc.velocity.server.BroadCast;
import net.kishax.mc.velocity.server.DoServerOnline;
import net.kishax.mc.velocity.server.PlayerDisconnect;
import net.kishax.mc.velocity.server.cmd.sub.interfaces.Request;
import net.kishax.mc.velocity.util.config.VelocityConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

public class VelocityRequest implements Request {
  public static Map<String, Boolean> PlayerReqFlags = new HashMap<>();
  private final ProxyServer server;
  private final VelocityConfig config;
  private final Logger logger;
  private final Database db;
  private final BroadCast bc;
  private final Discord discord;
  private final MessageEditor discordME;
  private final EmojiManager emoji;
  private final Luckperms lp;
  private final PlayerUtils pu;
  private final DoServerOnline dso;
  private final PlayerDisconnect pd;
  private String currentServerName = null;

  @Inject
  public VelocityRequest(ProxyServer server, Logger logger, VelocityConfig config, Database db, BroadCast bc,
      Discord discord, MessageEditor discordME, EmojiManager emoji, Luckperms lp, PlayerUtils pu, DoServerOnline dso,
      PlayerDisconnect pd) {
    this.server = server;
    this.logger = logger;
    this.config = config;
    this.db = db;
    this.bc = bc;
    this.discord = discord;
    this.discordME = discordME;
    this.emoji = emoji;
    this.lp = lp;
    this.pu = pu;
    this.dso = dso;
    this.pd = pd;
  }

  @Override
  public void execute(@NotNull CommandSource source, String[] args) {
    if (args.length < 2) {
      source.sendMessage(Component.text("引数が足りません。").color(NamedTextColor.RED));
      return;
    }
    String targetServerName = args[1];
    if (!(source instanceof Player)) {
      source.sendMessage(Component.text("このコマンドはプレイヤーのみが実行できます。").color(NamedTextColor.RED));
      return;
    }
    Player player = (Player) source;
    String playerName = player.getUsername(),
        playerUUID = player.getUniqueId().toString();
    if (args.length == 1 || targetServerName == null || targetServerName.isEmpty()) {
      player.sendMessage(Component.text("サーバー名を入力してください。").color(NamedTextColor.RED));
      return;
    }
    player.getCurrentServer().ifPresent(serverConnection -> {
      RegisteredServer registeredServer = serverConnection.getServer();
      currentServerName = registeredServer.getServerInfo().getName();
    });
    boolean containsServer = server.getAllServers().stream()
        .anyMatch(registeredServer -> registeredServer.getServerInfo().getName().equalsIgnoreCase(targetServerName));
    if (!containsServer) {
      player.sendMessage(Component.text("サーバー名が違います。").color(NamedTextColor.RED));
      return;
    }
    int permLevel = lp.getPermLevel(playerName);
    if (permLevel < 1) {
      player.sendMessage(Component.text("権限がありません。").color(NamedTextColor.RED));
      return;
    }
    String query = "SELECT * FROM members WHERE uuid=?;";
    try (Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(query)) {
      ps.setString(1, player.getUniqueId().toString());
      try (ResultSet minecrafts = ps.executeQuery()) {
        if (minecrafts.next()) {
          Timestamp beforeReqTime = minecrafts.getTimestamp("req");
          if (beforeReqTime != null) {
            long now_timestamp = Instant.now().getEpochSecond();
            long req_timestamp = beforeReqTime.getTime() / 1000L;
            long req_sa = now_timestamp - req_timestamp;
            long req_sa_minute = req_sa / 60;
            if (req_sa_minute <= config.getInt("Interval.Request", 0)) {
              player.sendMessage(Component.text("リクエストは" + config.getInt("Interval.Request", 0) + "分に1回までです。")
                  .color(NamedTextColor.RED));
              return;
            }
          }
        }
      }
      Map<String, Map<String, Object>> statusMap = dso.loadStatusTable(conn);
      statusMap.entrySet().stream()
          .filter(entry -> entry.getKey() instanceof String && entry.getKey().equals(targetServerName))
          .forEach(entry -> {
            Map<String, Object> serverInfo = entry.getValue();
            if (serverInfo.get("online") instanceof Boolean online && online) {
              player.sendMessage(Component.text(targetServerName + "サーバーは起動中です。").color(NamedTextColor.RED));
              logger.info(targetServerName + "サーバーは起動中です。");
              return;
            }
            if (serverInfo.get("exec") instanceof String) {
              if (permLevel < 3 && serverInfo.get("enter") instanceof Boolean enter && !enter) {
                player.sendMessage(Component.text("許可されていません。").color(NamedTextColor.RED));
                return;
              }
              if (serverInfo.get("memory") instanceof Integer memory) {
                int currentUsedMemory = dso.getCurrentUsedMemory(statusMap),
                    maxMemory = config.getInt("MaxMemory", 0);
                int futureMemory = currentUsedMemory + memory;
                if (maxMemory < futureMemory) {
                  String message = "メモリ超過のため、サーバーを起動できません。(" + futureMemory + "GB/" + maxMemory + "GB)";
                  player.sendMessage(Component.text(message).color(NamedTextColor.RED));
                  logger.info(message);
                  return;
                }
                String query2 = "UPDATE members SET req=CURRENT_TIMESTAMP WHERE uuid=?;";
                try (PreparedStatement ps2 = conn.prepareStatement(query2)) {
                  ps2.setString(1, player.getUniqueId().toString());
                  int rsAffected2 = ps2.executeUpdate();
                  if (rsAffected2 > 0) {
                    VelocityRequest.PlayerReqFlags.put(player.getUniqueId().toString(), true); // フラグを設定
                    try {
                      emoji.createOrgetEmojiId(playerName).thenApply(success -> {
                        if (success != null && !success.isEmpty()) {
                          String playerEmoji = emoji.getEmojiString(playerName, success);
                          try {
                            String randomUUID = UUID.randomUUID().toString();
                            try (Connection conn0 = db.getConnection()) {
                              db.insertLog(conn,
                                  "INSERT INTO requests (name, uuid, requuid, server) VALUES (?, ?, ?, ?);",
                                  new Object[] { playerName, playerUUID, randomUUID, targetServerName });
                            }

                            discord.sendRequestButtonWithMessage(playerEmoji + playerName + "が" + targetServerName
                                + "サーバーの起動リクエストを送信しました。\n起動しますか？(reqId: " + randomUUID + ")\n(管理者のみ実行可能です。)");
                          } catch (Exception e) {
                            logger.error("An error occurred at VelocityRequest#execute: {}", e);
                            return false;
                          }
                          Component message = Component.text("送信されました。")
                              .appendNewline()
                              .append(Component.text("管理者が3分以内に対応しますのでしばらくお待ちくださいませ。"))
                              .color(NamedTextColor.GREEN);

                          player.sendMessage(message);

                          TextComponent notifyComponent = Component.text()
                              .append(Component.text(playerName + "が" + targetServerName + "サーバーの起動リクエストを送信しました。")
                                  .color(NamedTextColor.AQUA))
                              .build();

                          bc.sendExceptPlayerMessage(notifyComponent, playerName);
                          try {
                            discordME.AddEmbedSomeMessage("Request", player, targetServerName);
                          } catch (Exception e) {
                            logger.error("An exception occurred while executing the AddEmbedSomeMessage method: {}",
                                e.getMessage());
                            for (StackTraceElement ste : e.getStackTrace()) {
                              logger.error(ste.toString());
                            }
                          }
                          try (Connection connection = db.getConnection()) {
                            db.insertLog(connection,
                                "INSERT INTO log (name,uuid,server,req,reqserver) VALUES (?,?,?,?,?);",
                                new Object[] { playerName, playerUUID, currentServerName, true, targetServerName });
                          } catch (SQLException | ClassNotFoundException e) {
                            logger.error("A SQLException | ClassNotFoundException error occurred: {}", e.getMessage());
                            for (StackTraceElement element : e.getStackTrace()) {
                              logger.error(element.toString());
                            }
                          }
                          return true;
                        } else {
                          return false;
                        }
                      }).thenAccept(result -> {
                        if (result) {
                          logger.info(playerName + "が" + targetServerName + "サーバーの起動リクエストを送信しました。");
                        } else {
                          logger.error("Start Error: Emoji is null or empty.");
                        }
                      }).exceptionally(ex -> {
                        logger.error("Start Error: " + ex.getMessage());
                        return null;
                      });
                    } catch (Exception e) {
                      logger.error("An exception occurred while executing the createOrgetEmojiId method: {}",
                          e.getMessage());
                      for (StackTraceElement ste : e.getStackTrace()) {
                        logger.error(ste.toString());
                      }
                    }
                  }
                } catch (SQLException e) {
                  logger.error("A SQLException error occurred: " + e.getMessage());
                  for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                  }
                }
              } else {
                player.sendMessage(Component.text("メモリが設定されていないため、サーバーを起動できません。").color(NamedTextColor.RED));
              }
            } else {
              player.sendMessage(Component.text("実行パスが設定されていないため、サーバーを起動できません。").color(NamedTextColor.RED));
            }
          });
    } catch (SQLException | ClassNotFoundException e) {
      logger.error("A SQLException | ClassNotFoundException error occurred: " + e.getMessage());
      for (StackTraceElement element : e.getStackTrace()) {
        logger.error(element.toString());
      }
    }
  }

  @Override
  public void execute2(Player player, String targetServerName) {
    String playerName = player.getUsername(),
        playerUUID = player.getUniqueId().toString();
    int permLevel = lp.getPermLevel(playerName);
    String query = "SELECT * FROM members WHERE uuid=?;";
    try (Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(query)) {
      ps.setString(1, player.getUniqueId().toString());
      try (ResultSet minecrafts = ps.executeQuery()) {
        if (minecrafts.next()) {
          Timestamp beforeReqTime = minecrafts.getTimestamp("req");
          if (beforeReqTime != null) {
            long now_timestamp = Instant.now().getEpochSecond();
            long req_timestamp = beforeReqTime.getTime() / 1000L;
            long req_sa = now_timestamp - req_timestamp;
            long req_sa_minute = req_sa / 60;
            if (req_sa_minute <= config.getInt("Interval.Request", 0)) {
              pd.playerDisconnect(
                  false,
                  player,
                  Component.text("リクエストは" + config.getInt("Interval.Request", 0) + "分に1回までです。")
                      .color(NamedTextColor.RED));
              return;
            }
          }
        }
      }
      Map<String, Map<String, Object>> statusMap = dso.loadStatusTable(conn);
      statusMap.entrySet().stream()
          .filter(entry -> entry.getKey() instanceof String && entry.getKey().equals(targetServerName))
          .forEach(entry -> {
            Map<String, Object> serverInfo = entry.getValue();
            if (serverInfo.get("exec") instanceof String) {
              if (permLevel < 3 && serverInfo.get("enter") instanceof Boolean enter && !enter) {
                pd.playerDisconnect(
                    false,
                    player,
                    Component.text("許可されていません。").color(NamedTextColor.RED));
                return;
              }
              if (serverInfo.get("memory") instanceof Integer memory) {
                int currentUsedMemory = dso.getCurrentUsedMemory(statusMap),
                    maxMemory = config.getInt("MaxMemory", 0);
                int futureMemory = currentUsedMemory + memory;
                if (maxMemory < futureMemory) {
                  String message = "メモリ超過のため、サーバーを起動できません。(" + futureMemory + "GB/" + maxMemory + "GB)";
                  pd.playerDisconnect(
                      false,
                      player,
                      Component.text(message).color(NamedTextColor.RED));
                  logger.info(message);
                  return;
                }
                String query2 = "UPDATE members SET req=CURRENT_TIMESTAMP WHERE uuid=?;";
                try (PreparedStatement ps2 = conn.prepareStatement(query2)) {
                  ps2.setString(1, player.getUniqueId().toString());
                  int rsAffected2 = ps2.executeUpdate();
                  if (rsAffected2 > 0) {
                    VelocityRequest.PlayerReqFlags.put(player.getUniqueId().toString(), true); // フラグを設定
                    try {
                      emoji.createOrgetEmojiId(playerName).thenApply(success -> {
                        if (success != null && !success.isEmpty()) {
                          String playerEmoji = emoji.getEmojiString(playerName, success);
                          try {
                            String randomUUID = UUID.randomUUID().toString();
                            try (Connection conn0 = db.getConnection()) {
                              db.insertLog(conn,
                                  "INSERT INTO requests (name, uuid, requuid, server) VALUES (?, ?, ?, ?);",
                                  new Object[] { playerName, playerUUID, randomUUID, targetServerName });
                            }

                            discord.sendRequestButtonWithMessage(playerEmoji + playerName + "が" + targetServerName
                                + "サーバーの起動リクエストを送信しました。\n起動しますか？(reqId: " + randomUUID + ")\n(管理者のみ実行可能です。)");
                          } catch (Exception e) {
                            logger.error("An error occurred at VelocityRequest#execute: {}", e);
                            return false;
                          }

                          pd.playerDisconnect(
                              false,
                              player,
                              Component.text()
                                  .append(Component.text("送信されました。"))
                                  .appendNewline()
                                  .append(Component.text("管理者が3分以内に対応しますのでしばらくお待ちくださいませ。"))
                                  .color(NamedTextColor.GREEN)
                                  .build());

                          try {
                            discordME.AddEmbedSomeMessage("Request", player, targetServerName);
                          } catch (Exception e) {
                            logger.error("An exception occurred while executing the AddEmbedSomeMessage method: {}",
                                e.getMessage());
                            for (StackTraceElement ste : e.getStackTrace()) {
                              logger.error(ste.toString());
                            }
                          }
                          try (Connection connection = db.getConnection()) {
                            db.insertLog(connection,
                                "INSERT INTO log (name,uuid,server,req,reqserver) VALUES (?,?,?,?,?);",
                                new Object[] { playerName, playerUUID, currentServerName, true, targetServerName });
                          } catch (SQLException | ClassNotFoundException e) {
                            logger.error("A SQLException | ClassNotFoundException error occurred: {}", e.getMessage());
                            for (StackTraceElement element : e.getStackTrace()) {
                              logger.error(element.toString());
                            }
                          }
                          return true;
                        } else {
                          return false;
                        }
                      }).thenAccept(result -> {
                        if (result) {
                          logger.info(playerName + "が" + targetServerName + "サーバーの起動リクエストを送信しました。");
                        } else {
                          logger.error("Start Error: Emoji is null or empty.");
                        }
                      }).exceptionally(ex -> {
                        logger.error("Start Error: " + ex.getMessage());
                        return null;
                      });
                    } catch (Exception e) {
                      logger.error("An exception occurred while executing the createOrgetEmojiId method: {}",
                          e.getMessage());
                      for (StackTraceElement ste : e.getStackTrace()) {
                        logger.error(ste.toString());
                      }
                    }
                  }
                } catch (SQLException e) {
                  logger.error("A SQLException error occurred: " + e.getMessage());
                  for (StackTraceElement element : e.getStackTrace()) {
                    logger.error(element.toString());
                  }
                }
              } else {
                pd.playerDisconnect(
                    false,
                    player,
                    Component.text("メモリが設定されていないため、サーバーを起動できません。").color(NamedTextColor.RED));
              }
            } else {
              pd.playerDisconnect(
                  false,
                  player,
                  Component.text("実行パスが設定されていないため、サーバーを起動できません。").color(NamedTextColor.RED));
            }
          });
    } catch (SQLException | ClassNotFoundException e) {
      logger.error("A SQLException | ClassNotFoundException error occurred: " + e.getMessage());
      for (StackTraceElement element : e.getStackTrace()) {
        logger.error(element.toString());
      }
    }
  }

  @Override
  public String getExecPath(String serverName) {
    String query = "SELECT * FROM status WHERE name=?;";
    try (Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(query)) {
      ps.setString(1, serverName);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getString("exec");
        }
      }
    } catch (SQLException | ClassNotFoundException e) {
      logger.error("A SQLException | ClassNotFoundException error occurred: " + e.getMessage());
      for (StackTraceElement element : e.getStackTrace()) {
        logger.error(element.toString());
      }
    }
    return null;
  }

  @Override
  public Map<String, String> paternFinderMapForReq(String buttonMessage) {
    // 正規表現パターンを更新して、reqIdもキャプチャできるようにする
    String pattern = "<(.*?)>(.*?)が(.*?)サーバーの起動リクエストを送信しました。\\n起動しますか？\\(reqId: (.*?)\\)\\n\\(管理者のみ実行可能です。\\)";
    Pattern compiledPattern = Pattern.compile(pattern);
    Matcher matcher = compiledPattern.matcher(buttonMessage);
    Map<String, String> resultMap = new HashMap<>();

    if (matcher.find()) {
      Optional<String> reqPlayerName = Optional.ofNullable(matcher.group(2));
      Optional<String> reqServerName = Optional.ofNullable(matcher.group(3));
      Optional<String> reqId = Optional.ofNullable(matcher.group(4));

      if (reqPlayerName.isPresent() && reqServerName.isPresent() && reqId.isPresent()) {
        String reqPlayerUUID = pu.getPlayerUUIDByNameFromDB(reqPlayerName.get());
        resultMap.put("playerName", reqPlayerName.get());
        resultMap.put("serverName", reqServerName.get());
        resultMap.put("playerUUID", reqPlayerUUID);
        resultMap.put("reqId", reqId.get());
      } else {
        logger.error("必要な情報が見つかりませんでした。");
      }
    } else {
      logger.error("パターンに一致するメッセージが見つかりませんでした。");
    }

    return resultMap;
  }
}
