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

import com.tpcbprocedures.*;

import com.tpcbprocedures.ResetBranch;

/** Holds TPC-B constants.  */
public final class TPCBConstants {
    private TPCBConstants() { assert false; }

    // Account constants
    // For development, use account numbers 100000, 100001, ... 100099 for branch 1
    //                                      200000, 200001, ... 200099 for branch 2, etc.
    // public static final int ACCOUNTS_PER_BRANCH = 100000;
    public static final int ACCOUNT_START_NUMBER_PER_BRANCH = 100000;
    public static final boolean debugSizing = false;
    public static final int ACCOUNTS_PER_BRANCH = debugSizing?10:100000; 
    // Teller constants
    public static final int TELLERS_PER_BRANCH = 10;
    // delta value range endpoints
    public static final int MIN_DELTA = -999999;
    public static final int MAX_DELTA = 999999;
    public static final int MIN_DATA = 12;
    public static final int MAX_DATA = 24;
  
    // It turns out that getSimpleName is slow: it calls getEnclosingClass and other crap. These
    // constants mean that if a name changes, the compile breaks, while not wasting time looking up
    // the names.
    public static final String INSERT_BRANCH = "InsertBranch";
    public static final String INSERT_ACCOUNT = "InsertAccount";
    public static final String INSERT_TELLER = "InsertTeller";
    public static final String INSERT_HISTORY = "InsertHistory";
    public static final String LOAD_BRANCH = LoadBranch.class.getSimpleName();
  
    public static final String TPCB_TXN = TPCBTransaction.class.getSimpleName();
    public static final String TPCB_TXN_MP = TPCBTransactionMP.class.getSimpleName();
    //public static final String MEASUREOVERHEAD = measureOverhead.class.getSimpleName();
    public static final String RESET_BRANCH = ResetBranch.class.getSimpleName();
    public static final String REPORT_BALS = DBCheckBalance.class.getSimpleName();
       
    public static final String[] TRANS_PROCS =
        {TPCB_TXN, TPCB_TXN_MP};
}
