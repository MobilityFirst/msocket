/**
 * Mobility First - Global Name Resolution Service (GNS)
 * Copyright (C) 2013 University of Massachusetts - Emmanuel Cecchet.
 * Contact: cecchet@cs.umass.edu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Initial developer(s): Emmanuel Cecchet.
 * Contributor(s): ______________________.
 */

package edu.umass.cs.msocket;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.logging.Level;
import edu.umass.cs.msocket.logger.MSocketLogger;

/**
 * This class implements the Inbuffer of the MSocket. Out of order data is read
 * from the input stream and stored in the Inbuffer. Inbuffer is implemented
 * as priority queue.
 *
 * @author <a href="mailto:cecchet@cs.umass.edu">Emmanuel Cecchet</a>
 * @version 1.0
 */
public class InBufferHeapImpl {


	PriorityQueue<InBufferStorageChunk> rbuf = null;


	int                            dataReadSeq        = 0;                                                 // assuming
	                                                                                                           // that
	                                                                                                           // data
	                                                                                                           // starts
	                                                                                                           // from
	                                                                                                           // 0
	                                                                                                           // seq
	                                                                                                           // num
	int                            byteRecvInInbuffer = 0;                                                 // mainly
	                                                                                                           // for
	                                                                                                           // ideal
	                                                                                                           // case
	                                                                                                           // of
	                                                                                                           // multipath

	InBufferHeapImpl()
	{
		Comparator<InBufferStorageChunk> comparator = new InBufferStorageChunkComparator();
	    rbuf = new PriorityQueue<InBufferStorageChunk>(10, comparator);
	}

	  public synchronized boolean putInBuffer(InBufferStorageChunk Obj)
	  {

		MSocketLogger.getLogger().log(Level.FINE, "PutInBuffer {0}", rbuf.size());
	    byteRecvInInbuffer += Obj.chunkSize; // may not be accurate if there are
	                                         // retransmissions due to migration or
	                                         // otherwise

	    if( dataReadSeq - (Obj.startSeqNum+Obj.chunkSize) >= 0 )
		{
			return false;
		}
	    rbuf.add(Obj);

	    MSocketLogger.getLogger().log(Level.FINE, "putInBuffer returned {0}", rbuf.size());
	    return true;
	  }

	  public int getInBuffer(byte[] b)
	  {
	    return getInBuffer(b, 0, b.length);
	  }

	  public synchronized int getInBuffer(byte[] b, int offset, int length)
	  {

		MSocketLogger.getLogger().log(Level.FINE, "getInBuffer called, size {0}", rbuf.size());
	    int numread = 0;
	    InBufferStorageChunk curChunk = rbuf.peek();

	    if(curChunk != null)
	    {
		    while( dataReadSeq - (curChunk.startSeqNum + curChunk.chunkSize) >= 0)
		    {

		    	MSocketLogger.getLogger().log(Level.FINE,"Data delete loop {0}, dataReadSeq {1}, curChunk.startSeqNum {2}, curChunk.chunkSize {3}.",new Object[]{rbuf.size(),dataReadSeq,curChunk.startSeqNum,curChunk.chunkSize});
		    	InBufferStorageChunk removed = rbuf.poll();
		    	removed.chunkData = null;
		    	removed = null;

		    	curChunk = rbuf.peek();
		    	if(curChunk == null)
		    		break;
		    }
	    }

	    while( rbuf.size()>0 )
	    {

	    	MSocketLogger.getLogger().log(Level.FINE,"Data read loop {0}, dataReadSeq {1}, curChunk.startSeqNum {2}, curChunk.chunkSize {3}.",new Object[]{rbuf.size(),dataReadSeq,curChunk.startSeqNum,curChunk.chunkSize});

	    	curChunk = rbuf.peek();

	    	// inordered data not there
	    	if(dataReadSeq - curChunk.startSeqNum < 0)
	    	{
	    		break;
	    	}
	    	// inordered data there
	    	if ( (dataReadSeq - curChunk.startSeqNum >= 0) && ( dataReadSeq - (curChunk.startSeqNum + curChunk.chunkSize) < 0) )
			{

				int srcPos =  Math.max(0, dataReadSeq - curChunk.startSeqNum);
				// FIXME: check for long to int conversion
				int cpylen = curChunk.chunkSize - srcPos;
				int actlen = 0;
				if ((numread + cpylen) - length > 0)
				{
					actlen = length - numread;
				}
				else
				{
					actlen = cpylen;
				}
				System.arraycopy(curChunk.chunkData, srcPos, b, offset+numread, actlen);
				numread += actlen;
				dataReadSeq += actlen;
				if (numread - length >= 0)
					{
						// completely read the current chunk, free it
			    		if( dataReadSeq - (curChunk.startSeqNum + curChunk.chunkSize) >= 0 )
			    		{
			    			InBufferStorageChunk removed = rbuf.poll();
					    	removed.chunkData = null;
					    	removed = null;
			    		}
						break;
					}
			}

	    	// completely read the current chunk, free it
	    	if( dataReadSeq - (curChunk.startSeqNum + curChunk.chunkSize) >= 0)
	    	{
	    		InBufferStorageChunk removed = rbuf.poll();
		    	removed.chunkData = null;
		    	removed = null;
	    	}
	    }


			MSocketLogger.getLogger().log(Level.FINE,"getInBuffer called returned {0} size: {1}", new Object[]{numread,rbuf.size()});

	    return numread;
	  }

  /**
	 * Checks if the given data seq num is for in ordered data,
	 * if that is the case then it is returned directly from stream
	 * and not stored in input buffer.
	 *
	 * @return
	 */
	public synchronized boolean isDataInOrder(int chunckStartSeq, int chunkLength) {

		// if dataReadSeq is in between this chunk data, then it is in-order
		if( ( dataReadSeq - chunckStartSeq >= 0) && ( dataReadSeq - (chunckStartSeq + chunkLength) < 0) )
		{
			return true;
		}
		return false;
	}

	/**
	 * Copy data read from stream to the app buffer. Also updates the dataReadSeqNum
	 * It bypasses the storing of data in input buffer
	 * @param readFromStream
	 * @param startSeqNum
	 * @param appBuffer
	 * @param offset
	 * @param appLen
	 */
	public synchronized int copyOrderedDataToAppBuffer(byte[] readFromStream, int startSeqNum,
			int chunkLen, byte[] appBuffer, int offset, int appLen)
	{
		if(chunkLen > 0)
		{
			MSocketLogger.getLogger().log(Level.FINE,"copyOrderedDataToAppBuffer, startSeqNum {0}, chunkLen {1}, offset {2}, appLen {3}, readFromStream[0] {4}", new Object[]{startSeqNum,chunkLen,offset,appLen,readFromStream[0]});
		}
		int actualCopied =0;
		if( (dataReadSeq - startSeqNum >= 0) && (dataReadSeq - (startSeqNum+chunkLen) < 0) )
		{
			int srcPos = (int)Math.max(0,dataReadSeq-startSeqNum);
			//FIXME: check for long to int conversion
			int cpylen=chunkLen-srcPos;
			actualCopied = cpylen;
			System.arraycopy(readFromStream, srcPos, appBuffer, offset , cpylen );
			dataReadSeq+=cpylen;
		}
		return actualCopied;
	}

	public long getDataReadSeqNum() {
		return dataReadSeq;
	}

	/**
	 * return the size of inbuffer in number of elements
	 * @return
	 */
	public long getInBufferSize() {
		return rbuf.size();
	}

	public static void main(String[] args)
    {
    }

}
