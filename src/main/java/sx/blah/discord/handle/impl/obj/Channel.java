/*
 * Discord4J - Unofficial wrapper for Discord API
 * Copyright (c) 2015
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package sx.blah.discord.handle.impl.obj;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import sx.blah.discord.Discord4J;
import sx.blah.discord.api.DiscordEndpoints;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.internal.DiscordUtils;
import sx.blah.discord.handle.impl.events.MessageSendEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.json.requests.InviteRequest;
import sx.blah.discord.json.requests.MessageRequest;
import sx.blah.discord.json.responses.ExtendedInviteResponse;
import sx.blah.discord.json.responses.MessageResponse;
import sx.blah.discord.util.HTTP403Exception;
import sx.blah.discord.util.Requests;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Channel implements IChannel {
    
    /**
     * User-friendly channel name (e.g. "general")
     */
    protected String name;

    /**
     * Channel ID.
     */
    protected final String id;

    /**
     * Messages that have been sent into this channel
     */
    protected final List<IMessage> messages;

    /**
     * Indicates whether or not this channel is a PM channel.
     */
    protected boolean isPrivate;

    /**
     * The guild this channel belongs to.
     */
    protected final IGuild parent;
	
	/**
     * The channel's topic message.
     */
    protected String topic;
	
	/**
     * The last read message.
     */
    protected String lastReadMessageID = null;
    
    /**
     * Whether the bot should send out a typing status
     */
    protected AtomicBoolean isTyping = new AtomicBoolean(false);
    
    /**
     * Keeps track of the time to handle repeated typing status broadcasts
     */
    protected AtomicLong typingTimer = new AtomicLong(0);
    
    /**
     * 5 seconds, the time it takes for one typing status to "wear off"
     */
    protected static final long TIME_FOR_TYPE_STATUS = 5000;
    
	/**
     * The client that created this object.
     */
    protected final IDiscordClient client;

    public Channel(IDiscordClient client, String name, String id, IGuild parent, String topic) {
        this(client, name, id, parent, topic, new ArrayList<>());
    }

    public Channel(IDiscordClient client, String name, String id, IGuild parent, String topic, List<IMessage> messages) {
        this.client = client;       
        this.name = name;
        this.id = id;
        this.messages = messages;
        this.parent = parent;
        this.isPrivate = false;
        this.topic = topic;
    }
	
	@Override
    public String getName() {
        return name;
    }
	
	@Override
    public String getID() {
        return id;
    }
	
	@Override
    public List<IMessage> getMessages() {
        return messages;
    }
	
	@Override
    public IMessage getMessageByID(String messageID) {
		for (IMessage message : messages) {
			if (message.getID().equalsIgnoreCase(messageID))
				return message;
		}

		return null;
	}
	
	/**
     * CACHES a message to the channel.
     * 
     * @param message The message.
     */
    public void addMessage(IMessage message) {
        if (message.getChannel().getID().equalsIgnoreCase(this.getID())) {
            messages.add(message);
            if (lastReadMessageID == null)
                lastReadMessageID = message.getID();
        }
    }
	
	@Override
    @Deprecated
    public IGuild getParent() {
        return parent;
    }
    
    @Override
    public IGuild getGuild() {
        return parent;
    }
	
	@Override
    public boolean isPrivate() {
        return isPrivate;
    }
	
	@Override
    public String getTopic() {
        return topic;
    }
	
	/**
     * Sets the CACHED topic for the channel.
     * 
     * @param topic The new channel topic
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }
	
	@Override
    public String mention() {
        return "<#" + this.getID() + ">";
    }
    
    @Override
    public IMessage sendMessage(String content) {
        return sendMessage(content, false);
    }
    
    @Override
    public IMessage sendMessage(String content, boolean tts) {
        if (client.isReady()) {
//            content = DiscordUtils.escapeString(content);
        
            try {
                MessageResponse response = DiscordUtils.GSON.fromJson(Requests.POST.makeRequest(DiscordEndpoints.CHANNELS + id + "/messages",
                        new StringEntity(DiscordUtils.GSON.toJson(new MessageRequest(content, new String[0], tts)), "UTF-8"),
                        new BasicNameValuePair("authorization", client.getToken()),
                        new BasicNameValuePair("content-type", "application/json")), MessageResponse.class);
            
                String time = response.timestamp;
                String messageID = response.id;
            
                IMessage message = new Message(client, messageID, content, client.getOurUser(), this, 
                        DiscordUtils.convertFromTimestamp(time), DiscordUtils.mentionsFromJSON(client, response),
                        DiscordUtils.attachmentsFromJSON(response));
                addMessage(message); //Had to be moved here so that if a message is edited before the MESSAGE_CREATE event, it doesn't error
                client.getDispatcher().dispatch(new MessageSendEvent(message));
                return message;
            } catch (HTTP403Exception e) {
                Discord4J.LOGGER.error("Received 403 error attempting to send message; is your login correct?");
                return null;
            }
        
        } else {
            Discord4J.LOGGER.error("Bot has not signed in yet!");
            return null;
        }
    }
	
	@Override
    public IMessage sendFile(File file) throws HTTP403Exception, IOException {
        if (client.isReady()) {
            //These next two lines of code took WAAAAAY too long to figure out than I care to admit
            HttpEntity fileEntity = MultipartEntityBuilder.create().addBinaryBody("file", file, 
                    ContentType.create(Files.probeContentType(file.toPath())), file.getName()).build();
            MessageResponse response = DiscordUtils.GSON.fromJson(Requests.POST.makeRequest(
                    DiscordEndpoints.CHANNELS + id + "/messages",
                    fileEntity, new BasicNameValuePair("authorization", client.getToken())), MessageResponse.class);
            IMessage message = new Message(client, response.id, response.content, client.getOurUser(), this,
                    DiscordUtils.convertFromTimestamp(response.timestamp), DiscordUtils.mentionsFromJSON(client, response),
                    DiscordUtils.attachmentsFromJSON(response));
            addMessage(message);
            client.getDispatcher().dispatch(new MessageSendEvent(message));
            return message;
        } else {
            Discord4J.LOGGER.error("Bot has not signed in yet!");
            return null;
        }
    }
    
    @Override
    public IInvite createInvite(int maxAge, int maxUses, boolean temporary, boolean useXkcdPass) {
        try {
            ExtendedInviteResponse response = DiscordUtils.GSON.fromJson(Requests.POST.makeRequest(DiscordEndpoints.CHANNELS + getID() + "/invites",
					new StringEntity(DiscordUtils.GSON.toJson(new InviteRequest(maxAge, maxUses, temporary, useXkcdPass))),
					new BasicNameValuePair("authorization", client.getToken()),
                    new BasicNameValuePair("content-type", "application/json")), ExtendedInviteResponse.class);
            
            return DiscordUtils.getInviteFromJSON(client, response);
        } catch (HTTP403Exception | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    @Override
    public synchronized void toggleTypingStatus() {
        isTyping.set(!isTyping.get());
        
        if (isTyping.get()) {
            typingTimer.set(System.currentTimeMillis()-TIME_FOR_TYPE_STATUS);
            new Thread(() -> {
                while (isTyping.get()) {
                    if (typingTimer.get() <= System.currentTimeMillis()-TIME_FOR_TYPE_STATUS) {
                        typingTimer.set(System.currentTimeMillis());
                        try {
                            Requests.POST.makeRequest(DiscordEndpoints.CHANNELS+getID()+"/typing",
                                    new BasicNameValuePair("authorization", client.getToken()));
                        } catch (HTTP403Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }
    }
    
    @Override
    public synchronized boolean getTypingStatus() {
        return isTyping.get();
    }
	
	@Override
    public String getLastReadMessageID() {
        return lastReadMessageID;
    }
	
	@Override
    public IMessage getLastReadMessage() {
        return getMessageByID(lastReadMessageID);
    }
	
	/**
     * Sets the CACHED last read message id.
     * 
     * @param lastReadMessageID The message id.
     */
    public void setLastReadMessageID(String lastReadMessageID) {
        this.lastReadMessageID = lastReadMessageID;
    }
    
    @Override 
    public String toString() {
        return mention();
    }
    
    @Override
    public boolean equals(Object other) {
        return this.getClass().isAssignableFrom(other.getClass()) && ((IChannel) other).getID().equals(getID());
    }
}