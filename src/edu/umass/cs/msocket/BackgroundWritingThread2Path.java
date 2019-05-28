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
import java.nio.ByteBuffer;
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
public class BackgroundWritingThread2Path
{
  private ConnectionInfo     cinfo                     = null;

  // indicates till how long this thread will run and retransmit data.
  // should come from the constructor
  private long               endSeqNum                 = 0;

  // used to keep track of how many chunks have been retransmitted from the
  // endSeqNum
  private long               currRetransmitEndSeqNum   = 0;

  // used to keep track of how many chunks have been retransmitted from the
  // getDataBaseSeqNum
  private long               currRetransmitStartSeqNum = 0;

  // for implementing retransmission, paths which have finished(zero outstanding
  // bytes) for original transmission
  private Vector<SocketInfo> finishedPaths             = null;
  private Vector<SocketInfo> unfinishedPaths           = null;

  public BackgroundWritingThread2Path(long endSeqNum, ConnectionInfo cinfo)
  {
    this.endSeqNum = endSeqNum;
    this.cinfo = cinfo;
    // MSocketLogger.getLogger().fine(cinfo.getServerOrClient() + " endSeqNum " + endSeqNum + "getDataBaseSeq num " + cinfo.getDataBaseSeq());
    MSocketLogger.getLogger().log(Level.FINE,"{0} -> endSeqNum: {1}, DataBaseSeqNum: {2}", new Object[]{cinfo.getServerOrClient(),endSeqNum,cinfo.getDataBaseSeq()});
    finishedPaths = new Vector<SocketInfo>();
    unfinishedPaths = new Vector<SocketInfo>();
  }

  public void run()
  {
    long runStart = System.currentTimeMillis();

    Vector<SocketInfo> socketList = new Vector<SocketInfo>();
    socketList.addAll(cinfo.getAllSocketInfo());

    // if there is just one path, no need for this thread
    if (socketList.size() < 2)
    {
      return;
    }

    // getTheFasterPath(socketList);
    waitForOnePathToFinish();

    // sleeping for 1 RTT for acks to arrive
    SocketInfo fastSocket = finishedPaths.get(0);
    if (fastSocket.getEstimatedRTT() > 0)
    {
      try
      {
        Thread.sleep(2 * fastSocket.getEstimatedRTT());
      }
      catch (InterruptedException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    long finishPath = System.currentTimeMillis();
    long totalRetransmit = 0;
    currRetransmitStartSeqNum = cinfo.getDataBaseSeq();
    currRetransmitEndSeqNum = endSeqNum; // starting re-transmission from the
    // back

    while (cinfo.getDataBaseSeq() - endSeqNum < 0)
    {
      continousAckReads();

      // ByteRangeInfo byteObj = returnNextChunkToRetransmit();
      ByteRangeInfo byteObj = returnNextChunkToRetransmitFromBegin();
      if (byteObj != null)
      {
    	  // MSocketLogger.getLogger().fine("sending seq num " + byteObj.getStartSeqNum() + "getDataBaseSeq num " + cinfo.getDataBaseSeq()
            // + " currRetransmitStartSeqNum " + currRetransmitStartSeqNum);
        MSocketLogger.getLogger().log(Level.FINE,"Sending seq num {0}, DataBaseSeqNum: {1}, currRetransmitStartSeqNum: {2}.", new Object[]{byteObj.getStartSeqNum(),cinfo.getDataBaseSeq(),currRetransmitStartSeqNum});
      }

      if (byteObj != null)
      {
        if ((byteObj.getStartSeqNum() + byteObj.getLength()) - cinfo.getDataBaseSeq() >= 0) // re-transmit
        // only if it
        // greater than
        // acknowlded
        // bytes
        {
          // MSocketLogger.getLogger().fine("sending seq num " + byteObj.getStartSeqNum() + "getDataBaseSeq num " + cinfo.getDataBaseSeq());
          MSocketLogger.getLogger().log(Level.FINE,"Sending seq num: {0}, DataBaseSeqNum: {1}", new Object[]{byteObj.getStartSeqNum(),cinfo.getDataBaseSeq()});
          byte[] retransmitData = cinfo.getDataFromOutBuffer(byteObj.getStartSeqNum(), byteObj.getStartSeqNum()
              + byteObj.getLength());

          int length = retransmitData.length;

          totalRetransmit = totalRetransmit + length;

          int currpos = 0;

          int remaining = length;
          long tempDataSendSeqNum = byteObj.getStartSeqNum();

          while ((currpos - length < 0) && (cinfo.getDataBaseSeq() - endSeqNum < 0))
          {
            // block until available to write
            cinfo.blockOnOutputStreamSelector();
            continousAckReads();

            // MSocketLogger.getLogger().fine("currpos " + currpos + "length " + length + " tempDataSendSeqNum " + tempDataSendSeqNum
                // + "cinfo.getDataBaseSeq() " + cinfo.getDataBaseSeq());
            MSocketLogger.getLogger().log(Level.FINE,"currpos: {0}, length: {1}, tempDataSendSeqNum: {2}, cinfo.getDataBaseSeq(): {3}", new Object[]{currpos,length,tempDataSendSeqNum,cinfo.getDataBaseSeq()});
            // reads input stream for ACKs an stores data in input buffer
            SocketInfo Obj = null;

            Obj = getRandomFinishedSocket(); // randomly choosing the
                                             // socket from finished sockets to
                                             // send chunk

            if (Obj != null)
            {
              while (!Obj.acquireLock())
                ;

              int tobesent = 0;
              if (remaining < MWrappedOutputStream.WRITE_CHUNK_SIZE)
              {
                tobesent = remaining;
              }
              else
              {
                tobesent = MWrappedOutputStream.WRITE_CHUNK_SIZE;
              }

              try
              {
                if (Obj.getneedToReqeustACK())
                {
                  handleMigrationInMultiPath(Obj);
                  Obj.releaseLock();
                  continue;
                }

                int arrayCopyOffset = currpos;
                DataMessage dm = new DataMessage(DataMessage.DATA_MESG, (int) tempDataSendSeqNum,
                    cinfo.getDataAckSeq(), tobesent, 0, retransmitData, arrayCopyOffset);
                byte[] writebuf = dm.getBytes();

                // exception of write means that socket is undergoing migration,
                // make it not active, and transfer same data chuk over another
                // available socket.
                // at receiving side, receiver will take care of redundantly
                // received data

                if ((Integer) Obj.queueOperations(SocketInfo.QUEUE_SIZE, null) > 0)
                {
                  attemptSocketWrite(Obj);
                  Obj.releaseLock();
                  continue;
                }
                else
                {
                  Obj.queueOperations(SocketInfo.QUEUE_PUT, writebuf);
                }

                attemptSocketWrite(Obj);

                Obj.updateSentBytes(tobesent);
                currpos += tobesent;
                remaining -= tobesent;
                tempDataSendSeqNum += tobesent;
                Obj.releaseLock();
              }
              catch (IOException ex)
              {
            	// MSocketLogger.getLogger().fine("Write exception caused");
                MSocketLogger.getLogger().log(Level.FINE,"Write exception caused");
                Obj.setStatus(false);
                Obj.setneedToReqeustACK(true);
                Obj.releaseLock();
              }
            }
            else
            {
              // throw exception and block or wait in while loop to check for
              // any available sockets
              // MSocketLogger.getLogger().fine("no socket avaialble for write, blocking");
              MSocketLogger.getLogger().log(Level.FINE,"No socket avaialble for write, blocking");
              synchronized (cinfo.getSocketMonitor())
              {
                while ((cinfo.getActiveSocket(MultipathPolicy.MULTIPATH_POLICY_RANDOM) == null)
                    && (cinfo.getMSocketState() == MSocketConstants.ACTIVE))
                {
                  try
                  {
                    cinfo.getSocketMonitor().wait();
                  }
                  catch (InterruptedException e)
                  {
                    e.printStackTrace();
                  }
                }
              }

              if (cinfo.getMSocketState() == MSocketConstants.CLOSED)
              {
                // socket is closed, no need to do any writes
                return;
              }
            }

          }
        }
      }
      else
      {
        // nothing left to retransmit
        break;
      }
    }
    // empty write queues before returning
    cinfo.emptyTheWriteQueues();
    long retransmitEnd = System.currentTimeMillis();
    // MSocketLogger.getLogger().fine("BackgroudMultiPathWritingThread finishes " + "finish path time" + (finishPath - runStart)
        // + "retransmit time " + (retransmitEnd - finishPath) + " totalRetransmit " + totalRetransmit);
    MSocketLogger.getLogger().log(Level.FINE,"BackgroudMultiPathWritingThread finished. Finished path time: {0}, Retransmit Time: {1}, totalRetransmit: {2}",new Object[]{(finishPath - runStart),(retransmitEnd - finishPath),totalRetransmit});
  }

  /**
   * @param tempDataSendSeqNum
   * @param Obj
   * @throws IOException
   */
  private void handleMigrationInMultiPath(SocketInfo Obj) throws IOException
  {

    // MSocketLogger.getLogger().fine("handleMigrationInMultiPath called");
    MSocketLogger.getLogger().log(Level.FINE,"handleMigrationInMultiPath called");
    // if queue size is > 0 then it means that there is a non-blocking
    // write pending and it should be sent first, instead of migration data
    if ((Integer) Obj.queueOperations(SocketInfo.QUEUE_SIZE, null) > 0)
    {
      attemptSocketWrite(Obj);
      return;
    }

    // MSocketLogger.getLogger().fine("HandleMigrationInMultiPath SocektId " + Obj.getSocketIdentifer());
    MSocketLogger.getLogger().log(Level.FINE,"HandleMigrationInMultiPath SocektId {0}",Obj.getSocketIdentifer() );
    cinfo.multiSocketRead();
    int dataAck = (int) cinfo.getDataBaseSeq();
    // MSocketLogger.getLogger().fine("DataAck from other side " + dataAck);
    MSocketLogger.getLogger().log(Level.FINE,"DataAck from other side {0}.", dataAck);
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
        attemptSocketWrite(Obj);
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
      if ((currByteR.getStartSeqNum() + currByteR.getLength()) - Obj.getHandleMigSeqNum() < 0)
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
      attemptSocketWrite(Obj);

    }

    Obj.setneedToReqeustACK(false);
    // MSocketLogger.getLogger().fine("HandleMigrationInMultiPath Complete");
    MSocketLogger.getLogger().log(Level.FINE,"HandleMigrationInMultiPath Complete");
  }

  private void attemptSocketWrite(SocketInfo Obj) throws IOException
  {
    Obj.getDataChannel().configureBlocking(false);

    byte[] writebuf = (byte[]) Obj.queueOperations(SocketInfo.QUEUE_GET, null);
    int curroffset = Obj.currentChunkWriteOffsetOper(-1, SocketInfo.VARIABLE_GET);
    ByteBuffer bytebuf = ByteBuffer.allocate(writebuf.length - curroffset);

    bytebuf.put(writebuf, curroffset, writebuf.length - curroffset);
    bytebuf.flip();
    long startTime = System.currentTimeMillis();
    int gotWritten = Obj.getDataChannel().write(bytebuf);

    if (gotWritten > 0)
    {
      // MSocketLogger.getLogger().fine("gotWritten " + gotWritten + " buf length " + writebuf.length + " send buffer "
          // + Obj.getSocket().getSendBufferSize() + " SocketID " + Obj.getSocketIdentifer());
      MSocketLogger.getLogger().log(Level.FINE,"gotWritten: {0}, Write Buffer Length: {1}, Send Buffer: {2}, SocketID: {3}.", new Object[]{gotWritten,writebuf.length,Obj.getSocket().getSendBufferSize(),Obj.getSocketIdentifer()});
      Obj.currentChunkWriteOffsetOper(gotWritten, SocketInfo.VARIABLE_UPDATE);
    }

    if (Obj.currentChunkWriteOffsetOper(-1, SocketInfo.VARIABLE_GET) == writebuf.length) // completely
                                                                                         // written,
                                                                                         // time
                                                                                         // to
                                                                                         // remove
                                                                                         // from
                                                                                         // head
                                                                                         // of
                                                                                         // queue
                                                                                         // and
                                                                                         // reset
                                                                                         // it
    {
      // MSocketLogger.getLogger().fine("currentChunkWriteOffset " + writebuf.length);
      MSocketLogger.getLogger().log(Level.FINE,"currentChunkWriteOffset {0}",writebuf.length );
      Obj.currentChunkWriteOffsetOper(0, SocketInfo.VARIABLE_SET);
      Obj.queueOperations(SocketInfo.QUEUE_REMOVE, null);
    }
    long endTime = System.currentTimeMillis();

    if (gotWritten > 0)
      // MSocketLogger.getLogger().fine("Using socketID " + Obj.getSocketIdentifer() + "Remote IP " + Obj.getSocket().getInetAddress()
          // + "for writing " + " time taken " + (endTime - startTime));
      MSocketLogger.getLogger().log(Level.FINE,"Using SocketID: {0}, Remote IP: {1}, time taken for writing: {2}", new Object[]{Obj.getSocketIdentifer(),Obj.getSocket().getInetAddress(),(endTime - startTime)});
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

  private void waitForOnePathToFinish()
  {
    while (cinfo.getDataBaseSeq() - endSeqNum < 0)
    {
      Vector<SocketInfo> socketList = new Vector<SocketInfo>();
      socketList.addAll(cinfo.getAllSocketInfo()); // if there is just one path,
                                                   // no need for this thread

      finishedPaths.clear();
      unfinishedPaths.clear();
      for (int i = 0; i < socketList.size(); i++)
      {
        SocketInfo Obj = socketList.get(i);
        if (Obj.getStatus())
        { // if there is less data outstading, less to get the Acks back,
          // consider it as //finished path
          if (Obj.getOutStandingBytes() <= (5 * MWrappedOutputStream.WRITE_CHUNK_SIZE))
          {
            // MSocketLogger.getLogger().fine("socket ID " + Obj.getSocketIdentifer() + " is found to have zero outstanding bytes "
                // + "Remote Address " + Obj.getSocket().getInetAddress() + " sent bytes " + Obj.getSentBytes());
            MSocketLogger.getLogger().log(Level.FINE,"SokcetID: {0}, is found to have zero outstanding bytes. Remote Address: {1}, Bytes Sent on this socket: {2}.", new Object[]{Obj.getSocketIdentifer(),Obj.getSocket().getInetAddress(),Obj.getSentBytes()});
            finishedPaths.add(Obj);
          }
          else
          {
            unfinishedPaths.add(Obj);
            // MSocketLogger.getLogger().fine("unifinished paths size " + unfinishedPaths.size());
            MSocketLogger.getLogger().log(Level.FINE,"unifinished paths size: {0}", unfinishedPaths.size());
          }
        }
      } // found some finished paths. // start retransmitting

      if (finishedPaths.size() > 0)
      {
        break;
      }
      cinfo.blockOnInputStreamSelector();
      continousAckReads();
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
      e.printStackTrace();
    }

    long newDataBaseSeqNum = cinfo.getDataBaseSeq();

    // if an ack was read, read it again
    if (newDataBaseSeqNum - oldDataBaseSeqNum > 0)
    {
      runAgain = true;
    }

    // } while(runAgain);
  }

}