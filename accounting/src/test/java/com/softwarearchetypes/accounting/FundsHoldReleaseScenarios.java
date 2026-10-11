package com.softwarearchetypes.accounting;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.softwarearchetypes.quantity.money.Money;

import static com.softwarearchetypes.quantity.money.Money.pln;
import static java.time.Clock.fixed;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundsHoldReleaseScenarios {

    private static final Instant FUNDED_AT = Instant.parse("2026-10-08T08:00:00Z");
    private static final Instant HELD_AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final Instant SETTLED_AT = Instant.parse("2026-10-08T11:00:00Z");
    private static final Instant CANCELED_AT = Instant.parse("2026-10-08T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-09T10:00:00Z");

    private AccountingFacade accounting;
    private AccountId wallet;
    private AccountId held;
    private AccountId merchant;
    private EntryView hold;

    @BeforeEach
    void setUp() {
        setUpAt(EXPIRES_AT);
    }

    @Test
    void cancellation_returns_unused_funds_and_preserves_the_original_hold() {
        TransactionView original = accounting.findTransactionBy(hold.transactionId()).orElseThrow();
        Transaction release = releaseRemaining().orElseThrow();

        assertThat(accounting.execute(release).success()).isTrue();

        assertBalances(1000, 0, 0);
        assertThat(remaining(hold)).isEqualTo(pln(0));
        assertThat(accounting.findTransactionBy(hold.transactionId())).contains(original);
        assertThat(accounting.findTransactionBy(release.id()).orElseThrow().type())
                .isEqualTo(TransactionType.of("HOLD_RELEASED"));
        assertAudit(release, "Customer canceled", pln(100));
        assertThat(accounting.balanceAsOf(held, CANCELED_AT.minusNanos(1))).hasValue(pln(100));
        assertThat(accounting.balanceAsOf(held, CANCELED_AT)).hasValue(pln(0));
    }

    @Test
    void cancellation_after_partial_settlement_returns_only_the_remainder() {
        settle(pln(40));
        Transaction release = releaseRemaining().orElseThrow();

        assertThat(accounting.execute(release).success()).isTrue();

        assertBalances(960, 0, 40);
        assertAudit(release, "Customer canceled", pln(60));
        assertThat(accounting.findAccount(held).orElseThrow().entries()).hasSize(3).contains(hold);
        assertThat(expire()).isEmpty();
    }

    @Test
    void repeating_cancellation_produces_no_additional_postings() {
        assertThat(accounting.execute(releaseRemaining().orElseThrow()).success()).isTrue();
        List<AccountView> before = accounting.findAll();

        assertThat(releaseRemaining()).isEmpty();
        assertThat(expire()).isEmpty();

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void stale_release_is_rejected_if_part_of_the_hold_was_settled() {
        Transaction release = releaseRemaining().orElseThrow();
        settle(pln(40));

        assertExecutionRejectedWithoutChanges(release);

        assertThat(accounting.execute(releaseRemaining().orElseThrow()).success()).isTrue();
        assertBalances(960, 0, 40);
    }

    @Test
    void release_cannot_return_more_than_the_unsettled_amount() {
        settle(pln(40));
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> release(pln(61))).isInstanceOf(IllegalArgumentException.class);

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void ledger_exposes_the_hold_deadline_without_silently_releasing_expired_funds() {
        assertThat(hold.validity()).isEqualTo(Validity.between(HELD_AT, EXPIRES_AT));
        assertThat(hold.validity().hasExpired(EXPIRES_AT)).isTrue();
        assertThat(accounting.findAccount(held).orElseThrow().entries()).contains(hold);
        assertThat(accounting.balanceAsOf(held, EXPIRES_AT)).hasValue(pln(100));
        assertBalances(900, 100, 0);
    }

    @Test
    void expiry_at_the_exact_deadline_returns_unused_funds_with_an_audit_trail() {
        TransactionView original = accounting.findTransactionBy(hold.transactionId()).orElseThrow();
        Transaction expiry = expire().orElseThrow();

        assertThat(accounting.execute(expiry).success()).isTrue();

        assertBalances(1000, 0, 0);
        assertThat(remaining(hold)).isEqualTo(pln(0));
        TransactionView recorded = accounting.findTransactionBy(expiry.id()).orElseThrow();
        assertThat(recorded.type()).isEqualTo(TransactionType.EXPIRATION_COMPENSATION);
        assertThat(recorded.refId()).isEqualTo(hold.transactionId());
        assertThat(recorded.appliesAt()).isEqualTo(EXPIRES_AT);
        assertThat(accounting.findTransactionBy(hold.transactionId())).contains(original);
        assertAudit(expiry, "Authorization expired", pln(100));
    }

    @Test
    void expiry_after_partial_settlement_returns_only_unused_funds() {
        settle(pln(40));
        Transaction expiry = expire().orElseThrow();

        assertThat(accounting.execute(expiry).success()).isTrue();

        assertBalances(960, 0, 40);
        assertAudit(expiry, "Authorization expired", pln(60));
        assertThat(remaining(hold)).isEqualTo(pln(0));
        assertThat(expire()).isEmpty();
    }

    @Test
    void fully_settled_hold_needs_no_expiry_transaction() {
        settle(pln(100));
        List<AccountView> before = accounting.findAll();

        assertThat(expire()).isEmpty();

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
        assertBalances(900, 0, 100);
    }

    @Test
    void repeated_expiry_does_not_release_the_same_funds_twice() {
        assertThat(accounting.execute(expire().orElseThrow()).success()).isTrue();
        List<AccountView> before = accounting.findAll();

        assertThat(expire()).isEmpty();

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void two_prebuilt_expiry_transactions_cannot_both_compensate_the_hold() {
        Transaction first = expire().orElseThrow();
        Transaction stale = expire().orElseThrow();
        assertThat(accounting.execute(first).success()).isTrue();

        assertExecutionRejectedWithoutChanges(stale);

        assertBalances(1000, 0, 0);
    }

    @Test
    void expiry_execution_rechecks_remaining_funds_after_a_late_recorded_settlement() {
        Transaction stale = expire().orElseThrow();
        settle(pln(40));

        assertExecutionRejectedWithoutChanges(stale);

        assertThat(accounting.execute(expire().orElseThrow()).success()).isTrue();
        assertBalances(960, 0, 40);
    }

    @Test
    void expiry_is_rejected_before_the_deadline() {
        setUpAt(EXPIRES_AT.minusNanos(1));
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(this::expire).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has not expired yet");

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void expiry_cannot_be_backdated_to_before_the_deadline() {
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> accounting.transaction()
                .occurredAt(EXPIRES_AT)
                .appliesAt(EXPIRES_AT.minusNanos(1))
                .compensatingExpired(hold.entryId())
                .withCompensationAccount(wallet)
                .build()).isInstanceOf(IllegalArgumentException.class);

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void expiry_does_not_release_another_hold_on_the_same_account() {
        EntryView otherHold = placeHold(pln(200), "auth-2", EXPIRES_AT.plusSeconds(3600));

        assertThat(accounting.execute(expire().orElseThrow()).success()).isTrue();

        assertBalances(800, 200, 0);
        assertThat(remaining(hold)).isEqualTo(pln(0));
        assertThat(remaining(otherHold)).isEqualTo(pln(200));
        assertThat(expire()).isEmpty();
    }

    @Test
    void compensated_hold_cannot_be_settled_later_with_an_earlier_effective_time() {
        assertThat(accounting.execute(expire().orElseThrow()).success()).isTrue();
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> settlement(pln(1))).isInstanceOf(IllegalArgumentException.class);

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void generic_expired_debit_compensation_also_exhausts_its_source() {
        Transaction original = accounting.transaction()
                .occurredAt(HELD_AT).appliesAt(HELD_AT).withTypeOf("EXPIRING_DEBIT")
                .executing()
                .debitFrom(wallet, pln(30), Validity.until(EXPIRES_AT))
                .creditTo(merchant, pln(30))
                .build();
        assertThat(accounting.execute(original).success()).isTrue();
        EntryView source = entriesOf(original.id()).stream()
                .filter(entry -> entry.accountId().equals(wallet)).findFirst().orElseThrow();
        Transaction compensation = accounting.transaction()
                .occurredAt(EXPIRES_AT).appliesAt(EXPIRES_AT)
                .compensatingExpired(source.entryId()).withCompensationAccount(merchant)
                .build().orElseThrow();

        assertThat(accounting.execute(compensation).success()).isTrue();

        assertBalances(900, 100, 0);
        assertThat(remaining(source)).isEqualTo(pln(0));
        assertThat(accounting.transaction().occurredAt(EXPIRES_AT).appliesAt(EXPIRES_AT)
                .compensatingExpired(source.entryId()).withCompensationAccount(merchant).build()).isEmpty();
    }

    @Test
    void ordinary_settlement_cannot_consume_a_hold_at_its_expiry_deadline() {
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> context(EXPIRES_AT, "Late settlement").withTypeOf("HOLD_SETTLED")
                .executing().debitFrom(held, pln(1), hold.entryId()).creditTo(merchant, pln(1)).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not valid at");

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void expiry_cannot_use_the_source_account_as_its_own_compensation_account() {
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> context(EXPIRES_AT, "Authorization expired")
                .compensatingExpired(hold.entryId()).withCompensationAccount(held).build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void rebuilding_expiry_does_not_accumulate_postings_and_returns_empty_after_execution() {
        var builder = context(EXPIRES_AT, "Authorization expired")
                .compensatingExpired(hold.entryId()).withCompensationAccount(wallet);
        builder.build().orElseThrow();
        Transaction rebuilt = builder.build().orElseThrow();

        assertThat(accounting.execute(rebuilt).success()).isTrue();

        assertAudit(rebuilt, "Authorization expired", pln(100));
        assertThat(builder.build()).isEmpty();
        assertBalances(1000, 0, 0);
    }

    private void setUpAt(Instant now) {
        accounting = AccountingConfiguration.inMemory(fixed(now, UTC)).facade();
        AccountId funding = createAccount("BANK:FUNDING", "ASSET");
        wallet = createAccount("CUSTOMER:123:WALLET", "LIABILITY");
        held = createAccount("CUSTOMER:123:HELD", "LIABILITY");
        merchant = createAccount("MERCHANT:456:SETTLEMENT", "LIABILITY");
        assertThat(accounting.transfer(funding, wallet, pln(1000), FUNDED_AT, FUNDED_AT).success()).isTrue();
        hold = placeHold(pln(100), "auth-1", EXPIRES_AT);
    }

    private EntryView placeHold(Money amount, String authorizationId, Instant expiresAt) {
        Transaction transaction = accounting.transaction()
                .occurredAt(HELD_AT).appliesAt(HELD_AT).withTypeOf("HOLD_PLACED")
                .withMetadata("customerId", "123", "authorizationId", authorizationId, "reason", "Hotel authorization")
                .executing()
                .debitFrom(wallet, amount)
                // Validity (L06): the deadline belongs to the held credit, not a separate Hold entity.
                .creditTo(held, amount, Validity.between(HELD_AT, expiresAt))
                .build();
        assertThat(accounting.execute(transaction).success()).isTrue();
        return entriesOf(transaction.id()).stream()
                .filter(entry -> entry.accountId().equals(held)).findFirst().orElseThrow();
    }

    private Optional<Transaction> releaseRemaining() {
        Money remainder = remaining(hold);
        return remainder.isZero() ? Optional.empty() : Optional.of(release(remainder));
    }

    private Transaction release(Money amount) {
        // Compensation + Allocation (L05/L06): return unused value without reversing settled funds.
        return context(CANCELED_AT, "Customer canceled").withTypeOf("HOLD_RELEASED")
                .executing().debitFrom(held, amount, hold.entryId()).creditTo(wallet, amount).build();
    }

    private Optional<Transaction> expire() {
        // Expiration compensation (L06): the existing builder computes and returns only the remainder.
        return context(EXPIRES_AT, "Authorization expired")
                .compensatingExpired(hold.entryId()).withCompensationAccount(wallet).build();
    }

    private Transaction settlement(Money amount) {
        return context(SETTLED_AT, "Merchant settlement").withTypeOf("HOLD_SETTLED")
                .executing().debitFrom(held, amount, hold.entryId()).creditTo(merchant, amount).build();
    }

    private void settle(Money amount) {
        assertThat(accounting.execute(settlement(amount)).success()).isTrue();
    }

    private TransactionBuilder context(Instant time, String reason) {
        return accounting.transaction().occurredAt(time).appliesAt(time)
                .withMetadata("customerId", "123", "authorizationId", "auth-1",
                        "holdTransactionId", hold.transactionId().value().toString(), "reason", reason);
    }

    private Money remaining(EntryView source) {
        return accounting.findAccount(source.accountId()).orElseThrow().entries().stream()
                .filter(entry -> entry.appliedTo().filter(source.entryId()::equals).isPresent())
                .map(EntryView::amount).reduce(source.amount(), Money::add);
    }

    private List<EntryView> entriesOf(TransactionId id) {
        return accounting.findTransactionBy(id).orElseThrow().entries().stream()
                .flatMap(accountEntries -> accountEntries.entries().stream()).toList();
    }

    private void assertExecutionRejectedWithoutChanges(Transaction transaction) {
        List<AccountView> before = accounting.findAll();
        assertThat(accounting.execute(transaction).failure()).isTrue();
        assertThat(accounting.findTransactionBy(transaction.id())).isEmpty();
        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    private void assertAudit(Transaction transaction, String reason, Money amount) {
        List<EntryView> entries = entriesOf(transaction.id());
        assertThat(entries).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.metadata().metadata()).containsEntry("customerId", "123")
                    .containsEntry("authorizationId", "auth-1")
                    .containsEntry("holdTransactionId", hold.transactionId().value().toString())
                    .containsEntry("reason", reason);
            assertThat(accounting.findAccount(entry.accountId()).orElseThrow().entries()).contains(entry);
        });
        assertThat(entries).filteredOn(entry -> entry.accountId().equals(held)).singleElement().satisfies(entry -> {
            assertThat(entry.amount()).isEqualTo(amount.negate());
            assertThat(entry.appliedTo()).contains(hold.entryId());
        });
        assertThat(entries).filteredOn(entry -> entry.accountId().equals(wallet)).singleElement()
                .satisfies(entry -> assertThat(entry.amount()).isEqualTo(amount));
        assertThat(entries.stream().map(EntryView::amount).reduce(pln(0), Money::add)).isEqualTo(pln(0));
    }

    private void assertBalances(int available, int onHold, int settled) {
        assertThat(accounting.balance(wallet)).hasValue(pln(available));
        assertThat(accounting.balance(held)).hasValue(pln(onHold));
        assertThat(accounting.balance(merchant)).hasValue(pln(settled));
    }

    private AccountId createAccount(String name, String type) {
        var result = accounting.createAccount(CreateAccount.generate(name, type));
        assertThat(result.success()).isTrue();
        return result.getSuccess();
    }
}
