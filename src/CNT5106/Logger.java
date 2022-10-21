import java.io.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;


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
	
	public void writeId()
	{
		curTime = LocalDateTime.now();
		try
		{
			FileWriter myFile = new FileWriter(filePath,true);
			String output = "";
			output = formatter.format(curTime)+": "+myPeerID + ": "+"This is " + " Output\n";
			myFile.append(output);
			myFile.close();
		}
		catch (IOException exception)
		{
			System.out.println(exception.getMessage());
		}
	}
	public static void main(String args[])
	{
		Logger myLog = new Logger("Bob.txt", 1);
		System.out.println(myLog.filePath);
		myLog.writeId();
		myLog.writeId();
	}
	
	//https://www.tutorialspoint.com/java/java_object_classes.htm
	//https://www.geeksforgeeks.org/java-program-to-write-into-a-file/
	//https://stackoverflow.com/questions/4871051/how-to-get-the-current-working-directory-in-java
	//https://stackoverflow.com/questions/4614227/how-to-add-a-new-line-of-text-to-an-existing-file-in-java
	
}

