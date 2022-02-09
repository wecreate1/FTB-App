package net.creeperhost.creeperlauncher.api;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.creeperhost.creeperlauncher.CreeperLauncher;
import net.creeperhost.creeperlauncher.api.data.*;
import net.creeperhost.creeperlauncher.api.data.friends.AddFriendData;
import net.creeperhost.creeperlauncher.api.data.friends.BlockFriendData;
import net.creeperhost.creeperlauncher.api.data.friends.GetFriendsData;
import net.creeperhost.creeperlauncher.api.data.instances.*;
import net.creeperhost.creeperlauncher.api.data.irc.*;
import net.creeperhost.creeperlauncher.api.data.other.*;
import net.creeperhost.creeperlauncher.api.handlers.profiles.*;
import net.creeperhost.creeperlauncher.api.handlers.*;
import net.creeperhost.creeperlauncher.api.handlers.friends.AddFriendHandler;
import net.creeperhost.creeperlauncher.api.handlers.friends.BlockFriendHandler;
import net.creeperhost.creeperlauncher.api.handlers.friends.GetFriendsHandler;
import net.creeperhost.creeperlauncher.api.handlers.instances.*;
import net.creeperhost.creeperlauncher.api.handlers.irc.*;
import net.creeperhost.creeperlauncher.api.handlers.other.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WebSocketMessengerHandler
{
    private static final Logger LOGGER = LogManager.getLogger();

//    private static Map<Class<? extends BaseData>, IMessageHandler<? extends BaseData>> handlers = new HashMap<>();
    private static final Map<String, Pair<Class<? extends BaseData>, IMessageHandler<? extends BaseData>>> register = new HashMap<>();
    static Gson gson = new Gson();

    static {
        register("installedInstances", InstalledInstancesData.class, new InstalledInstancesHandler());
        register("launchInstance", LaunchInstanceData.class, new LaunchInstanceHandler());
        register("installInstance", InstallInstanceData.class, new InstallInstanceHandler());
        register("cancelInstallInstance", CancelInstallInstanceData.class, new CancelInstallInstanceHandler());
        register("updateInstance", UpdateInstanceData.class, new UpdateInstanceHandler());
        register("uninstallInstance", UninstallInstanceData.class, new UninstallInstanceHandler());
        register("instanceConfigure", InstanceConfigureData.class, new InstanceConfigureHandler());
        register("instanceModToggle", InstanceModToggleData.class, new InstanceModToggleHandler());
        register("instanceBrowse", BrowseInstanceData.class, new BrowseInstanceHandler());
        register("getSettings", SettingsInfoData.class, new SettingsInfoHandler());
        register("saveSettings", SettingsConfigureData.class, new SettingsConfigureHandler());
        register("modalCallback", OpenModalData.ModalCallbackData.class, new ModalCallbackHandler());
        register("fileHash", FileHashData.class, new FileHashHandler());
        register("storeAuthDetails", StoreAuthDetailsData.class, new StoreAuthDetailsHandler());
        register("syncInstance", SyncInstanceData.class, new SyncInstanceHandler());
        register("ircConnect", IRCConnectData.class, new IRCConnectHandler());
        register("ircSendMessage", IRCSendMessageData.class, new IRCSendMessageHandler());
        register("ircQuitRequest", IRCQuitRequestData.class, new IRCQuitRequestHandler());
        register("uploadLogs", UploadLogsData.class, new UploadLogsHandler());
        register("getJavas", GetJavasData.class, new GetJavasHandler());
        register("getFriends", GetFriendsData.class, new GetFriendsHandler());
        register("blockFriend", BlockFriendData.class, new BlockFriendHandler());
        register("addFriend", AddFriendData.class, new AddFriendHandler());
        register("instanceMods", InstanceModsData.class, new InstanceModsHandler());
        register("yeetLauncher", YeetLauncherData.class, new YeetLauncherHandler());
        register("pong", PongLauncherData.class, new PongLauncherHandler());
        register("ping", PingLauncherData.class);
        register("messageClient", MessageClientData.class, new MessageClientHandler());
        register("shareInstance", ShareInstanceData.class, new ShareInstanceHandler());
        register("instanceInstallMod", InstanceInstallModData.class, new InstanceInstallModHandler());

        register("profiles.get", BaseData.class, new GetProfilesHandler());
        register("profiles.addMs", AddMsProfileHandler.Data.class, new AddMsProfileHandler());
        register("profiles.updateMs", UpdateMsProfileHandler.Data.class, new UpdateMsProfileHandler());
        register("profiles.addMc", AddMcProfileHandler.Data.class, new AddMcProfileHandler());
        register("profiles.updateMc", UpdateMcProfileHandler.Data.class, new UpdateMcProfileHandler());
        register("profiles.remove", RemoveProfileHandler.Data.class, new RemoveProfileHandler());
        register("profiles.setActiveProfile", SetActiveProfileHandler.Data.class, new SetActiveProfileHandler());
        register("profiles.mc.authenticate", AuthenticateMcProfileHandler.Data.class, new AuthenticateMcProfileHandler());
        register("profiles.mc.refresh", RefreshAuthenticationMcProfileHandler.Data.class, new RefreshAuthenticationMcProfileHandler());
    }

    public static void register(String name, Class<? extends BaseData> clazz, IMessageHandler<? extends BaseData> handler) {
        register.put(name, Pair.of(clazz, handler));
    }

    public static void register(String name, Class<? extends BaseData> clazz) {
        register.put(name, Pair.of(clazz, null));
    }

    public static void handleMessage(String data)
    {
        JsonParser parser = new JsonParser();
        JsonElement parse = parser.parse(data);
        if (parse.isJsonObject()) {
            JsonObject jsonObject = parse.getAsJsonObject();
            if (jsonObject.has("type")) {
                String type = jsonObject.get("type").getAsString();
                Pair<Class<? extends BaseData>, IMessageHandler<? extends BaseData>> entry = register.get(type);
                TypeToken typeToken = TypeToken.of(entry.getLeft());
                IMessageHandler<? extends BaseData> iMessageHandler = entry.getRight();
                if (iMessageHandler != null) {
                    try {
                        BaseData parsedData = gson.fromJson(data, typeToken.getType());
                        if (CreeperLauncher.isDevMode || (parsedData.secret != null && parsedData.secret.equals(CreeperLauncher.websocketSecret))) {
                            CompletableFuture.runAsync(() -> iMessageHandler.handle(parsedData), CreeperLauncher.taskExeggutor).exceptionally((t) -> {
                                LOGGER.error("Error handling message", t);
                                return null;
                            });
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    LOGGER.error("No handler for message type '{}'", type);
                }
            }
        }
    }
}
