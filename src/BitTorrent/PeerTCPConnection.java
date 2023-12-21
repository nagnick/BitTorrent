package BitTorrent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;

public class PeerTCPConnection implements Runnable { // spinning thread waiting for peer messages
    Thread thread;
    LinkedBlockingQueue<Message> inbox;
    Socket connection;
    DataInputStream in;
    DataOutputStream out;
    int peerID;
    int totalInMessages = 0;
    int totalOptimisticPeriods = 0;
    int totalPreferredPeriods = 0;
    double downloadRate = 0;
    boolean interested = false;
    boolean iamInterested = false;
    boolean choked = true; // my view of this peer whether I have choked it or not
    boolean iamChoked = true; // this peers view of me
    boolean haveFile = false; // used to decide when to terminate program
    private volatile boolean terminate; // to kill threads

    public PeerTCPConnection(LinkedBlockingQueue<Message> inbox, Socket connection){ // pass in peer info to form tcp connection
        this.inbox = inbox;
        this.connection = connection;
        terminate = false;
        try {
            out = new DataOutputStream(connection.getOutputStream());
            in = new DataInputStream(connection.getInputStream());
        }
        catch(Exception e){
            System.err.println("Error creating TCP connection ");
        }
    }
    public Message getHandShake(){ // use before starting thread and only once will fail if anyother message
        byte[] messageBytes = new byte[32]; // puts a zero int in message if bellow fails
        try {
            messageBytes = in.readNBytes(32);
        }
        catch (Exception e) {
            System.err.println("Error reading TCPIn handshake" + e.toString());
        }
        return new Message(messageBytes, true,peerID); // peerID not used for handshake peerId in message is read instead
    }
    public void start() {
        thread = new Thread(this);
        thread.start();
    }
    public void run(){
        try {
            // tcp network stuff
            while(!connection.isClosed() && !terminate && !thread.isInterrupted()) { // testing required
                // read bytes and create message to put in message queue inbox
                ByteBuffer lengthBuff = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
                int messageLength = lengthBuff.put(in.readNBytes(4)).getInt(0);
                ByteBuffer message = ByteBuffer.allocate(5+messageLength).order(ByteOrder.BIG_ENDIAN);
                message.putInt(0,messageLength);
                message.put(4,in.readNBytes(1)[0]);
                if(messageLength != 0) {
                    message.position(5);
                    message.put( in.readNBytes(messageLength),0,messageLength);
                }
                Message toPutInInbox = new Message(message.array(),false,peerID);
                inbox.put(toPutInInbox);
                if(toPutInInbox.type == Message.MessageTypes.piece)
                    incrementTotalInMessages(); // used to track download rate for choking/unchoking
            }
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
            System.err.println("TCP Thread was killed");
        }
        catch (Exception e) {
            System.err.println("TCP Thread was killed");
        }
    }
    public boolean send(Message message){
        try {
            out.write(message.toBytes());
            out.flush();
            return true;
        }
        catch (Exception e){
            System.err.println("Error writing bytes in send method"+ e);
        }
        return false;
    }
    public void close(){
        try {
            thread.interrupt();// ensure clean exit
            out.flush();
            out.close();
            in.close();
            thread.join();
            connection.close();
            terminate = true;
        }
        catch (Exception e){
            System.err.println("Closing TCP connection" + e);
        }
    }
    public void setPeerId(int ID){
        peerID = ID;
    }
    public void incrementTotalInMessages(){
        totalInMessages++;
    }
}