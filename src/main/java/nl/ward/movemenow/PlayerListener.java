package nl.ward.movemenow;

import net.md_5.bungee.api.AbstractReconnectHandler;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.logging.Level;

public class PlayerListener implements Listener {
    MoveMeNow plugin;

    public PlayerListener(MoveMeNow plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerKickEvent(ServerKickEvent ev) {
        ServerInfo kickedFrom;

        if (ev.getPlayer().getServer() != null) {
            kickedFrom = ev.getPlayer().getServer().getInfo();
        } else if (this.plugin.getProxy().getReconnectHandler() != null) {
            // If first server and ReconnectHandler
            kickedFrom = this.plugin.getProxy().getReconnectHandler().getServer(ev.getPlayer());
        } else {
            // If first server and no ReconnectHandler
            kickedFrom = AbstractReconnectHandler.getForcedHost(ev.getPlayer().getPendingConnection());

            // Can still be null if vhost is null
            if (kickedFrom == null) {
                kickedFrom = ProxyServer.getInstance().getServerInfo(ev.getPlayer().getPendingConnection().getListener().getDefaultServer());
            }
        }

        if (kickedFrom == null) return;

        List<String> moveToList = this.plugin.getConfig().getStringList("servers");

        // Avoid a recursive move loop
        if (moveToList.contains(kickedFrom.getName())) {
            return;
        }

        String kickReason = BaseComponent.toLegacyText(ev.getKickReasonComponent());

        // Check if kick message contains an exemption. If this is the case, don't move to one of the fallback servers.
        List<String> exemptionList = this.plugin.getConfig().getStringList("exemptions");
        for(String exemption : exemptionList) {
            if(kickReason.toLowerCase().contains(exemption.toLowerCase())) {
                return;
            }
        }

        for(String serverName : moveToList) {
            ServerInfo serverInfo = this.plugin.getProxy().getServerInfo(serverName);
            InetSocketAddress serverSocketAddress = (InetSocketAddress) serverInfo.getSocketAddress();

            String serverIp;
            int serverPort;
            boolean available = false;

            try {
                serverIp = serverSocketAddress.getHostString();
                serverPort = serverSocketAddress.getPort();
                available = isServerAvailable(serverIp, serverPort);
            } catch (Exception ignored) {}

            if (available) {
                ev.setCancelled(true);
                ev.setCancelServer(serverInfo);

                String[] moveMsg = plugin.getConfig().getString("message")
                        .replace("%old_server%", kickedFrom.getName())
                        .replace("%new_server%", serverName)
                        .replace("%kick_reason%", kickReason)
                        .split("\n");

                if (!(moveMsg.length == 1 && moveMsg[0].equals(""))) {
                    for (String line : moveMsg) {
                        ev.getPlayer().sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', line)));
                    }
                }

                this.plugin.getLogger().log(Level.INFO, String.format("Player %s was moved from %s to fallback server %s because '%s'", ev.getPlayer().getName(), kickedFrom.getName(), serverName, kickReason));
                break;
            } else this.plugin.getLogger().log(Level.INFO, String.format("Server %s is not available.", serverName));
        }

        this.plugin.getLogger().log(Level.WARNING, String.format("Player %s couldn't be moved from %s to any fallback server since there were none available.", ev.getPlayer().getName(), kickedFrom.getName()));
    }

    private boolean isServerAvailable(String ip, int port) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(ip, port), 1);
            s.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
