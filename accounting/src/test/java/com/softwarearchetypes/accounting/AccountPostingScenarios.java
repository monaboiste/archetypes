package com.softwarearchetypes.accounting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.softwarearchetypes.accounting.events.AccountingEvent;
import com.softwarearchetypes.common.events.EventHandler;
import com.softwarearchetypes.common.events.PublishedEvent;
import com.softwarearchetypes.quantity.money.Money;

import static com.softwarearchetypes.accounting.TransactionEntriesConstraint.BALANCING_CONSTRAINT;
import static com.softwarearchetypes.quantity.money.Money.pln;
import static java.time.Clock.fixed;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountPostingScenarios {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void account_rejects_foreign_entries_without_applying_earlier_entries_in_the_batch() {
        Account account = account("Memo");
        Entry valid = credit(account.id(), pln(10));
        Entry foreign = credit(AccountId.generate(), pln(20));

        assertThatThrownBy(() -> account.addEntries(List.of(valid, foreign)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Entry must belong to the receiving account");

        assertEmpty(account);
    }

    @Test
    void account_rejects_incompatible_currency_without_mutating_its_entries_or_events() {
        Account account = account("Memo");

        assertThatThrownBy(() -> account.addEntries(List.of(credit(account.id(), pln(10)), credit(account.id(), Money.usd(20)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Entry currency must match account currency");

        assertEmpty(account);
    }

    @Test
    void a_later_account_rejection_discards_prepared_changes_for_an_earlier_account() {
        Account first = account("First");
        Account second = account("Second");
        Map<Account, List<Entry>> postings = new LinkedHashMap<>();
        postings.put(first, List.of(credit(first.id(), pln(10))));
        postings.put(second, List.of(credit(second.id(), Money.usd(20))));

        assertThatThrownBy(() -> AccountPostingUnitOfWork.post(postings))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Entry currency must match account currency");

        assertEmpty(first);
        assertEmpty(second);
    }

    @Test
    void failed_staging_preserves_existing_balance_entries_and_pending_events() {
        Account first = account("First");
        Account second = account("Second");
        Entry existing = credit(first.id(), pln(5));
        first.addEntry(existing);
        List<AccountingEvent> pending = first.getPendingEvents();
        Map<Account, List<Entry>> postings = new LinkedHashMap<>();
        postings.put(first, List.of(credit(first.id(), pln(10))));
        postings.put(second, List.of(credit(second.id(), Money.usd(20))));

        assertThatThrownBy(() -> AccountPostingUnitOfWork.post(postings)).isInstanceOf(IllegalArgumentException.class);

        assertThat(first.balance()).isEqualTo(pln(5));
        assertThat(first.entries().toList()).containsExactly(existing);
        assertThat(first.getPendingEvents()).containsExactlyElementsOf(pending);
        assertEmpty(second);
    }

    @Test
    void successful_staging_preserves_existing_history_and_installs_new_entries_and_events() {
        Account first = account("First");
        Account second = account("Second");
        Entry existing = credit(first.id(), pln(5));
        first.addEntry(existing);
        AccountingEvent existingEvent = first.getPendingEvents().getFirst();
        Entry firstPosting = credit(first.id(), pln(10));
        Entry secondPosting = credit(second.id(), pln(20));
        Entries snapshot = first.entries();

        AccountPostingUnitOfWork.post(Map.of(first, List.of(firstPosting), second, List.of(secondPosting)));

        assertThat(first.balance()).isEqualTo(pln(15));
        assertThat(second.balance()).isEqualTo(pln(20));
        assertThat(first.entries().toList()).containsExactly(existing, firstPosting);
        assertThat(second.entries().toList()).containsExactly(secondPosting);
        assertThat(first.getPendingEvents()).hasSize(2).contains(existingEvent);
        assertThat(second.getPendingEvents()).hasSize(1);
        assertThat(snapshot.toList()).containsExactly(existing);
    }

    @Test
    void staging_keeps_balance_entries_and_events_detached_until_commit() {
        Account account = account("Memo");
        Entry entry = credit(account.id(), pln(10));

        Account.StagedPosting staged = account.stageEntries(List.of(entry));

        assertEmpty(account);
        staged.commit();
        assertThat(account.balance()).isEqualTo(pln(10));
        assertThat(account.entries().toList()).containsExactly(entry);
        assertThat(account.getPendingEvents()).hasSize(1);
    }

    @Test
    void a_prepared_posting_cannot_be_recommitted_over_newer_state() {
        Account account = account("Memo");
        Account.StagedPosting staged = account.stageEntries(List.of(credit(account.id(), pln(10))));
        staged.commit();
        AccountPostingUnitOfWork.post(Map.of(account, List.of(credit(account.id(), pln(20)))));

        assertThatThrownBy(staged::commit).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Staged posting has already been committed");

        assertThat(account.balance()).isEqualTo(pln(30));
        assertThat(account.entries().toList()).hasSize(2);
        assertThat(account.getPendingEvents()).hasSize(2);
    }

    @Test
    void an_entries_copy_does_not_provide_a_backdoor_to_change_account_history() {
        Account account = account("Memo");
        Entry existing = credit(account.id(), pln(5));
        account.addEntry(existing);

        account.entries().add(credit(account.id(), pln(100)));

        assertThat(account.entries().toList()).containsExactly(existing);
        assertThat(account.balance()).isEqualTo(pln(5));
        assertThat(account.getPendingEvents()).hasSize(1);
    }

    @Test
    void failed_postings_publish_no_events_and_cannot_leak_into_a_later_successful_save() {
        AccountingConfiguration configuration = AccountingConfiguration.inMemory(fixed(NOW, UTC));
        AccountingFacade accounting = configuration.facade();
        List<PublishedEvent> published = new ArrayList<>();
        configuration.eventPublisher().register(new EventHandler() {
            @Override
            public boolean supports(PublishedEvent event) {
                return event instanceof AccountingEvent;
            }

            @Override
            public void handle(PublishedEvent event) {
                published.add(event);
            }
        });
        AccountId first = createMemo(accounting, "First");
        AccountId second = createMemo(accounting, "Second");
        Transaction invalid = accounting.transaction().occurredAt(NOW).appliesAt(NOW).withTypeOf("INVALID_MEMO")
                .executing().creditTo(first, pln(10)).creditTo(second, Money.usd(20)).build();

        assertThat(accounting.execute(invalid).failure()).isTrue();

        assertThat(published).isEmpty();
        assertThat(accounting.findTransactionBy(invalid.id())).isEmpty();
        assertEmpty(configuration.accountRepository().find(first).orElseThrow());
        assertEmpty(configuration.accountRepository().find(second).orElseThrow());
        Transaction recovery = accounting.transaction().occurredAt(NOW).appliesAt(NOW).withTypeOf("VALID_MEMO")
                .executing().creditTo(first, pln(1)).creditTo(second, pln(2)).build();
        assertThat(accounting.execute(recovery).success()).isTrue();
        assertThat(published).hasSize(2);
        assertThat(accounting.findAccount(first).orElseThrow().entries()).hasSize(1);
        assertThat(accounting.findAccount(second).orElseThrow().entries()).hasSize(1);
        assertThat(configuration.accountRepository().find(first).orElseThrow().getPendingEvents()).isEmpty();
        assertThat(configuration.accountRepository().find(second).orElseThrow().getPendingEvents()).isEmpty();
    }

    @Test
    void committed_state_keeps_live_identity_and_prebuilt_commands_observe_current_balances() {
        AccountingConfiguration configuration = AccountingConfiguration.inMemory(fixed(NOW, UTC));
        AccountingFacade accounting = configuration.facade();
        AccountId available = createMemo(accounting, "Available");
        AccountId used = createMemo(accounting, "Used");
        Transaction grant = accounting.transaction().occurredAt(NOW).appliesAt(NOW).withTypeOf("GRANT")
                .executing().creditTo(available, pln(100)).build();
        assertThat(accounting.execute(grant).success()).isTrue();
        Account live = configuration.accountRepository().find(available).orElseThrow();
        Transaction first = use(accounting, available, used, 70);
        Transaction stale = use(accounting, available, used, 50);

        assertThat(accounting.execute(first).success()).isTrue();
        assertThat(accounting.execute(stale).failure()).isTrue();

        assertThat(configuration.accountRepository().find(available).orElseThrow()).isSameAs(live);
        assertThat(live.balance()).isEqualTo(pln(30));
        assertThat(accounting.balance(used)).hasValue(pln(70));
        assertThat(accounting.findTransactionBy(stale.id())).isEmpty();
        assertThat(live.entries().toList()).hasSize(2);
    }

    private Transaction use(AccountingFacade accounting, AccountId available, AccountId used, int amount) {
        return accounting.transaction().occurredAt(NOW).appliesAt(NOW).withTypeOf("USAGE")
                .withTransactionEntriesConstraint(BALANCING_CONSTRAINT.and(new NonNegativeBalanceConstraint(Set.of(available))))
                .executing().debitFrom(available, pln(amount)).creditTo(used, pln(amount)).build();
    }

    private Account account(String name) {
        return new Account(AccountId.generate(), AccountType.OFF_BALANCE, AccountName.of(name));
    }

    private Entry credit(AccountId account, Money amount) {
        return new AccountCredited(account, TransactionId.generate(), amount, NOW, NOW);
    }

    private AccountId createMemo(AccountingFacade accounting, String name) {
        var result = accounting.createAccount(CreateAccount.generate(name, "OFF_BALANCE"));
        assertThat(result.success()).isTrue();
        return result.getSuccess();
    }

    private void assertEmpty(Account account) {
        assertThat(account.balance()).isEqualTo(pln(0));
        assertThat(account.entries().toList()).isEmpty();
        assertThat(account.getPendingEvents()).isEmpty();
    }
}
