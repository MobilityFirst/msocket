/*******************************************************************************
 *
 * Mobility First - mSocket library
 * Copyright (C) 2013, 2014 - University of Massachusetts Amherst
 * Contact: arun@cs.umass.edu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Initial developer(s): Arun Venkataramani, Aditya Yadav, Emmanuel Cecchet.
 * Contributor(s): ______________________.
 *
 *******************************************************************************/

package edu.umass.cs.msocket;

import java.io.IOException;
import java.util.Random;
import java.util.Vector;
import java.util.logging.Level;
import edu.umass.cs.msocket.logger.MSocketLogger;

/**
 * This class implements the threads to do background writes for the default
 * multipath data scheduling policy. It is specifically designed for 2 path case
 *
 * @author <a href="mailto:cecchet@cs.umass.edu">Emmanuel Cecchet</a>
 * @version 1.0
 */
public class BackgroundWritingThread implements Runnable
{
  // thread can remain idle(if no write happens) for 5 seconds,
  // after that thread exits.
  public static final int    BACKGROUND_THREAD_IDLE_TIME = 5000;

  private ConnectionInfo     cinfo                       = null;

  // used to keep track of how many chunks have been retransmitted from the
  // endSeqNum
  // private long currRetransmitEndSeqNum = 0;

  // used to keep track of how many chunks have been retransmitted from the
  // getDataBaseSeqNum
  private long               currRetransmitStartSeqNum   = 0;

  // stores the send seq num till which the last retransmit worked,
  // so that it doesn't retransmit same stuff twice.
  private long               lastRetransmitSendSeqNum    = 0;

  // for implementing retransmission, paths which have finished(zero outstanding
  // bytes) for original transmission
  private Vector<SocketInfo> finishedPaths               = null;
  private Vector<SocketInfo> unfinishedPaths             = null;

  // to keep track of how many bytes retransmitted
  private long               totalRetransmitBytes        = 0;

  private boolean            runningStatus               = true;

  public BackgroundWritingThread(ConnectionInfo cinfo)
  {
    this.cinfo = cinfo;

      MSocketLogger.getLogger().log(Level.FINE, "{0}: DataBaseSeqNum {1}", new Object[]{cinfo.getServerOrClient(), cinfo.getDataBaseSeq()});
    finishedPaths = new Vector<SocketInfo>();
    unfinishedPaths = new Vector<SocketInfo>();
  }

  public void run()
  {

    MSocketLogger.getLogger().log(Level.FINE, "BackgroundWritingThread started");

        //Vector<SocketInfo> socketList = new Vector<SocketInfo>();
        //socketList.addAll(cinfo.getAllSocketInfo());
        //getTheFasterPath(socketList);
        long runStart = System.currentTimeMillis();
        waitForOnePathToFinish();
        long finishPath = System.currentTimeMillis();

        //if (!runningStatus)
        //	break;

        // acquiring exclusive read and write lock, no new
        // transmissions can happen during this transmission

        currRetransmitStartSeqNum = lastRetransmitSendSeqNum;
        // currRetransmitEndSeqNum = cinfo.getDataSendSeq(); // starting
        // re-transmission from the
        // back
      while ((cinfo.getDataBaseSeq() - cinfo.getDataSendSeq() < 0) &&
    		  (cinfo.getMSocketState() != MSocketConstants.CLOSED))
      {
        // ByteRangeInfo byteObj = returnNextChunkToRetransmit();
        ByteRangeInfo byteObj = returnNextChunkToRetransmitFromBegin();


        if (byteObj != null)
        {

          MSocketLogger.getLogger().log(Level.FINE, "Sending SeqNum {0}, DataBaseSeqNum {1}, currRetransmitStartSeqNum {2}.", new Object[]{byteObj.getStartSeqNum(),cinfo.getDataBaseSeq(),currRetransmitStartSeqNum});
          boolean operStatus = true;
          do
          {
        	  // block until available to write
              cinfo.blockOnOutputStreamSelector();

              cinfo.setState(ConnectionInfo.READ_WRITE, true);

        	   MSocketLogger.getLogger().log(Level.FINE, "READ_WRITE lock acquired in the Background thread");
        	  continousAckReads();

	          if ((byteObj.getStartSeqNum() + byteObj.getLength()) - cinfo.getDataBaseSeq() >= 0) // re-transmit
	          // only if it
	          // greater than
	          // acknowlded
	          // bytes
	          {

	           MSocketLogger.getLogger().log(Level.FINE, " SendingSeqNum: {0}, DataBaseSeqNum: {1}.", new Object[]{byteObj.getStartSeqNum(),cinfo.getDataBaseSeq()});
	            byte[] retransmitData = cinfo.getDataFromOutBuffer(byteObj.getStartSeqNum(), byteObj.getStartSeqNum()
	                + byteObj.getLength());
	            operStatus = retransmitChunk(retransmitData, byteObj);

	            totalRetransmitBytes = totalRetransmitBytes + retransmitData.length;
	          }
	          if(operStatus)
	          {
	        	  lastRetransmitSendSeqNum = byteObj.getStartSeqNum() + byteObj.getLength();
	          }
	          cinfo.setState(ConnectionInfo.ALL_READY, true);
          } while(!operStatus);
        }
        else
        {
          //lastRetransmitSendSeqNum = cinfo.getDataSendSeq();
          // nothing left to retransmit
          break;
        }
      }

      // work done, set it to inactive
      cinfo.setBackgroundThreadActive(false);


      MSocketLogger.getLogger().log(Level.FINE, "BackgroudMultiPathWritingThread stopped totalRetransmit {0}", totalRetransmitBytes);
  }

  /*public void stopRetransmissionThread()
  {
    runningStatus = false;
  }*/

  private boolean retransmitChunk(byte[] retransmitData, ByteRangeInfo byteObj)
  {
    int length = retransmitData.length;
    //int currpos = 0;

    //int remaining = length;
    long tempDataSendSeqNum = byteObj.getStartSeqNum();

    if ( /*(currpos < length) &&*/ (cinfo.getDataBaseSeq() - cinfo.getDataSendSeq() < 0)
        && (cinfo.getMSocketState() != MSocketConstants.CLOSED))
    {

      // reads input stream for ACKs an stores data in input buffer
      SocketInfo Obj = null;

      if(finishedPaths.size() > 0)
    	  Obj = finishedPaths.get(0);

      if (cinfo.getServerOrClient() == MSocketConstants.CLIENT)
      {

        MSocketLogger.getLogger().log(Level.FINE, "Length: {0}, tempDataSendSeqNum: {1}, cinfo.getDataBaseSeq(): {2}, resnding on {3}.",new Object[]{length,tempDataSendSeqNum,cinfo.getDataBaseSeq(),Obj.getSocketIdentifer()});
      }

      if (Obj != null)
      {
        while (!Obj.acquireLock());

        try
        {
          if (Obj.getneedToReqeustACK())
          {
            handleMigrationInMultiPath(Obj);
            //Obj.releaseLock();
            //continue;
          }

          int arrayCopyOffset = 0;
          DataMessage dm = new DataMessage(DataMessage.DATA_MESG, (int) tempDataSendSeqNum, cinfo.getDataAckSeq(),
              length, 0, retransmitData, arrayCopyOffset);
          byte[] writebuf = dm.getBytes();

          // exception of write means that socket is undergoing migration,
          // make it not active, and transfer same data chuk over another
          // available socket.
          // at receiving side, receiver will take care of redundantly
          // received data

          if ((Integer) Obj.queueOperations(SocketInfo.QUEUE_SIZE, null) > 0)
          {
            cinfo.attemptSocketWrite(Obj);
            Obj.releaseLock();
            return false;
          }
          else
          {
            Obj.queueOperations(SocketInfo.QUEUE_PUT, writebuf);
          }

          cinfo.attemptSocketWrite(Obj);

          Obj.updateSentBytes(length);
          Obj.releaseLock();
        }
        catch (IOException ex)
        {

          MSocketLogger.getLogger().log(Level.FINE, "Write exception caused");
          Obj.setStatus(false);
          Obj.setneedToReqeustACK(true);
          Obj.releaseLock();
          return false;
        }
      }
      else
      // no active finished socket, break. Retransmission not needed here.
      {
        return false;
      }
    }
    // retransmission succeeds
    return true;
  }

  /**
   * @param Obj
   * @throws IOException
   */
  private void handleMigrationInMultiPath(SocketInfo Obj) throws IOException
  {

  MSocketLogger.getLogger().log(Level.FINE, "handleMigrationInMultiPath called");
    // if queue size is > 0 then it means that there is a non-blocking
    // write pending and it should be sent first, instead of migration data
    if ((Integer) Obj.queueOperations(SocketInfo.QUEUE_SIZE, null) > 0)
    {
      //cinfo.attemptSocketWrite(Obj);
      return;
    }


    MSocketLogger.getLogger().log(Level.FINE, "HandleMigrationInMultiPath SocektId {0}.", Obj.getSocketIdentifer());
    cinfo.multiSocketRead();
    int dataAck = (int) cinfo.getDataBaseSeq();

    MSocketLogger.getLogger().log(Level.FINE, "DataAck from other side {0}", dataAck);
    Obj.byteInfoVectorOperations(SocketInfo.QUEUE_REMOVE, dataAck, -1);

    @SuppressWarnings("unchecked")
    Vector<ByteRangeInfo> byteVect = (Vector<ByteRangeInfo>) Obj.byteInfoVectorOperations(SocketInfo.QUEUE_GET, -1, -1);

    for (int i = 0; i < byteVect.size(); i++)
    {
      ByteRangeInfo currByteR = byteVect.get(i);

      if ((Integer) Obj.queueOperations(SocketInfo.QUEUE_SIZE, null) > 0)
      {
        // setting the point to start from next time
        Obj.setHandleMigSeqNum(currByteR.getStartSeqNum());
        //FIXME: need to acquire lock Obj.acquire lock
        cinfo.attemptSocketWrite(Obj);
        return;
      }

      cinfo.multiSocketRead();
      dataAck = (int) cinfo.getDataBaseSeq();

      // already acknowledged, no need to send again
      if (dataAck - (currByteR.getStartSeqNum() + currByteR.getLength()) > 0)
      {
        continue;
      }

      // if already sent
      if ((currByteR.getStartSeqNum() + currByteR.getLength()) -  Obj.getHandleMigSeqNum() < 0)
      {
        continue;
      }

      byte[] buf = cinfo.getDataFromOutBuffer(currByteR.getStartSeqNum(),
          currByteR.getStartSeqNum() + currByteR.getLength());
      int arrayCopyOffset = 0;
      DataMessage dm = new DataMessage(DataMessage.DATA_MESG, (int) currByteR.getStartSeqNum(), cinfo.getDataAckSeq(),
          buf.length, 0, buf, arrayCopyOffset);
      byte[] writebuf = dm.getBytes();

      Obj.queueOperations(SocketInfo.QUEUE_PUT, writebuf);
      cinfo.attemptSocketWrite(Obj);

    }

    Obj.setneedToReqeustACK(false);

    MSocketLogger.getLogger().log(Level.FINE, "HandleMigrationInMultiPath Complete");
  }

  /**
   * returns the next chunk to transmit from the dataBaseSeq num
   *
   * @return
   */
  private ByteRangeInfo returnNextChunkToRetransmitFromBegin()
  {
    int i = 0;
    ByteRangeInfo retByteRange = null;

    // if more chunks gets acknowledged the update the
    // chunk sending
    if (currRetransmitStartSeqNum - cinfo.getDataBaseSeq() < 0)
    {
      currRetransmitStartSeqNum = cinfo.getDataBaseSeq();
    }

    for (i = 0; i < unfinishedPaths.size(); i++)
    {
      SocketInfo Obj = unfinishedPaths.get(i);
      @SuppressWarnings("unchecked")
      Vector<ByteRangeInfo> getVect = (Vector<ByteRangeInfo>) Obj
          .byteInfoVectorOperations(SocketInfo.QUEUE_GET, -1, -1);
      int j = 0;
      // for (j = getVect.size(); j > 0; j--)
      for (j = 0; j < getVect.size(); j++)
      {
        ByteRangeInfo byter = getVect.get(j);
        // TODO: store the index, this continue might run many times
        if ((byter.getStartSeqNum() + byter.getLength()) - currRetransmitStartSeqNum <= 0)
        {
          continue;
        }

        if (((byter.getStartSeqNum() + byter.getLength()) - currRetransmitStartSeqNum > 0) && (retByteRange == null))
        {
          retByteRange = byter;
          break;
        }
        // for sorted chunk across multiple unfinished paths
        else if (((byter.getStartSeqNum() + byter.getLength()) - currRetransmitStartSeqNum > 0) && (retByteRange != null))
        {
          if (retByteRange.getStartSeqNum() - byter.getStartSeqNum() > 0)
          {
            retByteRange = byter;
            break;
          }
        }

      }

    }
    if (retByteRange != null)
    {
      currRetransmitStartSeqNum = retByteRange.getStartSeqNum() + retByteRange.getLength();
      return retByteRange;
    }
    else
    {
      return null;
    }
  }

  private SocketInfo getRandomFinishedSocket()
  {
    Random generator = new Random();
    Vector<SocketInfo> vect = new Vector<SocketInfo>();

    int i = 0;
    while (i < finishedPaths.size())
    {
      SocketInfo value = finishedPaths.get(i);

      if (value.getStatus()) // true means active
      {
        vect.add(value);
      }
      i++;
    }
    if (vect.size() == 0) // denotes all sockets under migration
      return null;

    int index = generator.nextInt(vect.size());
    return vect.get(index); // randomly choosing the socket to send chunk
  }

  private void getTheFasterPath(Vector<SocketInfo> socketList)
  {
    finishedPaths.clear();
    unfinishedPaths.clear();
    long bytesSent = -1;
    int fastSocketID = -1;
    for (int i = 0; i < socketList.size(); i++)
    {
      SocketInfo Obj = socketList.get(i);
      if (Obj.getStatus())
      {
        // faster path, sent more bytes in phase1
        if (Obj.getSentBytes() > bytesSent)
        {
          bytesSent = Obj.getSentBytes();
          fastSocketID = Obj.getSocketIdentifer();
        }
      }
    }

    for (int i = 0; i < socketList.size(); i++)
    {
      SocketInfo Obj = socketList.get(i);
      if (Obj.getStatus())
      {
        // if there is less data outstading, less to get the Acks back, consider
        // it as
        // finished path
        if (fastSocketID == Obj.getSocketIdentifer())
        {

          MSocketLogger.getLogger().log(Level.FINE, "SocketID: {0}, is found to be the faster path. Remote Address is: {1}. Sent bytes are: {2}.",new Object[]{Obj.getSocketIdentifer(),Obj.getSocket().getInetAddress(),Obj.getSentBytes()});
          finishedPaths.add(Obj);

        }
        else
        {
          unfinishedPaths.add(Obj);

          MSocketLogger.getLogger().log(Level.FINE, "unifinished paths size {0}. ", new Object[]{unfinishedPaths.size()});
        }
      }
    }
  }

  private void waitForOnePathToFinish()
  {
    while (cinfo.getDataBaseSeq() - cinfo.getDataSendSeq() < 0)
    {

      MSocketLogger.getLogger().log(Level.FINE, "waitForOnePathToFinish");
      Vector<SocketInfo> socketList = new Vector<SocketInfo>();
      socketList.addAll(cinfo.getAllSocketInfo()); // if there is just one path,
                                                   // no need for this thread

      finishedPaths.clear();
      unfinishedPaths.clear();
      for (int i = 0; i < socketList.size(); i++)
      {
        SocketInfo Obj = socketList.get(i);
        if (Obj.getStatus())
        {
          // if there is less data outstading, less to get the Acks back,
          // consider it as //finished path
          if (Obj.getOutStandingBytes() <= (5 * MWrappedOutputStream.WRITE_CHUNK_SIZE))
          {

          MSocketLogger.getLogger().log(Level.FINE,"SocketID: {0}, is found to have zero outstanding bytes. Remote Address: {1}. Sent Bytes: {2}. Outstanding Bytes: {3}.", new Object[]{Obj.getSocketIdentifer(),Obj.getSocket().getInetAddress(),Obj.getSentBytes(),Obj.getOutStandingBytes()});
            finishedPaths.add(Obj);
          }
          else
          {
            unfinishedPaths.add(Obj);

            MSocketLogger.getLogger().log(Level.FINE,"unifinished paths size {0}.",unfinishedPaths.size() );

            MSocketLogger.getLogger().log(Level.FINE,"SocketID: {0} waitForOnePathToFinish, Remote Address: {1}, Sent Bytes: {2}, Outstanding Bytes: {3}.",new Object[]{Obj.getSocketIdentifer(),Obj.getSocket().getInetAddress(),Obj.getSentBytes(),Obj.getOutStandingBytes()});
          }
        }
      } // found some finished paths.
        // start retransmitting

      if (finishedPaths.size() > 0)
      {
        // find the minimum outstanding bytes path
        // and treat that as finished and rest others
        // as unfinished
        SocketInfo minOutBytesPath = null;
        // more than 1 finished paths
        if (finishedPaths.size() > 1)
        {
          for (int i = 0; i < finishedPaths.size(); i++)
          {
            if (minOutBytesPath == null)
            {
              minOutBytesPath = finishedPaths.get(i);
            }
            else if (minOutBytesPath.getOutStandingBytes() > finishedPaths.get(i).getOutStandingBytes())
            {
              minOutBytesPath = finishedPaths.get(i);
            }
          }
          finishedPaths.clear();
          unfinishedPaths.clear();
          finishedPaths.add(minOutBytesPath);


          MSocketLogger.getLogger().log(Level.FINE,"SocketID: {0} added to finished path. Sentt Bytes: {1}, Outstanding Bytes: {2}.",new Object[]{minOutBytesPath.getSocketIdentifer(),minOutBytesPath.getSentBytes(),minOutBytesPath.getOutStandingBytes()});

          // rest others go into unfinished
          for (int i = 0; i < socketList.size(); i++)
          {
            if (minOutBytesPath.getSocketIdentifer() != socketList.get(i).getSocketIdentifer())
            {
              
              MSocketLogger.getLogger().log(Level.FINE,"SocketID: {0} added to the unfinished path. Sent Bytes: {1}, Outstanding Bytes: {2}.", new Object[]{socketList.get(i).getSocketIdentifer(),socketList.get(i).getSentBytes(),socketList.get(i).getOutStandingBytes()});
              unfinishedPaths.add(socketList.get(i));
            }
          }
        }

        break;
      }

      synchronized (cinfo.getInputStreamSelectorMonitor())
      {
        cinfo.blockOnInputStreamSelector();
      }
      if (!runningStatus)
        break;

      // need to get the lock to read, do not block here
      // as other thread might have read acks
      if (cinfo.setState(ConnectionInfo.READ_WRITE, true))
      {
        continousAckReads();
        cinfo.setState(ConnectionInfo.ALL_READY, true);
      }
    }
  }

  private void continousAckReads()
  {
    boolean runAgain = false;
    // do
    // {
    runAgain = false;
    long oldDataBaseSeqNum = cinfo.getDataBaseSeq();
    try
    {
      cinfo.multiSocketRead();
    }
    catch (IOException e)
    {
      // e.printStackTrace();
    	MSocketLogger.getLogger().fine(e.getMessage());
    }

    long newDataBaseSeqNum = cinfo.getDataBaseSeq();

    // if an ack was read, read it again
    if (newDataBaseSeqNum - oldDataBaseSeqNum > 0)
    {
      runAgain = true;
    }
    // } while(runAgain);
  }

  /**
   * returning the sorted order last chunk on unfinished paths
   *
   * @return
   */
  /*
   * private ByteRangeInfo returnNextChunkToRetransmit() { int i = 0;
   * ByteRangeInfo retByteRange = null; for (i = 0; i < unfinishedPaths.size();
   * i++) { SocketInfo Obj = unfinishedPaths.get(i);
   * @SuppressWarnings("unchecked") Vector<ByteRangeInfo> getVect =
   * (Vector<ByteRangeInfo>) Obj .byteInfoVectorOperations(SocketInfo.QUEUE_GET,
   * -1, -1); int j = 0; for (j = getVect.size(); j > 0; j--) { ByteRangeInfo
   * byter = getVect.get(j - 1); if (((byter.getStartSeqNum() <
   * currRetransmitEndSeqNum) && (retByteRange == null))) { retByteRange =
   * byter; break; } else if (retByteRange != null) { if
   * (retByteRange.getStartSeqNum() < byter.getStartSeqNum()) { retByteRange =
   * byter; break; } } } } if (retByteRange != null) { currRetransmitEndSeqNum =
   * retByteRange.getStartSeqNum(); return retByteRange; } else { return null; }
   * }
   */

  /**
   * returns true if there are outstanding bytes on any path
   *
   * @return
   */
/*  private boolean outstandingBytesOnAnyPath()
  {
    Vector<SocketInfo> socketList = new Vector<SocketInfo>();
    socketList.addAll(cinfo.getAllSocketInfo());

    // if there are less than 2 sockets, then
    // where will the retransmission happen
    if (socketList.size() < 2)
      return false;

    boolean retvalue = false;
    for (int i = 0; i < socketList.size(); i++)
    {
      SocketInfo sockObj = socketList.get(i);

      if (sockObj.getStatus()) // if active
      {
        if (sockObj.getOutStandingBytes() > 0)
        {
          retvalue = true;
          break;
        }
      }
    }

    if (retvalue)
    {
      // something new has been sent, need to retransmit has come
      if (lastRetransmitSendSeqNum < cinfo.getDataSendSeq())
      {
        retvalue = true;
      }
      else
      // no need to retransmit, already been retransmitted
      {
        retvalue = false;
      }
    }
    return retvalue;
  }*/

}
