/* This file is part of VoltDB.
 * Copyright (C) 2008-2011 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com;

import org.voltdb.VoltTable;
import org.voltdb.types.TimestampType;
import org.voltdb.client.ClientResponse;
import com.Clock;
import com.procedures.LoadStatus;
import com.procedures.DBCheck;
import com.procedures.delivery;
import com.procedures.neworder;
import com.procedures.remoteNeworder;

import org.voltdb.client.ProcedureCallback;
import org.voltdb.client.Client;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.NoConnectionsException;
import org.voltdb.client.ProcCallException;
import org.voltdb.client.exampleutils.AppHelper;
import org.voltdb.client.exampleutils.ClientConnection;
import org.voltdb.client.exampleutils.ClientConnectionPool;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class MyTPCC
    implements TPCCSimulation.ProcCaller
{
    private ClientConnection m_clientCon;
    final TPCCSimulation tpccSim;
    private final ScaleParameters scaleParams;

    private AppHelper m_helpah;

    private String procNames[];
    private AtomicLong procCounts[];

    public static long minExecutionMilliseconds = 999999999l;
    public static long maxExecutionMilliseconds = -1l;
    public static long totExecutionMilliseconds = 0;
    public static long totExecutions = 0;
    public static long totExecutionsLatency = 0;
    public static long[] latencyCounter = new long[] {0,0,0,0,0,0,0,0,0};
    public static boolean checkLatency = false;
    public static final ReentrantLock counterLock = new ReentrantLock();

    public static void main(String args[])
    {
        (new MyTPCC(args)).run();
    }

    public void run()
    {
        long transactions_per_second = m_helpah.longValue("rate-limit");
        long transactions_per_milli = transactions_per_second / 1000l;
        long client_feedback_interval_secs = m_helpah.longValue("display-interval");
        long testDurationSecs = m_helpah.longValue("duration");
        long lag_latency_seconds = 0;
        long lag_latency_millis = lag_latency_seconds * 1000l;
        long thisOutstanding = 0;
        long lastOutstanding = 0;
        final String statsFile = m_helpah.stringValue("statsfile");

        long transactions_this_second = 0;
        long last_millisecond = System.currentTimeMillis();
        long this_millisecond = System.currentTimeMillis();

        setTransactionDisplayNames();

        long startTime = System.currentTimeMillis();
        long endTime = startTime + (1000l * testDurationSecs);
        long currentTime = startTime;
        long lastFeedbackTime = startTime;
        long numSPCalls = 0;
        long startRecordingLatency = startTime + lag_latency_millis;

        while (endTime > currentTime)
        {
            numSPCalls++;

            try
            {
                tpccSim.doOne();
            }
            catch (IOException e)
            {}

            transactions_this_second++;
            if (transactions_this_second >= transactions_per_milli)
            {
                this_millisecond = System.currentTimeMillis();
                while (this_millisecond <= last_millisecond)
                {
                    this_millisecond = System.currentTimeMillis();
                }
                last_millisecond = this_millisecond;
                transactions_this_second = 0;
            }

            currentTime = System.currentTimeMillis();

            if ((!checkLatency) && (currentTime >= startRecordingLatency))
            {
                // time to start recording latency information
                checkLatency = true;
            }

            if (currentTime >= (lastFeedbackTime + (client_feedback_interval_secs * 1000)))
            {
                final long elapsedTimeMillis2 = System.currentTimeMillis() - startTime;
                lastFeedbackTime = currentTime;

                final long runTimeMillis = endTime - startTime;

                float elapsedTimeSec2 = (System.currentTimeMillis() - startTime) / 1000F;
                if (totExecutionsLatency == 0)
                {
                    totExecutionsLatency = 1;
                }

                double percentComplete = ((double) elapsedTimeMillis2 / (double) runTimeMillis) * 100;
                if (percentComplete > 100.0)
                {
                    percentComplete = 100.0;
                }

                counterLock.lock();
                try
                {
                    thisOutstanding = numSPCalls - totExecutions;

                    double avgLatency = (double) totExecutionMilliseconds / (double) totExecutionsLatency;
                    double tps = numSPCalls / elapsedTimeSec2;

                    System.out.printf("%.3f%% Complete | Allowing %,d SP calls/sec: made %,d SP calls at %,.2f SP/sec | outstanding = %d (%d) | min = %d | max = %d | avg = %.2f\n",
                            percentComplete, (transactions_per_milli * 1000l), numSPCalls, tps, thisOutstanding, (thisOutstanding - lastOutstanding), minExecutionMilliseconds, maxExecutionMilliseconds, avgLatency);
                    for (int i = 0; i < procNames.length; i++)
                    {
                        System.out.printf("%16s: %10d total,", procNames[i], procCounts[i].intValue());
                    }
                    System.out.println();

                    lastOutstanding = thisOutstanding;
                }
                finally
                {
                    counterLock.unlock();
                }
            }
        }

        try
        {
            m_clientCon.drain();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        long elapsedTimeMillis = System.currentTimeMillis() - startTime;
        float elapsedTimeSec = elapsedTimeMillis / 1000F;

        System.out.println("============================== BENCHMARK RESULTS ==============================");
        System.out.printf("Time: %d ms\n", elapsedTimeMillis);
        System.out.printf("Total transactions: %d\n", numSPCalls);
        System.out.printf("Transactions per second: %.2f\n", (float)numSPCalls / elapsedTimeSec);
        for (int i = 0; i < procNames.length; i++)
        {
            System.out.printf("%23s: %10d total %12.2f txn/s %12.2f txn/m\n",
                    procNames[i], procCounts[i].intValue(), procCounts[i].floatValue() / elapsedTimeSec, procCounts[i].floatValue() * 60 / elapsedTimeSec);
        }
        System.out.println("===============================================================================\n");

        System.out.println("\n");
        System.out.println("*************************************************************************");
        System.out.println("System Statistics");
        System.out.println("*************************************************************************");

        System.out.printf(" - Ran for %,.2f seconds\n", elapsedTimeSec);
        System.out.printf(" - Performed %,d Stored Procedure calls\n", numSPCalls);
        System.out.printf(" - At %,.2f calls per second\n", numSPCalls / elapsedTimeSec);
        System.out.printf(" - Average Latency = %.2f ms\n", ((double) totExecutionMilliseconds / (double) totExecutionsLatency));
        System.out.printf(" -   Latency   0ms -  25ms = %,d\n", latencyCounter[0]);
        System.out.printf(" -   Latency  25ms -  50ms = %,d\n", latencyCounter[1]);
        System.out.printf(" -   Latency  50ms -  75ms = %,d\n", latencyCounter[2]);
        System.out.printf(" -   Latency  75ms - 100ms = %,d\n", latencyCounter[3]);
        System.out.printf(" -   Latency 100ms - 125ms = %,d\n", latencyCounter[4]);
        System.out.printf(" -   Latency 125ms - 150ms = %,d\n", latencyCounter[5]);
        System.out.printf(" -   Latency 150ms - 175ms = %,d\n", latencyCounter[6]);
        System.out.printf(" -   Latency 175ms - 200ms = %,d\n", latencyCounter[7]);
        System.out.printf(" -   Latency 200ms+        = %,d\n", latencyCounter[8]);

        // 3. Performance statistics
        System.out.println(
          "\n\n-------------------------------------------------------------------------------------\n"
        + " System Statistics\n"
        + "-------------------------------------------------------------------------------------\n\n");
        System.out.print(m_clientCon.getStatistics(Constants.TRANS_PROCS).toString(false));

        // Dump stats to file
        try {
            m_clientCon.saveStatistics(statsFile);
        } catch (IOException e) {
            System.err.println("Unable to save statistics file: " + e.getMessage());
        }

        m_clientCon.close();
    }

    public MyTPCC(String args[])
    {
        m_helpah = new AppHelper(MyTPCC.class.getCanonicalName());
        m_helpah.add("duration", "run_duration_in_seconds", "Benchmark duration, in seconds.", 180);
        m_helpah.add("warehouses", "number_of_warehouses", "Number of warehouses", 12);
        m_helpah.add("scale-factor", "scale_factor", "Scale factor", 1.0);
        m_helpah.add("skew-factor", "skew_factor", "Skew factor", 0.0);
        m_helpah.add("load-threads", "number_of_load_threads", "Number of load threads", 4);
        m_helpah.add("rate-limit", "rate_limit", "Rate limit to start from (tps)", 200000);
        m_helpah.add("display-interval", "display_interval_in_seconds", "Interval for performance feedback, in seconds.", 10);
        m_helpah.add("servers", "comma_separated_server_list", "List of VoltDB servers to connect to.", "localhost");
        m_helpah.setArguments(args);

        // default values
        int warehouses = m_helpah.intValue("warehouses");
        double scalefactor = m_helpah.doubleValue("scale-factor");
        double skewfactor = m_helpah.doubleValue("skew-factor");

        String servers = m_helpah.stringValue("servers");
        System.out.printf("Connecting to servers: %s\n", servers);
        int sleep = 1000;
        while(true)
        {
            try
            {
                m_clientCon = ClientConnectionPool.get(servers, 21212);
                break;
            }
            catch (Exception e)
            {
                System.err.printf("Connection failed - retrying in %d second(s).\n", sleep/1000);
                try {Thread.sleep(sleep);} catch(Exception tie){}
                if (sleep < 8000)
                    sleep += sleep;
            }
        }
        System.out.println("Connected.  Starting benchmark.");
        Date startTime = new Date();

        try
        {
            if ((int)(m_clientCon.execute(LoadStatus.class.getSimpleName()).getResults()[0].fetchRow(0).getLong(0)) == 0)
                (new MyLoader(args, m_clientCon)).run();
            else
                while ((int)(m_clientCon.execute(LoadStatus.class.getSimpleName()).getResults()[0].fetchRow(0).getLong(0)) < warehouses)
                    ;
            System.out.println("Load took " + ((new Date()).getTime() - startTime.getTime()) + "ms");
			System.out.println("Pausing 5 secs...");
			Thread.sleep(5000);
            System.out.println("Initial report:");
			reportDB();

			firstTests();
			//reportDB();
	
			System.out.println("GWW - finished firstTest...");

        }
        catch (Exception e)
        {
            System.exit(-1);
        }
        	
        // makeForRun requires the value cLast from the load generator in
        // order to produce a valid generator for the run. Thus the sort
        // of weird eat-your-own ctor pattern.
        RandomGenerator.NURandC base_loadC = new RandomGenerator.NURandC(0,0,0);
        RandomGenerator.NURandC base_runC = RandomGenerator.NURandC.makeForRun(
                new RandomGenerator.Implementation(0), base_loadC);
        RandomGenerator rng = new RandomGenerator.Implementation(0);
        rng.setC(base_runC);
        scaleParams = ScaleParameters.makeWithScaleFactor(warehouses, scalefactor);
        tpccSim = new TPCCSimulation(this, rng, new Clock.RealTime(), scaleParams, false, skewfactor);
    }
	private void firstTests() {
		try {
			Clock clock;
			TimestampType now;
			ClientResponse cr;
//			System.out
//			.println("===============================================================================\n");
//
//			System.out.println("Initially, trying one insertDup Txn...");
//			clock = new Clock.RealTime();
//			now = clock.getDateTime();
//			cr = m_clientCon.execute(
//					// first branch, 1, its first account 100000, first teller
//					// 10
//					InsertDupTransaction.class.getSimpleName(), 1, 100000, 10, 100,
//					now);
//
//			printResultTables(cr);
			System.out
			.println("===============================================================================\n");
			System.out.println("Initially, trying one delivery Transaction...");
			clock = new Clock.RealTime();
			now = clock.getDateTime();
			cr = m_clientCon.execute(
					// first warehouse 1, carrier_ID 66, time
					// delta 100
					delivery.class.getSimpleName(), 1, 66, now);

			printResultTables(cr);
			System.out
			.println("===============================================================================\n");
			System.out.println("Now trying one neworder using only warehouse 1");
			int items[] = {1,2,3};
			short supware[] = {1,1,1};  // all warehouse 1
			int quantity[] = {1,2,3};
			cr = m_clientCon.execute(
					// first warehouse, 1, 
					// first district 1, customer 1, now, ...
					neworder.class.getSimpleName(), 1, 1, 1, now, items, supware, quantity);
			printResultTables(cr);
			System.out
			.println("===============================================================================\n");
			System.out.println("Now trying one remote neworder using only warehouse 1");
			int items1[] = {1,2,3};
			short supware1[] = {1,1,1};  // all warehouse 1
			int quantity1[] = {1,2,3};
			cr = m_clientCon.execute(
					// first warehouse, 1, 
					// first district 1, customer 1, now, ...
					remoteNeworder.class.getSimpleName(), 1, 1, 1, now, items1, supware1, quantity1);
			printResultTables1(cr);

			System.out
			.println("===============================================================================\n");

			reportDB();
			System.out
			.println("===============================================================================\n");
			System.out.println("Trying @AdHoc...");
		   cr = m_clientCon.execute("@AdHoc",
				       "SELECT COUNT(*) FROM ITEM; SELECT COUNT(*) FROM HISTORY ");
				
		   printResultTables1(cr);

		} catch (Exception e) {
			System.out.println("Problem executing first SPs " + e);
			e.printStackTrace();
			System.exit(-1);
		}

	}
	
	// print result table info where 1-row tables are expected
	// that should have a scalar value 
	// such as results of TPCB transactions, but don't fail
	// if the row is missing
	private void printResultTables(ClientResponse cr) {
		System.out.println("...returned status" + cr.getStatus());
		VoltTable[] results = cr.getResults();
		System.out.println("... got back " + results.length + "tables");
		for (int i = 0; i < results.length; i++)
			System.out.println("#rows = " + results[i].getRowCount()
					+ " #cols = " + results[i].getColumnCount() + " in table# = " + i);
		for (int i = 0; i < results.length; i++)
			if (results[i].getRowCount() == 1 && results[i].getColumnCount() == 1)
				System.out.println("statement provided scalar = "
						+ results[i].asScalarLong() + " for table # " + i);
			else
				System.out.println("no scalar value available for table "	+ i);
	}
	
	private void printResultTables1(ClientResponse cr) {
		System.out.println("...returned status" + cr.getStatus());
		VoltTable[] results = cr.getResults();
		System.out.println("... got back " + results.length + "tables");
		for (int i = 0; i < results.length; i++) {
			System.out.println("#rows = " + results[i].getRowCount()
					+ " #cols = " + results[i].getColumnCount() + " in table# = " + i);
			System.out.println(" table = " + results[i]);
		}
		
	}
	
    private void reportDB() throws Exception {
		VoltTable[] results = m_clientCon
				.execute(DBCheck.class.getSimpleName()).getResults();
		System.out.println("See "+results.length+"tables");
		long nWarehouses = results[0].asScalarLong();
		long nDistricts = results[1].asScalarLong();
		long nItems = results[2].asScalarLong();
		long nCustomers = results[3].asScalarLong();
		long nCustomerNames = results[4].asScalarLong();
		System.out.println("TPCC database:");
		System.out.println(nWarehouses + " warehouse records");
		System.out.println(nDistricts + " district records");
		System.out.println(nItems + " item records");
		System.out.println(nCustomers + " customer records");
		System.out.println(nCustomerNames + " customer name records");

		if (results.length > 7) {
		long nHistory = results[5].asScalarLong();
		long nOrders = results[6].asScalarLong();
		long nNewOrders = results[7].asScalarLong();
		long nOrderLine = results[8].asScalarLong();

		System.out.println(nHistory + " history records");
		System.out.println(nOrders + " order records");
		System.out.println(nNewOrders + " neworder records");
		System.out.println(nOrderLine + " order line records");
		}
	}

    // Delivery

    class DeliveryCallback
        implements ProcedureCallback
    {
        @Override
        public void clientCallback(ClientResponse clientResponse)
        {
            boolean status = clientResponse.getStatus() == ClientResponse.SUCCESS;
//            assert status;
            if (status && clientResponse.getResults()[0].getRowCount()
                    != scaleParams.districtsPerWarehouse)
            {
                System.err.println(
                        "Only delivered from "
                        + clientResponse.getResults()[0].getRowCount()
                        + " districts.");
            }
            procCounts[TPCCSimulation.Transaction.DELIVERY.ordinal()].incrementAndGet();


            counterLock.lock();
            try
            {
                totExecutions++;

                if (checkLatency)
                {
                    long executionTime =  clientResponse.getClientRoundtrip();

                    totExecutionsLatency++;
                    totExecutionMilliseconds += executionTime;

                    if (executionTime < minExecutionMilliseconds)
                    {
                        minExecutionMilliseconds = executionTime;
                    }

                    if (executionTime > maxExecutionMilliseconds)
                    {
                        maxExecutionMilliseconds = executionTime;
                    }

                    // change latency to bucket
                    int latencyBucket = (int) (executionTime / 25l);
                    if (latencyBucket > 8)
                    {
                        latencyBucket = 8;
                    }
                    latencyCounter[latencyBucket]++;
                }
            }
            finally
            {
                counterLock.unlock();
            }
        }
    }

    @Override
    public void callDelivery(short w_id, int carrier, TimestampType date)
        throws IOException
    {
        try
        {
            m_clientCon.executeAsync(new DeliveryCallback(),
                                     Constants.DELIVERY, w_id, carrier, date);
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    // NewOrder

    class NewOrderCallback
        implements ProcedureCallback
    {
        public NewOrderCallback(boolean rollback)
        {
            super();
            this.cbRollback = rollback;
        }

        @Override
        public void clientCallback(ClientResponse clientResponse)
        {
            boolean status = clientResponse.getStatus() == ClientResponse.SUCCESS;
//          assert this.cbRollback || status : 
//    		"client response problem: status = " + clientResponse.getStatus();
			if (!(this.cbRollback || status)) {
				System.out.println("client call for neworder failed: status = "
						+ clientResponse.getStatus()
						+ " exes: " + totExecutions);
			} 
			
            procCounts[TPCCSimulation.Transaction.NEW_ORDER.ordinal()].incrementAndGet();


            counterLock.lock();
            try
            {
                totExecutions++;

                if (checkLatency)
                {
                    long executionTime =  clientResponse.getClientRoundtrip();

                    totExecutionsLatency++;
                    totExecutionMilliseconds += executionTime;

                    if (executionTime < minExecutionMilliseconds)
                    {
                        minExecutionMilliseconds = executionTime;
                    }

                    if (executionTime > maxExecutionMilliseconds)
                    {
                        maxExecutionMilliseconds = executionTime;
                    }

                    // change latency to bucket
                    int latencyBucket = (int) (executionTime / 25l);
                    if (latencyBucket > 8)
                    {
                        latencyBucket = 8;
                    }
                    latencyCounter[latencyBucket]++;
                }
            }
            finally
            {
                counterLock.unlock();
            }
        }

        private final boolean cbRollback;
    }

    int randomIndex = 0;
    @Override
    public void callNewOrder(boolean rollback, boolean remoteCall, Object... paramlist)
        throws IOException
    {
        try
        {
        	m_clientCon.executeAsync(new NewOrderCallback(rollback), 
    				remoteCall ? Constants.REMOTE_NEWORDER : Constants.NEWORDER, paramlist);
        	
//        	if(remoteCall)
//        		m_clientCon.executeAsync(new NewOrderCallback(rollback), 
//        				Constants.REMOTE_NEWORDER, paramlist);
//        	else
//        		m_clientCon.executeAsync(new NewOrderCallback(rollback),
//                        Constants.NEWORDER, paramlist);
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    // Order status

    class VerifyBasicCallback
        implements ProcedureCallback
    {
        private final TPCCSimulation.Transaction m_transactionType;
        private final String m_procedureName;

        /**
         * A generic callback that does not credit a transaction. Some transactions
         * use two procedure calls - this counts as one transaction not two.
         */
        VerifyBasicCallback()
        {
            m_transactionType = null;
            m_procedureName = null;
        }

        /** A generic callback that credits for the transaction type passed. */
        VerifyBasicCallback(TPCCSimulation.Transaction transaction, String procName)
        {
            m_transactionType = transaction;
            m_procedureName = procName;
        }

        @Override
        public void clientCallback(ClientResponse clientResponse)
        {
            // TODO: Necessary?
            boolean abortExpected = false;
            if (m_procedureName != null && (m_procedureName.equals(Constants.ORDER_STATUS_BY_NAME)
                || m_procedureName.equals(Constants.ORDER_STATUS_BY_ID)))
                abortExpected = true;
            // eoneil: changed && to || here to avoid asserts or printed errors
            boolean status = clientResponse.getStatus() == ClientResponse.SUCCESS || abortExpected;
            // assert status;
    		if (!status) {
				System.out.println("client call for " + m_procedureName + 
						" failed: status = " + clientResponse.getStatus()
						+ " exes: " + totExecutions);
			} 

            if (m_transactionType != null)
            {
                procCounts[m_transactionType.ordinal()].incrementAndGet();


                counterLock.lock();
                try
                {
                    totExecutions++;

                    if (checkLatency)
                    {
                        long executionTime =  clientResponse.getClientRoundtrip();

                        totExecutionsLatency++;
                        totExecutionMilliseconds += executionTime;

                        if (executionTime < minExecutionMilliseconds)
                        {
                            minExecutionMilliseconds = executionTime;
                        }

                        if (executionTime > maxExecutionMilliseconds)
                        {
                            maxExecutionMilliseconds = executionTime;
                        }

                        // change latency to bucket
                        int latencyBucket = (int) (executionTime / 25l);
                        if (latencyBucket > 8)
                        {
                            latencyBucket = 8;
                        }
                        latencyCounter[latencyBucket]++;
                    }
                }
                finally
                {
                    counterLock.unlock();
                }
            }
        }
    }

    @Override
    public void callOrderStatus(String proc, Object... paramlist)
        throws IOException
    {
        try
        {
            m_clientCon.executeAsync(new VerifyBasicCallback(TPCCSimulation.Transaction.ORDER_STATUS,
                                                                                 proc), proc, paramlist);
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    // Payment

    @Override
    public void callPaymentById(short w_id, byte d_id, double h_amount,
            short c_w_id, byte c_d_id, int c_id, TimestampType now) throws IOException
    {
        try
        {
            if (scaleParams.warehouses > 1 && c_w_id != w_id)
            {
/*
            	m_clientCon.executeAsync(new VerifyBasicCallback(), Constants.PAYMENT_BY_ID_W,
                                         w_id, d_id, h_amount, c_w_id, c_d_id, c_id, now);
                m_clientCon.executeAsync(new VerifyBasicCallback(TPCCSimulation.Transaction.PAYMENT,
                                                                 Constants.PAYMENT_BY_ID_C),
                                                                 Constants.PAYMENT_BY_ID_C,
                                                                 w_id, d_id, h_amount, c_w_id, c_d_id, c_id, now);
*/
            	m_clientCon.executeAsync(new VerifyBasicCallback(TPCCSimulation.Transaction.PAYMENT,
            													Constants.REMOTE_PAYMENT_BY_ID),
            													Constants.REMOTE_PAYMENT_BY_ID,
            													w_id, d_id, h_amount, c_w_id, c_d_id, c_id, now);
            }
            else
            {
            	assert(c_w_id == w_id);
                m_clientCon.executeAsync(new VerifyBasicCallback(TPCCSimulation.Transaction.PAYMENT,
                                                                 Constants.PAYMENT_BY_ID),
                                                                 Constants.PAYMENT_BY_ID,
                                                                 w_id, d_id, h_amount, c_w_id, c_d_id, c_id, now);
            }
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    @Override
    public void callPaymentByName(short w_id, byte d_id, double h_amount,
            short c_w_id, byte c_d_id, byte[] c_last, TimestampType now) throws IOException
    {
        try
        {
//            if ((scaleParams.warehouses > 1) || (c_last != null))
        	if(scaleParams.warehouses > 1 && w_id != c_w_id)
            {
/*            	
                m_clientCon.executeAsync(new VerifyBasicCallback(), Constants.PAYMENT_BY_NAME_W,
                                         w_id, d_id, h_amount, c_w_id, c_d_id, c_last, now);
                m_clientCon.executeAsync(new VerifyBasicCallback(TPCCSimulation.Transaction.PAYMENT,
                                                                 Constants.PAYMENT_BY_NAME_C),
                                                                 Constants.PAYMENT_BY_NAME_C, w_id, d_id, h_amount,
                                                                 c_w_id, c_d_id, c_last, now);
*/
        		assert(c_last != null);
                m_clientCon.executeAsync(new VerifyBasicCallback(TPCCSimulation.Transaction.PAYMENT,
                        										Constants.REMOTE_PAYMENT_BY_NAME),
                        										Constants.REMOTE_PAYMENT_BY_NAME, 
                        										w_id, d_id, h_amount, c_w_id, c_d_id, c_last, now);

            }
            else
            {
                m_clientCon.executeAsync(new VerifyBasicCallback(TPCCSimulation.Transaction.PAYMENT,
                                                                 Constants.PAYMENT_BY_NAME),
                                                                 Constants.PAYMENT_BY_NAME, w_id,
                                                                 d_id, h_amount, c_w_id, c_d_id, c_last, now);
            }
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    // StockLevel

    class StockLevelCallback
        implements ProcedureCallback
    {
        @Override
        public void clientCallback(ClientResponse clientResponse)
        {
            boolean status = clientResponse.getStatus() == ClientResponse.SUCCESS;
//            assert status;
            procCounts[TPCCSimulation.Transaction.STOCK_LEVEL.ordinal()].incrementAndGet();


            counterLock.lock();
            try
            {
                totExecutions++;

                if (checkLatency)
                {
                    long executionTime =  clientResponse.getClientRoundtrip();

                    totExecutionsLatency++;
                    totExecutionMilliseconds += executionTime;

                    if (executionTime < minExecutionMilliseconds)
                    {
                        minExecutionMilliseconds = executionTime;
                    }

                    if (executionTime > maxExecutionMilliseconds)
                    {
                        maxExecutionMilliseconds = executionTime;
                    }

                    // change latency to bucket
                    int latencyBucket = (int) (executionTime / 25l);
                    if (latencyBucket > 8)
                    {
                        latencyBucket = 8;
                    }
                    latencyCounter[latencyBucket]++;
                }
            }
            finally
            {
                counterLock.unlock();
            }
        }
    }

    @Override
    public void callStockLevel(short w_id, byte d_id, int threshold)
        throws IOException
    {
        final StockLevelCallback cb = new StockLevelCallback();
        try
        {
            m_clientCon.executeAsync(cb, Constants.STOCK_LEVEL, w_id, d_id, threshold);
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    class ResetWarehouseCallback implements ProcedureCallback
    {
        @Override
        public void clientCallback(ClientResponse clientResponse)
        {
            if (clientResponse.getStatus() == ClientResponse.SUCCESS)
            {
                procCounts[TPCCSimulation.Transaction.RESET_WAREHOUSE.ordinal()].incrementAndGet();


                counterLock.lock();
                try
                {
                    totExecutions++;

                    if (checkLatency)
                    {
                        long executionTime =  clientResponse.getClientRoundtrip();

                        totExecutionsLatency++;
                        totExecutionMilliseconds += executionTime;

                        if (executionTime < minExecutionMilliseconds)
                        {
                            minExecutionMilliseconds = executionTime;
                        }

                        if (executionTime > maxExecutionMilliseconds)
                        {
                            maxExecutionMilliseconds = executionTime;
                        }

                        // change latency to bucket
                        int latencyBucket = (int) (executionTime / 25l);
                        if (latencyBucket > 8)
                        {
                            latencyBucket = 8;
                        }
                        latencyCounter[latencyBucket]++;
                    }
                }
                finally
                {
                    counterLock.unlock();
                }
            }
        }
    }

    @Override
    public void callResetWarehouse(long w_id, long districtsPerWarehouse,
            long customersPerDistrict, long newOrdersPerDistrict)
    throws IOException
    {
        try
        {
            m_clientCon.executeAsync(new ResetWarehouseCallback(),
                                     Constants.RESET_WAREHOUSE, w_id, districtsPerWarehouse,
                                     customersPerDistrict, newOrdersPerDistrict);
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    private void setTransactionDisplayNames()
    {
        procNames = new String[TPCCSimulation.Transaction.values().length];
        procCounts = new AtomicLong[procNames.length];
        for (int ii = 0; ii < TPCCSimulation.Transaction.values().length; ii++)
        {
            procNames[ii] = TPCCSimulation.Transaction.values()[ii].displayName;
            procCounts[ii] = new AtomicLong(0L);
        }
    }
}
