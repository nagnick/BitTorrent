package CNT5106;

import java.nio.charset.StandardCharsets;

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
    int peerID; // handshake only field maybe useful in message queue
    public Message(int length,MessageTypes type,String payload){
        this.length = length;
        this.type = type;
        this.payload = payload;
    }
    public Message(byte[] input){ // fix to read byte representations
        String messageContent = String.valueOf(input);
        if(messageContent.substring(0,18).equals(handShake.substring(0,18))){
            this.type = MessageTypes.handShake;
            this.peerID = Integer.getInteger(messageContent.substring(28));
        }
        else{
            this.length = Integer.getInteger(messageContent.substring(0,4));
            this.type = switch ( Integer.getInteger(messageContent.substring(4,5))){
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
            this.payload = messageContent.substring(5);
        }
    }
    byte[] toBytes(){ // easy to send
        if(type == MessageTypes.handShake){
            return (handShake + peerID).getBytes(StandardCharsets.UTF_8); // FIX to print peer in 4 byte representation
        }
        else {
            return (String.valueOf(length) + String.valueOf(type.ordinal()) + payload).getBytes(StandardCharsets.UTF_8); // convert everything to sting then to char[] aka byte array
            // type.ordinal returns the int representation
            // FIX all above to print in byte representations
        }
    }
}
