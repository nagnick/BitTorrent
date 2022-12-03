package CNT5106;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Comparator;
import java.util.concurrent.LinkedBlockingQueue;

public class PeerTCPConnection extends Thread { // spinning thread waiting for peer messages
    LinkedBlockingQueue<Message> inbox;
    Socket connection;
    ObjectInputStream in;
    ObjectOutputStream out;
    int peerID;
    int totalInMessages = 0;
    int totalOptimisticPeriods = 0;
    int totalPreferredPeriods = 0;
    double downloadRate = 0;
    boolean interested = false;
    boolean choked = true; // my view of this peer whether I have choked it or not
    boolean iamChoked = true; // this peers view of me
    boolean haveFile = false; // used to decide when to terminate program

    public PeerTCPConnection(LinkedBlockingQueue<Message> inbox, Socket connection){ // pass in peer info to form tcp connection
        this.inbox = inbox;
        this.connection = connection;
        try {
            out = new ObjectOutputStream(connection.getOutputStream());
            in = new ObjectInputStream(connection.getInputStream());
        }
        catch(Exception e){
            System.out.println("Error creating TCP connection ");
        }
    }
    public Message getHandShake(){ // use before starting thread and only once will fail if anyother message
        byte[] messageBytes = {0,0,0,0,0,0,0,0,0}; // puts a zero int in message if bellow fails
        try {
            messageBytes = in.readNBytes(32);
        }
        catch (Exception e) {
            System.out.println("Error reading TCPIn handshake");
        }
        return new Message(messageBytes, true,peerID); // peerID not used for handshake peerId in message is read instead
    }
    public void start(){
        try {
            // tcp network stuff
            while(!connection.isClosed()) { // testing required
                // read bytes and create message to put in message queue inbox
                ByteBuffer lengthBuff = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
                int messageLength = lengthBuff.put(in.readNBytes(4)).getInt(0);
                ByteBuffer message = ByteBuffer.allocate(5+messageLength).order(ByteOrder.BIG_ENDIAN);
                message.putInt(0,messageLength);
                message.put(4,in.readNBytes(1));
                message.put(5,in.readNBytes(messageLength));
                inbox.put(new Message(message.array(),false,peerID));
                incrementTotalInMessages(); // used to track download rate for choking/unchoking
            }
        }
        catch (Exception e) {
            System.out.println("Error running TCPIn thread");
        }
    }
    public boolean send(Message message){
        System.out.println("Sending message: " + String.valueOf(message));
        try {
            out.write(message.toBytes());
            return true;
        }
        catch (Exception e){
            System.out.println("Error writing bytes in send method");
        }
        return false;
    }
    public void close(){
        try {
            connection.close();
            in.close();
            out.flush();
            out.close();
        }
        catch (Exception e){
            System.out.println("Error closing TCP connection");
        }
    }
    public void setPeerId(int ID){
        peerID = ID;
    }
    public void incrementTotalInMessages(){
        totalInMessages++;
    }
}
