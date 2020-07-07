import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractHost {
	
	protected volatile AtomicBoolean USER_INTERRUPTED = new AtomicBoolean(false);
	protected final static int BUFFER_SIZE = 8096; //10 Ko
	protected final static int DEFAULT_PORT = 42999;
	protected final byte FLAG_MESSAGE = (byte) 0x01;
	protected final byte FLAG_DATA_STREAM_OFFER = (byte) 0x02;
	protected final byte FLAG_FILE_SEND_RQST = (byte) 0x03;
	protected final byte FLAG_FILE_SEND_ACCEPT = (byte) 0x04;
	protected final byte FLAG_FILE_SEND_DENY = (byte) 0x05;
	protected final byte FLAG_PRIVATE_MESSAGE = (byte) 0x06;
	protected final byte FLAG_CONNECTED_MESSAGE = (byte) 0x07;
	protected final byte FLAG_DISCONNECTED_MESSAGE = (byte) 0x08;
	protected final byte FLAG_CLIENTS_LIST = (byte) 0x09;
	protected final byte FLAG_FILE = (byte) 0x0a;
	
	public volatile int avancement_transfert = 0;
	
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
		//System.out.println("Size of received message : " + messageSize);

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
	
	protected void sendMessage(OutputStream os, byte[] message) //Envoie un tableau de Byte brute
	{
		try {
			os.write(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected void sendMessage(OutputStream os, String message)
	{
		sendMessage(os, formatMessage(FLAG_MESSAGE, message));
	}

	public static byte[] formatMessage(byte command, String data)
	{
		/*
		 * Envoie une commande de type 'command' contenant les données dans 'message'
		 */
		
		int messageSize = 0;
		byte[] byteArrayMessage = null;
		
		if(data != null) {
			byteArrayMessage = data.getBytes();
			messageSize = byteArrayMessage.length;
		}
		
		ByteBuffer messageBytes = ByteBuffer.allocate(messageSize + 5);

		messageBytes.putInt(messageSize + 1);
		messageBytes.put(command); // écriture de la commande
		if(data != null) // écriture du message
			messageBytes.put(byteArrayMessage);
		
		//sendMessage(os, messageBytes.array());
		return messageBytes.array();
	}
	
	protected byte[] receiveFile(InputStream is, int length) throws IOException //recoit un fichier et le retourne sous forme de tableau de bytes
	{		
		int b = 0;
		int readBytes = 0;
		int index = 0;
		byte[] internalBuffer = new byte[length];
		avancement_transfert = 0;

		while(readBytes < length && (b = is.read(internalBuffer, index, Math.min(BUFFER_SIZE, length - readBytes))) != -1)
		{
			readBytes += b;
			index += b;
			//Mise à jour de l'affichage de l'avancement
			avancement_transfert = readBytes*100/length;
			System.out.println("Avancement : " + avancement_transfert + "%");
		}
		
		if(b == -1)
			throw new IOException("End of stream");
		
		System.out.println("Finished receiving");
		return internalBuffer;
	}
}
