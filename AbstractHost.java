import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractHost {
	
	protected volatile AtomicBoolean USER_INTERRUPTED = new AtomicBoolean(false);
	protected final static int DEFAULT_PORT = 42999;
	protected final byte FLAG_MESSAGE = (byte) 0x01;
	protected final byte FLAG_DATA_STREAM_OFFER = (byte) 0x02;
	protected final byte FLAG_FILE_SEND_RQST = (byte) 0x03;
	protected final byte FLAG_FILE_SEND_ACCEPT = (byte) 0x04;
	protected final byte FLAG_FILE_SEND_DENY = (byte) 0x05;
	
	public class ClosedConnectionException extends IOException {
		public ClosedConnectionException(String string) {
			super(string);
		}
	}
	
	public static String getLocalAddress() throws SocketException, UnknownHostException
	{
		/*
		 * Retourne l'adresse IP de la machine locale
		 */
		try(final DatagramSocket socket = new DatagramSocket())
		{
			socket.connect(InetAddress.getByName("192.168.1.1"), 80);
			return socket.getLocalAddress().getHostAddress();
		}
	}
	
	public void close() {
		throw new UnsupportedOperationException("Close method Not yet implemented");
	}
	
	protected byte[] readStream(InputStream is) throws IOException
	{	
		//Lecture de taille de message
		int messageSize = 0;

		//Lecture de la taille du message
		ByteBuffer sBuf = ByteBuffer.allocate(4); //size byte buffer
		for(int i = 0; i < 4; i++) {
			int sb = is.read();
			if(sb < 0) throw new ClosedConnectionException("Connexion fermée par le pair");
			sBuf.put((byte) sb);
		}
		messageSize = sBuf.getInt(0);
		final int messageSize_copy = messageSize;
		System.out.println("Size of received message : " + messageSize);

		byte[] buffer = new byte[4096];
		int index = 0;
		while(messageSize > 0)
		{
			int size = is.read(buffer, index, messageSize);
			if(size < 0) //Si outputStream de l'autre pair est fermé alors fermer ce socket
				throw new ClosedConnectionException("Connexion fermée par le pair");
			
			index += size;
			messageSize -= size;
		}
		
		return Arrays.copyOf(buffer, messageSize_copy);
	}
}
