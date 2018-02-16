package protocols;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

import filesystem.Chunk;
import filesystem.FileInstance;
import filesystem.Metadata;
import server.Peer;
import utils.Constants;
import utils.Message;
import utils.TypeMessage;

public class Backup {
	private Peer peer;
	
	public Backup(Peer peer) {
		this.peer = peer;
	}
	
	public void sendFileChunks(String path, String version, String senderId, int repDegree) {		
		Metadata metadata = new Metadata(path);						// gets metadata (filename, lastModified and owner) using the given file path
		String fileId = metadata.generateFileId();					// generate file id using metadata information
		
		System.out.println("*** BACKUP: Attempting to backup file " + metadata.getFilename() + " ***");
		
		byte[] buffer = new byte[Constants.MAX_CHUNK_SIZE];			// buffer to get each chunk's data
		int chunkNo = 0;
		
		if(!peer.getFileKeeper().fileExists(fileId)) {				// checks whether the file instance exists on this peer
			FileInstance f = new FileInstance(fileId, repDegree);	// if file instance doesn't exist, create a new one
			peer.getFileKeeper().addFile(f);
			
			System.out.println("Added file " + fileId + " to file keeper...");
		} else {
			Vector<Chunk> chunks = new Vector<Chunk>();						// if file already exists on file keeper...
			peer.getFileKeeper().getFile(fileId).setChunks(chunks);			// ...reset chunks...
			peer.getFileKeeper().getFile(fileId).setRepDegree(repDegree);	// ...and replication degree
		}
		
		FileInputStream in = null;
		
		try {
			in = new FileInputStream(path);
		} catch (FileNotFoundException e) {
			System.out.println("*** BACKUP: File was not opened correctly! Given path is not correct. ***");
			e.printStackTrace();
		}
		
		int bytesRead;
		
		try {
			bytesRead = in.read(buffer);
		
			while(bytesRead != -1)
			{	
				if (!this.putchunk(version, senderId, fileId, chunkNo, repDegree, buffer)){
					System.err.println("*** BACKUP: Error sending file!! ***");
					return;
				}
				
				chunkNo++;
				bytesRead = in.read(buffer);
			}
			
			if(bytesRead == 0){
				if (!this.putchunk(version, senderId, fileId, chunkNo, repDegree, null)) {
					System.err.println("*** BACKUP: Error sending file!! ***");
					return;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public boolean putchunk(String version, String senderId, String fileId, int chunkNo, int repDegree, byte[] body) {
        boolean done = false;
        int tries = 0;
        int delay = 1000;

        String header = Message.createHeader(TypeMessage.PUTCHUNK, version, senderId, fileId, chunkNo, repDegree);
        Message msg = null;
        
        if(body != null)
        	msg = new Message(header, body);		// creates PUTCHUNK message to send over the mdb channel
        else msg = new Message(header);
        
        while (!done && tries < 5) {				// "The initiator will send at most 5 PUTCHUNK messages per chunk"
			peer.getMdbChannel().sendMessage(msg);	// send message over the MDB channel (backup channel). All opened MDB channels will receive this message.
			
        	try {
				Thread.sleep(delay);				// "The initiator-peer collects the confirmation messages during a time interval of one second"
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        	
        	if (!peer.getFileKeeper().getFile(msg.getFileId()).chunkExists(msg.getChunkNo())) {		// checks if chunk instance already exists on this peer
        		Chunk chunk = new Chunk(msg.getFileId(), msg.getChunkNo(), msg.getBody());
        		peer.getFileKeeper().getFile(msg.getFileId()).addChunk(chunk);						// if chunk instance does not exist on this peer, one is created
			}
        	
        	int actualRepDegree = peer.getFileKeeper().getChunkRepDegree(msg.getFileId(), msg.getChunkNo());
        	
        	System.out.println("\r\n\t\tACTUAL REPLICATION DEGREE OF CHUNK " + msg.getChunkNo() + ": " + actualRepDegree+ "\r\n");
        	
        	if (repDegree <= actualRepDegree) {				// check whether desired replication degree (repDegree) has been reached
        		System.out.println("*** BACKUP: Backup of chunk " + msg.getChunkNo() + " from file " + msg.getFileId() + " was successful ***");
        		done = true;
        	} else {
        		System.out.println("*** BACKUP: Couldn't store chunk " + msg.getChunkNo() + " with desired replication degree. Trying again... ***");
        		tries++;
        		delay = 2*delay;		// double the time interval for receiving confirmation messages
        		
        		System.out.println("NUMBER OF TRIES " + tries);
        		
        		if(tries > 5) {
        			System.err.println("*** BACKUP: Error sending PUTCHUNK message. Maximum number of tries achieved ***");
        			return false;
        		}
        	}
        }

		return true;
	}
	
	public void stored(String version, String senderId, String fileId, int chunkNo){
	    String header = Message.createHeader(TypeMessage.STORED, version, senderId, fileId, chunkNo);
		Message msg = new Message(header);
		
		Random randomGenerator = new Random();
		Integer randomInt = randomGenerator.nextInt(400);
		
		try {
			Thread.sleep(randomInt);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		peer.getMcChannel().sendMessage(msg);
	}

	public Peer getPeer() {
		return peer;
	}
}