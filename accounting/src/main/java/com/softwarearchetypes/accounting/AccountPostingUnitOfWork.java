package com.softwarearchetypes.accounting;

import java.util.List;
import java.util.Map;

final class AccountPostingUnitOfWork {

    private AccountPostingUnitOfWork() {
    }

    static void post(Map<Account, List<Entry>> entries) {
        // Unit of Work: finish staging every account before installing any prepared state.
        List<Account.StagedPosting> staged = entries.entrySet().stream()
                .map(posting -> posting.getKey().stageEntries(posting.getValue()))
                .toList();
        staged.forEach(Account.StagedPosting::commit);
    }
}
