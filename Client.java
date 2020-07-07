import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client extends AbstractHost{
	
	Socket connectionSocket;
	OutputStream os;
	InputStream is;
	protected volatile AtomicBoolean connected = new AtomicBoolean(false);
	protected volatile Semaphore semWaitFileTransfert = new Semaphore(1, true);
	LinkedList<Message> inbox;
	String username;
	public volatile LinkedList<String> clientsList = null;

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
	
	@Override
	protected byte[] readStream(InputStream is) throws IOException {
		semWaitFileTransfert.release(); //Libère sémaphore pour pouvoir être utilisé pour un transfert de fichier ou le prochain message
		return super.readStream(is);
	}
	
	public void runListenThread()
	{
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				//Boucle d'écoute des messages reçus
				while(connected.get())
				{					
					try { 
						semWaitFileTransfert.acquire(); //vérifie qu'il n'y a pas de transfert de fichiers en cours
						messageProcessor(readStream(is));
					}catch(IOException | InterruptedException e) {
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
		
		String message = null;
		switch(buffer[0])
		{
			case FLAG_MESSAGE:
			case FLAG_PRIVATE_MESSAGE:
			case FLAG_CONNECTED_MESSAGE:
			case FLAG_DISCONNECTED_MESSAGE:
				message = new String(buffer, 1, buffer.length - 1);
				synchronized (inbox) {
					inbox.addLast(new Message(FLAG_MESSAGE, message));
				}
				System.out.println(message);
				break;
				
			case FLAG_CLIENTS_LIST: //Les usernames des clients arrivent dans un seul String séparés par des ';' la liste est mise à jour
				StringTokenizer st = new StringTokenizer(new String(buffer, 1, buffer.length - 1), ";");
				clientsList = new LinkedList<String>();
				while(st.hasMoreTokens()) {
					String cl = st.nextToken();
					//System.out.println(cl);
					clientsList.add(cl);
				}
				
				inbox.addLast(new Message(FLAG_CLIENTS_LIST, null)); /*Ajoute un message dans l'inbox qui alerte que la liste d'utilisateurs
				a été mise à jour*/
				break;
				
			case FLAG_FILE_SEND_RQST:
				message = new String(buffer, 1, buffer.length - 1);
				synchronized (inbox) {
					inbox.addLast(new Message(FLAG_FILE_SEND_RQST, message));
				}
				System.out.println(message);
				break;
				
			case FLAG_FILE:
				message = new String(buffer, 1, buffer.length - 1);
				synchronized (inbox) {
					inbox.addLast(new Message(FLAG_FILE, message));
					try {
						semWaitFileTransfert.acquire();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				System.out.println(message);
				break;
		}
		
		return 0;
	}
	
	public void saveFile(String path, byte[] data) { //Enregistre les données de 'data' dans un fichier dans le chemin 'path'
		File f = new File(path);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(f);
			fos.write(data);
			fos.flush();
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected void sendFile(String name, int length, byte[] data) {
		//send file-share request
		String request = name + "|" + length;
		System.out.println("size : " + length);
		sendMessage(os, formatMessage(FLAG_FILE, request));		
		
		try {
			os.write(data);
			System.out.println("Finished sending");
		}catch (IOException e)	{
			close();
			e.printStackTrace();
		}
	}
	
	protected void sendFile(String filePath)
	{
		//pas encore terminée
		System.out.println("sending : " + filePath);
		
		File f = new File(filePath);
		if(!f.exists()) {
			System.err.println("Fichier ou dossier inexistant : " + filePath);
			return;
		}
		
		if(f.isDirectory()) {
			System.err.println("Ceci est un dossier, veuillez choisir un fichier");
			return;
		}
		
		//send file-share request
		try {
			//Read file and send it
			BufferedInputStream fileBis = new BufferedInputStream(new FileInputStream(f));
			byte[] internalBuffer = new byte[(int) f.length()];
			fileBis.read(internalBuffer);
			
			/*while((b = fileBis.read(internalBuffer)) != -1) {
				os.write(internalBuffer, 0, b);
				count += b;
			}*/
			
			fileBis.close();
			sendFile(f.getName(), (int) f.length(), internalBuffer);
		}catch (IOException e)	{
			e.printStackTrace();
		}
	}
	
	public void refuseFileTransfert() {
		sendMessage(os, formatMessage(FLAG_FILE_SEND_DENY, null));
	}
	
	public void acceptFileTransfert() {
		sendMessage(os, formatMessage(FLAG_FILE_SEND_ACCEPT, null));
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
	
	@Override
	public byte[] receiveFile(InputStream is, int length) throws IOException {
		byte[] data =  super.receiveFile(is, length);
		semWaitFileTransfert.release(); //Libère le thread qui essaie de lire des messages
		
		return data;
	}
	
	public static void main(String args[]) throws UnknownHostException, IOException, InterruptedException {
		Scanner sc = new Scanner(System.in);
		System.out.println("Connexion au serveur 192.168.1.41");
		System.out.println("Veuillez entrer votre username:");
		Client client = new Client("192.168.1.41", sc.nextLine());
		client.runListenThread();
		Thread.sleep(500);
		System.out.println("Liste des clients connectés: ");
		client.requestClientsList();
		sc.nextLine();
		client.sendFile("C:\\Users\\YacineM\\Desktop\\memes\\cringe.png");
		/*/while(true) {
			//client.sendMessage(client.os, sc.nextLine());
			//Thread.sleep(2000);
			client.acceptFileTransfert();
			sc.nextLine();
			while(client.inbox.size() > 0 && client.inbox.peekFirst().type != client.FLAG_FILE) {
				client.inbox.pop();
			}
			
			String message = client.inbox.pop().content;
			int length = Integer.valueOf(message.substring(message.indexOf("|") + 1));
			byte[] data = client.receiveFile(client.is, length);
			client.saveFile("C:/Users/YacineM/Desktop/testfile.png", data);
		//}*/
	}
}
