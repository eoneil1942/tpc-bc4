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

/** Stores the scaling parameters for loading and running. */
public class TPCBScaleParameters {
    public TPCBScaleParameters(int branches, int accountsPerBranch, int tellersPerBranch) {
              assert branches > 0;
        this.branches = branches;
        assert 1 <= tellersPerBranch &&
                tellersPerBranch <= TPCBConstants.TELLERS_PER_BRANCH;
        this.tellersPerBranch = tellersPerBranch;
        assert 1 <= accountsPerBranch &&
                accountsPerBranch <= TPCBConstants.ACCOUNTS_PER_BRANCH;
        this.accountsPerBranch = accountsPerBranch;
    }

    public static TPCBScaleParameters makeDefault(int branches) {
        return new TPCBScaleParameters( branches,
        		TPCBConstants.ACCOUNTS_PER_BRANCH, TPCBConstants.TELLERS_PER_BRANCH);
    }

    public static TPCBScaleParameters makeWithScaleFactor(int branches, double scaleFactor) {
        assert scaleFactor >= 1.0;

        int accounts = (int) (TPCBConstants.ACCOUNTS_PER_BRANCH/scaleFactor);
        if (accounts <= 0) accounts = 1;
        int tellers = (int) (TPCBConstants.TELLERS_PER_BRANCH/scaleFactor);
        if (tellers <= 0) tellers = 1;

        return new TPCBScaleParameters(branches, accounts, tellers);
    }
    
    public String toString() {
        String out = "";
        out += branches + " branches\n";
        out += tellersPerBranch + " tellers/branch\n";
        out += accountsPerBranch + " accounts/branch\n";
        return out;
    }
    
    public final int branches;
    public final int tellersPerBranch;
    public final int accountsPerBranch;
}
