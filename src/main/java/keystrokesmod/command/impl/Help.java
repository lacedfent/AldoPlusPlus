package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;

public class Help extends Command {
    public Help() {
        super("help");
    }

    @Override
    public void execute(CommandInput input) {
        String prefix = prefixed("");
        replyWithHeader("&7Chat commands - &dGeneral");
        reply(" &b" + prefix + "ign/" + prefix + "name &7Copy your username.");
        reply(" &b" + prefix + "ping &7Estimate your ping.");
        reply(" &b" + prefix + "friend/" + prefix + "enemy [name/clear] &7Adds as enemy/friend.");
        reply(" &b" + prefix + "track [name/clear] &7Renders esp on targets.");
        replyWithHeader("&7Chat commands - &dModules");
        reply(" &b" + prefix + "t/" + prefix + "toggle [module] &7Toggle a module.");
        reply(" &b" + prefix + "namehider [name] &7Set name hider name.");
        reply(" &b" + prefix + "binds (key) &7List module binds.");
        reply(" &b" + prefix + "prefix [char] &7Change the chat command prefix.");
        replyWithHeader("&7Chat commands - &dProfiles");
        reply(" &b" + prefix + "profiles &7List loaded profiles.");
        reply(" &b" + prefix + "profiles save (name) &7Save current settings as a profile.");
        reply(" &b" + prefix + "profiles load [name] &7Load a profile.");
        reply(" &b" + prefix + "profiles delete [name] &7Delete a profile.");
        reply(" &b" + prefix + "profiles rename [oldname] [newname] &7Rename a profile.");
    }
}
