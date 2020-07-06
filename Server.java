import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Server extends AbstractHost{
	private LinkedList<ClientLog> clientsList;
	private ServerSocket serverSocket;
	private String localIP;
	private volatile Semaphore semWaitApproval = new Semaphore(0, true);
	private volatile Semaphore semApproved = new Semaphore(0, true);

	public Server() throws UnknownHostException, IOException
	{
		this(getLocalAddress(), 0);
	}
	
	public Server(int port) throws UnknownHostException, IOException
	{
		this(getLocalAddress(), port);
	}
	
	public Server(String hostIP) throws UnknownHostException, IOException
	{
		this(hostIP, 0);
	}
	
	public Server(String hostingIP, int port) throws UnknownHostException, IOException
	{
		localIP = hostingIP;
		serverSocket = new ServerSocket(port, 10, InetAddress.getByName(localIP));
		clientsList = new LinkedList<ClientLog>();
	}
	
	public void waitForConnections()
	{
		//D�claration du Thread attendant la connexion
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				while(true) {
					System.out.println("En attente de connexion sur l'addresse : " + serverSocket.getLocalSocketAddress() +  "...");
					try
					{
						Socket connectionSocket = serverSocket.accept();
						System.out.println("H�te " + connectionSocket.getInetAddress().getHostName() + " / " + 
								connectionSocket.getInetAddress().getHostAddress() +
								":" + connectionSocket.getPort() + " connect�.");	//connection accept�e
						
						//attendre la reception du username
						String username = fetchUsername(connectionSocket.getInputStream());
						System.out.println("Username du client re�u: " + username);
						
						//une fois re�u ajouter le client � la liste
						ClientLog client = new ClientLog(username, connectionSocket);
						synchronized(clientsList) {
							if(!clientsList.contains(client)) {
								clientsList.add(client);
								//Lancer Thread d'�coute
								runListenThread(client);
							}else {
								client.close();
								System.out.println("Client d�j� connect� !");
							}
						}
						System.out.println("liste des clients: ");
						for(ClientLog c : clientsList)
							System.out.println(c.username);
					}
					catch(IOException e)
					{
						/*
						 * d�tecte si connexion impossible ou bien arr�t�e par le thread principal 
						*/
						if(!USER_INTERRUPTED.get()) {
							System.err.println("Impossible de se connecter au client.");
							e.printStackTrace();
						}
						else break;
					}
				}
			}
		});
		
		listenThread.start();
	}
	
	private String fetchUsername(InputStream is) throws IOException {
		/*
		 * Lit le username du client qui vient de se connecter (c'est le premier message que le client envoie)
		 */
		
		//Lecture de la taille du username
		int remainingBytes = is.read();
		
		byte buffer[] = new byte[30];
		int size = 0;
		int index = 0;
		while(remainingBytes > 0)	{
			size = is.read(buffer, index, remainingBytes);
			if(size < 0)
				break;
			
			index += size;
			remainingBytes -= size;
		}
		
		if(remainingBytes == -1 || size == -1)	//Si OutputStream du client est ferm� alors fermer ce socket
			throw new ClosedConnectionException("Connexion ferm�e par le client");
		
		return new String(buffer).trim();
	}
	
	private void runListenThread(ClientLog client)
	{
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				//Boucle d'�coute des messages re�us
				InputStream is = client.is;
				while(client.connected.get())
				{
					//System.err.println("Entered while loop");
					try { 
						messageProcessor(client, readStream(is));
					}catch(IOException e) {
						if(client.connected.get()) {
							try {
								client.close();
							} catch (IOException e1) {
								e1.printStackTrace();
							}
						}
					}
				}
				
				//System.err.println("Exited while loop");
				synchronized(clientsList) {
					clientsList.remove(client);
				}
				
				broadcastMessage(null, client.username + " s'est d�connect�.");
			}
		});
		
		listenThread.start();
	}
	
	private ClientLog getClientByName(String username) {
		synchronized (clientsList) {
			for(ClientLog c : clientsList) {
				if(c.username.equals(username))
					return c;
			}
		}
		
		return null;
	}
	
	private byte messageProcessor(ClientLog sender, byte[] buffer)
	{
		if(buffer == null || buffer.length < 1)
			return -1;
		//String out = "";
		/*out += connectionSocket.getInetAddress().getHostName() + ":" + 
				connectionSocket.getPort() + " :> ";*/
		
		String message;
		switch(buffer[0])
		{
			case FLAG_MESSAGE:
				message = sender.username + ": ";
				message += new String(buffer, 1, buffer.length - 1);
				broadcastMessage(sender, message);
				break;
				
			case FLAG_PRIVATE_MESSAGE:
				String data = new String(buffer, 1, buffer.length - 1);
				
				int separator = data.indexOf(';');
				if(separator > 0) {
					String username = data.substring(0, separator);
					ClientLog receiver = getClientByName(username);
					if(receiver != null) {
						message = sender.username + " (en priv�): ";
						message += data.substring(separator+1, data.length());
						sendMessage(receiver.os, formatMessage(FLAG_PRIVATE_MESSAGE, message));
					}
				}
				break;
				
			case FLAG_CLIENTS_LIST:
				String list = "";
				for(ClientLog client : clientsList) {
					list += client.username + ";";
				}
				sendMessage(sender.os, formatMessage(FLAG_CLIENTS_LIST, list));
				break;
				
			case FLAG_FILE:
				StringTokenizer st = new StringTokenizer(new String(buffer, 1, buffer.length - 1), "|");
				try {
					String fileName = st.nextToken();
					int length = Integer.valueOf(st.nextToken());
					byte[] fileData = receiveFile(sender.is, length);
					broadcastFile(sender, fileName, length, fileData);
				}catch(Exception e) {
					e.printStackTrace();
				}
				break;
			
			case FLAG_FILE_SEND_ACCEPT:
				semApproved.release();
				break;
				
			case FLAG_FILE_SEND_DENY:
				approvedSend.set(false);
				semWaitApproval.release();
				break;
				
			/*case FLAG_DATA_STREAM_OFFER: 
				out+= "Open data socket offer received, port : " + 
						Integer.valueOf(new String(buffer, 1, buffer.length - 1));
				System.out.println(out);
				return FLAG_DATA_STREAM_OFFER;*/
		}
		//System.out.println(out);
		
		return 0;
	}
	
	private void broadcastFile(ClientLog sender, String fileName, int length, byte[] fileData) {
		synchronized(clientsList) {
			for(ClientLog client : clientsList) {
				if(!client.equals(sender)) {
					sendFile(client.os, fileName, length, fileData);
				}
			}
		}
	}
	
	private void sendFile(OutputStream os, String fileName, int length, byte[] fileData) {
		//Envoie de la demande de transfert de fichier
		sendMessage(os, formatMessage(FLAG_FILE_SEND_RQST, fileName + " (" + length + " octets)"));
		
		//Lancement du thread qui attend la r�ponse et envoie le fichier
		Thread fileTransfertThread = new Thread(new Runnable() {
			public void run()
			{
				try {
					if(semWaitApproval.tryAcquire(30, TimeUnit.SECONDS)) {
						if(semApproved.tryAcquire(0, TimeUnit.SECONDS)) {
							//� continuer Envoyer ici le fichier
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
		
		fileTransfertThread.start();
	}
	
	private void broadcastMessage(ClientLog sender, byte[] message) {
		synchronized(clientsList) {
			for(ClientLog client : clientsList) {
				if(!client.equals(sender)) {
					sendMessage(client.os, message);
				}
			}
		}
	}
	
	private void broadcastMessage(ClientLog sender, byte command, String message) {
		byte[] formattedMessage = formatMessage(command, message);
		broadcastMessage(sender, formattedMessage);
	}
	
	private void broadcastMessage(ClientLog sender, String message) {
		broadcastMessage(sender, FLAG_MESSAGE, message);
	}
	
	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		Server serveur = new Server(DEFAULT_PORT);
		serveur.waitForConnections();
	}
}
