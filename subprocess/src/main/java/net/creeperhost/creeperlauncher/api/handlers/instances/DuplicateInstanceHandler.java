package net.creeperhost.creeperlauncher.api.handlers.instances;

import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.WebSocketAPI;
import net.creeperhost.creeperlauncher.api.data.BaseData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.pack.LocalInstance;

import java.io.IOException;
import java.util.UUID;

public class DuplicateInstanceHandler implements IMessageHandler<DuplicateInstanceHandler.Request> {
    @Override
    public void handle(Request data) {
        LocalInstance instance = Instances.getInstance(UUID.fromString(data.uuid));
        if (instance == null) {
            Settings.webSocketAPI.sendMessage(new Reply(data, false, "Unable to locate instance", "-1"));
            return;
        }

        try {
            LocalInstance newInstance = instance.duplicate(data.newName);
            if (newInstance == null) {
                Settings.webSocketAPI.sendMessage(new Reply(data, false, "Failed to duplicate instance...", "-1"));
                return;
            }

            Instances.refreshInstances();
            Settings.webSocketAPI.sendMessage(new Reply(data, true, "Duplicated instance!", newInstance.getUuid().toString()));
        } catch (IOException e) {
            WebSocketAPI.LOGGER.error("Unable to duplicate instance because of", e);
            Settings.webSocketAPI.sendMessage(new Reply(data, false, "Failed to duplicate instance...", "-1"));
        }
    }
    
    public static class Request extends BaseData {
        public String uuid;
        public String newName;
    }
    
    private static class Reply extends Request {
        public String message;
        public boolean success;

        public Reply(Request data, boolean success, String message, String uuid) {
            this.requestId = data.requestId;
            this.type = data.type + "Reply";
            this.uuid = uuid;
            this.message = message;
            this.success = success;
        }
    } 
}

