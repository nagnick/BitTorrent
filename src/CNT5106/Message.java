package CNT5106;
public class Message {
    public enum MessageTypes{
        choke,//0
        unchoke,//1
        interested,//2
        notInterested,//3
        have,//4
        bitfield,//5
        request,//6
        piece//7
    }
    int length; // 4 bytes does not include itself first part in message
    byte type; // 1 byte second part in message
    String payload; // variable size message payload last in message
    public Message(int length,byte type,String payload){
        this.length = length;
        this.type = type;
        this.payload = payload;
    }
    char[] toBytes(){ // easy to send
        return (String.valueOf(length) + String.valueOf(type) + payload).toCharArray(); // convert everything to sting then to char[] aka byte array
    }
}
