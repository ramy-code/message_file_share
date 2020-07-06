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

public class Server extends AbstractHost{
	private LinkedList<ClientLog> clientsList;
	private ServerSocket serverSocket;
	private String localIP;

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
		//Déclaration du Thread attendant la connexion
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				while(true) {
					System.out.println("En attente de connexion sur l'addresse : " + serverSocket.getLocalSocketAddress() +  "...");
					try
					{
						Socket connectionSocket = serverSocket.accept();
						System.out.println("Hôte " + connectionSocket.getInetAddress().getHostName() + " / " + 
								connectionSocket.getInetAddress().getHostAddress() +
								":" + connectionSocket.getPort() + " connecté.");	//connection acceptée
						
						//attendre la reception du username
						String username = fetchUsername(connectionSocket.getInputStream());
						System.out.println("Username du client reçu: " + username);
						
						//une fois reçu ajouter le client à la liste
						ClientLog client = new ClientLog(username, connectionSocket);
						synchronized(clientsList) {
							if(!clientsList.contains(client)) {
								clientsList.add(client);
								//Lancer Thread d'écoute
								runListenThread(client);
							}else {
								client.close();
								System.out.println("Client déjà connecté !");
							}
						}
						System.out.println("liste des clients: ");
						for(ClientLog c : clientsList)
							System.out.println(c.username);
					}
					catch(IOException e)
					{
						/*
						 * détecte si connexion impossible ou bien arrêtée par le thread principal 
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
		
		if(remainingBytes == -1 || size == -1)	//Si OutputStream du client est fermé alors fermer ce socket
			throw new ClosedConnectionException("Connexion fermée par le client");
		
		return new String(buffer).trim();
	}
	
	private void runListenThread(ClientLog client)
	{
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				//Boucle d'écoute des messages reçus
				InputStream is = client.is;
				while(client.connected.get())
				{
					System.err.println("Entered while loop");
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
				
				System.err.println("Exited while loop");
				synchronized(clientsList) {
					clientsList.remove(client);
				}
			}
		});
		
		listenThread.start();
	}
	
	private ClientLog getClientByName(String username) {
		for(ClientLog c : clientsList) {
			if(c.username.equals(username))
				return c;
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
						message = sender.username + " (en privé): ";
						message += data.substring(separator+1, data.length());
						sendMessage(receiver.os, formatMessage(FLAG_PRIVATE_MESSAGE, message));
					}
				}
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
	
	private void broadcastMessage(ClientLog sender, byte[] message) {
		synchronized(clientsList) {
			for(ClientLog client : clientsList) {
				if(!client.equals(sender))
					sendMessage(client.os, message);
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
		Thread.sleep(10000);
		//serveur.broadcastMessage(null ,"ceci est un message test de la part du serveur");
	}
}
