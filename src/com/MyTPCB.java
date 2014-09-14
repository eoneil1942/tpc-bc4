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
import com.tpcbprocedures.DBCheck1;
import com.tpcbprocedures.DBCheckBalance;
import com.tpcbprocedures.LoadStatus;
import com.tpcbprocedures.DBCheck;
import com.tpcbprocedures.TPCBTransaction;
import com.tpcbprocedures.TPCBTransaction2Shot;
import com.tpcbprocedures.TPCBTransactionMP;
import com.tpcbprocedures.TPCBTransactionMP2Shot;

import org.voltdb.client.ProcedureCallback;
import org.voltdb.client.exampleutils.AppHelper;
import org.voltdb.client.exampleutils.ClientConnection;
import org.voltdb.client.exampleutils.ClientConnectionPool;
import org.voltdb.exceptions.ConstraintFailureException;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class MyTPCB implements TPCBSimulation.ProcCaller {
	private ClientConnection m_clientCon;
	final TPCBSimulation tpcbSim;
	private final TPCBScaleParameters scaleParams;

	private AppHelper m_helpah;

	private String procNames[];
	private AtomicLong procCounts[];
	private int nCFEs = 0;	// CONATRINT_FAILURE_EXCEPTION, especially ESCROW_OUT_OF_BOUND
	private int nUAs = 0;	// USER_ABORT
	private boolean balance_checking = false;

	public static long minExecutionMilliseconds = 999999999l;
	public static long maxExecutionMilliseconds = -1l;
	public static long totExecutionMilliseconds = 0;
	public static long totExecutions = 0;
	public static long totExecutionsLatency = 0;
	public static long[] latencyCounter = new long[] { 0, 0, 0, 0, 0, 0, 0, 0,
			0 };
	public static boolean checkLatency = false;
	public static boolean testing = true;  // run firstTests, etc.
	public static final ReentrantLock counterLock = new ReentrantLock();

	public static void main(String args[]) {
		(new MyTPCB(args)).run();
	}

	public void run() {
		long transactions_per_second = m_helpah.longValue("rate-limit");
		long transactions_per_milli = transactions_per_second / 1000l;
		long client_feedback_interval_secs = m_helpah
				.longValue("display-interval");
		long testDurationSecs = m_helpah.longValue("duration");
		int branches = m_helpah.intValue("branches");
		long lag_latency_seconds = 0;
		long lag_latency_millis = lag_latency_seconds * 1000l;
		long thisOutstanding = 0;
		long lastOutstanding = 0;
	//	final String statsFile = m_helpah.stringValue("stats");

		long transactions_this_second = 0;
		long last_millisecond = System.currentTimeMillis();
		long this_millisecond = System.currentTimeMillis();

		// setTransactionDisplayNames();  //moved earlier

		long startTime = System.currentTimeMillis();
		long endTime = startTime + (1000l * testDurationSecs);
		long currentTime = startTime;
		long lastFeedbackTime = startTime;
		long numSPCalls = 0;
		long startRecordingLatency = startTime + lag_latency_millis;
		long lastNumSPCalls = numSPCalls;
		float lastElapsedTimeSec2 = 0;
		long lastExeMs = totExecutionMilliseconds;
		long lastExesLat = totExecutionsLatency;

		while (endTime > currentTime) {
			numSPCalls++;

			try {
				tpcbSim.doOne();
			} catch (IOException e) {
			}

			transactions_this_second++;
			if (transactions_this_second >= transactions_per_milli) {
				this_millisecond = System.currentTimeMillis();
				while (this_millisecond <= last_millisecond) {
					this_millisecond = System.currentTimeMillis();
				}
				last_millisecond = this_millisecond;
				transactions_this_second = 0;
			}

			currentTime = System.currentTimeMillis();

			if ((!checkLatency) && (currentTime >= startRecordingLatency)) {
				// time to start recording latency information
				checkLatency = true;
			}

			if (currentTime >= (lastFeedbackTime + (client_feedback_interval_secs * 1000))) {
				final long elapsedTimeMillis2 = System.currentTimeMillis()
						- startTime;
				lastFeedbackTime = currentTime;

				final long runTimeMillis = endTime - startTime;

				float elapsedTimeSec2 = (System.currentTimeMillis() - startTime) / 1000F;
				if (totExecutionsLatency == 0) {
					totExecutionsLatency = 1;
				}

				double percentComplete = ((double) elapsedTimeMillis2 / (double) runTimeMillis) * 100;
				if (percentComplete > 100.0) {
					percentComplete = 100.0;
				}

				counterLock.lock();
				try {
					thisOutstanding = numSPCalls - totExecutions;

					double avgLatency = (double) totExecutionMilliseconds
							/ (double) totExecutionsLatency;
					double tps = numSPCalls / elapsedTimeSec2;
					double curTps = (numSPCalls - lastNumSPCalls)/(elapsedTimeSec2-lastElapsedTimeSec2); 
					double curAveLatency = (double)(totExecutionMilliseconds-lastExeMs)/((double)(totExecutionsLatency -lastExesLat));

					System.out
							.printf("%.3f%% Complete | Allowing %,d SP calls/sec: made %,d SP calls at %,.2f SP/sec | outstanding = %d (%d) | min = %d | max = %d | avg = %.2f\n",
									percentComplete,
									(transactions_per_milli * 1000l),
									numSPCalls, tps, thisOutstanding,
									(thisOutstanding - lastOutstanding),
									minExecutionMilliseconds,
									maxExecutionMilliseconds, avgLatency);
					System.out.printf("INCR: made %,d SP calls at %,.2f SP/sec, avgLat = %.2f\n",(numSPCalls - lastNumSPCalls), curTps, curAveLatency);

					for (int i = 0; i < procNames.length; i++) {
						System.out.printf("%16s: %10d total,", procNames[i],
								procCounts[i].intValue());
				
					}
							
							
					System.out.println();

					lastOutstanding = thisOutstanding;
					lastNumSPCalls = numSPCalls;
					lastElapsedTimeSec2 = elapsedTimeSec2;
					lastExeMs = totExecutionMilliseconds;
					lastExesLat = totExecutionsLatency;
				} finally {
					counterLock.unlock();
				}
			}
		}

		try {
			m_clientCon.drain();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}

		long elapsedTimeMillis = System.currentTimeMillis() - startTime;
		float elapsedTimeSec = elapsedTimeMillis / 1000F;

		System.out
				.println("============================== BENCHMARK RESULTS ==============================");
		System.out.printf("Time: %d ms\n", elapsedTimeMillis);
		System.out.printf("Total transactions: %d\n", numSPCalls);
		System.out.printf("Transactions per second: %.2f\n", (float) numSPCalls
				/ elapsedTimeSec);
//		System.out.printf("Number of OOBs: %d\n", nOOBs);
		System.out.printf("Number of CFEs: %d\n", nCFEs);
		System.out.printf("Number of UAs: %d\n", nUAs);
		for (int i = 0; i < procNames.length; i++) {
			System.out.printf("%23s: %10d total %12.2f txn/s %12.2f txn/m\n",
					procNames[i], procCounts[i].intValue(),
					procCounts[i].floatValue() / elapsedTimeSec,
					procCounts[i].floatValue() * 60 / elapsedTimeSec);
		}
		System.out
				.println("===============================================================================\n");

		System.out.println("\n");
		System.out
				.println("*************************************************************************");
		System.out.println("System Statistics");
		System.out
				.println("*************************************************************************");

		System.out.printf(" - Ran for %,.2f seconds\n", elapsedTimeSec);
		System.out.printf(" - Performed %,d Stored Procedure calls\n",
				numSPCalls);
		System.out.printf(" - At %,.2f calls per second\n", numSPCalls
				/ elapsedTimeSec);
		System.out
				.printf(" - Average Latency = %.2f ms\n",
						((double) totExecutionMilliseconds / (double) totExecutionsLatency));
		System.out.printf(" -   Latency   0ms -  25ms = %,d\n",
				latencyCounter[0]);
		System.out.printf(" -   Latency  25ms -  50ms = %,d\n",
				latencyCounter[1]);
		System.out.printf(" -   Latency  50ms -  75ms = %,d\n",
				latencyCounter[2]);
		System.out.printf(" -   Latency  75ms - 100ms = %,d\n",
				latencyCounter[3]);
		System.out.printf(" -   Latency 100ms - 125ms = %,d\n",
				latencyCounter[4]);
		System.out.printf(" -   Latency 125ms - 150ms = %,d\n",
				latencyCounter[5]);
		System.out.printf(" -   Latency 150ms - 175ms = %,d\n",
				latencyCounter[6]);
		System.out.printf(" -   Latency 175ms - 200ms = %,d\n",
				latencyCounter[7]);
		System.out.printf(" -   Latency 200ms+        = %,d\n",
				latencyCounter[8]);

		// 3. Performance statistics
		System.out
				.println("\n\n-------------------------------------------------------------------------------------\n"
						+ " System Statistics\n"
						+ "-------------------------------------------------------------------------------------\n\n");
		System.out.print(m_clientCon.getStatistics(TPCBConstants.TRANS_PROCS)
				.toString(false));

		// Dump stats to file
//		try {
//			m_clientCon.saveStatistics(statsFile);
//		} catch (IOException e) {
//			System.err.println("Unable to save statistics file: "
//					+ e.getMessage());
//		}
		try {
			System.out.println("Final report:");
			reportDB(); // should see history records now
			reportDBBalances(branches);
		} catch (Exception e) {
			System.err.println("Problem with CheckDB " + e.getMessage());
		}

		m_clientCon.close();
	}

	public MyTPCB(String args[]) {
		System.out.println("MyTPCB Starting...");
		m_helpah = new AppHelper(MyTPCB.class.getCanonicalName());
		m_helpah.add("duration", "run_duration_in_seconds",
				"Benchmark duration, in seconds.", 180);
		m_helpah.add("branches", "number_of_branches", "Number of branches", 12);
		m_helpah.add("scale-factor", "scale_factor", "Scale factor", 1.0);
		m_helpah.add("skew-factor", "skew_factor", "Skew factor", 0.0);
		m_helpah.add("load-threads", "number_of_load_threads",
				"Number of load threads", 4);
		m_helpah.add("rate-limit", "rate_limit",
				"Rate limit to start from (tps)", 200000);
		m_helpah.add("display-interval", "display_interval_in_seconds",
				"Interval for performance feedback, in seconds.", 10);
		m_helpah.add("servers", "comma_separated_server_list",
				"List of VoltDB servers to connect to.", "localhost");
		m_helpah.add("MP-percent", "transaction_MP_percent",
				"% of transactions that are MP", 15);

		System.out.println("args ");
		for (String s: args)
			System.out.println(" " + s);
		m_helpah.printActualUsage();
		m_helpah.setArguments(args);
		m_helpah.printActualUsage();

		int branches = m_helpah.intValue("branches");
		double scalefactor = m_helpah.doubleValue("scale-factor");
		double skewfactor = m_helpah.doubleValue("skew-factor");
		int transactionMPPercent = m_helpah.intValue("MP-percent");

		String servers = m_helpah.stringValue("servers");
		System.out.printf("Connecting to servers: %s\n", servers);
		int sleep = 1000;
		while (true) {
			try {
				m_clientCon = ClientConnectionPool.get(servers, 21212);
				break;
			} catch (Exception e) {
				System.err.printf(
						"Connection failed - retrying in %d second(s).\n",
						sleep / 1000);
				try {
					Thread.sleep(sleep);
				} catch (Exception tie) {
				}
				if (sleep < 8000)
					sleep += sleep;
			}
		}
		System.out.println("Connected.  Starting benchmark.");

		try {

			if ((int) (m_clientCon.execute(LoadStatus.class.getSimpleName())
					.getResults()[0].fetchRow(0).getLong(0)) == 0)
				(new MyTPCBLoader(args, m_clientCon)).run();
			else
				while ((int) (m_clientCon.execute(
						LoadStatus.class.getSimpleName()).getResults()[0]
						.fetchRow(0).getLong(0)) < branches)
					;

			// This prints info but then the server stops working
			// VoltTable[] results = m_clientCon.execute("@SystemCatalog",
			// "TABLES").getResults();
			// System.out.println("Information about the database schema:");
			// for (VoltTable node : results)
			// System.out.println(node.toString());
			// System.out.println("Information done");
			// m_clientCon.execute(CheckCatalog.class.getSimpleName());
			if (testing) {
			System.out.println("Initial report:");
			reportDB();
			reportDBBalances(branches);
			firstTests();
			reportDB();
			reportDBBalances(branches);
			System.out.println("GWW - finished firstTest...");
			}

		} catch (Exception e) {
			System.out.println("Problem executing SPs " + e);
			e.printStackTrace();
			System.exit(-1);
		}

		// makeForRun requires the value cLast from the load generator in
		// order to produce a valid generator for the run. Thus the sort
		// of weird eat-your-own ctor pattern.
		RandomGenerator.NURandC base_loadC = new RandomGenerator.NURandC(0, 0,
				0);
		RandomGenerator.NURandC base_runC = RandomGenerator.NURandC.makeForRun(
				new RandomGenerator.Implementation(0), base_loadC);
		RandomGenerator rng = new RandomGenerator.Implementation(0);
		rng.setC(base_runC);

		scaleParams = TPCBScaleParameters.makeWithScaleFactor(branches,
				scalefactor);
		tpcbSim = new TPCBSimulation(this, rng, new Clock.RealTime(),
				scaleParams, false, skewfactor, transactionMPPercent);
		setTransactionDisplayNames(); 
		if (testing) {
		try {

			System.out.println(" do one txn via doOne(), as in benchmark ");
			tpcbSim.doOne();
//			System.out.println("nOOBs = " + nOOBs);
			System.out.println("nCFEs = " + nCFEs);
			System.out.println("nUAs = " + nUAs);
			//assert(nOOBs == 1);
			Thread.sleep(2000);
			reportDBBalances(branches);
			Thread.sleep(5000);
		} catch (Exception e) {
			System.out.println("tpcbSim.doOne failed " + e);
		}
		}
		System.out.println("GWW - start true test...");	
	}

	private void firstTests() {

		Clock clock;
		TimestampType now;
		ClientResponse cr;
		try {
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
			System.out.println("Initially, trying one TPCBTransaction...");
			clock = new Clock.RealTime();
			now = clock.getDateTime();
			cr = m_clientCon.execute(
					// first branch 1, its first teller 10, first account 100000
					// delta 100
					TPCBTransaction.class.getSimpleName(), 1, 10, 100000, 100,
					now);

			printResultTables(cr);
			System.out
			.println("===============================================================================\n");
			System.out.println("Now trying one TPCBTransaction2Shot...");
			clock = new Clock.RealTime();
			now = clock.getDateTime();
			cr = m_clientCon.execute(
					// first branch 1, its first teller 10, first account 100000
					// delta 100
					TPCBTransaction2Shot.class.getSimpleName(), 1, 10, 100000, 100,
					now);

			printResultTables(cr);
			System.out
			.println("===============================================================================\n");
			System.out.println("Now trying one TPCBTransactionMP using only branch 1");
			cr = m_clientCon.execute(
					// first branch, 1, "other branch" is also branch 1 
					// first teller 10, first account 100000, delta 100
					TPCBTransactionMP.class.getSimpleName(), 1, 10, 100000, 1,
					100, now);
			printResultTables(cr);
			System.out
			.println("===============================================================================\n");
			System.out.println("Now trying one TPCBTransactionMP using only branch 1, 2 shots (not now)");
			cr = m_clientCon.execute(
					// first branch, 1, "other branch" is also branch 1 
					// first teller 10, first account 100000, delta 100
					TPCBTransactionMP.class.getSimpleName(), 1, 10, 100000, 1,
					100, now);
			printResultTables(cr);

			System.out
			.println("===============================================================================\n");

			System.out.println("Trying second TPCBTransaction after MP...");
			clock = new Clock.RealTime();
			now = clock.getDateTime();
			cr = m_clientCon.execute(
					// first branch 1, its first teller 10, first account 100000
					// delta 100
					TPCBTransaction.class.getSimpleName(), 1, 10, 100000, 100,
					now);
			printResultTables(cr);
			System.out
			.println("===============================================================================\n");

			System.out.println("===============================================================================\n");
			System.out.println("Now trying one TPCBTransactionMP using branches 1 and 2");
			cr = m_clientCon.execute(
					// first branch, 1, first teller 10, branch 2 first account 200000, other
					// branch 2, delta 100
					//TPCBTransactionMP.class.getSimpleName(), 1, 10, 200000, 2, 100, now);
					// first branch, 1, first teller 10, branch 3 first account 300000, other
					// branch 3, delta 100
					TPCBTransactionMP.class.getSimpleName(), 1, 10, 300000, 3, 100, now);
			printResultTables(cr);
			System.out.println("===============================================================================\n");
			System.out.println("Now trying one TPCBTransactionMP using branches 1 and 2, 2 shot");
			cr = m_clientCon.execute(
					// first branch, 1, first teller 10, branch 2 first account 200000, other
					// branch 2, delta 100
					//TPCBTransactionMP.class.getSimpleName(), 1, 10, 200000, 2, 100, now);
					// first branch, 1, first teller 10, branch 3 first account 300000, other
					// branch 3, delta 100
					TPCBTransactionMP2Shot.class.getSimpleName(), 1, 10, 300000, 3, 100, now);
			printResultTables(cr);
		// System.exit(1);  //TODO
		} catch (Exception e) {
			System.out.println("Problem executing first SPs " + e);
			e.printStackTrace();
			System.exit(1);
		}
		try{
			System.out
			.println("===============================================================================\n");
			System.out
			.println("===============================================================================\n");

			System.out.println("Trying third TPCBTransaction after second MP, big negative delta");
			clock = new Clock.RealTime();
			now = clock.getDateTime();
			cr = m_clientCon.execute(
					// first branch 1, its first teller 10, first account 100000
					// delta 100
					TPCBTransaction.class.getSimpleName(), 1, 10, 100000, -1000,
					now);
			printResultTables(cr);
			System.out
			.println("===============================================================================\n");

			reportDB();
			
		//	reportDB1();

		} catch (Exception e) {
			System.out.println("Expected exception: " + e);
			//e.printStackTrace();
		}
		return;
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
					+ " in table# = " + i);
		for (int i = 0; i < results.length; i++)
			if (results[i].getRowCount() == 1)
				System.out.println("1-row results provided scalar = "
						+ results[i].asScalarLong() + " for table # " + i);
			else
				System.out.println("no rows for table # " + i);
	}
	
	private void reportDB() throws Exception {
		VoltTable[] results = m_clientCon
				.execute(DBCheck.class.getSimpleName()).getResults();
		long nBranches = results[0].asScalarLong();
		long nAccounts = results[1].asScalarLong();
		long nTellers = results[2].asScalarLong();
		long nHistoryRecs = results[3].asScalarLong();
		System.out.println("TPCB database:");
		System.out.println(nBranches + " branch records");
		System.out.println(nAccounts + " account records");
		System.out.println(nTellers + " teller records");
		System.out.println(nHistoryRecs + " history records");
	}
	private void reportDB1() throws Exception {
		System.out.println("reportDB1 starting...");
		VoltTable[] results = m_clientCon
				.execute(DBCheck1.class.getSimpleName()).getResults();
		long nBranches = results[0].getRowCount();
		long nAccounts = results[1].getRowCount();
		long nTellers = results[2].getRowCount();
		long nHistoryRecs = results[3].asScalarLong();
		long nAccounts1 = results[4].getRowCount();
		System.out.println("TPCB database:");
		System.out.println(nBranches + " branch records");
		System.out.println(nAccounts + " account records");
		System.out.println(nTellers + " teller records");
		System.out.println(nHistoryRecs + " history records");
		System.out.println(nAccounts1 + " account records, asking for one");
		System.out.println(" branch records:");
		VoltTable branches = results[0];
		while (branches.advanceRow()) {
			System.out.print(" b_id: " + branches.getLong(0));
			System.out.print(" b_balance: " + branches.getLong(1));
			System.out.println(" b_filler: " + branches.getString(2));
		}
		VoltTable accounts = results[1];
		System.out.println("first 10 account records");
		//int count = 0;
		while (accounts.advanceRow()) { // && count < 10) {
			if (accounts.getLong(0) < 100010) {
			System.out.print(" a_id: " + accounts.getLong(0));
			System.out.print(" a_b_id: " + accounts.getLong(1));
			System.out.print(" a_balance: " + accounts.getLong(2));
			System.out.println(" a_filler: " + accounts.getString(3));
			}
		//	count ++;
		}
		VoltTable tellers = results[2];
		while (tellers.advanceRow()) {
			System.out.print(" t_id: " + tellers.getLong(0));
			System.out.print(" t_b_id: " + tellers.getLong(1));
			System.out.print(" t_balance: " + tellers.getLong(2));
			System.out.println(" t_filler: " + tellers.getString(3));
		}
		accounts = results[4];
		System.out.println(" among first 10 account records");
		while (accounts.advanceRow()) { //&& count < 10) {
			if (accounts.getLong(0) < 100010) {
			//System.out.print(" a_id: " + accounts.getLong(0));
			//System.out.print(" a_b_id: " + accounts.getLong(1));
			System.out.print(" a_balance: " + accounts.getLong(0));
			//System.out.println(" a_filler: " + accounts.getString(3));
			}
		}
		System.out.println("reportDB1 ending");

	}
	private void reportDBBalances(int branches) throws Exception {
		System.out.println("reportDBBalances starting...");
		VoltTable[] results = m_clientCon
				.execute(DBCheckBalance.class.getSimpleName()).getResults();
		analyzeDBBalances(results, branches);
	}
		
	private void analyzeDBBalances(VoltTable[] results, int branches) throws Exception {
			System.out.println("getDBBalances starting...");
			// results are per-branch totals
		long nBranches = results[0].getRowCount();
		long nAccounts = results[1].getRowCount();
		long nTellers = results[2].getRowCount();
//		long nHistory = results[3].getRowCount();
		assert (nBranches == branches);
		assert (nAccounts == branches);
		assert (nTellers == branches);
		//assert (nHistory == branches); // history table may be empty
		System.out.println("TPCB database:");
		System.out.println("branch bals:");
		VoltTable branchBals = results[0];
		long branchTotal = 0;
		while (branchBals.advanceRow()) {
			System.out.print(" b_id: " + branchBals.getLong(0));
			System.out.println(" balance: " + branchBals.getLong(1));
			branchTotal += branchBals.getLong(1);
		}
		VoltTable accountBals = results[1];
		long accountTotal = 0;
		//System.out.println("account bals by branch");
		// can't balance by branch, so don't print them out
		while (accountBals.advanceRow()) { 
			//System.out.print(" a_b_id: " + accountBals.getLong(0));
			//System.out.println(" sum_balance: " + accountBals.getLong(1));
			accountTotal += accountBals.getLong(1);
		}
		VoltTable tellerBals = results[2];
		long tellerTotal = 0;
		System.out.println("teller bals by branch");
		while (tellerBals.advanceRow()) {
			System.out.print(" t_b_id: " + tellerBals.getLong(0));
			System.out.println(" sum_balance: " + tellerBals.getLong(1));
			tellerTotal += tellerBals.getLong(1);
		}
		VoltTable historyBals = results[3];
		long historyTotal = 0;
		System.out.println("history sum deltas by branch");
		while (historyBals.advanceRow()) { 
			System.out.print(" t_b_id: " + historyBals.getLong(0));
			System.out.println(" sum_deltas: " + historyBals.getLong(1));
			historyTotal += historyBals.getLong(1);
		}
		System.out.println("totals: ");
		System.out.println("branch: " + branchTotal);
		System.out.println("teller: " + tellerTotal);
		System.out.println("account: " + accountTotal);
		System.out.println("history: " + historyTotal);
		if (branchTotal != tellerTotal || branchTotal!= accountTotal || branchTotal!= historyTotal)
			System.out.println("Balancing failed");
		System.out.println("reportDBBalances ending");

	}

	class VerifyBasicCallback implements ProcedureCallback {
		private final TPCBSimulation.Transaction m_transactionType;
		private final String m_procedureName;

		/**
		 * A generic callback that does not credit a transaction. Some
		 * transactions use two procedure calls - this counts as one transaction
		 * not two.
		 */
		VerifyBasicCallback() {
			m_transactionType = null;
			m_procedureName = null;
		}

		/** A generic callback that credits for the transaction type passed. */
		VerifyBasicCallback(TPCBSimulation.Transaction transaction,
				String procName) {
			m_transactionType = transaction;
			m_procedureName = procName;
		}

		@Override
		public String toString() {
			return "CB for " + m_procedureName;
		}

		@Override
		public void clientCallback(ClientResponse clientResponse) {
	
			boolean status = clientResponse.getStatus() == ClientResponse.SUCCESS;
			//             assert status : 
    		// "client response problem: status = " + clientResponse.getStatus();

			if (!status) {
//				System.out.println("class of Exception: " + clientResponse.getException().getClass().toString());
				// TODO find this info?
//				if (clientResponse.getException() instanceof ConstraintFailureException){
//					nCFEs++;
//				}
				//else
				if(clientResponse.getStatus() == ClientResponse.USER_ABORT)
					nUAs++;
//				else if (clientResponse.getStatusString().contains("OOB")) {
//					//System.out.println("OOB for " + m_transactionType.name());
//					nOOBs++;
//				}
				else {
				System.out.println("client call for "
						+ m_transactionType.name() + 
						" failed: status = " + clientResponse.getStatus() +
						" status string = " + clientResponse.getStatusString() +
						" app status = " + clientResponse.getAppStatus() +
	//					" exception = " + clientResponse.getException() +
						" totExecutions = " + totExecutions);
				}
			} 
			//else
				//System.out.println("Success for client call to " + m_transactionType.name());
			if (m_transactionType.name().startsWith("TPCB")) {
			//	VoltTable[] results = clientResponse.getResults();
				// check affected row counts for actual update/inserts
			//	for (int i = 1; i < 5; i++)
			//		assert (results[i].asScalarLong() == 1);
				// for (int i = 0; i<5; i++)
				// if (!(results[i].asScalarLong() == 1))
				// System.out.println("failed result: "+
				// results[i].asScalarLong() +
				// " i = " + i+ " exes: "+totExecutions);
			}
			if (m_transactionType.name().startsWith("REPORT")) {
				VoltTable[] results = clientResponse.getResults();
				try {
				analyzeDBBalances(results, 2);  //TODO: drop 2nd param
				} catch (Exception e) {
					System.out.println("exception from getDBBalances: "+e);
				}
			}

			if (m_transactionType != null) {
				procCounts[m_transactionType.ordinal()].incrementAndGet();

				counterLock.lock();
				try {
					totExecutions++;

					if (checkLatency) {
						long executionTime = clientResponse
								.getClientRoundtrip();

						totExecutionsLatency++;
						totExecutionMilliseconds += executionTime;

						if (executionTime < minExecutionMilliseconds) {
							minExecutionMilliseconds = executionTime;
						}

						if (executionTime > maxExecutionMilliseconds) {
							maxExecutionMilliseconds = executionTime;
						}

						// change latency to bucket
						int latencyBucket = (int) (executionTime / 25l);
						if (latencyBucket > 8) {
							latencyBucket = 8;
						}
						latencyCounter[latencyBucket]++;
					}
				} finally {
					counterLock.unlock();
				}
			}
		}
	}

	// TPC-B Txn

	@Override
	public void callTPCB_Txn(boolean isMP, int b_id, int a_id, int other_b_id,
			int t_id, long delta, TimestampType now) throws IOException {
		try {
			
			if (isMP) {
				//System.out.println("sending TPCB txn, delta "+delta);
				m_clientCon.executeAsync(new VerifyBasicCallback(
						TPCBSimulation.Transaction.TPCB_TXN_MP,
						TPCBConstants.TPCB_TXN_MP), TPCBConstants.TPCB_TXN_MP,
						b_id, t_id, a_id, other_b_id, delta, now);
				if (balance_checking) {
					System.out.println("sending REPORT_BAL txn, delta "+delta);
				m_clientCon.executeAsync(new VerifyBasicCallback(
						TPCBSimulation.Transaction.REPORT_BALS,
						TPCBConstants.REPORT_BALS), TPCBConstants.REPORT_BALS);
				}
			} else {
				//System.out.println("sending TPCB_MP txn, delta "+delta);
				assert (other_b_id == 0); // only needed for MP case
				m_clientCon.executeAsync(new VerifyBasicCallback(
						TPCBSimulation.Transaction.TPCB_TXN,
						TPCBConstants.TPCB_TXN), TPCBConstants.TPCB_TXN, b_id,
						t_id, a_id, delta, now);
			}
		} catch (Exception e) {
			System.out.println("see exception in callTPCB_Txn: " + e);  // happens if connection lost
			throw new IOException(e);
		}
	}
	

	class ResetBranchCallback implements ProcedureCallback {
		@Override
		public void clientCallback(ClientResponse clientResponse) {
			if (clientResponse.getStatus() == ClientResponse.SUCCESS) {
				procCounts[TPCBSimulation.Transaction.RESET_BRANCH.ordinal()]
						.incrementAndGet();

				System.out.println("Txn succeeded!");
				counterLock.lock();
				try {
					totExecutions++;

					if (checkLatency) {
						long executionTime = clientResponse
								.getClientRoundtrip();

						totExecutionsLatency++;
						totExecutionMilliseconds += executionTime;

						if (executionTime < minExecutionMilliseconds) {
							minExecutionMilliseconds = executionTime;
						}

						if (executionTime > maxExecutionMilliseconds) {
							maxExecutionMilliseconds = executionTime;
						}

						// change latency to bucket
						int latencyBucket = (int) (executionTime / 25l);
						if (latencyBucket > 8) {
							latencyBucket = 8;
						}
						latencyCounter[latencyBucket]++;
					}
				} finally {
					counterLock.unlock();
				}
			} else
				System.out.println("Txn failed!");
		}
	}

	@Override
	public void callResetBranch(int b_id, int accountsPerBranch,
			int tellersPerBranch) throws IOException {
		try {
			m_clientCon.executeAsync(new ResetBranchCallback(),
					TPCBConstants.RESET_BRANCH, b_id, accountsPerBranch,
					tellersPerBranch);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	private void setTransactionDisplayNames() {
		procNames = new String[TPCBSimulation.Transaction.values().length];
		System.out.println("setTransactionDisplayNames: #names = " +procNames.length );
		procCounts = new AtomicLong[procNames.length];
		for (int ii = 0; ii < TPCBSimulation.Transaction.values().length; ii++) {
			procNames[ii] = TPCBSimulation.Transaction.values()[ii].displayName;
			procCounts[ii] = new AtomicLong(0L);
		}
	}
}
