package com.softwarearchetypes.accounting;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.softwarearchetypes.quantity.money.Money;

import static com.softwarearchetypes.quantity.money.Money.pln;
import static java.time.Clock.fixed;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundsHoldSettlementScenarios {

    private static final Instant FUNDED_AT = Instant.parse("2026-10-08T08:00:00Z");
    private static final Instant HELD_AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final Instant SETTLED_AT = Instant.parse("2026-10-08T11:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private final AccountingFacade accounting = AccountingConfiguration.inMemory(fixed(NOW, UTC)).facade();
    private final AccountId funding = createAccount("BANK:FUNDING", "ASSET");
    private final AccountId wallet = createAccount("CUSTOMER:123:WALLET", "LIABILITY");
    private final AccountId held = createAccount("CUSTOMER:123:HELD", "LIABILITY");
    private final AccountId merchant = createAccount("MERCHANT:456:SETTLEMENT", "LIABILITY");
    private EntryView hold;

    @BeforeEach
    void fundWalletAndPlaceHold() {
        assertThat(accounting.transfer(funding, wallet, pln(1000), FUNDED_AT, FUNDED_AT).success()).isTrue();
        hold = placeHold(pln(100), "auth-1");
    }

    @Test
    void full_settlement_is_linked_to_the_hold_in_public_ledger_reads() {
        TransactionView original = accounting.findTransactionBy(hold.transactionId()).orElseThrow();

        Transaction settlement = settlement(pln(100));
        assertThat(accounting.execute(settlement).success()).isTrue();

        assertBalances(900, 0, 100);
        TransactionView settled = accounting.findTransactionBy(settlement.id()).orElseThrow();
        assertThat(settled.type()).isEqualTo(TransactionType.of("HOLD_SETTLED"));
        assertThat(settled.occurredAt()).isEqualTo(SETTLED_AT);
        assertThat(settled.appliesAt()).isEqualTo(SETTLED_AT);
        assertThat(settled.id()).isNotEqualTo(original.id());
        assertThat(entriesOf(settlement.id())).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.transactionId()).isEqualTo(settlement.id());
            assertThat(entry.metadata().metadata())
                    .containsEntry("holdTransactionId", hold.transactionId().value().toString())
                    .containsEntry("authorizationId", "auth-1")
                    .containsEntry("customerId", "123");
            assertThat(accounting.findAccount(entry.accountId()).orElseThrow().entries()).contains(entry);
        });
        assertThat(entriesOf(settlement.id())).filteredOn(entry -> entry.accountId().equals(held))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.type()).isEqualTo(EntryView.EntryType.DEBIT);
                    assertThat(entry.amount()).isEqualTo(pln(-100));
                    assertThat(entry.appliedTo()).contains(hold.entryId());
                });
        assertThat(entriesOf(settlement.id())).filteredOn(entry -> entry.accountId().equals(merchant))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.type()).isEqualTo(EntryView.EntryType.CREDIT);
                    assertThat(entry.amount()).isEqualTo(pln(100));
                    assertThat(entry.appliedTo()).isEmpty();
                });
        assertThat(entriesOf(settlement.id()).stream().map(EntryView::amount).reduce(pln(0), Money::add))
                .isEqualTo(pln(0));
        assertThat(accounting.findTransactionBy(hold.transactionId())).contains(original);
        assertThat(remaining(hold)).isEqualTo(pln(0));
    }

    @Test
    void partial_settlements_leave_an_auditable_remainder_without_debiting_wallet_again() {
        Transaction first = settlement(pln(30));
        assertThat(accounting.execute(first).success()).isTrue();
        Transaction second = settlementBuilder(hold, SETTLED_AT.plusSeconds(60))
                .debitFrom(held, pln(20), hold.entryId())
                .creditTo(merchant, pln(20))
                .build();

        assertThat(accounting.execute(second).success()).isTrue();

        assertBalances(900, 50, 50);
        assertThat(remaining(hold)).isEqualTo(pln(50));
        assertThat(accounting.findAccount(held).orElseThrow().entries()).hasSize(3).contains(hold);
        assertThat(accounting.findAccount(held).orElseThrow().entries())
                .filteredOn(entry -> entry.appliedTo().filter(hold.entryId()::equals).isPresent())
                .extracting(EntryView::transactionId).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(accounting.balanceAsOf(held, SETTLED_AT.minusNanos(1))).contains(pln(100));
        assertThat(accounting.balanceAsOf(held, SETTLED_AT)).contains(pln(70));
    }

    @Test
    void subsequent_settlement_can_consume_exactly_the_remaining_hold() {
        assertThat(accounting.execute(settlement(pln(40))).success()).isTrue();

        assertThat(accounting.execute(settlement(pln(60))).success()).isTrue();

        assertBalances(900, 0, 100);
        assertThat(remaining(hold)).isEqualTo(pln(0));
    }

    @Test
    void settlement_uses_only_the_selected_hold_not_other_funds_on_the_same_account() {
        EntryView secondHold = placeHold(pln(200), "auth-2");

        Transaction secondSettlement = settlementBuilder(secondHold, SETTLED_AT)
                .debitFrom(held, pln(150), secondHold.entryId())
                .creditTo(merchant, pln(150))
                .build();
        assertThat(accounting.execute(secondSettlement).success()).isTrue();

        assertBalances(700, 150, 150);
        assertThat(remaining(hold)).isEqualTo(pln(100));
        assertThat(remaining(secondHold)).isEqualTo(pln(50));
        assertRejectedWithoutLedgerChanges(() -> settlement(pln(101)));
    }

    @Test
    void settlement_cannot_exceed_the_remainder_after_partial_settlement() {
        assertThat(accounting.execute(settlement(pln(80))).success()).isTrue();

        assertRejectedWithoutLedgerChanges(() -> settlement(pln(21)));

        assertBalances(900, 20, 80);
        assertThat(remaining(hold)).isEqualTo(pln(20));
    }

    @Test
    void fully_consumed_hold_cannot_be_settled_again_even_when_another_hold_has_funds() {
        placeHold(pln(200), "auth-2");
        assertThat(accounting.execute(settlement(pln(100))).success()).isTrue();

        assertRejectedWithoutLedgerChanges(() -> settlement(pln(1)));

        assertBalances(700, 200, 100);
    }

    @Test
    void settlement_cannot_allocate_a_different_customers_credit() {
        AccountId otherWallet = createAccount("CUSTOMER:789:WALLET", "LIABILITY");
        AccountId otherHeld = createAccount("CUSTOMER:789:HELD", "LIABILITY");
        assertThat(accounting.transfer(funding, otherWallet, pln(100), FUNDED_AT, FUNDED_AT).success()).isTrue();
        assertThat(accounting.transfer(otherWallet, otherHeld, pln(100), HELD_AT, HELD_AT).success()).isTrue();
        EntryView otherCredit = accounting.findAccount(otherHeld).orElseThrow().entries().getFirst();

        assertRejectedWithoutLedgerChanges(() -> settlementBuilder(hold, SETTLED_AT)
                .debitFrom(held, pln(50), otherCredit.entryId())
                .creditTo(merchant, pln(50))
                .build());
    }

    @Test
    void settlement_must_reference_a_credit_not_the_wallet_debit_of_the_hold() {
        EntryView walletDebit = entriesOf(hold.transactionId()).stream()
                .filter(entry -> entry.accountId().equals(wallet)).findFirst().orElseThrow();

        assertRejectedWithoutLedgerChanges(() -> settlementBuilder(hold, SETTLED_AT)
                .debitFrom(wallet, pln(50), walletDebit.entryId())
                .creditTo(merchant, pln(50))
                .build());
    }

    @Test
    void settlement_cannot_reference_a_missing_entry() {
        assertRejectedWithoutLedgerChanges(() -> settlementBuilder(hold, SETTLED_AT)
                .debitFrom(held, pln(50), EntryId.generate())
                .creditTo(merchant, pln(50))
                .build());
    }

    @Test
    void settlement_cannot_apply_before_the_hold_becomes_effective() {
        assertRejectedWithoutLedgerChanges(() -> settlementBuilder(hold, HELD_AT.minusNanos(1))
                .debitFrom(held, pln(50), hold.entryId())
                .creditTo(merchant, pln(50))
                .build());
    }

    @Test
    void allocated_settlement_requires_a_positive_amount() {
        assertRejectedWithoutLedgerChanges(() -> settlement(pln(0)));
        assertRejectedWithoutLedgerChanges(() -> settlement(pln(-1)));
    }

    @Test
    void all_debits_allocated_to_one_hold_in_a_transaction_share_its_remainder() {
        assertRejectedWithoutLedgerChanges(() -> settlementBuilder(hold, SETTLED_AT)
                .debitFrom(held, pln(60), hold.entryId())
                .debitFrom(held, pln(60), hold.entryId())
                .creditTo(merchant, pln(120))
                .build());
    }

    @Test
    void multiple_debits_can_consume_exactly_one_hold_in_a_transaction() {
        Transaction settlement = settlementBuilder(hold, SETTLED_AT)
                .debitFrom(held, pln(40), hold.entryId())
                .debitFrom(held, pln(60), hold.entryId())
                .creditTo(merchant, pln(100))
                .build();

        assertThat(accounting.execute(settlement).success()).isTrue();

        assertBalances(900, 0, 100);
        assertThat(remaining(hold)).isEqualTo(pln(0));
    }

    @Test
    void execution_rechecks_the_remainder_when_two_settlements_were_built_before_either_executed() {
        Transaction first = settlement(pln(80));
        Transaction stale = settlement(pln(30));
        assertThat(accounting.execute(first).success()).isTrue();
        List<AccountView> before = accounting.findAll();

        var result = accounting.execute(stale);

        assertThat(result.failure()).isTrue();
        assertThat(result.getFailure()).contains("allocation");
        assertThat(accounting.findTransactionBy(stale.id())).isEmpty();
        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
        assertBalances(900, 20, 80);
    }

    private Transaction settlement(Money amount) {
        return settlementBuilder(hold, SETTLED_AT)
                // Manual Allocation (L06): consumption belongs to this credit, not to the pooled held balance.
                .debitFrom(held, amount, hold.entryId())
                .creditTo(merchant, amount)
                .build();
    }

    private TransactionBuilder.TransactionEntriesBuilder settlementBuilder(EntryView source, Instant appliesAt) {
        // Command + Builder DSL (L04): each partial settlement is a new balanced ledger operation.
        return accounting.transaction()
                .occurredAt(SETTLED_AT)
                .appliesAt(appliesAt)
                .withTypeOf("HOLD_SETTLED")
                .withMetadata("customerId", "123",
                        "authorizationId", source.metadata().metadata().get("authorizationId"),
                        "holdTransactionId", source.transactionId().value().toString(),
                        "reason", "Merchant settlement")
                .executing();
    }

    private EntryView placeHold(Money amount, String authorizationId) {
        Transaction transaction = accounting.transaction()
                .occurredAt(HELD_AT)
                .appliesAt(HELD_AT)
                .withTypeOf("HOLD_PLACED")
                .withMetadata("customerId", "123", "authorizationId", authorizationId, "reason", "Hotel authorization")
                .executing()
                .debitFrom(wallet, amount)
                .creditTo(held, amount)
                .build();
        assertThat(accounting.execute(transaction).success()).isTrue();
        return entriesOf(transaction.id()).stream()
                .filter(entry -> entry.accountId().equals(held)).findFirst().orElseThrow();
    }

    private Money remaining(EntryView source) {
        // Read model (L08): reconstruct the remainder from original facts without rewriting the hold.
        return accounting.findAccount(source.accountId()).orElseThrow().entries().stream()
                .filter(entry -> entry.appliedTo().filter(source.entryId()::equals).isPresent())
                .map(EntryView::amount)
                .reduce(source.amount(), Money::add);
    }

    private List<EntryView> entriesOf(TransactionId id) {
        return accounting.findTransactionBy(id).orElseThrow().entries().stream()
                .flatMap(accountEntries -> accountEntries.entries().stream()).toList();
    }

    private void assertRejectedWithoutLedgerChanges(Runnable operation) {
        List<AccountView> before = accounting.findAll();
        assertThatThrownBy(operation::run).isInstanceOf(IllegalArgumentException.class);
        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
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
