import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client extends AbstractHost{
	
	Socket connectionSocket;
	OutputStream os;
	InputStream is;
	protected volatile AtomicBoolean connected = new AtomicBoolean(false);
	LinkedList<Message> inbox;
	String username;
	LinkedList<String> clientsList = null;

	public Client(String ServerIP, String username) throws UnknownHostException, IOException
	{
		this(ServerIP, DEFAULT_PORT, username);
	}
	
	public Client(String ServerIP, int port, String username) throws UnknownHostException, IOException
	{
		System.out.println("Tentative de connexion au serveur " + ServerIP + ":" + port);
		connectionSocket = new Socket(InetAddress.getByName(ServerIP), port);
		System.out.println("Connect�, enregistrement du username...");
		os = connectionSocket.getOutputStream();
		is = connectionSocket.getInputStream();
		sendUsername(username);
		connected.set(true);
		System.out.println("Username enregistr�");
		inbox = new LinkedList<Message>();
		this.username = username;
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
	
	public void runListenThread()
	{
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				//Boucle d'�coute des messages re�us
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
	
	public void requestClientsList() {
		sendMessage(os, formatMessage(FLAG_CLIENTS_LIST, null));
	}
	
	private byte messageProcessor(byte[] buffer)
	{
		if(buffer == null || buffer.length < 1)
			return -1;
		
		switch(buffer[0])
		{
			case FLAG_MESSAGE:
			case FLAG_PRIVATE_MESSAGE:
			case FLAG_CONNECTED_MESSAGE:
			case FLAG_DISCONNECTED_MESSAGE:
				String message = new String(buffer, 1, buffer.length - 1);
				synchronized (inbox) {
					inbox.addLast(new Message(FLAG_MESSAGE, message));
				}
				System.out.println(message);
				break;
				
			case FLAG_CLIENTS_LIST: //Les usernames des clients arrivent dans un seul String s�par�s par des ';' la liste est mise � jour
				StringTokenizer st = new StringTokenizer(new String(buffer, 1, buffer.length - 1), ";");
				clientsList = new LinkedList<String>();
				while(st.hasMoreTokens()) {
					String cl = st.nextToken();
					//System.out.println(cl);
					clientsList.add(cl);
				}
				
				inbox.addLast(new Message(FLAG_CLIENTS_LIST, null)); /*Ajoute un message dans l'inbox qui alerte que la liste d'utilisateurs
				a �t� mise � jour*/
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
	
	protected void sendPrivateMessage(OutputStream os, String username, String message) //format of the 'message' parameter: "username;actual_message"
	{
		sendMessage(os, formatMessage(FLAG_PRIVATE_MESSAGE, username + ';' + message));
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
		/*Scanner sc = new Scanner(System.in);
		System.out.println("Connexion au serveur 192.168.1.41");
		System.out.println("Veuillez entrer votre username:");
		Client client = new Client("192.168.1.41", sc.nextLine());
		client.runListenThread();
		Thread.sleep(500);
		System.out.println("Liste des clients connect�s: ");
		client.requestClientsList();
		while(true) {
			client.sendMessage(client.os, sc.nextLine());
		}*/
	}
}
