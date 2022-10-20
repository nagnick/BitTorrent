package CNT5106;
import CNT5106.Message;

import java.io.*;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
//import java.util.regex;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

public class Peer {
    Integer myID; //ID of this peer
    String logFileName; //the name of the file this peer should be logging to
    String commonConfigFileName; //the common config file name
    String peerInfoFileName; //the peer info file name
    int numPreferredNeighbors; //number of preferred neighbors this peer should have
    int unchokingInterval;
    int optimisticUnchokingInterval;
    String desiredFileName; //the file we are trying to get
    int desiredFileSize; //the size of the file we want
    int pieceSize; //the size of the pieces of the file we want
    boolean haveFile; //indicate if I have entire file or not

    Thread serverThread;
    LinkedBlockingQueue<Message> inbox = new LinkedBlockingQueue<Message>(); // all recev tcp threads write to here
    ConcurrentHashMap<Integer,TCPIn> peerInConnections = new ConcurrentHashMap<Integer, TCPIn>(); // have these peers add messages to a thread safe queue
    ConcurrentHashMap<Integer,TCPOut> peerOutConnections = new ConcurrentHashMap<Integer, TCPOut>(); // peer connections to send messages
    ConcurrentHashMap<Integer,Boolean> peerFileMap = new ConcurrentHashMap<Integer, Boolean>(); // map peer IDs to status of having file or not
    
    public Peer(){ //default constructor, normally shouldn't use this
        myID = -1; //set a default ID for self.
    }
    
    public Peer(int peerID, String logFileName, String commonConfigFileName, String peerInfoFileName) {
        myID = peerID;
        this.logFileName = logFileName;
        this.commonConfigFileName = commonConfigFileName;
        this.peerInfoFileName = peerInfoFileName;
        
        //read in the common config file and set the other attributes for the peer.
    	Pattern prefNeighborsRegex = Pattern.compile("^(NUmberOfPreferredNeighbors)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for number of preferred neighbors config directive
    	Pattern unchokingIntervalRegex = Pattern.compile("^(UnchokingInterval)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for unchoking interval config directive
    	Pattern optUnchokingIntervalRegex = Pattern.compile("^(OptimisticUnchokingInterval)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for optimistic unchoking interval config directive
    	Pattern fileNameRegex = Pattern.compile("^(FileName)\\s(.{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for file name config directive
    	Pattern	fileSizeRegex = Pattern.compile("^(FileSize)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for file size config directive
    	Pattern pieceSizeRegex = Pattern.compile("^(PieceSize)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for piece size config directive
    	
    	Scanner configFile = new Scanner(commonConfigFileName);
    	while(configFile.hasNextLine()) //keep looping while we have more lines to read
    	{
    		String configLine = configFile.nextLine(); //pull in the config line
    		if(prefNeighborsRegex.matcher(configLine).find()) //config line is for number of preferred neighbors
    		{
    			this.numPreferredNeighbors = Integer.parseInt(prefNeighborsRegex.matcher(configLine).group(1)); //extract the value for config directive from regex, cast to int, and store it.
    		}
    		else if(unchokingIntervalRegex.matcher(configLine).find())
    		{
    			this.unchokingInterval = Integer.parseInt(unchokingIntervalRegex.matcher(configLine).group(1)); //extract the value for config directive from regex, cast to int, and store it.
    		}
    		else if(optUnchokingIntervalRegex.matcher(configLine).find())
    		{
    			this.optimisticUnchokingInterval = Integer.parseInt(optUnchokingIntervalRegex.matcher(configLine).group(1)); //extract the value for config directive from regex, cast to int, and store it.
    		}
    		else if(fileNameRegex.matcher(configLine).find())
    		{
    			this.desiredFileName = fileNameRegex.matcher(configLine).group(1);
    		}
    		else if(fileSizeRegex.matcher(configLine).find())
    		{
    			this.desiredFileSize = Integer.parseInt(fileSizeRegex.matcher(configLine).group(1));
    		}
    		else if(pieceSizeRegex.matcher(configLine).find())
    		{
    			this.pieceSize = Integer.parseInt(pieceSizeRegex.matcher(configLine).group(1));
    		}
    	}
    	configFile.close(); //we're done with the common config file, close it out.
    }
    
    public void Connect(){ // parse manifest file and connect to peers
    	Scanner peerInfoFile = new Scanner(peerInfoFileName);
    	Pattern peerInfoRegex = Pattern.compile("^(\\d{1,})\\s([a-zA-Z\\d-\\.]{1,})\\s(\\d{1,})\\s(0|1)$",Pattern.CASE_INSENSITIVE);
        // connect to other peers with help from manifest file
        // read file connect to those peers probably need to try multiple times as other peers may not be up yet
        int currentLineNumber = 0; //keep track of what line number we're on.as it determines what we should do when we hit our own entry
    	boolean isFirstPeer = false; //are we the first peer listed in the file?
    	int serverListenPort = 0; //what port we should be listening on
    	
        while(peerInfoFile.hasNextLine()) { // start connecting to peers change while peer list not empty from manifest file
        	String peerInfoLine = peerInfoFile.nextLine(); //pull the current line into a string
        	Matcher peerInfoMatcher = peerInfoRegex.matcher(peerInfoLine); //match the line against the peer info regex so we can extract the attributes from subgroups.
        	if(peerInfoMatcher.find()) //only continue if the line is in expected format, otherwise silently ignore the line
        	{
        		int currentPeerID = Integer.parseInt(peerInfoMatcher.group(0));
        		String peerHostName = peerInfoMatcher.group(1);
        		int peerListenPort = Integer.parseInt(peerInfoMatcher.group(2));
        		boolean peerHasFile = (peerInfoMatcher.group(3) == "1");
        		
        		if(currentPeerID == myID)
        		{
        			serverListenPort = peerListenPort;
        			haveFile = peerHasFile;
        			if(currentLineNumber == 0) //we're the first peer
        			{
        				isFirstPeer = true;
        			}
        		}
        		else
        		{
		            // spin up several threads for each peer that connects
		            try {
		            	if(!isFirstPeer) //only try to connect when we're not the first peer
		            	{
			                Socket peerSocket = new Socket(peerHostName, peerListenPort); // connect to a peer
			                TCPOut peerOut = new TCPOut(peerSocket); // add to list
			                TCPIn peerIn = new TCPIn(inbox,peerSocket); // add to list
			                peerOut.send(new Message(myID));// send handshake
			                Message peerHandshake = peerIn.getHandShake();
			
			                peerIn.setPeerId(peerHandshake.peerID); // set peerID for tracking of message origin in message queue
			                peerOut.setPeerId(peerHandshake.peerID); // important later when messages are mixed in queue to track their origin
			
			                peerIn.start(); // start that peers thread
			                peerInConnections.put(peerHandshake.peerID,peerIn);
			                peerOutConnections.put(peerHandshake.peerID,peerOut);
		            	}
		            	peerFileMap.put(currentPeerID, peerHasFile); //still build the map of which peers have what files.
		            }
		            catch (ConnectException e) {
		                System.err.println("Connection refused. Peer not found");
		            }
		            catch(UnknownHostException unknownHost){
		                System.err.println("You are trying to connect to an unknown host!");
		            }
		            catch(IOException ioException){
		                ioException.printStackTrace();
		            }
        		}
        	}
		currentLineNumber++;
        }
// use this lambda style if you need to spin up a random thread at any point just dont capture it
        final int serverPort = serverListenPort;
        serverThread = new Thread(() -> { // listen for other peers wishing to connect with me on seperate thread
            try {
                ServerSocket listener = new ServerSocket(serverPort); // passive listener on own thread
                while(true) { // need to add map duplicate insert checks as some peers may try to connect after we have already connected
                    Socket peerSocket = listener.accept(); // this blocks waiting for new connections
                    TCPOut peerOut = new TCPOut(peerSocket); // add to list
                    TCPIn peerIn = new TCPIn(inbox, peerSocket); // add to list
                    peerOut.send(new Message(myID));// send handshake
                    Message peerHandshake = peerIn.getHandShake();

                    peerIn.setPeerId(peerHandshake.peerID); // set peerID for tracking of message origin in message queue
                    peerOut.setPeerId(peerHandshake.peerID);

                    peerIn.start(); // start that peers thread
                    peerInConnections.put(peerHandshake.peerID, peerIn);
                    peerOutConnections.put(peerHandshake.peerID, peerOut);
                }
            }
            catch (Exception e){
                System.out.println("Error running server sockets");
            }
        });
        serverThread.start();

    }
    public boolean getFile(){ // actual work done here
        // start main process of asking peers for bytes of file
        while(!inbox.isEmpty()){ // add && file is incomplete
            //process messages and respond appropriately
            System.out.println(inbox.peek().type.toString());
            inbox.remove();
        }
        return false; // failed to get all bytes of file from network
    }
    public static void main(String args[])
    {
    	final int peerID = Integer.parseInt(args[0]);  //peerID is specified at time of execution, pull it from args
        final String logFileName = "log_peer_" + args[0] + ".log";
        final String commonConfigFile = "Common.cfg";
        final String peerInfoConfigFile = "PeerInfo.cfg";
        
    	Peer me = new Peer(peerID, logFileName, commonConfigFile, peerInfoConfigFile);
        //me.Connect();
        //me.getFile(); work in progress
        Message myMessage = new Message(5, Message.MessageTypes.unchoke,"Hello");
        byte[] temp = myMessage.toBytes();
        System.out.println(Arrays.toString(temp));
        System.out.println(Arrays.toString((myMessage = new Message(temp,false,100)).toBytes()));
        System.out.println(myMessage.toString());
        System.out.println(Arrays.toString(temp = new Message(128).toBytes()));
        System.out.println(Arrays.toString((myMessage = new Message(temp,true,101)).toBytes()));
        System.out.println(myMessage.toString());
    }
}
