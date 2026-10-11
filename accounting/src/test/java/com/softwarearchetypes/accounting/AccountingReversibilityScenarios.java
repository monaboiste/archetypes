package com.softwarearchetypes.accounting;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.softwarearchetypes.quantity.money.Money;

import static com.softwarearchetypes.accounting.TransactionEntriesConstraint.BALANCING_CONSTRAINT;
import static com.softwarearchetypes.quantity.money.Money.pln;
import static java.time.Clock.fixed;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountingReversibilityScenarios {

    private static final Instant FUNDED_AT = Instant.parse("2026-10-08T08:00:00Z");
    private static final Instant HELD_AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final Instant SETTLED_AT = Instant.parse("2026-10-08T11:00:00Z");
    private static final Instant CORRECTED_AT = Instant.parse("2026-10-08T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-09T10:00:00Z");
    private final AccountingFacade accounting = AccountingConfiguration.inMemory(fixed(EXPIRES_AT.plusSeconds(3600), UTC)).facade();
    private final AccountId funding = account("BANK:FUNDING", "ASSET");
    private final AccountId wallet = account("CUSTOMER:123:WALLET", "LIABILITY");
    private final AccountId held = account("CUSTOMER:123:HELD", "LIABILITY");
    private final AccountId merchant = account("MERCHANT:456:SETTLEMENT", "LIABILITY");
    private final AccountId pool = account("BANK:LIMIT_GRANTS", "OFF_BALANCE");
    private final AccountId dailyAvailable = account("CUSTOMER:123:DAILY:2026-10-08:AVAILABLE", "OFF_BALANCE");
    private final AccountId dailyUsed = account("CUSTOMER:123:DAILY:2026-10-08:USED", "OFF_BALANCE");
    private final AccountId monthlyAvailable = account("CUSTOMER:123:MONTHLY:2026-10:AVAILABLE", "OFF_BALANCE");
    private final AccountId monthlyUsed = account("CUSTOMER:123:MONTHLY:2026-10:USED", "OFF_BALANCE");
    private final AccountId audit = account("CUSTOMER:123:LIMIT_DECISIONS", "OFF_BALANCE");
    private Transaction grants;

    @BeforeEach
    void provisionFundsAndAllowances() {
        assertThat(accounting.transfer(funding, wallet, pln(1000), FUNDED_AT, FUNDED_AT).success()).isTrue();
        grants = accounting.transaction().occurredAt(FUNDED_AT).appliesAt(FUNDED_AT).withTypeOf("LIMIT_GRANTED")
                .withMetadata("customerId", "123", "reason", "Period allowance")
                .executing().debitFrom(pool, pln(1300)).creditTo(dailyAvailable, pln(500))
                .creditTo(monthlyAvailable, pln(800)).build();
        execute(grants);
    }

    @Test
    void reversing_an_unused_authorization_restores_money_limits_and_the_approval_memo() {
        Transaction hold = hold(100);
        TransactionView original = accounting.findTransactionBy(hold.id()).orElseThrow();

        Transaction reversal = reverse(hold, CORRECTED_AT);
        execute(reversal);

        assertBalances(1000, 0, 0, 500, 800, 0);
        assertInverse(hold, reversal);
        assertOriginalUnchanged(original);
        assertThat(accounting.balance(audit)).hasValue(pln(0));
    }

    @Test
    void reversal_entries_preserve_context_and_record_the_new_correction_reason() {
        Transaction hold = hold(100);

        Transaction reversal = reverse(hold, CORRECTED_AT);
        execute(reversal);

        assertThat(entries(reversal)).allSatisfy(entry -> assertThat(entry.metadata().metadata())
                .containsEntry("customerId", "123").containsEntry("authorizationId", hold.id().value().toString())
                .containsEntry("reason", "Operator correction").containsEntry("correctedBy", "operator-1"));
        assertThat(entries(hold)).allSatisfy(entry ->
                assertThat(entry.metadata().metadata()).containsEntry("reason", "Card authorization"));
    }

    @Test
    void reversing_partial_settlement_restores_capacity_of_the_original_hold() {
        Transaction hold = hold(100);
        Transaction settlement = settle(hold, 40);

        Transaction reversal = reverse(settlement, CORRECTED_AT);
        execute(reversal);
        Transaction replacement = settlement(hold, 100, CORRECTED_AT.plusSeconds(1));
        execute(replacement);

        assertBalances(900, 0, 100, 400, 700, 100);
        assertInverse(settlement, reversal);
        assertThat(accounting.balanceAsOf(held, SETTLED_AT)).hasValue(pln(60));
        assertThat(accounting.balanceAsOf(held, CORRECTED_AT)).hasValue(pln(100));
    }

    @Test
    void full_hold_reversal_requires_undoing_its_settlements_first() {
        Transaction hold = hold(100);
        Transaction settlement = settle(hold, 40);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> reverse(hold, CORRECTED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(before);

        execute(reverse(settlement, CORRECTED_AT));
        execute(reverse(hold, CORRECTED_AT.plusSeconds(1)));
        assertBalances(1000, 0, 0, 500, 800, 0);
    }

    @Test
    void other_held_funds_cannot_cover_a_full_reversal_of_a_partially_used_hold() {
        Transaction hold = hold(100);
        settle(hold, 40);
        hold(100);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> reverse(hold, CORRECTED_AT)).isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(before);
    }

    @Test
    void allocating_a_reversal_credit_cannot_bypass_the_original_holds_remaining_capacity() {
        Transaction hold = hold(100);
        Transaction undo = reverse(settle(hold, 40), CORRECTED_AT);
        execute(undo);
        execute(settlement(hold, 100, CORRECTED_AT.plusSeconds(1)));
        hold(100);
        EntryView restoredCredit = credit(undo, held);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> accounting.transaction().occurredAt(CORRECTED_AT).appliesAt(CORRECTED_AT)
                .withTypeOf("HOLD_SETTLED").executing().debitFrom(held, pln(40), restoredCredit.entryId())
                .creditTo(merchant, pln(40)).build()).isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(before);
    }

    @Test
    void direct_and_descendant_allocations_in_one_transaction_share_the_same_ancestor_capacity() {
        Transaction hold = hold(100);
        Transaction undo = reverse(settle(hold, 40), CORRECTED_AT);
        execute(undo);
        hold(100);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> accounting.transaction().occurredAt(CORRECTED_AT).appliesAt(CORRECTED_AT)
                .withTypeOf("HOLD_SETTLED").executing()
                .debitFrom(held, pln(70), credit(hold, held).entryId())
                .debitFrom(held, pln(40), credit(undo, held).entryId())
                .creditTo(merchant, pln(110)).build()).isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(before);
    }

    @Test
    void allocating_a_reversal_credit_cannot_extend_its_ancestor_holds_validity() {
        Transaction hold = hold(100);
        Transaction undo = reverse(settle(hold, 40), EXPIRES_AT.plusSeconds(1));
        execute(undo);
        EntryView restoredCredit = credit(undo, held);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> accounting.transaction().occurredAt(EXPIRES_AT.plusSeconds(2)).appliesAt(EXPIRES_AT.plusSeconds(2))
                .withTypeOf("HOLD_SETTLED").executing().debitFrom(held, pln(40), restoredCredit.entryId())
                .creditTo(merchant, pln(40)).build()).isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(before);
    }

    @Test
    void two_prebuilt_reversals_cannot_both_undo_the_same_operation() {
        Transaction hold = hold(100);
        Transaction first = reverse(hold, CORRECTED_AT);
        Transaction stale = reverse(hold, CORRECTED_AT);
        execute(first);

        assertRejectedWithoutChanges(stale);

        assertBalances(1000, 0, 0, 500, 800, 0);
    }

    @Test
    void prebuilt_hold_reversal_rechecks_consumption_before_execution() {
        Transaction hold = hold(100);
        Transaction reversal = reverse(hold, CORRECTED_AT);
        settle(hold, 40);

        assertRejectedWithoutChanges(reversal);

        assertBalances(900, 60, 40, 400, 700, 100);
    }

    @Test
    void reversal_of_reversal_reinstates_consumption_on_the_original_hold() {
        Transaction hold = hold(100);
        Transaction settlement = settle(hold, 40);
        Transaction undo = reverse(settlement, CORRECTED_AT);
        execute(undo);
        Transaction reinstate = reverse(undo, CORRECTED_AT.plusSeconds(1));

        execute(reinstate);

        assertInverse(undo, reinstate);
        assertThatThrownBy(() -> settlement(hold, 61, CORRECTED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
        execute(settlement(hold, 60, CORRECTED_AT.plusSeconds(2)));
        assertBalances(900, 0, 100, 400, 700, 100);
    }

    @Test
    void reinstating_a_settlement_cannot_consume_capacity_already_reused_from_its_ancestor() {
        Transaction hold = hold(100);
        Transaction settlement = settle(hold, 40);
        Transaction undo = reverse(settlement, CORRECTED_AT);
        execute(undo);
        execute(settlement(hold, 100, CORRECTED_AT.plusSeconds(1)));
        hold(100);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> reverse(undo, CORRECTED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(before);
        assertBalances(800, 100, 100, 300, 600, 200);
    }

    @Test
    void prebuilt_reversal_of_reversal_rechecks_ancestor_capacity() {
        Transaction hold = hold(100);
        Transaction settlement = settle(hold, 40);
        Transaction undo = reverse(settlement, CORRECTED_AT);
        execute(undo);
        Transaction reinstate = reverse(undo, CORRECTED_AT.plusSeconds(2));
        execute(settlement(hold, 100, CORRECTED_AT.plusSeconds(1)));
        hold(100);

        assertRejectedWithoutChanges(reinstate);
    }

    @Test
    void cancellation_reversal_restores_money_and_usage_then_allows_a_correct_release() {
        Transaction hold = hold(100);
        Transaction settled = settle(hold, 40);
        Transaction release = release(hold, 60, CORRECTED_AT);
        execute(release);
        assertBalances(960, 0, 40, 460, 760, 40);

        Transaction undoRelease = reverse(release, CORRECTED_AT.plusSeconds(1));
        execute(undoRelease);

        assertBalances(900, 60, 40, 400, 700, 100);
        assertInverse(release, undoRelease);
        execute(reverse(settled, CORRECTED_AT.plusSeconds(2)));
        execute(release(hold, 100, CORRECTED_AT.plusSeconds(3)));
        assertBalances(1000, 0, 0, 500, 800, 0);
    }

    @Test
    void reversing_expiry_restores_the_remainder_but_does_not_extend_the_hold_validity() {
        Transaction hold = hold(100);
        settle(hold, 40);
        Transaction expiry = expiry(hold);
        execute(expiry);

        Transaction undo = reverse(expiry, EXPIRES_AT.plusSeconds(1));
        execute(undo);

        assertInverse(expiry, undo);
        assertThatThrownBy(() -> settlement(hold, 60, EXPIRES_AT.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not valid at");
        Transaction correctedExpiry = expiry(hold);
        execute(correctedExpiry);
        assertBalances(960, 0, 40, 400, 700, 100);
        assertThat(accounting.transaction().occurredAt(EXPIRES_AT).appliesAt(EXPIRES_AT)
                .compensatingExpired(credit(hold, held).entryId()).withCompensationAccount(wallet).build()).isEmpty();
    }

    @Test
    void unused_limit_grants_can_be_reversed_without_erasing_the_original_period() {
        TransactionView original = accounting.findTransactionBy(grants.id()).orElseThrow();

        Transaction undo = reverse(grants, CORRECTED_AT);
        execute(undo);

        assertInverse(grants, undo);
        assertOriginalUnchanged(original);
        assertThat(accounting.balance(dailyAvailable)).hasValue(pln(0));
        assertThat(accounting.balance(monthlyAvailable)).hasValue(pln(0));
        assertThat(accounting.balanceAsOf(dailyAvailable, HELD_AT)).hasValue(pln(500));
    }

    @Test
    void configured_balance_rules_reject_reversing_an_allowance_that_has_been_used() {
        hold(100);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> reverse(grants, CORRECTED_AT)).isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(before);
    }

    @Test
    void audit_corrections_preserve_the_rejected_attempt_and_do_not_affect_money() {
        Transaction rejection = accounting.transaction().occurredAt(HELD_AT).appliesAt(HELD_AT)
                .withTypeOf("LIMIT_ATTEMPT_REJECTED")
                .withMetadata("customerId", "123", "authorizationId", "declined-1", "decision", "REJECTED", "reason", "Daily limit")
                .executing().creditTo(audit, pln(600)).build();
        execute(rejection);
        TransactionView original = accounting.findTransactionBy(rejection.id()).orElseThrow();

        Transaction correction = reverse(rejection, CORRECTED_AT);
        execute(correction);

        assertInverse(rejection, correction);
        assertOriginalUnchanged(original);
        assertThat(accounting.balance(audit)).hasValue(pln(0));
        assertThat(accounting.findAccount(audit).orElseThrow().entries()).hasSize(2);
        assertBalances(1000, 0, 0, 500, 800, 0);
    }

    @Test
    void an_informational_entry_cannot_be_reversed_twice_without_undoing_its_first_reversal() {
        Transaction memo = accounting.transaction().occurredAt(HELD_AT).appliesAt(HELD_AT).withTypeOf("AUDIT")
                .executing().creditTo(audit, pln(600)).build();
        execute(memo);
        Transaction first = reverse(memo, CORRECTED_AT);
        Transaction stale = reverse(memo, CORRECTED_AT);
        execute(first);

        assertRejectedWithoutChanges(stale);

        assertThat(accounting.balance(audit)).hasValue(pln(0));
    }

    @Test
    void an_executed_command_cannot_be_posted_twice() {
        Transaction hold = hold(100);
        List<AccountView> before = accounting.findAll();

        var result = accounting.execute(hold);

        assertThat(result.failure()).isTrue();
        assertThat(result.getFailure()).contains("already exists");
        assertUnchanged(before);
    }

    @Test
    void reusing_a_transaction_id_cannot_overwrite_history_or_post_different_amounts() {
        Transaction hold = hold(100);
        TransactionView original = accounting.findTransactionBy(hold.id()).orElseThrow();
        Transaction collision = accounting.transaction().id(hold.id()).occurredAt(CORRECTED_AT).appliesAt(CORRECTED_AT)
                .withTypeOf("DIFFERENT_OPERATION").executing().debitFrom(wallet, pln(200)).creditTo(held, pln(200)).build();
        List<AccountView> before = accounting.findAll();

        assertThat(accounting.execute(collision).failure()).isTrue();

        assertUnchanged(before);
        assertOriginalUnchanged(original);
    }

    @Test
    void facade_cannot_execute_a_reversal_of_an_unrecorded_original() {
        Transaction preview = accounting.transaction().occurredAt(HELD_AT).appliesAt(HELD_AT).withTypeOf("PREVIEW")
                .executing().debitFrom(wallet, pln(10)).creditTo(held, pln(10)).build();
        Transaction reversal = accounting.transaction().occurredAt(CORRECTED_AT).appliesAt(CORRECTED_AT)
                .reverting(preview).build();

        assertRejectedWithoutChanges(reversal);
    }

    @Test
    void reversal_cannot_apply_before_its_original_operation() {
        Transaction hold = hold(100);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> reverse(hold, HELD_AT.minusNanos(1))).isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(before);
    }

    @Test
    void transaction_posting_collections_cannot_be_mutated_to_rewrite_history() {
        Transaction hold = hold(100);
        TransactionView original = accounting.findTransactionBy(hold.id()).orElseThrow();

        assertThatThrownBy(() -> hold.entries().values().iterator().next().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        assertOriginalUnchanged(original);
    }

    @Test
    void an_invalid_account_currency_is_rejected_before_any_account_receives_entries() {
        Transaction incompatible = accounting.transaction().occurredAt(HELD_AT).appliesAt(HELD_AT).withTypeOf("BAD_MEMO")
                .executing().creditTo(dailyUsed, pln(10)).creditTo(monthlyUsed, Money.usd(10)).build();

        assertRejectedWithoutChanges(incompatible);
        Transaction recovery = accounting.transaction().occurredAt(CORRECTED_AT).appliesAt(CORRECTED_AT).withTypeOf("VALID_MEMO")
                .executing().creditTo(dailyUsed, pln(1)).creditTo(monthlyUsed, pln(1)).build();
        execute(recovery);
        assertThat(accounting.findAccount(dailyUsed).orElseThrow().entries()).hasSize(1);
        assertThat(accounting.findAccount(monthlyUsed).orElseThrow().entries()).hasSize(1);
    }

    @Test
    void batch_execution_returns_all_successful_transaction_ids() {
        Transaction first = settlement(hold(100), 20, SETTLED_AT);
        Transaction second = accounting.transaction().occurredAt(SETTLED_AT).appliesAt(SETTLED_AT).withTypeOf("AUDIT_NOTE")
                .executing().creditTo(audit, pln(1)).build();

        var result = accounting.execute(first, second);

        assertThat(result.success()).isTrue();
        assertThat(result.getSuccess()).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void batch_stops_on_failure_and_prior_successes_remain_explicitly_reversible() {
        Transaction hold = hold(100);
        Transaction first = settlement(hold, 80, SETTLED_AT);
        Transaction stale = settlement(hold, 30, SETTLED_AT);
        Transaction later = accounting.transaction().occurredAt(SETTLED_AT).appliesAt(SETTLED_AT).withTypeOf("AUDIT_NOTE")
                .executing().creditTo(audit, pln(1)).build();

        var result = accounting.execute(first, stale, later);

        assertThat(result.failure()).isTrue();
        assertThat(accounting.findTransactionBy(first.id())).isPresent();
        assertThat(accounting.findTransactionBy(stale.id())).isEmpty();
        assertThat(accounting.findTransactionBy(later.id())).isEmpty();
        execute(reverse(first, CORRECTED_AT));
        execute(settlement(hold, 100, CORRECTED_AT.plusSeconds(1)));
        assertBalances(900, 0, 100, 400, 700, 100);
    }

    private Transaction hold(int amount) {
        TransactionId id = TransactionId.generate();
        Transaction hold = accounting.transaction().id(id).occurredAt(HELD_AT).appliesAt(HELD_AT).withTypeOf("HOLD_AUTHORIZED")
                .withMetadata("customerId", "123", "authorizationId", id.value().toString(), "reason", "Card authorization")
                .withTransactionEntriesConstraint(protectedBalances())
                .executing().debitFrom(wallet, pln(amount)).creditTo(held, pln(amount), Validity.until(EXPIRES_AT))
                .debitFrom(dailyAvailable, pln(amount)).creditTo(dailyUsed, pln(amount))
                .debitFrom(monthlyAvailable, pln(amount)).creditTo(monthlyUsed, pln(amount))
                .creditTo(audit, pln(amount)).build();
        execute(hold);
        return hold;
    }

    private Transaction settle(Transaction hold, int amount) {
        Transaction settlement = settlement(hold, amount, SETTLED_AT);
        execute(settlement);
        return settlement;
    }

    private Transaction settlement(Transaction hold, int amount, Instant at) {
        return accounting.transaction().occurredAt(at).appliesAt(at).withTypeOf("HOLD_SETTLED")
                .withMetadata("customerId", "123", "holdTransactionId", hold.id().value().toString())
                .withTransactionEntriesConstraint(protectedBalances())
                .executing().debitFrom(held, pln(amount), credit(hold, held).entryId()).creditTo(merchant, pln(amount)).build();
    }

    private Transaction release(Transaction hold, int amount, Instant at) {
        return accounting.transaction().occurredAt(at).appliesAt(at).withTypeOf("HOLD_RELEASED")
                .withMetadata("customerId", "123", "holdTransactionId", hold.id().value().toString())
                .withTransactionEntriesConstraint(protectedBalances())
                .executing().debitFrom(held, pln(amount), credit(hold, held).entryId()).creditTo(wallet, pln(amount))
                .debitFrom(dailyUsed, pln(amount), credit(hold, dailyUsed).entryId()).creditTo(dailyAvailable, pln(amount))
                .debitFrom(monthlyUsed, pln(amount), credit(hold, monthlyUsed).entryId()).creditTo(monthlyAvailable, pln(amount)).build();
    }

    private Transaction expiry(Transaction hold) {
        return accounting.transaction().occurredAt(EXPIRES_AT).appliesAt(EXPIRES_AT)
                .withMetadata("reason", "Authorization expired")
                .compensatingExpired(credit(hold, held).entryId()).withCompensationAccount(wallet).build().orElseThrow();
    }

    private Transaction reverse(Transaction original, Instant at) {
        // Reversal + Specification (L05/L09): opposite postings, explicit source links and current-state guards.
        return accounting.transaction().occurredAt(at).appliesAt(at)
                .withMetadata("reason", "Operator correction", "correctedBy", "operator-1")
                .withTransactionEntriesConstraint(protectedBalances()).reverting(original).build();
    }

    private TransactionEntriesConstraint protectedBalances() {
        return BALANCING_CONSTRAINT.and(new NonNegativeBalanceConstraint(Set.of(
                wallet, held, merchant, dailyAvailable, dailyUsed, monthlyAvailable, monthlyUsed)));
    }

    private void assertInverse(Transaction original, Transaction reversal) {
        TransactionView view = accounting.findTransactionBy(reversal.id()).orElseThrow();
        assertThat(view.refId()).isEqualTo(original.id());
        assertThat(view.type()).isEqualTo(TransactionType.REVERSAL);
        assertThat(entries(reversal)).hasSize(entries(original).size());
        assertThat(entries(original)).allSatisfy(source -> assertThat(entries(reversal))
                .filteredOn(entry -> entry.appliedTo().filter(source.entryId()::equals).isPresent())
                .singleElement().satisfies(entry -> {
                    assertThat(entry.accountId()).isEqualTo(source.accountId());
                    assertThat(entry.amount()).isEqualTo(source.amount().negate());
                    assertThat(entry.entryId()).isNotEqualTo(source.entryId());
                }));
    }

    private void assertOriginalUnchanged(TransactionView previous) {
        TransactionView current = accounting.findTransactionBy(previous.id()).orElseThrow();
        assertThat(current).isEqualTo(previous);
    }

    private void assertRejectedWithoutChanges(Transaction transaction) {
        List<AccountView> before = accounting.findAll();
        assertThat(accounting.execute(transaction).failure()).isTrue();
        assertThat(accounting.findTransactionBy(transaction.id())).isEmpty();
        assertUnchanged(before);
    }

    private void assertUnchanged(List<AccountView> before) {
        assertThat(accounting.findAll()).hasSize(before.size());
        assertThat(before).allSatisfy(previous -> {
            AccountView current = accounting.findAccount(previous.id()).orElseThrow();
            assertThat(current.balance()).isEqualTo(previous.balance());
            assertThat(current.entries()).containsExactlyInAnyOrderElementsOf(previous.entries());
        });
    }

    private void assertBalances(int available, int onHold, int settled, int daily, int monthly, int used) {
        assertThat(accounting.balance(wallet)).hasValue(pln(available));
        assertThat(accounting.balance(held)).hasValue(pln(onHold));
        assertThat(accounting.balance(merchant)).hasValue(pln(settled));
        assertThat(accounting.balance(dailyAvailable)).hasValue(pln(daily));
        assertThat(accounting.balance(monthlyAvailable)).hasValue(pln(monthly));
        assertThat(accounting.balance(dailyUsed)).hasValue(pln(used));
        assertThat(accounting.balance(monthlyUsed)).hasValue(pln(used));
    }

    private EntryView credit(Transaction transaction, AccountId account) {
        return entries(transaction).stream().filter(entry -> entry.accountId().equals(account)
                && entry.type() == EntryView.EntryType.CREDIT).findFirst().orElseThrow();
    }

    private List<EntryView> entries(Transaction transaction) {
        return accounting.findTransactionBy(transaction.id()).orElseThrow().entries().stream()
                .flatMap(accountEntries -> accountEntries.entries().stream()).toList();
    }

    private void execute(Transaction transaction) {
        var result = accounting.execute(transaction);
        assertThat(result.success()).as("Transaction execution: %s", result.failure() ? result.getFailure() : "success").isTrue();
    }

    private AccountId account(String name, String type) {
        var result = accounting.createAccount(CreateAccount.generate(name, type));
        assertThat(result.success()).isTrue();
        return result.getSuccess();
    }
}
