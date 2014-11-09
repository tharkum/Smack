/**
 *
 * Copyright 2010 Jive Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smack;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jivesoftware.smack.ConnectionConfiguration.ConnectionConfigurationBuilder;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PlainStreamElement;
import org.jivesoftware.smack.packet.TopLevelStreamElement;

/**
 * A dummy implementation of {@link XMPPConnection}, intended to be used during
 * unit tests.
 * 
 * Instances store any packets that are delivered to be send using the
 * {@link #sendPacket(Packet)} method in a blocking queue. The content of this queue
 * can be inspected using {@link #getSentPacket()}. Typically these queues are
 * used to retrieve a message that was generated by the client.
 * 
 * Packets that should be processed by the client to simulate a received stanza
 * can be delivered using the {@linkplain #processPacket(Packet)} method.
 * It invokes the registered packet interceptors and listeners.
 * 
 * @see XMPPConnection
 * @author Guenther Niess
 */
public class DummyConnection extends AbstractXMPPConnection {

    private boolean anonymous = false;
    private boolean reconnect = false;

    private String connectionID;
    private Roster roster;

    private final BlockingQueue<TopLevelStreamElement> queue = new LinkedBlockingQueue<TopLevelStreamElement>();

    public static ConnectionConfigurationBuilder<?,?> getDummyConfigurationBuilder() {
        return DummyConnectionConfiguration.builder().setServiceName("example.org").setUsernameAndPassword("dummy",
                        "dummypass");
    }

    public DummyConnection() {
        this(getDummyConfigurationBuilder().build());
    }

    public DummyConnection(ConnectionConfiguration configuration) {
        super(configuration);

        for (ConnectionCreationListener listener : XMPPConnectionRegistry.getConnectionCreationListeners()) {
            listener.connectionCreated(this);
        }
        user = config.getUsername()
                        + "@"
                        + config.getServiceName()
                        + "/"
                        + (config.getResource() != null ? config.getResource() : "Test");
    }

    @Override
    protected void connectInternal() {
        connected = true;
        connectionID = "dummy-" + new Random(new Date().getTime()).nextInt();

        if (reconnect) {
            for (ConnectionListener listener : getConnectionListeners()) {
                listener.reconnectionSuccessful();
            }
        }
    }

    @Override
    protected void shutdown() {
        user = null;
        connectionID = null;
        roster = null;
        authenticated = false;
        anonymous = false;
        
        for (ConnectionListener listener : getConnectionListeners()) {
            listener.connectionClosed();
        }
        reconnect = true;
    }

    @Override
    public String getConnectionID() {
        if (!isConnected()) {
            return null;
        }
        if (connectionID == null) {
            connectionID = "dummy-" + new Random(new Date().getTime()).nextInt();
        }
        return connectionID;
    }

    @Override
    public Roster getRoster() {
        if (isAnonymous()) {
            return null;
        }
        if (roster == null) {
            roster = new Roster(this);
        }
        return roster;
    }

    @Override
    public boolean isAnonymous() {
        return anonymous;
    }

    @Override
    public boolean isSecureConnection() {
        return false;
    }

    @Override
    public boolean isUsingCompression() {
        return false;
    }

    @Override
    protected void loginNonAnonymously()
            throws XMPPException {
        user = config.getUsername()
                + "@"
                + config.getServiceName()
                + "/" 
                + (config.getResource() != null ? config.getResource() : "Test");
        roster = new Roster(this);
        anonymous = false;
        authenticated = true;
    }

    @Override
    public void loginAnonymously() throws XMPPException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to server.");
        }
        if (isAuthenticated()) {
            throw new IllegalStateException("Already logged in to server.");
        }
        anonymous = true;
        authenticated = true;
    }

    @Override
    public void send(PlainStreamElement element) {
        if (SmackConfiguration.DEBUG_ENABLED) {
            System.out.println("[SEND]: " + element.toXML());
        }
        queue.add(element);
    }

    @Override
    protected void sendPacketInternal(Packet packet) {
        if (SmackConfiguration.DEBUG_ENABLED) {
            System.out.println("[SEND]: " + packet.toXML());
        }
        queue.add(packet);
    }

    /**
     * Returns the number of packets that's sent through {@link #sendPacket(Packet)} and
     * that has not been returned by {@link #getSentPacket()}.
     * 
     * @return the number of packets which are in the queue.
     */
    public int getNumberOfSentPackets() {
        return queue.size();
    }

    /**
     * Returns the first packet that's sent through {@link #sendPacket(Packet)}
     * and that has not been returned by earlier calls to this method.
     * 
     * @return a sent packet.
     * @throws InterruptedException
     */
    @SuppressWarnings("unchecked")
    public <P extends TopLevelStreamElement> P getSentPacket() throws InterruptedException {
        return (P) queue.poll();
    }

    /**
     * Returns the first packet that's sent through {@link #sendPacket(Packet)}
     * and that has not been returned by earlier calls to this method. This
     * method will block for up to the specified number of seconds if no packets
     * have been sent yet.
     * 
     * @return a sent packet.
     * @throws InterruptedException
     */
    @SuppressWarnings("unchecked")
    public <P extends TopLevelStreamElement> P getSentPacket(int wait) throws InterruptedException {
        return (P) queue.poll(wait, TimeUnit.SECONDS);
    }

    /**
     * Processes a packet through the installed packet collectors and listeners
     * and letting them examine the packet to see if they are a match with the
     * filter.
     *
     * @param packet the packet to process.
     */
    public void processPacket(Packet packet) {
        if (SmackConfiguration.DEBUG_ENABLED) {
            System.out.println("[RECV]: " + packet.toXML());
        }

        invokePacketCollectorsAndNotifyRecvListeners(packet);
    }

    public static class DummyConnectionConfiguration extends ConnectionConfiguration {
        protected DummyConnectionConfiguration(DummyConnectionConfigurationBuilder builder) {
            super(builder);
        }

        public static DummyConnectionConfigurationBuilder builder() {
            return new DummyConnectionConfigurationBuilder();
        }

        public static class DummyConnectionConfigurationBuilder
                        extends
                        ConnectionConfigurationBuilder<DummyConnectionConfigurationBuilder, DummyConnectionConfiguration> {

            private DummyConnectionConfigurationBuilder() {
            }

            @Override
            public DummyConnectionConfiguration build() {
                return new DummyConnectionConfiguration(this);
            }

            @Override
            protected DummyConnectionConfigurationBuilder getThis() {
                return this;
            }
        }
    }
}
