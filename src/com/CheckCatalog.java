package com;

import java.io.IOException;

import org.voltdb.ProcInfo;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.client.ClientConfig;

@ProcInfo(singlePartition = false)
/**
 * Loads initial data into TPCB tables.
 */
public class CheckCatalog extends VoltProcedure {

	public long run() throws VoltAbortException {

		final ClientConfig clientConfig = new ClientConfig("program", "none");

		final org.voltdb.client.Client voltclient = org.voltdb.client.ClientFactory
				.createClient(clientConfig);

		try {
			voltclient.createConnection("localhost");
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}

		VoltTable[] results = null;
		try {
			results = voltclient.callProcedure("@SystemCatalog", "TABLES")
					.getResults();
			System.out.println("Information about the database schema:");
			for (VoltTable node : results)
				System.out.println(node.toString());
		} catch (Exception e1) {
			return -1;
		}

		return 0;
	}

}
