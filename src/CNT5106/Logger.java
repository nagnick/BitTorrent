package CNT5106;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class Logger {
	
	String filePath;
	String fileName;
	int myPeerID;
	DateTimeFormatter formatter;
	LocalDateTime curTime;
	
	public Logger(String name, int id)
	{
		fileName = name;
		myPeerID = id;
		filePath = System.getProperty("user.dir");
		filePath = filePath +"\\"+ fileName;
		formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");
		curTime = LocalDateTime.now();
	}
	
	
	public void logMessage(String type,int peerID2, ArrayList<Integer> prefNeighbors,int optUnchokedNeighbor,int pieceIndex, int numPieces)
	{
		
		if(type.equals("TCPConnection"))
		{
			logTCPConnection(peerID2);
		}
		else if(type.equals("changePrefNeighbors"))
		{
			logChangePrefNeighbors(prefNeighbors);
		}
		else if(type.equals("changeOptUnchokedNeighbor"))
		{
			logChangeOptUnchokedNeighbor(optUnchokedNeighbor);
		}
		else if(type.equals("unchoking"))
		{
			logUnchoking(peerID2);
		}
		else if(type.equals("choking"))
		{
			logChoking(peerID2);
		}
		else if(type.equals("recvHaveMessage"))
		{
			logRecvHaveMessage(peerID2,pieceIndex);
		}
		else if(type.equals("recvIntMessage"))
		{
			logRecvIntMessage(peerID2);
		}
		else if(type.equals("recvNotIntMessage"))
		{
			logRecvNotIntMessage(peerID2);
		}
		else if(type.equals("downloadingPiece"))
		{
			logDownloadingPiece(peerID2, pieceIndex, numPieces);
		}
		else if(type.equals("downloadCompletion"))
		{
			logDownloadCompletion();
		}
		else
		{
			System.out.println("Not a valid log type");
		}
	}
	public void logTCPConnection(int peerID2)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " is connected from Peer "+ peerID2 +"\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logChangePrefNeighbors(ArrayList<Integer> prefNeighbors)
	{
		String output = "";
		curTime = LocalDateTime.now();
		output = formatter.format(curTime)+": Peer "+myPeerID + " has the preferred neighbors ";
		if(prefNeighbors != null && prefNeighbors.size() != 0) {
			for (int i = 0; i < prefNeighbors.size() - 1; i++) {
				output = output + prefNeighbors.get(i) + ", ";
			}

			output = output + prefNeighbors.get(prefNeighbors.size() - 1) + "\n";

			try {
				FileWriter myFile = new FileWriter(filePath, true);
				myFile.append(output);
				myFile.close();
			} catch (IOException exception) {
				System.out.println(exception.getMessage());
			}
		}
	}
	public void logChangeOptUnchokedNeighbor(int optUnchokedNeighbor)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " has the optimistically unchoked neighbor "
			+ optUnchokedNeighbor +"\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logUnchoking(int peerID2)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " is unchoked by "+ peerID2 +"\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logChoking(int peerID2)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " is choked by "+ peerID2 +"\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logRecvHaveMessage(int peerID2, int pieceIndex)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " received the 'have' message from "
			+ peerID2 +" for the piece "+ pieceIndex +"\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logRecvIntMessage(int peerID2)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " received the 'interested' message from "+ peerID2 +"\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logRecvNotIntMessage(int peerID2)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " received the 'not interested' message from "+ peerID2 +"\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logDownloadingPiece(int peerID2, int pieceIndex, int numPieces)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " has downloaded the piece "
			+pieceIndex+" from "+ peerID2 +". Now the number of pieces it has is "+numPieces+"\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logDownloadCompletion()
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " has downloaded the complete file\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logCommonConfig(int prefNeighbors, int unchokeInterval, int optUnchokeInterval, String fileName, int fileSize, int pieceSize)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " has parsed the Common.cfg file\n";
			myFile.append(output);
			output = formatter.format(curTime)+": #prefNeighbors-"+prefNeighbors + " unchoke interval-"+unchokeInterval+"\n";
			myFile.append(output);
			output = formatter.format(curTime)+":optimisticUnchokeInterval-"+optUnchokeInterval+ " fileName-"+fileName+"\n"; 
			myFile.append(output);
			output = formatter.format(curTime)+": fileSize-"+fileSize + " pieceSize-"+pieceSize+"\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public void logPeerConfig(int currentPeerID,String peerHostName, int peerListenPort, boolean hasFile)
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": Peer "+myPeerID + " has parsed the PeerInfo.cfg file\n";
			myFile.append(output);
			output = formatter.format(curTime)+": Peer ID-"+currentPeerID + " PeerHostName-"+peerHostName+"\n";
			myFile.append(output);
			output = formatter.format(curTime)+": PeerListenPort-"+peerListenPort+ " HasFile-"+hasFile+"\n"; 
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
}