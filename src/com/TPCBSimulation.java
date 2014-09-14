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

import java.io.IOException;

import org.voltdb.types.TimestampType;

public class TPCBSimulation
{
    // type used by at least VoltDBClient and JDBCClient
    public static enum Transaction {
     
        TPCB_TXN("TPCB Txn"),
        TPCB_TXN_MP("TPCB MP Txn"),
        RESET_BRANCH("Reset Branch"),
        REPORT_BALS("Report Balances");

        private Transaction(String displayName) { this.displayName = displayName; }
        public final String displayName;
    }
    public interface ProcCaller {
        public void callResetBranch(int b_id, int accountsPerBranch,
                int tellersPerBranch)
        throws IOException;
     
        public void callTPCB_Txn(boolean isMP, int bid,int aid, int other_bid, int tid, long delta, TimestampType now)
        throws IOException;
       
    }

    private final TPCBSimulation.ProcCaller client;
    private final RandomGenerator generator;
    private final Clock clock;
    public TPCBScaleParameters parameters;
    private final boolean useBranchAffinity;
    private final long affineBranch;
    private final double m_skewFactor;
    static long lastAssignedBranchId = 1;
    private final int transactionMPPercent;

    public TPCBSimulation(TPCBSimulation.ProcCaller client, RandomGenerator generator,
                          Clock clock, TPCBScaleParameters parameters, boolean useBranchAffinity,
                          double skewFactor, int transactionMPPercent)
    {
        assert parameters != null;
        this.client = client;
        this.generator = generator;
        this.clock = clock;
        this.parameters = parameters;
        this.useBranchAffinity = useBranchAffinity;
        this.affineBranch = lastAssignedBranchId;
        this.transactionMPPercent = transactionMPPercent;
        m_skewFactor = skewFactor;

        lastAssignedBranchId += 1;
        if (lastAssignedBranchId > parameters.branches)
            lastAssignedBranchId = 1;
    }

    private short generateBranchId() {
        if (useBranchAffinity)
            return (short)this.affineBranch;
        else
            return (short)generator.skewedNumber(1, parameters.branches, m_skewFactor);
    }

 
    /** Executes a reset branch transaction. */
    public void doResetBranch() throws IOException {
        int b_id = generateBranchId();
        client.callResetBranch(b_id, parameters.accountsPerBranch, parameters.tellersPerBranch);
    }
    

  private int generateTellerId(int bid) {
      return (int)(TPCBConstants.TELLERS_PER_BRANCH*bid + generator.number(0, parameters.tellersPerBranch-1));
  }

  private int generateAccountIdInBranch(int bid) {
      return (int)(TPCBConstants.ACCOUNT_START_NUMBER_PER_BRANCH*(bid) + generator.number(0, parameters.accountsPerBranch-1));
  }

    /** Executes a TPCB transaction. */
    public void doTPCBTransaction()  throws IOException {
        int x = generator.number(1, 100);
        boolean isMP = false;
        int b_id = generateBranchId();
        int t_id = generateTellerId(b_id);
        int a_id;
        int other_b_id = 0;
        if (parameters.branches == 1 || x <= 100 - transactionMPPercent) {
            // 85% or whatever: dealing through own branch (or there is only 1 branch
           a_id = generateAccountIdInBranch(b_id);
        } else {
        	isMP = true;
            // 15%: dealing through another branch:
            // select in range [1, num_branches] excluding b_id
            other_b_id = (int)generator.numberExcluding(1, parameters.branches,
                    b_id);
            assert other_b_id != b_id;
            // System.out.println("gen MP txn, other_b_id = " + other_b_id);
            a_id = generateAccountIdInBranch(other_b_id);
        }
        long delta = generator.number(TPCBConstants.MIN_DELTA, TPCBConstants.MAX_DELTA);
        //long delta = -1000;
        //System.out.println("gen b_id = " + b_id + " t_id = " + t_id + " a_id = " + a_id + " delta = " + delta);

        TimestampType now = clock.getDateTime();
        
        client.callTPCB_Txn(isMP, b_id, a_id, other_b_id, t_id, delta, now);
    }

    public int doOne() throws IOException {
    	doTPCBTransaction();
    	return 0;
    }

}
