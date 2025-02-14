package net.creeperhost.creeperlauncher.api.handlers.instances;

import net.creeperhost.creeperlauncher.Instances;
import net.creeperhost.creeperlauncher.Settings;
import net.creeperhost.creeperlauncher.api.data.instances.UninstallInstanceData;
import net.creeperhost.creeperlauncher.api.handlers.IMessageHandler;
import net.creeperhost.creeperlauncher.pack.LocalInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UninstallInstanceHandler implements IMessageHandler<UninstallInstanceData>
{
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void handle(UninstallInstanceData data)
    {
        try
        {
            LocalInstance instance = Instances.getInstance(data.uuid);
            if (instance != null) {
                instance.uninstall();
                Settings.webSocketAPI.sendMessage(new UninstallInstanceData.Reply(data, "success", ""));
            } else {
                Settings.webSocketAPI.sendMessage(new UninstallInstanceData.Reply(data, "error", "Instance does not exist."));
            }
        } catch (Exception err)
        {
            LOGGER.error("Error uninstalling pack", err);
            Settings.webSocketAPI.sendMessage(new UninstallInstanceData.Reply(data, "error", err.toString()));
        }

    }
}
