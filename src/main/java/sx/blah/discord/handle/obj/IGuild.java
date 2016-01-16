package sx.blah.discord.handle.obj;

import java.util.List;

/**
 * This class defines a guild/server/clan/whatever it's called.
 */
public interface IGuild {
    
    /**
     * Gets the user id for the owner of this guild.
     * 
     * @return The owner id.
     */
    String getOwnerID();
    
    /**
     * Gets the user object for the owner of this guild.
     * 
     * @return The owner.
     */
    IUser getOwner();
    
    /**
     * Gets the icon id for this guild.
     * 
     * @return The icon id.
     */
    String getIcon();
    
    /**
     * Gets the direct link to the guild's icon.
     * 
     * @return The icon url.
     */
    String getIconURL();
    
    /**
     * Gets all the channels on the server.
     * 
     * @return All channels on the server.
     */
    List<IChannel> getChannels();
    
    /**
     * Gets a channel on the guild by a specific channel id.
     * 
     * @param id The ID of the channel you want to find.
     * @return The channel with given ID.
     */
    IChannel getChannelByID(String id);
    
    /**
     * Gets all the users connected to the guild.
     * 
     * @return All users connected to the guild.
     */
    List<IUser> getUsers();
    
    /**
     * Gets a user by its id in the guild.
     * 
     * @param id ID of the user you want to find.
     * @return The user with given ID.
     */
    IUser getUserByID(String id);
    
    /**
     * Gets the name of the guild.
     * 
     * @return The name of the guild
     */
    String getName();
    
    /**
     * Gets the id of the guild.
     * 
     * @return The ID of this guild.
     */
    String getID();
}