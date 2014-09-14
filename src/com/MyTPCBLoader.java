/* This file is part of VoltDB.
 * Copyright (C) 2008-2011 VoltDB Inc.
 *
 * This file contains original code and/or modifications of original code.
 * Any modifications made by VoltDB Inc. are licensed under the following
 * terms and conditions:
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
/* Copyright (C) 2008
 * Evan Jones
 * Massachusetts Institute of Technology
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
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.voltdb.VoltTable;
import org.voltdb.VoltType;
import org.voltdb.types.TimestampType;
import org.voltdb.client.ProcCallException;
import org.voltdb.client.NoConnectionsException;
import org.voltdb.client.exampleutils.AppHelper;
import org.voltdb.client.exampleutils.ClientConnection;

import java.util.concurrent.Semaphore;

/**
 * TPC-C database loader. Note: The methods order id parameters from "top level"
 * to "low level" parameters. However, the insert stored procedures use the
 * defined TPC-C table order, which goes from "low level" to "top level" (except
 * in the case of order line, which is inconsistent). As as example, this class
 * uses (o_w_id, o_d_id, o_id), whereas the order table is defined as (o_id,
 * o_d_id, o_w_id).
 */
public class MyTPCBLoader {
	private AppHelper m_helpah;
	private final ClientConnection m_voltClient;
	/**
	 * Number of threads to create to do the loading.
	 */
	private final LoadThread m_loadThreads[];
	private final int m_branches;

	private static final Semaphore m_finishedLoadThreads = new Semaphore(0);

	public MyTPCBLoader(String args[], ClientConnection voltClient) {
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
		m_helpah.add("MP-percent", "transaction_MP_percent", "% of transactions that are MP", 15);
		m_helpah.setArguments(args);

		initTableNames();
		int branches = m_helpah.intValue("branches");
		double scaleFactor = m_helpah.doubleValue("scale-factor");
		int loadThreads = m_helpah.intValue("load-threads");

		if (loadThreads > branches) {
			System.out
					.println("Specified number of load threads exceeds number of branches. Setting former equal to latter.");
			loadThreads = branches;
		}

		m_branches = branches;
		m_loadThreads = new LoadThread[loadThreads];

		for (int ii = 0; ii < loadThreads; ii++) {
			TPCBScaleParameters parameters = TPCBScaleParameters
					.makeWithScaleFactor(branches, scaleFactor);
			assert parameters != null;

			RandomGenerator generator = new RandomGenerator.Implementation(0);
			TimestampType generationDateTime = new TimestampType();
			RandomGenerator.NURandC loadC = RandomGenerator.NURandC
					.makeForLoad(generator);
			generator.setC(loadC);

			m_loadThreads[ii] = new LoadThread(generator, generationDateTime,
					parameters, ii);
		}

		m_voltClient = voltClient;
	}

	private String[] table_names = new String[8];
	private final static int IDX_BRANCHES = 0;
	private final static int IDX_ACCOUNTS = 1;
	private final static int IDX_TELLERS = 2;
	private final static int IDX_HISTORIES = 3;

	private void initTableNames() {
		table_names[IDX_BRANCHES] = "branch";
		table_names[IDX_ACCOUNTS] = "district";
		table_names[IDX_TELLERS] = "customer";
		table_names[IDX_HISTORIES] = "history";
	}

	/**
	 * Hint used when constructing the Client to control the size of buffers
	 * allocated for message serialization
	 * 
	 * @return
	 */
	protected int getExpectedOutgoingMessageSize() {
		return 10485760;
	}

	private void rethrowExceptionLoad(String procedureName,
			Object... parameters) {
		try {
			System.out.println("GWW - calling server for loading " + procedureName + "\n\t\t" + parameters.toString());
			
			VoltTable ret[] = m_voltClient.execute(procedureName, parameters)
					.getResults();
			System.out.println("GWW - finished calling server for loading");
			assert ret.length == 0;
		} catch (ProcCallException e) {
			e.printStackTrace();
			System.exit(-1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	class LoadThread extends Thread {

		private final RandomGenerator m_generator;
		private final TimestampType m_generationDateTime;
		private final TPCBScaleParameters m_parameters;

		/** table data FOR CURRENT BRANCH (LoadBranch is partitioned on BID). */
		private final VoltTable[] data_tables = new VoltTable[4]; // non
																	// replicated
																	// tables
		private volatile boolean m_doMakeReplicated = false;

		public LoadThread(RandomGenerator generator,
				TimestampType generationDateTime,
				TPCBScaleParameters parameters, int index) {
			super("Load Thread " + index);
			m_generator = generator;
			this.m_generationDateTime = generationDateTime;
			this.m_parameters = parameters;
		}

		@Override
		public void run() {
			Integer branchId = null;
			while ((branchId = availableBranchIds.poll()) != null) {
				System.err.println("Loading branch " + branchId);
				createDataTables();
				makeBranch(branchId);
				for (int i = 0; i < data_tables.length; ++i)
					data_tables[i] = null;
			}
			if (m_doMakeReplicated) {
				try {
					m_finishedLoadThreads.acquire(m_loadThreads.length - 1);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				// makeReplicated();
				m_doMakeReplicated = false;
			} else {
				m_finishedLoadThreads.release();
			}
			System.out.println("GWW - load thread done");
		}

		public void start(boolean doMakeReplicated) {
			m_doMakeReplicated = doMakeReplicated;
			super.start();
		}

		/**
		 * Inserts a new item with id, generated according to the TPC-C
		 * specification 4.3.3.1.
		 * 
		 * @param items
		 * @param id
		 * @param original
		 */

		public void generateBranch(int b_id) {
			ArrayList<Object> insertParameters = new ArrayList<Object>();
			String b_filler = m_generator.astring(TPCBConstants.MIN_DATA,
					TPCBConstants.MAX_DATA);
			insertParameters.add(b_id);
			insertParameters.add(0); // bal
			insertParameters.add(b_filler);
			data_tables[IDX_BRANCHES].addRow(insertParameters.toArray());
		}

	
		public void generateAccount(int a_id, int a_b_id) {
			String a_filler = m_generator.astring(TPCBConstants.MIN_DATA,
					TPCBConstants.MAX_DATA);
			@SuppressWarnings("unchecked")
			ArrayList<Object> insertParameters = new ArrayList<Object>(
					Arrays.asList(a_id, a_b_id, 0, a_filler));
			data_tables[IDX_ACCOUNTS].addRow(insertParameters.toArray());
		}

		public void generateTeller(int t_id, int t_b_id) {
			String t_filler = m_generator.astring(TPCBConstants.MIN_DATA,
					TPCBConstants.MAX_DATA);
			@SuppressWarnings("unchecked")
			ArrayList<Object> insertParameters = new ArrayList<Object>(
					Arrays.asList(t_id, t_b_id, 0, t_filler));
			data_tables[IDX_TELLERS].addRow(insertParameters.toArray());
		}

		public void generateHistory(int h_b_id, int h_a_id, int h_t_id) {
			TimestampType h_date = m_generationDateTime;
			double h_amount = 0;
			String h_data = m_generator.astring(TPCBConstants.MIN_DATA,
					TPCBConstants.MAX_DATA);

			data_tables[IDX_HISTORIES].addRow(h_b_id, h_a_id, h_t_id, h_date,
					h_amount, h_data);
		}

		// branch 2 has tellers 20, 21, ..., 29, accounts 200000 to 299999,
		// or for development, if accountsPerBranch = 100, accounts 200000 to 200099
		public void makeBranch(int b_id) {
			generateBranch(b_id);
			int first_aid = b_id * TPCBConstants.ACCOUNT_START_NUMBER_PER_BRANCH; 
			for (int a_id = first_aid, i=0; i < m_parameters.accountsPerBranch; ++a_id, ++i) {
				generateAccount(a_id, b_id);
			}
			int first_tid = b_id * m_parameters.tellersPerBranch;
			for (int t_id = first_tid, i=0; i < m_parameters.tellersPerBranch; ++t_id, ++i) {
				generateTeller(t_id, b_id);
			}

			commitDataTables(b_id); // flushout current data to avoid
									// outofmemory
			
			System.out.println("GWW - branch " + b_id + " made.");
		}

		/** Send to data to VoltDB and/or to the jdbc connection */
		private void commitDataTables(int b_id) {
			if (m_voltClient != null) {
				commitDataTables_VoltDB(b_id);
			}
			for (int i = 0; i < data_tables.length; ++i) {
				if (data_tables[i] != null) {
					data_tables[i].clearRowData();
				}
			}
		}

		private void commitDataTables_VoltDB(long w_id) {
			Object[] params = new Object[data_tables.length + 1];
			params[0] = (short) w_id;
			for (int i = 0; i < data_tables.length; ++i) {
				if (data_tables[i] != null && data_tables[i].getRowCount() > 0) {
					params[i + 1] = data_tables[i];
				}
			}
			rethrowExceptionLoad(TPCBConstants.LOAD_BRANCH, params);
		}

		private void createDataTables() {
			// customerNames.ensureRowCapacity(parameters.branchs *
			// parameters.districtsPerBranch * parameters.customersPerDistrict);
			// customerNames.ensureStringCapacity(parameters.branchess *
			// parameters.districtsPerBranch * parameters.customersPerDistrict *
			// (64));

			// non replicated tables
			for (int i = 0; i < data_tables.length; ++i)
				data_tables[i] = null;
			data_tables[IDX_BRANCHES] = new VoltTable(new VoltTable.ColumnInfo(
					"B_ID", VoltType.INTEGER), new VoltTable.ColumnInfo(
					"B_BALANCE", VoltType.INTEGER), new VoltTable.ColumnInfo(
					"B_FILLER", VoltType.STRING));
			// t.ensureRowCapacity(1);
			// t.ensureStringCapacity(200);

			data_tables[IDX_ACCOUNTS] = new VoltTable(new VoltTable.ColumnInfo(
					"A_ID", VoltType.INTEGER), new VoltTable.ColumnInfo(
					"A_B_ID", VoltType.INTEGER), new VoltTable.ColumnInfo(
					"A_BALANCE", VoltType.INTEGER), new VoltTable.ColumnInfo(
					"A_FILLER", VoltType.STRING));
			// t.ensureRowCapacity(1);
			// t.ensureStringCapacity(1 * (16 + 96 + 2 + 9));

			data_tables[IDX_TELLERS] = new VoltTable(new VoltTable.ColumnInfo(
					"T_ID", VoltType.INTEGER), new VoltTable.ColumnInfo(
					"T_B_ID", VoltType.INTEGER), new VoltTable.ColumnInfo(
					"T_BALANCE", VoltType.INTEGER), new VoltTable.ColumnInfo(
					"A_FILLER", VoltType.STRING));

			data_tables[IDX_HISTORIES] = new VoltTable(
					new VoltTable.ColumnInfo("H_B_ID", VoltType.INTEGER),
					new VoltTable.ColumnInfo("H_A_ID", VoltType.INTEGER),
					new VoltTable.ColumnInfo("H_T_ID", VoltType.INTEGER),
					new VoltTable.ColumnInfo("H_DELTA", VoltType.INTEGER),
					new VoltTable.ColumnInfo("H_FILLER", VoltType.STRING));
			// t.ensureRowCapacity(parameters.customersPerDistrict);
			// t.ensureStringCapacity(parameters.customersPerDistrict * (32));
		}
	}

	private ConcurrentLinkedQueue<Integer> availableBranchIds = new ConcurrentLinkedQueue<Integer>();

	public void run() throws NoConnectionsException {
		ArrayList<Integer> branchIds = new ArrayList<Integer>();
		for (int ii = 1; ii <= m_branches; ii++) {
			branchIds.add(ii);
		}
		// Shuffling spreads the loading out across physical hosts better
		Collections.shuffle(branchIds);
		availableBranchIds.addAll(branchIds);

		boolean doMakeReplicated = true;
		for (LoadThread loadThread : m_loadThreads) {
			loadThread.start(doMakeReplicated);
			doMakeReplicated = false;
		}

		for (int ii = 0; ii < m_loadThreads.length; ii++) {
			try {
				m_loadThreads[ii].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		System.out.println("GWW - start to drain client");
		
		try {
			m_voltClient.drain();
		} catch (InterruptedException e) {
			return;
		}
	}
}
