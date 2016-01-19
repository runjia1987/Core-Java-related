package org.jackJew.io.NIO;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.UUID;

/**
 * NIO client
 * @see NIOServer
 * @author Jack
 *
 */
public class NIOClient {

	final String HOST = "127.0.0.1";
	final int PORT = 9999;
	final int MAX_REQUESTS = 10;
	private ByteBuffer buffer = ByteBuffer.allocate(1 << 10);
	private String clientName;
	private boolean firstContact = true;
	
	public NIOClient(String name){
		this.clientName = name;
	}

	public void startListen() {
		SocketChannel channel = null;
		Selector selector = null;
		try {
			channel = SocketChannel.open();
			selector = Selector.open();
			channel.configureBlocking(false);
			channel.register(selector, SelectionKey.OP_CONNECT);
			channel.connect(new InetSocketAddress(HOST, PORT));
			
			int requestTimes = 0;
			while(requestTimes++ < MAX_REQUESTS){
				System.out.println(this.clientName + " wait for incoming events... times: " + requestTimes);
				// a blocking selection operation waiting for incomming events
				selector.select();
				
				Iterator<SelectionKey> keysItr = selector.selectedKeys().iterator();
				while(keysItr.hasNext()){
					SelectionKey key = keysItr.next();
					keysItr.remove();
					if(key.isValid()) {
						handleKey(key, selector);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				selector.close();
				channel.close();
				System.out.println(this.clientName + " closed channel.");
			} catch (Exception e) {
				e.printStackTrace();
			}			
		}
	}
	
	private void handleKey(SelectionKey key, Selector selector) throws Exception {
		System.out.println(key.isConnectable() + "," + key.isReadable() + "," + key.isWritable());
		SocketChannel channel = (SocketChannel) key.channel();
		if(key.isConnectable()){
			boolean connected = true;
			if(! channel.isConnected()){
				if(channel.finishConnect()){
					// A non-blocking connection operation,
					// true if, and only if, this channel's socket is now connected
					System.out.println(this.clientName + " connected to " + channel.socket().getInetAddress());
				} else{
					// fail to connect in this try
					connected = false;
				}
			}
			if(connected){
				// connected, deregister OP_CONNECT
				channel.register(selector, SelectionKey.OP_READ);
			}
			// not connected, then keep selecting in next loop
		} else if(key.isReadable()){
			buffer.clear();
			int read = channel.read(buffer);
			if(read > 0){
				buffer.flip();
				System.out.println(this.clientName + " receive: " + new String(buffer.array(), 0, read));
				// don't register channels for OP_WRITE until you have something to write
				channel.register(selector, SelectionKey.OP_WRITE);
			} else if(read == -1) {
				// de-register OP_READ to avoid infinite EOS events loop
				key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
			} else if(read == 0) {
				System.out.println(this.clientName + " Warn: receive nothing. ");
			}
		} else if(key.isWritable()){
			System.out.println(this.clientName + " will write...");
			if( firstContact ) {
				firstContact = false;
				// write client name to server
				channel.write(ByteBuffer.wrap(this.clientName.getBytes()));
			} else {
				String uuidStr = UUID.randomUUID().toString();
				System.out.println(this.clientName + " sends response: " + uuidStr);
				channel.write(ByteBuffer.wrap(uuidStr.getBytes()));
			}
			channel.register(selector, SelectionKey.OP_READ);
		}
	}

}
