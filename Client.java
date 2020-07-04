import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client extends AbstractHost{
	
	Socket connectionSocket;
	OutputStream os;
	InputStream is;
	protected volatile AtomicBoolean connected = new AtomicBoolean(false);
	LinkedList<String> inbox;

	public Client(String ServerIP, String username) throws UnknownHostException, IOException
	{
		this(ServerIP, DEFAULT_PORT, username);
	}
	
	public Client(String ServerIP, int port, String username) throws UnknownHostException, IOException
	{
		System.out.println("Tentative de connexion au serveur " + ServerIP + ":" + port);
		connectionSocket = new Socket(InetAddress.getByName(ServerIP), port);
		System.out.println("Connecté, enregistrement du username...");
		os = connectionSocket.getOutputStream();
		is = connectionSocket.getInputStream();
		sendUsername(username);
		connected.set(true);
		System.out.println("Username enregistré");
		inbox = new LinkedList<String>();
	}
	
	public void sendUsername(String username) throws IOException {
		byte usernameSize = 0;
		byte[] byteArray = null;
		
		if(username != null) {
			byteArray = username.getBytes();
			usernameSize = (byte) byteArray.length;
		}
		
		ByteBuffer messageBytes = ByteBuffer.allocate(usernameSize + 1);
		
		System.out.println((int) usernameSize);
		messageBytes.put(usernameSize);
		messageBytes.put(byteArray);
		
		os.write(messageBytes.array());
	}
	
	private void runListenThread()
	{
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				//Boucle d'écoute des messages reçus
				while(connected.get())
				{					
					try { 
						messageProcessor(readStream(is));
					}catch(IOException e) {
						if(connected.get()) {
							close();
						}
					}
				}
			}
		});
		
		listenThread.start();
	}
	
	private byte messageProcessor(byte[] buffer)
	{
		if(buffer == null || buffer.length < 1)
			return -1;
		
		switch(buffer[0])
		{
			case FLAG_MESSAGE:
			case FLAG_PRIVATE_MESSAGE:
				String message = new String(buffer, 1, buffer.length - 1);
				synchronized (inbox) {
					inbox.addLast(message);
				}
				System.out.println(message);
				break;
				
			/*case FLAG_DATA_STREAM_OFFER: 
				out+= "Open data socket offer received, port : " + 
						Integer.valueOf(new String(buffer, 1, buffer.length - 1));
				System.out.println(out);
				return FLAG_DATA_STREAM_OFFER;
				
			case FLAG_FILE_SEND_RQST:
				if(!fileTransfert.get()) { 
					StringTokenizer st = new StringTokenizer(new String(buffer, 1, buffer.length - 1));
					try {
						receiveFile(st.nextToken(), Long.valueOf(st.nextToken()));
					}catch(Exception e) {
						e.printStackTrace();
						fileTransfert.set(false);
					}
				}
				else denyFileShare();
				break;
				
			case FLAG_FILE_SEND_ACCEPT:
				approvedSend.set(true);
				semWaitApproval.release();
				break;
				
			case FLAG_FILE_SEND_DENY:
				approvedSend.set(false);
				semWaitApproval.release();
				break;
			*/
		}
		//System.out.println(out);
		
		return 0;
	}
	
	public void close() {
		connected.set(false);
		try {
			is.close();
			os.close();
			connectionSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String args[]) throws UnknownHostException, IOException, InterruptedException {
		Scanner sc = new Scanner(System.in);
		System.out.println("Connexion au serveur 192.168.1.41");
		System.out.println("Veuillez entrer votre username:");
		Client client = new Client("192.168.1.41", sc.nextLine());
		client.runListenThread();
		while(true) {
			client.sendMessage(client.os, sc.nextLine());
		}
	}
}
