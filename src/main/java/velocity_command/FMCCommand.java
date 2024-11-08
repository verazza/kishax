package velocity_command;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import common.Database;
import common.Luckperms;
import common.PlayerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import velocity.Config;
import velocity.Main;
import velocity.RomajiConversion;

public class FMCCommand implements SimpleCommand {
    private final Logger logger;
    private final Config config;
    private final Database db;
    private final PlayerUtils pu;
    private final Luckperms lp;
    public List<String> subcommands = new ArrayList<>(Arrays.asList("debug", "hub", "reload", "req", "start", "stop", "stp", "retry", "debug", "cancel", "perm","maintenance","conv","chat","cend"));
    public List<String> bools = new ArrayList<>(Arrays.asList("true", "false"));

    @Inject
    public FMCCommand(Logger logger, Config config, Database db, PlayerUtils pu, Luckperms lp) {
        this.logger = logger;
        this.config = config;
        this.db = db;
        this.pu = pu;
        this.lp = lp;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0 || !subcommands.contains(args[0].toLowerCase())) return;
        String subCommand = args[0];
        if (source instanceof Player player) {
            if (!lp.hasPermission(player.getUsername(),"fmc.proxy." + subCommand)) {
                source.sendMessage(Component.text("権限がありません。").color(NamedTextColor.RED));
                return;
            }
        }
        Objects.requireNonNull(source);
        switch (subCommand.toLowerCase()) {
            case "debug" -> Main.getInjector().getInstance(Debug.class).execute(source, args);
            case "start" -> Main.getInjector().getInstance(StartServer.class).execute(source, args);
            case "stop" -> Main.getInjector().getInstance(StopServer.class).execute(source, args);
            case "hub" -> Main.getInjector().getInstance(Hub.class).execute(invocation);
            case "retry" -> Main.getInjector().getInstance(Retry.class).execute(invocation);
            case "reload" -> Main.getInjector().getInstance(ReloadConfig.class).execute(source, args);
            case "stp" -> Main.getInjector().getInstance(ServerTeleport.class).execute(source, args);
            case "req" -> Main.getInjector().getInstance(RequestInterface.class).execute(source, args);
            case "perm" -> Main.getInjector().getInstance(Perm.class).execute(source, args);
            case "maintenance" -> Main.getInjector().getInstance(Maintenance.class).execute(source, args);
            case "conv" -> Main.getInjector().getInstance(SwitchRomajiConvType.class).execute(source, args);
            case "chat" -> Main.getInjector().getInstance(SwitchChatType.class).execute(source, args);
            case "cend" -> Main.getInjector().getInstance(CEnd.class).execute(invocation);
            default -> source.sendMessage(Component.text("Unknown subcommand: " + subCommand));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        List<String> ret = new ArrayList<>();
        switch (args.length) {
        	case 0, 1 -> {
                for (String subcmd : subcommands) {
                    if (!source.hasPermission("fmc.proxy." + subcmd)) continue;
                    ret.add(subcmd);
                }
                return ret;
            }
            case 2 -> {
                if (!source.hasPermission("fmc.proxy." + args[0].toLowerCase())) return Collections.emptyList();
                switch (args[0].toLowerCase()) {
                    case "start", "req" -> {
                        for (String offlineServer : getServersList(false)) {
                            ret.add(offlineServer);
                        }
                        return ret;
                    }
                    case "stop", "stp" -> {
                        for (String onlineServer : getServersList(true)) {
                            ret.add(onlineServer);
                        }
                        return ret;
                    }
                    case "perm" -> {
                        for (String args1 : Perm.args1) {
                            ret.add(args1);
                        }
                        return ret;
                    }
                    case "maintenance" -> {
                        for (String args1 : Maintenance.args1) {
                            ret.add(args1);
                        }
                        return ret;
                    }
                    case "chat" -> {
                        for (String args1 : SwitchChatType.args1) {
                            ret.add(args1);
                        }
                        return ret;
                    }
                    case "conv" -> {
                        for (String arg1 : SwitchRomajiConvType.args1) {
                            if(source.hasPermission("fmc.proxy.conv."+arg1)) {
                                ret.add(arg1);
                            }
                        }
                        for(String arg1_1 : SwitchRomajiConvType.args1_1) {
                            if(source.hasPermission("fmc.proxy.conv.*")) {
                                ret.add(arg1_1);
                            }
                        }
                        return ret;
                    }
                    default -> {
                        return Collections.emptyList();
                    }
                }
            }
            case 3 -> {
                if (!source.hasPermission("fmc.proxy." + args[0].toLowerCase())) return Collections.emptyList();
                switch (args[0].toLowerCase()) {
                    case "conv" -> {
                        switch(args[1].toLowerCase()) {
                            case "add", "remove" -> {
                                for (Map.Entry<String, String> entry : RomajiConversion.csvSets.entrySet()) {
                                    ret.add(entry.getKey());
                                }
                                return ret;
                            }
                        }
                    }
                	case "perm"-> {
                		switch (args[1].toLowerCase()) {
                            case "add", "remove" -> {
                                List<String> permS = config.getList("Permission.Short_Name");
                                for (String permS1 : permS) {
                                    ret.add(permS1);
                                }
                                return ret;
                            }
                        }
                    }
                	case "maintenance"-> {
                		switch (args[1].toLowerCase()) {
                            case "switch" -> {
                                for(String args2 : Maintenance.args2) {
                                    ret.add(args2);
                                }
                                return ret;
                            }
                            case "add" -> {
                                List<String> notAllowList = Main.getInjector().getInstance(Maintenance.class).getMenteNotAllowMembers();
                                for (String args2 : notAllowList) {
                                    ret.add(args2);
                                }
                                return ret;
                            }
                            case "remove" -> {
                                List<String> allowList = Main.getInjector().getInstance(Maintenance.class).getMenteAllowMembers();
                                for (String args2 : allowList) {
                                    ret.add(args2);
                                }
                                return ret;
                            }
                        }
                    }
                }
            }
            case 4 -> {
                if (!source.hasPermission("fmc.proxy." + args[0].toLowerCase())) return Collections.emptyList();
                switch (args[0].toLowerCase()) {
                    case "conv" -> {
                        switch (args[1].toLowerCase()) {
                            case "add"-> {
                                for (Map.Entry<String, String> entry : RomajiConversion.csvSets.entrySet()) {
                                    if(entry.getKey().equalsIgnoreCase(args[2])) {
                                        ret.add(entry.getValue());
                                    }
                                }
                                return ret;
                            }
                        }
                    }
                	case "perm"-> {
                		switch (args[1].toLowerCase()) {
                            case "add", "remove"-> {
                            	pu.loadPlayers(); // プレイヤーリストをロード
                                List<String> permS = config.getList("Permission.Short_Name");
                                if (permS.contains(args[2].toLowerCase())) {
                                    for (String player : pu.getPlayerList()) {
                                        ret.add(player);
                                    }
                                }
                                return ret;
                            }
                        }
                    }
                	case "maintenance"-> {
                		switch (args[1].toLowerCase()) {
                            case "switch"-> {
                            	switch (args[2].toLowerCase()) {
                            		case "discord"-> {
                            			for(String args3 : Maintenance.args3) {
                                    		ret.add(args3);
                                    	}
                                    	return ret;
                                    }
                            	}
                            }
                        }
                    }
                }
            }
            case 5 -> {
                if (!source.hasPermission("fmc.proxy." + args[0].toLowerCase())) return Collections.emptyList();
                switch (args[0].toLowerCase()) {
                    case "conv" -> {
                        switch (args[1].toLowerCase()) {
                            case "add"-> {
                                if(source.hasPermission("fmc.proxy.conv.*")) {
                                    for(String bool : bools) {
                                        ret.add(bool);
                                    }
                                    return ret;
                                }
                            }
                        }
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private List<String> getServersList(boolean isOnline) {
        List<String> servers = new ArrayList<>();
        String query = "SELECT * FROM status WHERE online=? AND exception!=1 AND exception2!=1;";
        try (Connection conn = db.getConnection();
            PreparedStatement ps = conn.prepareStatement(query)) {
            if (isOnline) {
                ps.setBoolean(1, true);
            } else {
                ps.setBoolean(1, false);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    servers.add(rs.getString("name"));
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            logger.error("A SQLException | ClassNotFoundException error occurred: " + e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                logger.error(element.toString());
            }
        }
        return servers;
    }
}
