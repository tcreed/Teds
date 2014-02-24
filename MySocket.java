package us.wsu.compnet.reliabletransfer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sender code completed by Ted Reed
 * Instructor Dr. Murtuza Jadliwala
 */

/** 
 * This class implements a sender of the Stop-And-Wait reliable transfer protocol
 */
public class MySocket
{			
	private int windowSize;	// For the SR protocol only: size of the window 
	private int timeoutDuration;// The duration of a timeout.
	private int seqNum; //packet sequence number
        private int lastSentSeqNum; //packet sequence number for last packet sent 
        private int ackSeqNumber; // sequence number that the receiver is saying
                                  //it last received  
        private int port;// port number sender and receiver operate with
        InetAddress IPAddress;//IP address of receiver
        
	private PipedInputStream internalInputStream;// Stream from which we
        // read data from the upper layer
	private PipedOutputStream upperLayerStream;// Stream given to upper
        // layer, connected to internalInputStream
	
	private MySocket stateMutex;// Mutual access object

	private WaitForAcks waitForAcks;// Thread responsible for ack handling 
	private FromUpperLayer fromUpperLayer;	// Thread responsible for
        // handling data from the upper layer
	private Timer timeoutTimer;// Timer used for scheduling data packet
        //timeouts
      
        private DatagramSocket senderSocket;//socket used by sender to
                            //communicate with the upperlayer and receiver
        private LinkedList packetBuffer;//a variable for a linked list that acts
        //as a buffer for the data teh sender is reading in from the upper layer

	/**
	 * Creates a sender socket for communication with a receiver at
         * a given IP address and port. 
	 */
	public MySocket(InetAddress IPAddress, int port) throws IOException
        {
		// Open the UDP socket.
                senderSocket = new DatagramSocket();

		// Initialize the streams.
		this.internalInputStream = new PipedInputStream(); 
		this.upperLayerStream =
                        new PipedOutputStream(internalInputStream);
                this.port = port;
                this.IPAddress = IPAddress;
		
		// Initialize packet buffer and other state variables.

               packetBuffer = new LinkedList(); 
               packetBuffer.clear();
               seqNum = 0;
               ackSeqNumber = 0;
                 
		
		// The mutex.
		this.stateMutex = this;

		// Initialize threads and timer.

		this.waitForAcks = new WaitForAcks();
		this.fromUpperLayer = new FromUpperLayer();
		this.timeoutTimer = new Timer(true);		

		// Start the threads.
		(new Thread(waitForAcks)).start();
		(new Thread(fromUpperLayer)).start();
	}
	
	/**
	 * Returns the OutputStream associated with the socket. 
	 */
	public OutputStream getOutputStream ()
        {
		return upperLayerStream;
	}

	/**
	 * Encapsulates the given data packet into a UDP datagram and sends
         * it over the network.  
	 */
	private synchronized void send(MyDataPacket packet)
        {
                // Send a datagram with the data packet.
                byte[] bytePacketData;// 
                DatagramPacket aSenderPacket;//
                int bytePacketLength;// 
                
                lastSentSeqNum = packet.getSeqNum();
                
                bytePacketData = packet.toByteArray();
                bytePacketLength = bytePacketData.length;
                        
                aSenderPacket = new DatagramPacket(bytePacketData,0
                            ,bytePacketLength,IPAddress,port);
                              
           
                // Send a datagram with the data packet.
                try
                {                   
                    senderSocket.send(aSenderPacket);

                    // Schedule PacketTimeout timer task associated to this
                    //packet
                    timeoutTimer.schedule(new PacketTimeout(packet)
                            ,timeoutDuration);     
                }
                catch (IOException ex) {
                    Logger.getLogger(MySocket.class.getName()).log(Level.
                            SEVERE, null, ex);
                }              
             					
	}

	/**
	 * Class responsible for buffering and/or sending data arriving from
         * the upper layer
	 */
        
	private class FromUpperLayer implements Runnable
        {           
            @Override
		public void run()
                {                 
                    byte[] bytePacketData = new byte[MyDataPacket.MAX_SIZE];
                    //bytePacketData is byte array with sequence number and
                    //payload                  
               
                    byte[] dataFromApplication; //data read in from application
                    //to be sent to receiver  
                    int bytePacketLength;
                    
                    while(true)
                    {           
                        bytePacketLength = bytePacketData.length;
                        MyDataPacket aMyDataPacket = null;       
                        dataFromApplication = new byte[bytePacketLength];
                        int numberOfBytes = 0;
 
                    try
                    {                       
                         // Read data from upper layer in to
                                //bytePacketData
                         numberOfBytes = internalInputStream.read	
                                        (dataFromApplication);      
                         
                    } catch (IOException ex)
                    {
                        Logger.getLogger(MySocket.class.getName())
                                .log(Level.SEVERE, null, ex);
                    }
                                           
                    if(numberOfBytes > 0)
                    {
                        // Create and store a MyDataPacket
                        aMyDataPacket = new MyDataPacket
                                (seqNum,dataFromApplication,numberOfBytes );
                    }
                        
                    // thread  A from source to be buffered
                    synchronized (stateMutex)
                    {                               
                        packetBuffer.add(aMyDataPacket); 
                        seqNum++;// Increments the value of the sequence
                        //number used for creating data packets
                    }

                    // thread B to read from buffer
                    synchronized (stateMutex)
                    {   
                        if(lastSentSeqNum == ackSeqNumber)
                        {
                        // If not waiting for an ack, send the packet 

                            aMyDataPacket = (MyDataPacket)packetBuffer.remove();

                            send(aMyDataPacket);//sends data packet 

                        }    

                    } 

                }
            } 
	}
 
	/**
	 * Class responsible for handing incoming ack packets
	 */
	private class WaitForAcks implements Runnable
        {
            public void run()
            {
   
                byte[] ackDataGram = new byte[MyAckPacket.MAX_SIZE];
                //holds incoming datagram with ack data
                int length = ackDataGram.length;//number of bytes of data the
                //ack dataGram holds
        
                while(true)
                {
                    //create buffer for receiving ack datagrams
                    DatagramPacket receiveAck = new DatagramPacket
                            (ackDataGram, length);
                    try
                    {                                    
                        // Wait for an ack
                        senderSocket.receive(receiveAck);// receives datagram 
                        //acks from receiver. Method blocks while waiting for
                        //datagrams             
                    }
                    catch (IOException ex)
                    {
                        Logger.getLogger(MySocket.class.getName()).log(Level
                                .SEVERE, null, ex);
                    }
                    //return sequence number for ack
                    ackSeqNumber = new MyAckPacket(ackDataGram).getSeqNum();                   
                 }
            }
	}
				
	/**
	 * Class responsible for handling a data packet timeout
	 */
	private class PacketTimeout extends TimerTask
        {
            MyDataPacket curPacket = null;

            public PacketTimeout(MyDataPacket curPacket)//constructor
            {
                    this.curPacket = curPacket;
            }

            @Override
            public void run()
            {
                // Resend packet if not acked yet
                if(lastSentSeqNum != ackSeqNumber)
                {
                    send(curPacket);//sends data packet 
                }
            }	
	}
	
	public int getWindowSize()
        {
		return windowSize;
	}

	public void setWindowSize(int windowSize)
        {
		this.windowSize = windowSize;
	}
	
	public int getTimeoutDuration()
        {
		return timeoutDuration;
	}

	public void setTimeoutDuration(int timeoutDuration)
        {
		this.timeoutDuration = timeoutDuration;
	}
}




