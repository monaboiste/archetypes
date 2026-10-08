package com.softwarearchetypes.accounting;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.softwarearchetypes.quantity.money.Money;

import static com.softwarearchetypes.quantity.money.Money.pln;
import static java.time.Clock.fixed;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundsHoldScenarios {

    private static final Instant FUNDED_AT = Instant.parse("2026-10-08T08:00:00Z");
    private static final Instant AUTHORIZED_AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final Instant APPLIES_AT = Instant.parse("2026-10-08T10:00:01Z");
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private final AccountingFacade accounting = AccountingConfiguration.inMemory(fixed(NOW, UTC)).facade();
    private final AccountId funding = createAccount("BANK:FUNDING", "ASSET");
    // Semantic Accounts (L07): held value is separate from spendable value, not a mutable hold flag.
    private final AccountId wallet = createAccount("CUSTOMER:123:WALLET", "LIABILITY");
    private final AccountId held = createAccount("CUSTOMER:123:HELD", "LIABILITY");
    private final AccountId merchant = createAccount("MERCHANT:456:SETTLEMENT", "LIABILITY");

    @BeforeEach
    void fundWallet() {
        assertThat(accounting.transfer(funding, wallet, pln("1000.00"), FUNDED_AT, FUNDED_AT).success()).isTrue();
    }

    @Test
    void hold_moves_funds_out_of_available_balance_without_settling() {
        placeHold(wallet, held, pln("125.50"), context("123", "auth-1", "Hotel authorization"));

        assertThat(accounting.balance(wallet)).hasValue(pln("874.50"));
        assertThat(accounting.balance(held)).hasValue(pln("125.50"));
        assertThat(accounting.balance(wallet).orElseThrow().add(accounting.balance(held).orElseThrow()))
                .isEqualTo(pln("1000.00"));
        assertThat(accounting.balance(merchant)).hasValue(pln(0));
        assertThat(accounting.findAccount(merchant).orElseThrow().entries()).isEmpty();
        assertThat(accounting.balanceAsOf(wallet, APPLIES_AT.minusNanos(1))).hasValue(pln("1000.00"));
        assertThat(accounting.balanceAsOf(held, APPLIES_AT.minusNanos(1))).hasValue(pln(0));
        assertThat(accounting.balanceAsOf(wallet, APPLIES_AT)).hasValue(pln("874.50"));
        assertThat(accounting.balanceAsOf(held, APPLIES_AT)).hasValue(pln("125.50"));
    }

    @Test
    void ledger_explains_who_how_much_and_why_using_the_hold_transaction_identity() {
        Map<String, String> metadata = context("123", "auth-1", "Hotel authorization");

        TransactionId holdId = placeHold(wallet, held, pln("125.50"), metadata);

        TransactionView transaction = accounting.findTransactionBy(holdId).orElseThrow();
        assertThat(transaction.id()).isEqualTo(holdId);
        assertThat(transaction.type()).isEqualTo(TransactionType.of("HOLD_PLACED"));
        assertThat(transaction.occurredAt()).isEqualTo(AUTHORIZED_AT);
        assertThat(transaction.appliesAt()).isEqualTo(APPLIES_AT);
        assertThat(transaction.refId()).isNull();
        List<EntryView> entries = entriesOf(holdId);
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(EntryView::entryId).doesNotHaveDuplicates();
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.transactionId()).isEqualTo(holdId);
            assertThat(entry.metadata().metadata()).isEqualTo(metadata);
            assertThat(entry.occurredAt()).isEqualTo(AUTHORIZED_AT);
            assertThat(entry.appliesAt()).isEqualTo(APPLIES_AT);
            assertThat(accounting.findAccount(entry.accountId()).orElseThrow().entries()).contains(entry);
        });
        assertThat(entries).filteredOn(entry -> entry.accountId().equals(wallet)).singleElement().satisfies(entry -> {
            assertThat(entry.type()).isEqualTo(EntryView.EntryType.DEBIT);
            assertThat(entry.amount()).isEqualTo(pln("-125.50"));
        });
        assertThat(entries).filteredOn(entry -> entry.accountId().equals(held)).singleElement().satisfies(entry -> {
            assertThat(entry.type()).isEqualTo(EntryView.EntryType.CREDIT);
            assertThat(entry.amount()).isEqualTo(pln("125.50"));
        });
        // Double-entry consistency (L09): signed postings conserve the customer's funds.
        assertThat(entries.stream().map(EntryView::amount).reduce(pln(0), Money::add)).isEqualTo(pln(0));
    }

    @Test
    void equal_amount_holds_remain_individually_identifiable_on_one_held_account() {
        TransactionId first = placeHold(wallet, held, pln(100), context("123", "auth-1", "Hotel"));
        TransactionId second = placeHold(wallet, held, pln(100), context("123", "auth-2", "Car rental"));

        assertThat(first).isNotEqualTo(second);
        assertThat(accounting.balance(wallet)).hasValue(pln(800));
        assertThat(accounting.balance(held)).hasValue(pln(200));
        assertThat(accounting.findAccount(held).orElseThrow().entries()).hasSize(2)
                .extracting(EntryView::transactionId).containsExactlyInAnyOrder(first, second);
        assertThat(entriesOf(first)).allSatisfy(entry ->
                assertThat(entry.metadata().metadata()).isEqualTo(context("123", "auth-1", "Hotel")));
        assertThat(entriesOf(second)).allSatisfy(entry ->
                assertThat(entry.metadata().metadata()).isEqualTo(context("123", "auth-2", "Car rental")));
    }

    @Test
    void holds_for_different_customers_have_separate_balances_and_context() {
        AccountId otherWallet = createAccount("CUSTOMER:789:WALLET", "LIABILITY");
        AccountId otherHeld = createAccount("CUSTOMER:789:HELD", "LIABILITY");
        assertThat(accounting.transfer(funding, otherWallet, pln(500), FUNDED_AT, FUNDED_AT).success()).isTrue();

        TransactionId first = placeHold(wallet, held, pln(100), context("123", "auth-1", "Hotel"));
        TransactionId second = placeHold(otherWallet, otherHeld, pln(200), context("789", "auth-2", "Car rental"));

        assertThat(accounting.balance(wallet)).hasValue(pln(900));
        assertThat(accounting.balance(held)).hasValue(pln(100));
        assertThat(accounting.balance(otherWallet)).hasValue(pln(300));
        assertThat(accounting.balance(otherHeld)).hasValue(pln(200));
        assertThat(entriesOf(first)).allSatisfy(entry -> {
            assertThat(entry.accountId()).isIn(wallet, held);
            assertThat(entry.metadata().metadata()).containsEntry("customerId", "123");
        });
        assertThat(entriesOf(second)).allSatisfy(entry -> {
            assertThat(entry.accountId()).isIn(otherWallet, otherHeld);
            assertThat(entry.metadata().metadata()).containsEntry("customerId", "789");
        });
    }

    @Test
    void caller_cannot_rewrite_a_recorded_holds_context() {
        Map<String, String> original = context("123", "auth-1", "Hotel authorization");
        Map<String, String> supplied = new HashMap<>(original);
        TransactionId holdId = placeHold(wallet, held, pln(100), supplied);

        supplied.put("reason", "Changed after posting");
        supplied.put("customerId", "someone-else");

        assertThat(entriesOf(holdId)).allSatisfy(entry -> assertThat(entry.metadata().metadata()).isEqualTo(original));
        assertThat(accounting.findAccount(held).orElseThrow().entries()).singleElement().satisfies(entry ->
                assertThat(entry.metadata().metadata()).isEqualTo(original));
    }

    @Test
    void ledger_reader_cannot_rewrite_a_recorded_holds_context() {
        Map<String, String> metadata = context("123", "auth-1", "Hotel authorization");
        TransactionId holdId = placeHold(wallet, held, pln(100), metadata);
        EntryView heldEntry = accounting.findAccount(held).orElseThrow().entries().getFirst();

        assertThatThrownBy(() -> heldEntry.metadata().metadata().put("reason", "Changed by reader"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(entriesOf(holdId)).allSatisfy(entry -> assertThat(entry.metadata().metadata()).isEqualTo(metadata));
    }

    @Test
    void unbalanced_hold_is_rejected_without_posting_any_entries() {
        TransactionId holdId = TransactionId.generate();

        assertThatThrownBy(() -> accounting.transaction()
                .id(holdId)
                .occurredAt(AUTHORIZED_AT)
                .appliesAt(APPLIES_AT)
                .withTypeOf("HOLD_PLACED")
                .withMetadata(MetaData.of(context("123", "auth-1", "Hotel authorization")))
                .executing()
                .debitFrom(wallet, pln(100))
                .creditTo(held, pln(90))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Entry balance within transaction must always be 0");

        assertThat(accounting.findTransactionBy(holdId)).isEmpty();
        assertThat(accounting.balance(wallet)).hasValue(pln(1000));
        assertThat(accounting.balance(held)).hasValue(pln(0));
        assertThat(accounting.findAccount(wallet).orElseThrow().entries()).hasSize(1);
        assertThat(accounting.findAccount(held).orElseThrow().entries()).isEmpty();
    }

    private TransactionId placeHold(AccountId wallet, AccountId held, Money amount, Map<String, String> metadata) {
        // Command + Builder/Factory DSL (L04): a hold is an ordinary balanced transaction, not a new entity.
        Transaction hold = accounting.transaction()
                .occurredAt(AUTHORIZED_AT)
                .appliesAt(APPLIES_AT)
                .withTypeOf("HOLD_PLACED")
                // Tagged Entries (L03/L07): preserve business context on both sides of the transaction.
                .withMetadata(MetaData.of(metadata))
                .executing()
                .debitFrom(wallet, amount)
                .creditTo(held, amount)
                .build();
        var result = accounting.execute(hold);
        assertThat(result.success()).isTrue();
        return result.getSuccess();
    }

    private List<EntryView> entriesOf(TransactionId id) {
        return accounting.findTransactionBy(id).orElseThrow().entries().stream()
                .flatMap(accountEntries -> accountEntries.entries().stream())
                .toList();
    }

    private Map<String, String> context(String customerId, String authorizationId, String reason) {
        return Map.of("customerId", customerId, "authorizationId", authorizationId, "reason", reason);
    }

    private AccountId createAccount(String name, String type) {
        var result = accounting.createAccount(CreateAccount.generate(name, type));
        assertThat(result.success()).isTrue();
        return result.getSuccess();
    }
}
