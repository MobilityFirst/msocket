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
import java.util.Vector;
import java.util.logging.Level;
import edu.umass.cs.msocket.logger.MSocketLogger;

/**
 * This class implements the threads to do background writes for the default
 * multipath data scheduling policy.
 *
 * @author <a href="mailto:cecchet@cs.umass.edu">Emmanuel Cecchet</a>
 * @version 1.0
 */
public class BackgroudMultiPathWritingThread
{
  private ConnectionInfo     cinfo                   = null;

  // indicates till how long this thread will run and retransmit data.
  // should come from the constructor
  private long               endSeqNum               = 0;

  // used to keep track of how many chunks have been retransmitted from the
  // endSeqNum
  private long               currRetransmitEndSeqNum = 0;

  // for implementing retransmission, paths which have finished(zero outstanding
  // bytes) for original transmission
  private Vector<SocketInfo> finishedPaths           = null;
  private Vector<SocketInfo> unfinishedPaths         = null;

  public BackgroudMultiPathWritingThread(long endSeqNum, ConnectionInfo cinfo)
  {
    this.endSeqNum = endSeqNum;
    this.cinfo = cinfo;

      MSocketLogger.getLogger().log(Level.FINE,"endSeqNum: {0}, DataBaseSeqNum: {1}",new Object[]{endSeqNum,cinfo.getDataBaseSeq()});
    finishedPaths = new Vector<SocketInfo>();
    unfinishedPaths = new Vector<SocketInfo>();
  }

  public void run()
  {
    Vector<SocketInfo> socketList = new Vector<SocketInfo>();
    socketList.addAll(cinfo.getAllSocketInfo());

    for (int i = 0; i < socketList.size(); i++)
    {
      SocketInfo Obj = socketList.get(i);
      if (Obj.getOutStandingBytes() == 0)
      {
        MSocketLogger.getLogger().log(Level.FINE,"SocketID {0} is found to have zero outstanding bytes.",Obj.getSocketIdentifer());
      }
      else
      {
        unfinishedPaths.add(Obj);

          MSocketLogger.getLogger().log(Level.FINE,"Number of unfinished paths: {0}", unfinishedPaths.size());
      }
    }

    currRetransmitEndSeqNum = endSeqNum; // starting re-transmission from the
                                         // back
    try
    {
      cinfo.multiSocketRead();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }

    while (cinfo.getDataBaseSeq() - currRetransmitEndSeqNum < 0)
    {
      try
      {
        cinfo.multiSocketRead();
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }

      socketList.clear();
      socketList.addAll(unfinishedPaths);
      Vector<SocketInfo> tempVect = new Vector<SocketInfo>();
      for (int i = 0; i < socketList.size(); i++)
      {
        SocketInfo Obj = socketList.get(i);
        if (Obj.getOutStandingBytes() == 0)
        {

            MSocketLogger.getLogger().log(Level.FINE,"SocketID {0} is found to have zero outstanding bytes.",Obj.getSocketIdentifer());
          finishedPaths.add(Obj);

        }
        else
        {

            MSocketLogger.getLogger().log(Level.FINE,"SokcetID {0}, is in the unfinished paths list.",Obj.getSocketIdentifer());
          tempVect.add(Obj);
        }
      }
      unfinishedPaths.clear();
      unfinishedPaths.addAll(tempVect);

      ByteRangeInfo byteObj = returnNextChunkToRetransmit();
      if (byteObj != null)
        if (byteObj.getStartSeqNum() - cinfo.getDataBaseSeq() >= 0) // re-transmit
                                                                // only if it
                                                                // greater than
                                                                // acknowlded
                                                                // bytes
        {

          MSocketLogger.getLogger().log(Level.FINE,"Sending Sequence Number {0}, DataBaseSeqNum {1}", new Object[]{byteObj.getStartSeqNum(),cinfo.getDataBaseSeq()});
          byte[] retransmitData = cinfo.getDataFromOutBuffer(byteObj.getStartSeqNum(), byteObj.getStartSeqNum()
              + byteObj.getLength());

          int length = retransmitData.length;

          int currpos = 0;

          int remaining = length;
          long tempDataSendSeqNum = byteObj.getStartSeqNum();

          MultipathPolicy writePolicy = MultipathPolicy.MULTIPATH_POLICY_ROUNDROBIN;

          while ((currpos - length < 0) && (cinfo.getDataBaseSeq() - currRetransmitEndSeqNum < 0))
          {
            try
            {
              cinfo.multiSocketRead();
            }
            catch (IOException e)
            {
              e.printStackTrace();
            }


            MSocketLogger.getLogger().log(Level.FINE,"currpos {0}, length {1}, tempDataSendSeqNum {2}, Connection DataBaseSeqNum {3}.", new Object[]{currpos,length,tempDataSendSeqNum,cinfo.getDataBaseSeq()});
            // reads input stream for ACKs an stores data in input buffer
            SocketInfo Obj = null;
            Obj = cinfo.getActiveSocket(writePolicy); // randomly choosing the
                                                      // socket to send chunk

            // only allow byteranges to be sent on new sockets
            if (checkIfByteRangeAgainSentOnSamePath(byteObj, Obj))
            {
              continue;
            }

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

                // FIXME: how to handle migration here

                int arrayCopyOffset = currpos;
                DataMessage dm = new DataMessage(DataMessage.DATA_MESG, (int) tempDataSendSeqNum,
                    cinfo.getDataAckSeq(), tobesent, 0, retransmitData, arrayCopyOffset);
                byte[] writebuf = dm.getBytes();

                // exception of write means that socket is undergoing migration,
                // make it not active, and transfer same data chuk over another
                // available socket.
                // at receiving side, receiver will take care of redundantly
                // received data

                if (writePolicy == MultipathPolicy.MULTIPATH_POLICY_ROUNDROBIN)
                {
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

    MSocketLogger.getLogger().log(Level.FINE,"BackgroudMultiPathWritingThread finished.");
  }

  /**
   * @param Obj
   * @throws IOException
   */
  private void handleMigrationInMultiPath(SocketInfo Obj) throws IOException
  {

  MSocketLogger.getLogger().log(Level.FINE,"handleMigrationInMultiPath called");
    // if queue size is > 0 then it means that there is a non-blocking
    // write pending and it should be sent first, instead of migration data
    if ((Integer) Obj.queueOperations(SocketInfo.QUEUE_SIZE, null) > 0)
    {
      attemptSocketWrite(Obj);
      return;
    }


    MSocketLogger.getLogger().log(Level.FINE,"HandleMigrationInMultiPath SocketID {0}",Obj.getSocketIdentifer());
    cinfo.multiSocketRead();
    int dataAck = (int) cinfo.getDataBaseSeq();

    MSocketLogger.getLogger().log(Level.FINE,"DataAck from other side  {0}", dataAck);
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

      MSocketLogger.getLogger().log(Level.FINE,"Wrote {0}, WriteBuffer length {1}, SendBuffer size: {2}, SocketID {3}.", new Object[]{gotWritten,writebuf.length,Obj.getSocket().getSendBufferSize(),Obj.getSocketIdentifer()});
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

      MSocketLogger.getLogger().log(Level.FINE,"Write Buffer length {0}", writebuf.length);
      Obj.currentChunkWriteOffsetOper(0, SocketInfo.VARIABLE_SET);
      Obj.queueOperations(SocketInfo.QUEUE_REMOVE, null);
    }
    long endTime = System.currentTimeMillis();

    if (gotWritten > 0)
    
    MSocketLogger.getLogger().log(Level.FINE,"Using socketID {0}, Remote IP {1}. Time taken for writing {2}.", new Object[]{Obj.getSocketIdentifer(),Obj.getSocket().getInetAddress(),(endTime - startTime)});
  }

  /**
   * returning the sorted order last chunk on unfinished paths
   *
   * @return
   */
  private ByteRangeInfo returnNextChunkToRetransmit()
  {
    int i = 0;
    ByteRangeInfo retByteRange = null;
    for (i = 0; i < unfinishedPaths.size(); i++)
    {
      SocketInfo Obj = unfinishedPaths.get(i);
      @SuppressWarnings("unchecked")
      Vector<ByteRangeInfo> getVect = (Vector<ByteRangeInfo>) Obj
          .byteInfoVectorOperations(SocketInfo.QUEUE_GET, -1, -1);
      int j = 0;
      for (j = getVect.size(); j > 0; j--)
      {
        ByteRangeInfo byter = getVect.get(j - 1);
        if (((byter.getStartSeqNum() - currRetransmitEndSeqNum < 0) && (retByteRange == null)))
        {
          retByteRange = byter;
          break;
        }
        else if (retByteRange != null)
        {
          if (retByteRange.getStartSeqNum() - byter.getStartSeqNum() < 0)
          {
            retByteRange = byter;
            break;
          }
        }
      }
    }
    if (retByteRange != null)
    {
      currRetransmitEndSeqNum = retByteRange.getStartSeqNum();
      return retByteRange;
    }
    else
    {
      return null;
    }
  }

  private boolean ifSocketFinished(SocketInfo Obj)
  {
    int i = 0;
    for (i = 0; i < finishedPaths.size(); i++)
    {
      if (Obj.getSocketIdentifer() == finishedPaths.get(i).getSocketIdentifer())
      {
        return true;
      }
    }
    return false;
  }

  private boolean checkIfByteRangeAgainSentOnSamePath(ByteRangeInfo byter, SocketInfo dstPath)
  {
    if (byter.getSocketId() == dstPath.getSocketIdentifer())
      return true;
    else
      return false;
  }

}
