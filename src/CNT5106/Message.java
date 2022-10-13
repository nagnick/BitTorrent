package CNT5106;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class Message {
    public enum MessageTypes{
        choke,//0
        unchoke,//1
        interested,//2
        notInterested,//3
        have,//4
        bitfield,//5
        request,//6
        piece,//7
        handShake //8 special case to denote HandShakeMessage
    }
    int length; // 4 bytes does not include itself first part in message
    MessageTypes type; // 1 byte second part in message
    String payload; // variable size message payload last in message
    String handShake = "P2PFILESHARINGPROJ0000000000"; // handshake only field
    int peerID; // handshake only field maybe useful in message queue!!!! to know what came from what
    public Message(int length,MessageTypes type,String payload){ // make any other message
        this.length = length;
        this.type = type;
        this.payload = payload;
    }
    public Message(int peerID){ // make handshake message
        this.peerID = peerID;
        type = MessageTypes.handShake;
    }
    public Message(byte[] input, boolean handshake){ // fix to read byte representations
        ByteBuffer mybuff = ByteBuffer.allocate(input.length).put(input).order(ByteOrder.BIG_ENDIAN);
        if(handshake){
            this.type = MessageTypes.handShake;
            this.peerID = mybuff.getInt(28);
        }
        else{
            this.length = mybuff.getInt(0);
            this.type = switch ((int)mybuff.get(4)){
                case(0)-> MessageTypes.choke;
                case(1)-> MessageTypes.unchoke;
                case(2)-> MessageTypes.interested;
                case(3)-> MessageTypes.notInterested;
                case(4)-> MessageTypes.have;
                case(5)-> MessageTypes.bitfield;
                case(6)-> MessageTypes.request;
                case(7)-> MessageTypes.piece;
                default -> throw new RuntimeException("Unexpected message type in char[] constructor of Message\n");
            };
            this.payload = new String(Arrays.copyOfRange(input,5,input.length));
        }
    }
    byte[] toBytes(){ // easy to send
        if(type == MessageTypes.handShake){
            ByteBuffer mybuff = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN); // is this right little endian
            mybuff.put(handShake.getBytes());
            mybuff.position(28).putInt(peerID);
            return mybuff.array();
        }
        else {
            ByteBuffer mybuff = ByteBuffer.allocate(5 + length).order(ByteOrder.BIG_ENDIAN); // is this right little endian
            mybuff.putInt(length);
            mybuff.position(4).put((byte)type.ordinal());
            mybuff.position(5).put(payload.getBytes());
            return mybuff.array();
            // type.ordinal returns the int representation
        }
    }

    @Override
    public String toString() {
        String myString;
        if(type == MessageTypes.handShake) {
            myString = "HandShakeHeader: " + handShake +" PeerID: " + peerID;
        }
        else{
           myString = "Length: " +length +" Type: "+ type.toString()+" Payload: " + payload;
        }
        return myString;
    }
}
